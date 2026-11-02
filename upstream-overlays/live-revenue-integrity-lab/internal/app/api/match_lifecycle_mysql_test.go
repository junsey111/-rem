package apiserver

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"sync"
	"testing"
	"time"

	"github.com/go-sql-driver/mysql"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"github.com/pressly/goose/v3"
)

func TestMatchLifecycleMySQL(t *testing.T) {
	dsn := os.Getenv("MYSQL_TEST_DSN")
	if dsn == "" {
		t.Skip("MYSQL_TEST_DSN required; creates and drops an isolated database")
	}
	config, err := mysql.ParseDSN(dsn)
	if err != nil {
		t.Fatal(err)
	}
	config.DBName, config.ParseTime, config.MultiStatements = "", true, true
	admin, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		t.Fatal(err)
	}
	defer admin.Close()
	name := fmt.Sprintf("live_test_pk_%d", time.Now().UnixNano())
	if _, err := admin.Exec("CREATE DATABASE `" + name + "`"); err != nil {
		t.Fatal(err)
	}
	defer func() {
		if _, err := admin.Exec("DROP DATABASE `" + name + "`"); err != nil {
			t.Error(err)
		}
	}()
	config.DBName = name
	db, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	_, file, _, _ := runtime.Caller(0)
	provider, err := goose.NewProvider(goose.DialectMySQL, db, os.DirFS(filepath.Join(filepath.Dir(file), "../../../migrations")), goose.WithExcludeNames([]string{"00002_seed.sql"}))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := provider.Up(context.Background()); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(`
		INSERT INTO users(user_id,age_years,region_code,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES
		(1,25,'US','GOOD',TRUE,TRUE,'NORMAL'),(2,25,'US','GOOD',TRUE,TRUE,'NORMAL'),(3,25,'US','GOOD',TRUE,TRUE,'NORMAL'),(4,25,'US','GOOD',TRUE,TRUE,'NORMAL');
		INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES (11,1,'OPEN',UTC_TIMESTAMP(6)),(12,1,'OPEN',UTC_TIMESTAMP(6)),(22,2,'OPEN',UTC_TIMESTAMP(6)),(33,3,'OPEN',UTC_TIMESTAMP(6));
		INSERT INTO wallet_accounts(user_id,currency,available_balance) VALUES (4,'COIN',1000);
	`); err != nil {
		t.Fatal(err)
	}
	s := New("", db, nil, "pk-test-secret", "", "internal-test-secret", testAccountAuthority(t, "pk-test-secret"))
	ts := httptest.NewServer(s.httpServer.Handler)
	defer ts.Close()
	type response struct {
		status int
		body   []byte
		err    error
	}
	call := func(user uint64, method, path, body string) response {
		header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"HS256"}`))
		payload := base64.RawURLEncoding.EncodeToString([]byte(fmt.Sprintf(`{"userInfo":{"id":%d},"exp":%d,"iat":%d}`, user, time.Now().Unix()+60, time.Now().Unix())))
		mac := hmac.New(sha256.New, []byte("pk-test-secret"))
		mac.Write([]byte(header + "." + payload))
		req, err := http.NewRequest(method, ts.URL+path, bytes.NewBufferString(body))
		if err != nil {
			return response{err: err}
		}
		req.Header.Set("Authorization", "Bearer "+header+"."+payload+"."+base64.RawURLEncoding.EncodeToString(mac.Sum(nil)))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("X-Internal-Key", "internal-test-secret")
		res, err := ts.Client().Do(req)
		if err != nil {
			return response{err: err}
		}
		defer res.Body.Close()
		raw, err := io.ReadAll(res.Body)
		return response{res.StatusCode, raw, err}
	}
	check := func(r response, expected int) map[string]any {
		t.Helper()
		if r.err != nil || r.status != expected {
			t.Fatalf("expected %d, got %d: %s (%v)", expected, r.status, r.body, r.err)
		}
		var data map[string]any
		if err := json.Unmarshal(r.body, &data); err != nil {
			t.Fatal(err)
		}
		return data
	}
	// Legacy values without provenance must never appear as supplied profile data.
	profilePath := "/v1/users/me/profile"
	data := check(call(1, "GET", profilePath, ""), 200)
	if data["age_years"] != nil || data["region_code"] != nil || data["source"] != "UNKNOWN" {
		t.Fatalf("legacy defaults exposed: %v", data)
	}
	data = check(call(99, "GET", profilePath, ""), 200)
	if data["age_years"] != nil || data["region_code"] != nil {
		t.Fatal("new account received invented demographics")
	}
	var age sql.NullInt64
	var region sql.NullString
	if err := db.QueryRow(`SELECT age_years,region_code FROM users WHERE user_id=99`).Scan(&age, &region); err != nil || age.Valid || region.Valid {
		t.Fatalf("invented defaults persisted: %v %v %v", age, region, err)
	}
	// Open-live policy is enforced on the server, including direct internal calls.
	livePath := "/v1/internal/lives"
	liveBody := `{"live_session_id":999,"creator_id":99}`
	check(call(99, "POST", livePath, liveBody), 403)
	for _, profile := range []string{`{"age_years":17,"region_code":"US"}`, `{"age_years":18,"region_code":"KR"}`} {
		check(call(99, "PUT", profilePath, profile), 200)
		check(call(99, "POST", livePath, liveBody), 403)
	}
	check(call(99, "PUT", profilePath, `{"age_years":19,"region_code":"KR"}`), 200)
	check(call(99, "POST", livePath, liveBody), 201)
	check(call(1, "PUT", profilePath, `{"age_years":25,"region_code":"ZZ"}`), 400)
	check(call(1, "PUT", profilePath, `{"age_years":0,"region_code":"US"}`), 400)
	check(call(1, "PUT", profilePath, `{"age_years":25,"region_code":"United States"}`), 400)
	for _, user := range []uint64{1, 2, 3} {
		check(call(user, "PUT", profilePath, `{"age_years":25,"region_code":"us","can_go_live":true,"account_standing":"GOOD"}`), 200)
	}
	if _, err := db.Exec(`UPDATE users SET can_go_live=FALSE,live_gifts_enabled=FALSE,account_standing='RESTRICTED' WHERE user_id=1`); err != nil {
		t.Fatal(err)
	}
	check(call(1, "POST", "/v1/internal/users", `{"user_id":1,"age_years":18,"region_code":"KR","can_go_live":true,"live_gifts_enabled":true}`), 201)
	check(call(1, "PUT", profilePath, `{"age_years":26,"region_code":"US","can_go_live":true}`), 200)
	var liveEnabled, giftEnabled bool
	var standing string
	if err := db.QueryRow(`SELECT can_go_live,live_gifts_enabled,account_standing FROM users WHERE user_id=1`).Scan(&liveEnabled, &giftEnabled, &standing); err != nil || liveEnabled || giftEnabled || standing != "RESTRICTED" {
		t.Fatalf("profile/sync changed permissions: %v %v %s %v", liveEnabled, giftEnabled, standing, err)
	}
	check(call(1, "POST", livePath, `{"live_session_id":998,"creator_id":1}`), 403)
	if _, err := db.Exec(`UPDATE users SET can_go_live=TRUE,live_gifts_enabled=TRUE,account_standing='GOOD' WHERE user_id=1`); err != nil {
		t.Fatal(err)
	}
	invite := func(own, other int) uint64 {
		t.Helper()
		data := check(call(1, "POST", "/v1/match-invitations", fmt.Sprintf(`{"live_session_id":%d,"opponent_live_session_id":%d}`, own, other)), 201)
		return uint64(data["invitation_id"].(float64))
	}
	a, b := invite(11, 22), invite(12, 33)
	path := func(id uint64) string { return fmt.Sprintf("/v1/match-invitations/%d/respond", id) }
	check(call(4, "POST", path(a), `{"accept":true}`), 403)
	check(call(2, "POST", path(a), `{}`), 400)
	// Different live sessions of the same creator must still exclude one another.
	var results [2]response
	var wg sync.WaitGroup
	for i, task := range []struct{ user, id uint64 }{{2, a}, {3, b}} {
		wg.Add(1)
		go func(i int, user, id uint64) {
			defer wg.Done()
			results[i] = call(user, "POST", path(id), `{"accept":true}`)
		}(i, task.user, task.id)
	}
	wg.Wait()
	winner, wins := 0, 0
	for i, r := range results {
		if r.status == 200 {
			winner = i
			wins++
		} else {
			check(r, 409)
		}
	}
	if wins != 1 {
		t.Fatalf("concurrent accepts produced %d matches: %+v", wins, results)
	}
	matchID, ownLive, otherLive, opponent := a, uint64(11), uint64(22), uint64(2)
	if winner == 1 {
		matchID, ownLive, otherLive, opponent = b, 12, 33, 3
	}
	check(call(opponent, "POST", path(matchID), `{"accept":true}`), 409)
	for _, live := range []uint64{ownLive, otherLive} {
		data := check(call(4, "GET", fmt.Sprintf("/v1/lives/%d/match", live), ""), 200)
		if uint64(data["match_id"].(float64)) != matchID {
			t.Fatal("observer cannot discover the active match")
		}
	}
	scorePath := fmt.Sprintf("/v1/matches/%d/score", matchID)
	data = check(call(4, "GET", scorePath, ""), 200)
	if len(data["scores"].([]any)) != 2 {
		t.Fatal("score must include both real participants before gifts")
	}
	check(call(4, "GET", fmt.Sprintf("/v1/matches/%d", matchID), ""), 200)
	closePath := fmt.Sprintf("/v1/matches/%d/close", matchID)
	check(call(4, "POST", closePath, `{}`), 403)
	svc := service.New(db)
	for _, side := range []struct{ user, live uint64 }{{1, ownLive}, {opponent, otherLive}} {
		gift, appErr, err := svc.SendGift(context.Background(), service.GiftRequest{RequestID: fmt.Sprintf("side-%d", side.user), ViewerID: 4, CreatorID: side.user, LiveSessionID: side.live, MatchID: &matchID, GiftID: "HEART", Quantity: 2})
		if err != nil || appErr != nil || !gift.Accepted || gift.MatchPointsAdded != 10 {
			t.Fatalf("gift: %+v %v %v", gift, appErr, err)
		}
	}
	check(call(opponent, "POST", closePath, `{}`), 200)
	check(call(1, "POST", closePath, `{}`), 200)
	data = check(call(4, "GET", scorePath, ""), 200)
	for _, raw := range data["scores"].([]any) {
		if raw.(map[string]any)["points"].(float64) != 10 {
			t.Fatal("final scores changed")
		}
	}
	_, appErr, err := svc.SendGift(context.Background(), service.GiftRequest{RequestID: "late-gift", ViewerID: 4, CreatorID: 1, LiveSessionID: ownLive, MatchID: &matchID, GiftID: "HEART", Quantity: 1})
	if err != nil || appErr == nil || appErr.Code != "MATCH_CLOSED" {
		t.Fatalf("closed match gift: %v %v", appErr, err)
	}
	var balance int
	if err := db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=4`).Scan(&balance); err != nil || balance != 980 {
		t.Fatalf("unexpected balance %d %v", balance, err)
	}
	if check(call(4, "GET", fmt.Sprintf("/v1/lives/%d/match", ownLive), ""), 200)["match_id"].(float64) != 0 {
		t.Fatal("closed match still discoverable as active")
	}
	// Either participant going offline must close the shared PK atomically.
	next := invite(11, 22)
	check(call(2, "POST", path(next), `{"accept":true}`), 200)
	check(call(2, "POST", "/v1/lives/22/close", `{}`), 202)
	data = check(call(4, "GET", fmt.Sprintf("/v1/matches/%d", next), ""), 200)
	if data["status"] != "CLOSED" {
		t.Fatal("host closed but PK remains open")
	}
	next = invite(11, 33)
	check(call(1, "POST", "/v1/lives/11/close", `{}`), 202)
	check(call(3, "POST", path(next), `{"accept":true}`), 409)
}
