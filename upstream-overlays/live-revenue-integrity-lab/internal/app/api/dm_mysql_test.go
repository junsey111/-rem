package apiserver

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
	"unicode/utf8"
)

func TestDmGiftAndVideoUnlockHTTP(t *testing.T) {
	db := newOperationsTestDB(t)
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES
		(11,25,'US','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL'),
		(12,25,'US','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL'),
		(13,16,'US','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL'),
		(14,25,'US','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL'),
		(15,25,'US','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL'),
		(16,25,'US','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL')`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(`INSERT INTO wallet_accounts(user_id,currency,available_balance) VALUES (11,'COIN',100),(15,'COIN',0),(16,'COIN',0)`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(`INSERT INTO gift_catalog(gift_id,display_name,coin_price,match_points,diamond_reward,enabled) VALUES ('LONGNAME',REPEAT('N',50),1,1,1,TRUE),('OFFGIFT','Off',1,1,1,FALSE)`); err != nil {
		t.Fatal(err)
	}
	const secret = "dm-test-secret"
	srv := New("127.0.0.1:0", db, nil, secret, "pay-secret", "internal-test", testAccountAuthority(t, secret))
	ts := httptest.NewServer(srv.httpServer.Handler)
	t.Cleanup(ts.Close)

	call := func(user uint64, method, path, body string) (int, map[string]any) {
		t.Helper()
		header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"HS256","typ":"JWT"}`))
		payload := base64.RawURLEncoding.EncodeToString([]byte(fmt.Sprintf(`{"userInfo":{"id":%d},"exp":%d,"iat":%d}`, user, time.Now().Unix()+120, time.Now().Unix())))
		mac := hmac.New(sha256.New, []byte(secret))
		_, _ = mac.Write([]byte(header + "." + payload))
		token := header + "." + payload + "." + base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
		req, err := http.NewRequest(method, ts.URL+path, bytes.NewBufferString(body))
		if err != nil {
			t.Fatal(err)
		}
		if user != 0 {
			req.Header.Set("Authorization", "Bearer "+token)
		}
		req.Header.Set("Content-Type", "application/json")
		res, err := ts.Client().Do(req)
		if err != nil {
			t.Fatal(err)
		}
		defer res.Body.Close()
		raw, err := io.ReadAll(res.Body)
		if err != nil {
			t.Fatal(err)
		}
		parsed := map[string]any{}
		if len(bytes.TrimSpace(raw)) > 0 && json.Unmarshal(raw, &parsed) != nil {
			t.Fatalf("%s %s: %d not json: %s", method, path, res.StatusCode, raw)
		}
		return res.StatusCode, parsed
	}
	coins := func(user int) int64 {
		t.Helper()
		var balance int64
		if err := db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=? AND currency='COIN'`, user).Scan(&balance); err != nil {
			t.Fatal(err)
		}
		return balance
	}
	debits := func(user int, requestID string) int {
		t.Helper()
		var n int
		if err := db.QueryRow(`SELECT COUNT(*) FROM wallet_transactions WHERE actor_user_id=? AND request_id=? AND tx_type IN ('DM_GIFT_DEBIT','DM_VIDEO_UNLOCK')`, user, requestID).Scan(&n); err != nil {
			t.Fatal(err)
		}
		return n
	}
	mustCode := func(status int, body map[string]any, want int, code string) {
		t.Helper()
		if status != want || body["code"] != code {
			t.Fatalf("got %d %v, want %d %s", status, body, want, code)
		}
		if _, wrapped := body["data"]; wrapped {
			t.Fatalf("error wrapped in data: %v", body)
		}
	}

	for _, path := range []string{"/v1/dm/gifts", "/v1/dm/video-unlocks"} {
		status, body := call(0, http.MethodPost, path, `{}`)
		mustCode(status, body, http.StatusUnauthorized, "UNAUTHORIZED")
	}
	status, body := call(0, http.MethodGet, "/v1/dm/gifts/10000000-0000-4000-8000-000000000001", "")
	mustCode(status, body, http.StatusUnauthorized, "UNAUTHORIZED")
	status, body = call(0, http.MethodGet, "/v1/dm/videos/video-1/access", "")
	mustCode(status, body, http.StatusUnauthorized, "UNAUTHORIZED")
	status, _ = call(11, http.MethodPost, "/v1/gifts", `{}`)
	if status == http.StatusNotFound {
		t.Fatal("live gift route missing")
	}

	const giftID = "10000000-0000-4000-8000-000000000001"
	giftBody := fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"recipient_id":12,"gift_id":"ROSE","quantity":1,"sent_at_ms":0}`, giftID)
	status, body = call(11, http.MethodPost, "/v1/dm/gifts", giftBody)
	if status != http.StatusCreated {
		t.Fatalf("gift create: %d %v", status, body)
	}
	if body["data"] != nil || body["accepted"] != true || body["status"] != "ACCEPTED" {
		t.Fatalf("receipt shape: %v", body)
	}
	orderID, _ := body["gift_order_id"].(string)
	if orderID == "" || body["gift_id"] != "ROSE" || body["quantity"] != float64(1) || body["request_id"] != giftID || body["charged_coins"] != float64(1) {
		t.Fatalf("gift echo: %v", body)
	}
	name, _ := body["gift_name"].(string)
	if name == "" || utf8.RuneCountInString(name) > 40 {
		t.Fatalf("gift_name %q", name)
	}
	if body["new_balance"] != float64(99) || coins(11) != 99 || debits(11, giftID) != 1 {
		t.Fatalf("balance=%d receipt=%v", coins(11), body)
	}
	var liveOrders int
	if err := db.QueryRow(`SELECT COUNT(*) FROM gift_orders`).Scan(&liveOrders); err != nil || liveOrders != 0 {
		t.Fatalf("live gift orders changed: %d %v", liveOrders, err)
	}

	status, again := call(11, http.MethodPost, "/v1/dm/gifts", giftBody)
	if status != http.StatusOK || again["idempotency"] != "replayed" || again["gift_order_id"] != orderID || again["charged_coins"] != float64(1) || coins(11) != 99 {
		t.Fatalf("replay: %d %v balance=%d", status, again, coins(11))
	}
	status, body = call(11, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"recipient_id":12,"gift_id":"ROSE","quantity":2,"sent_at_ms":0}`, giftID))
	mustCode(status, body, http.StatusConflict, "IDEMPOTENCY_PAYLOAD_MISMATCH")
	if coins(11) != 99 || debits(11, giftID) != 1 {
		t.Fatal("conflict debited")
	}
	status, body = call(11, http.MethodGet, "/v1/dm/gifts/"+giftID, "")
	if status != http.StatusOK || body["gift_order_id"] != orderID || body["accepted"] != true || body["charged_coins"] != float64(1) {
		t.Fatalf("read receipt: %d %v", status, body)
	}
	status, body = call(12, http.MethodGet, "/v1/dm/gifts/"+giftID, "")
	if status != http.StatusNotFound {
		t.Fatalf("other viewer read: %d %v", status, body)
	}
	status, body = call(11, http.MethodGet, "/v1/dm/gifts/10000000-0000-4000-8000-000000000099", "")
	if status != http.StatusNotFound {
		t.Fatalf("missing receipt: %d %v", status, body)
	}

	const poorGift = "10000000-0000-4000-8000-000000000002"
	status, body = call(15, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":15,"recipient_id":12,"gift_id":"ROSE","quantity":1,"sent_at_ms":0}`, poorGift))
	mustCode(status, body, http.StatusConflict, "INSUFFICIENT_BALANCE")
	if coins(15) != 0 || debits(15, poorGift) != 0 {
		t.Fatal("insufficient gift debited")
	}
	if _, err := db.Exec(`UPDATE wallet_accounts SET available_balance=20 WHERE user_id=15`); err != nil {
		t.Fatal(err)
	}
	status, body = call(15, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":15,"recipient_id":12,"gift_id":"ROSE","quantity":1,"sent_at_ms":0}`, poorGift))
	if status != http.StatusCreated || body["charged_coins"] != float64(1) || coins(15) != 19 || debits(15, poorGift) != 1 {
		t.Fatalf("retry after top-up: %d %v balance=%d", status, body, coins(15))
	}

	const selfID = "10000000-0000-4000-8000-000000000003"
	status, body = call(11, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"recipient_id":11,"gift_id":"ROSE","quantity":1,"sent_at_ms":0}`, selfID))
	mustCode(status, body, http.StatusBadRequest, "SELF_GIFT")
	if coins(11) != 99 || debits(11, selfID) != 0 {
		t.Fatal("self gift debited")
	}
	const missingGift = "10000000-0000-4000-8000-000000000004"
	status, body = call(11, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"recipient_id":12,"gift_id":"NOPE","quantity":1,"sent_at_ms":0}`, missingGift))
	mustCode(status, body, http.StatusNotFound, "GIFT_NOT_FOUND")
	status, body = call(11, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"recipient_id":12,"gift_id":"OFFGIFT","quantity":1,"sent_at_ms":0}`, "10000000-0000-4000-8000-000000000005"))
	mustCode(status, body, http.StatusNotFound, "GIFT_NOT_FOUND")
	if coins(11) != 99 {
		t.Fatal("missing gift debited")
	}
	const youngID = "10000000-0000-4000-8000-000000000006"
	status, body = call(11, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"recipient_id":13,"gift_id":"ROSE","quantity":1,"sent_at_ms":0}`, youngID))
	mustCode(status, body, http.StatusForbidden, "RECIPIENT_NOT_ELIGIBLE")
	status, body = call(11, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"recipient_id":13,"gift_id":"ROSE","quantity":1,"sent_at_ms":0}`, youngID))
	mustCode(status, body, http.StatusForbidden, "RECIPIENT_NOT_ELIGIBLE")
	status, body = call(11, http.MethodPost, "/v1/dm/gifts", fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"recipient_id":13,"gift_id":"HEART","quantity":1,"sent_at_ms":0}`, youngID))
	mustCode(status, body, http.StatusConflict, "IDEMPOTENCY_PAYLOAD_MISMATCH")
	if coins(11) != 99 || debits(11, youngID) != 0 {
		t.Fatal("ineligible recipient debited")
	}

	status, earn := call(12, http.MethodGet, "/v1/creators/me/earnings", "")
	if status != http.StatusOK || earn["diamonds"].(float64) < 2 {
		t.Fatalf("recipient earnings: %d %v", status, earn)
	}

	const unlockID = "20000000-0000-4000-8000-000000000001"
	unlockBody := fmt.Sprintf(`{"request_id":"%s","viewer_id":11,"video_id":"video-1","owner_id":14,"price_coins":10,"sent_at_ms":0}`, unlockID)
	status, body = call(11, http.MethodGet, "/v1/dm/videos/video-1/access", "")
	if status != http.StatusOK || body["unlocked"] != false {
		t.Fatalf("locked access: %d %v", status, body)
	}
	status, body = call(11, http.MethodPost, "/v1/dm/video-unlocks", unlockBody)
	if status != http.StatusCreated || body["accepted"] != true || body["unlocked"] != true || body["video_id"] != "video-1" || body["request_id"] != unlockID || body["charged_coins"] != float64(10) {
		t.Fatalf("unlock: %d %v", status, body)
	}
	if coins(11) != 89 || debits(11, unlockID) != 1 {
		t.Fatalf("unlock balance=%d", coins(11))
	}
	status, again = call(11, http.MethodPost, "/v1/dm/video-unlocks", unlockBody)
	if status != http.StatusOK || again["idempotency"] != "replayed" || again["charged_coins"] != float64(10) || coins(11) != 89 {
		t.Fatalf("unlock replay: %d %v balance=%d", status, again, coins(11))
	}
	status, body = call(11, http.MethodPost, "/v1/dm/video-unlocks", `{"request_id":"20000000-0000-4000-8000-000000000002","viewer_id":11,"video_id":"video-1","owner_id":14,"price_coins":10,"sent_at_ms":0}`)
	if status != http.StatusOK || body["idempotency"] != "replayed" || body["charged_coins"] != float64(0) || coins(11) != 89 || debits(11, "20000000-0000-4000-8000-000000000002") != 0 {
		t.Fatalf("second unlock charged: %d %v balance=%d", status, body, coins(11))
	}
	for _, user := range []uint64{11, 14} {
		status, body = call(user, http.MethodGet, "/v1/dm/videos/video-1/access", "")
		if status != http.StatusOK || body["unlocked"] != true || body["video_id"] != "video-1" {
			t.Fatalf("user %d access: %d %v", user, status, body)
		}
	}
	status, body = call(16, http.MethodGet, "/v1/dm/videos/video-1/access", "")
	if status != http.StatusOK || body["unlocked"] != false {
		t.Fatalf("stranger access: %d %v", status, body)
	}
	status, body = call(15, http.MethodPost, "/v1/dm/video-unlocks", `{"request_id":"20000000-0000-4000-8000-000000000003","viewer_id":15,"video_id":"video-1","owner_id":14,"price_coins":11,"sent_at_ms":0}`)
	mustCode(status, body, http.StatusConflict, "PRICE_MISMATCH")
	if coins(15) != 19 || debits(15, "20000000-0000-4000-8000-000000000003") != 0 {
		t.Fatal("price mismatch debited")
	}
	status, body = call(15, http.MethodPost, "/v1/dm/video-unlocks", `{"request_id":"20000000-0000-4000-8000-000000000003","viewer_id":15,"video_id":"video-1","owner_id":14,"price_coins":10,"sent_at_ms":0}`)
	if status != http.StatusCreated || body["charged_coins"] != float64(10) || coins(15) != 9 {
		t.Fatalf("priced unlock: %d %v balance=%d", status, body, coins(15))
	}
	status, body = call(15, http.MethodPost, "/v1/dm/video-unlocks", `{"request_id":"20000000-0000-4000-8000-000000000004","viewer_id":15,"video_id":"video-1","owner_id":99,"price_coins":10,"sent_at_ms":0}`)
	mustCode(status, body, http.StatusNotFound, "VIDEO_NOT_FOUND")

	status, body = call(16, http.MethodPost, "/v1/dm/video-unlocks", `{"request_id":"20000000-0000-4000-8000-000000000005","viewer_id":16,"video_id":"video-free","owner_id":14,"price_coins":0,"sent_at_ms":0}`)
	if status != http.StatusCreated || body["unlocked"] != true || body["charged_coins"] != float64(0) || coins(16) != 0 || debits(16, "20000000-0000-4000-8000-000000000005") != 0 {
		t.Fatalf("free unlock: %d %v balance=%d", status, body, coins(16))
	}
	status, body = call(16, http.MethodGet, "/v1/dm/videos/video-free/access", "")
	if status != http.StatusOK || body["unlocked"] != true {
		t.Fatalf("free access: %d %v", status, body)
	}
	const lateID = "20000000-0000-4000-8000-000000000006"
	status, body = call(16, http.MethodPost, "/v1/dm/video-unlocks", fmt.Sprintf(`{"request_id":"%s","viewer_id":16,"video_id":"video-late","owner_id":14,"price_coins":5,"sent_at_ms":0}`, lateID))
	mustCode(status, body, http.StatusConflict, "INSUFFICIENT_BALANCE")
	var quotes int
	if err := db.QueryRow(`SELECT COUNT(*) FROM dm_video_quotes WHERE video_id='video-late'`).Scan(&quotes); err != nil || quotes != 0 || coins(16) != 0 || debits(16, lateID) != 0 {
		t.Fatalf("late quote=%d balance=%d err=%v", quotes, coins(16), err)
	}
	if _, err := db.Exec(`UPDATE wallet_accounts SET available_balance=5 WHERE user_id=16`); err != nil {
		t.Fatal(err)
	}
	status, body = call(16, http.MethodPost, "/v1/dm/video-unlocks", fmt.Sprintf(`{"request_id":"%s","viewer_id":16,"video_id":"video-late","owner_id":14,"price_coins":5,"sent_at_ms":0}`, lateID))
	if status != http.StatusCreated || body["charged_coins"] != float64(5) || coins(16) != 0 {
		t.Fatalf("late unlock: %d %v balance=%d", status, body, coins(16))
	}
	status, body = call(16, http.MethodGet, "/v1/dm/videos/video-late/access", "")
	if status != http.StatusOK || body["unlocked"] != true {
		t.Fatalf("late access: %d %v", status, body)
	}

	status, body = call(11, http.MethodPost, "/v1/dm/gifts", `{"request_id":"10000000-0000-4000-8000-000000000007","viewer_id":11,"recipient_id":12,"gift_id":"LONGNAME","quantity":1,"sent_at_ms":0}`)
	longName, _ := body["gift_name"].(string)
	if status != http.StatusCreated || utf8.RuneCountInString(longName) != 40 || strings.TrimSpace(longName) == "" {
		t.Fatalf("long gift name: %d %q %v", status, longName, body)
	}

	var ledger int64
	if err := db.QueryRow(`SELECT COALESCE(SUM(amount),0) FROM wallet_entries`).Scan(&ledger); err != nil || ledger != 0 {
		t.Fatalf("ledger not balanced: %d %v", ledger, err)
	}
	var reserved int64
	if err := db.QueryRow(`
		SELECT COALESCE(SUM(CASE WHEN account_code='PLATFORM_RESERVED_COINS' THEN amount ELSE 0 END),0)
		FROM wallet_entries e JOIN wallet_transactions t ON t.tx_id=e.tx_id
		WHERE t.tx_type IN ('DM_GIFT_DEBIT','DM_VIDEO_UNLOCK')
	`).Scan(&reserved); err != nil || reserved <= 0 {
		t.Fatalf("platform reserve: %d %v", reserved, err)
	}
}
