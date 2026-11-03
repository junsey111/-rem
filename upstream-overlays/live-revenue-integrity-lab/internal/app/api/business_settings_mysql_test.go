package apiserver

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
)

func TestBusinessSettingsVersionReplayAndAudit(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	call := func(path, method, body string, want int) map[string]any {
		t.Helper()
		w := httptest.NewRecorder()
		s.handleBusinessSettings(w, httptest.NewRequest(method, "/v1/internal/business/"+path, strings.NewReader(body)))
		if w.Code != want {
			t.Fatalf("%s: %d %s", path, w.Code, w.Body.String())
		}
		var data map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &data); err != nil {
			t.Fatal(err)
		}
		return data
	}
	body := func(n, v int, enabled bool) string {
		return fmt.Sprintf(`{"request_id":"20000000-0000-4000-8000-%012d","operator_id":9,"expected_version":%d,"reason":"test policy","enabled":%v,"inviter_coins":17,"invitee_coins":5}`, n, v, enabled)
	}
	call("referrals", "PUT", body(1, 0, true), 200)
	call("referrals", "PUT", body(1, 0, true), 200)
	call("referrals", "PUT", body(1, 0, false), 409)
	call("referrals", "PUT", body(2, 0, false), 409)
	call("referrals", "PUT", body(2, 1, false), 200)
	call("referrals", "PUT", body(1, 0, true), 200)
	policy, err := s.svc.ReadReferralSettings(context.Background(), service.ReferralPolicy{Enabled: true})
	if err != nil || policy.Enabled || policy.Version != 2 || policy.InviterCoins != 17 {
		t.Fatalf("policy %+v %v", policy, err)
	}
	call("referrals", "PUT", strings.Replace(body(3, 2, true), `"inviter_coins":17`, `"inviter_coins":100001`, 1), 400)
	if _, err = db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'); INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(11,1,'OPEN',UTC_TIMESTAMP(6))`); err != nil {
		t.Fatal(err)
	}
	price := `{"request_id":"20000000-0000-4000-8000-000000000010","operator_id":9,"expected_version":0,"reason":"price","unlock_price_coins":30}`
	call("watch-prices/11", "PUT", price, 200)
	if err = s.svc.SetWatchPrice(context.Background(), 1, 11, 40); err != nil {
		t.Fatal(err)
	}
	call("watch-prices/11", "PUT", strings.Replace(price, "000000000010", "000000000011", 1), 409)
	call("watch-prices/11", "PUT", price, 200)
	got := call("watch-prices/11", "GET", "", 200)
	if got["unlock_price_coins"] != float64(40) || got["version"] != float64(2) {
		t.Fatalf("price %+v", got)
	}
	if _, err = db.Exec(`UPDATE live_sessions SET status='CLOSED' WHERE live_session_id=11`); err != nil {
		t.Fatal(err)
	}
	call("watch-prices/11", "PUT", strings.Replace(strings.Replace(price, "000000000010", "000000000012", 1), `"expected_version":0`, `"expected_version":2`, 1), 409)
	audit := call("actions", "GET", "", 200)
	if len(audit["actions"].([]any)) != 3 {
		t.Fatalf("audit %+v", audit)
	}
	call("watch-prices/011", "GET", "", 400)
	call("referrals", "PUT", strings.TrimSuffix(body(4, 2, true), "}")+`,"unknown":true}`, 400)
}

func TestConcurrentBusinessPolicyWrites(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	var wg sync.WaitGroup
	statuses := make(chan int, 2)
	for n := 1; n <= 2; n++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			w := httptest.NewRecorder()
			body := fmt.Sprintf(`{"request_id":"30000000-0000-4000-8000-%012d","operator_id":9,"expected_version":0,"reason":"concurrent","enabled":false,"inviter_coins":0,"invitee_coins":0}`, n)
			s.handleBusinessSettings(w, httptest.NewRequest("PUT", "/v1/internal/business/referrals", strings.NewReader(body)))
			statuses <- w.Code
		}(n)
	}
	wg.Wait()
	close(statuses)
	counts := map[int]int{}
	for code := range statuses {
		counts[code]++
	}
	if counts[200] != 1 || counts[409] != 1 {
		t.Fatalf("statuses %v", counts)
	}
}

func TestSavedReferralPolicyControlsRewardsAndKeepsReceipts(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),(2,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),(3,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL')`); err != nil {
		t.Fatal(err)
	}
	save := func(n, version int, enabled bool) {
		t.Helper()
		w := httptest.NewRecorder()
		body := fmt.Sprintf(`{"request_id":"40000000-0000-4000-8000-%012d","operator_id":9,"expected_version":%d,"reason":"reward test","enabled":%v,"inviter_coins":17,"invitee_coins":5}`, n, version, enabled)
		s.handleBusinessSettings(w, httptest.NewRequest("PUT", "/v1/internal/business/referrals", strings.NewReader(body)))
		if w.Code != 200 {
			t.Fatalf("save %d %s", w.Code, w.Body.String())
		}
	}
	save(1, 0, true)
	profile, err := s.svc.ReferralProfile(context.Background(), 1, service.ReferralPolicy{})
	if err != nil {
		t.Fatal(err)
	}
	req := service.ReferralRedeemRequest{RequestID: "40000000-0000-4000-8000-000000000010", Code: profile.Code}
	receipt, appErr, err := s.svc.RedeemReferral(context.Background(), 2, req, service.ReferralPolicy{})
	if err != nil || appErr != nil || receipt.InviterCoins != 17 || receipt.InviteeCoins != 5 {
		t.Fatalf("receipt %+v %v %v", receipt, appErr, err)
	}
	save(2, 1, false)
	replay, appErr, err := s.svc.RedeemReferral(context.Background(), 2, req, service.ReferralPolicy{})
	if err != nil || appErr != nil || replay.Idempotency != "replayed" || replay.InviteeCoins != 5 {
		t.Fatalf("replay %+v %v %v", replay, appErr, err)
	}
	req.RequestID = "40000000-0000-4000-8000-000000000011"
	_, appErr, err = s.svc.RedeemReferral(context.Background(), 3, req, service.ReferralPolicy{Enabled: true, InviterCoins: 1000, InviteeCoins: 1000})
	if err != nil || appErr == nil || appErr.Code != "REFERRALS_DISABLED" {
		t.Fatalf("disabled %v %v", appErr, err)
	}
	var count int
	if err = db.QueryRow(`SELECT COUNT(*) FROM wallet_transactions WHERE tx_type='REFERRAL_REWARD'`).Scan(&count); err != nil || count != 1 {
		t.Fatalf("transactions %d %v", count, err)
	}
}
