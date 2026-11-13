package apiserver

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"
)

func TestCreatorIncomeIncludesPrivateSourcesWithOwnershipAndStablePaging(t *testing.T) {
	db := newOperationsTestDB(t)
	exec := func(query string, args ...any) {
		t.Helper()
		if _, err := db.Exec(query, args...); err != nil {
			t.Fatal(err)
		}
	}
	exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES
 (1,25,'CN','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL'),(2,25,'CN','SELF_REPORTED','GOOD',FALSE,FALSE,'NORMAL'),(3,25,'CN','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL')`)
	exec(`INSERT INTO review_requests(user_id,request_id,kind,target_type,target_id,content,evidence_reference,status) VALUES(1,'income-review','CREATOR','USER',1,'name phone','','APPROVED')`)
	exec(`UPDATE finance_settings SET rules_json='{"diamond_units":10,"fiat_fen":100}' WHERE singleton=1`)
	now := time.Now().UTC().Truncate(time.Microsecond)
	exec(`INSERT INTO live_sessions(live_session_id,creator_id,status,started_at,closed_at) VALUES(1,1,'CLOSED',?,?)`, now.Add(-time.Minute), now)
	exec(`INSERT INTO gift_orders(viewer_id,creator_id,live_session_id,gift_id,quantity,charged_coins,match_points_added,diamond_reward,status,request_id,body_hash,created_at) VALUES(2,1,1,'ROSE',1,10,10,10,'ACCEPTED','income-live',UNHEX(REPEAT('00',32)),?)`, now)
	gift := func(owner int, status, request string, reward int) {
		exec(`INSERT INTO dm_gift_orders(viewer_id,recipient_id,request_id,gift_id,gift_name,quantity,charged_coins,diamond_reward,status,sent_at_ms,body_hash,created_at) VALUES(2,?,?,'ROSE','玫瑰',1,10,?,?,1,UNHEX(REPEAT('00',32)),?)`, owner, request, reward, status, now)
	}
	for i := 0; i < 32; i++ {
		gift(1, "ACCEPTED", fmt.Sprintf("income-private-%d", i), 2)
	}
	gift(3, "ACCEPTED", "other-owner", 1000)
	gift(1, "REJECTED", "rejected", 1000)
	exec(`INSERT INTO dm_video_unlocks(viewer_id,video_id,owner_id,request_id,price_coins,charged_coins,diamond_reward,body_hash,created_at) VALUES(2,'video-1',1,'income-video',10,10,3,UNHEX(REPEAT('00',32)),?)`, now)
	exec(`INSERT INTO dm_call_charges(call_id,payer_id,owner_id,price_coins,post_balance,created_at) VALUES(REPEAT('a',32),2,1,4,100,?)`, now)
	exec(`INSERT INTO wallet_transactions(tx_type,actor_user_id,request_id,created_at) VALUES('WATCH_UNLOCK',2,'income-watch',?)`, now)
	exec(`INSERT INTO live_watch_unlocks(user_id,request_id,live_session_id,creator_id,charged_coins,diamond_reward,post_balance,tx_id,body_hash,created_at) SELECT 2,'income-watch',1,1,10,5,100,tx_id,UNHEX(REPEAT('00',32)),? FROM wallet_transactions WHERE request_id='income-watch'`, now)
	s := &Server{db: db, svc: service.New(db)}
	call := func(user uint64, query string, status int) map[string]any {
		t.Helper()
		r := httptest.NewRequest("GET", "/v1/creators/me/income"+query, nil)
		r = r.WithContext(context.WithValue(r.Context(), authUserIDKey, user))
		w := httptest.NewRecorder()
		s.handleCreatorIncome(w, r)
		if w.Code != status {
			t.Fatalf("income %d: %s", w.Code, w.Body.String())
		}
		var value map[string]any
		json.Unmarshal(w.Body.Bytes(), &value)
		return value
	}
	first := call(1, "", 200)
	next := first["next_before"].(string)
	if next == "" {
		t.Fatal("missing cursor")
	}
	second := call(1, "?before="+url.QueryEscape(next), 200)
	seen := map[string]bool{}
	kinds := map[string]int{}
	var total float64
	for _, page := range []map[string]any{first, second} {
		for _, item := range page["records"].([]any) {
			row := item.(map[string]any)
			id := row["id"].(string)
			if seen[id] {
				t.Fatal("duplicate page record")
			}
			seen[id] = true
			kinds[row["source"].(string)]++
			total += row["income_fen"].(float64)
		}
	}
	if len(seen) != 36 || len(kinds) != 5 || total != 860 {
		t.Fatalf("incorrect income: count=%d kinds=%v fen=%v", len(seen), kinds, total)
	}
	filtered := call(1, "?source=PAID_VIDEO", 200)["records"].([]any)
	if len(filtered) != 1 {
		t.Fatal("source filtering")
	}
	call(2, "", 403)
	call(1, "?source=BAD", 400)
	call(1, "?before=invalid", 400)
	r := httptest.NewRequest("GET", "/v1/creators/me/dashboard", nil)
	r = r.WithContext(context.WithValue(r.Context(), authUserIDKey, uint64(1)))
	w := httptest.NewRecorder()
	s.handleCreatorDashboard(w, r)
	var dashboard map[string]any
	json.Unmarshal(w.Body.Bytes(), &dashboard)
	if w.Code != 200 || dashboard["monthly_income_fen"] != float64(860) || dashboard["total_income_fen"] != float64(860) {
		t.Fatalf("dashboard must include private income: %s", w.Body.String())
	}
	if err := s.svc.SetDmCallPrice(context.Background(), 2, 10); err == nil || err.HTTPStatus != 403 {
		t.Fatal("ordinary user set paid price")
	}
	if err := s.svc.SetDmCallPrice(context.Background(), 1, 10); err != nil {
		t.Fatal(err)
	}
	// All charged private sources are withdrawable, while un-reconciled live
	// gifts and another creator's income must never enter this balance.
	available, err := s.svc.Withdrawable(context.Background(), 1)
	if err != nil || available.Earned != 76 || available.Available != 76 || available.Reserved != 0 {
		t.Fatalf("private earnings omitted or live gifts counted early: %+v %v", available, err)
	}
	rules := service.FinanceRules{WithdrawalsEnabled: true, DiamondUnits: 10, FiatFen: 100, MinimumDiamonds: 1, PayoutMethods: []string{"bank"}}
	if _, err = s.svc.UpdateFinanceSettings(context.Background(), service.SettingsCommand{RequestID: "4ba90f10-04dd-465d-8d84-ae703bfbdb36", OperatorID: 9, Reason: "test", Rules: rules}); err != nil {
		t.Fatal(err)
	}
	request := service.WithdrawalRequest{RequestID: "9e40fb27-b524-41c0-9b13-58e08ccf12d7", Diamonds: 76, PayoutMethod: "bank", PayoutAccount: "test-only", PayoutName: "test"}
	if _, err = s.svc.CreateWithdrawal(context.Background(), 1, request); err != nil {
		t.Fatal("private earnings cannot be withdrawn", err)
	}
	if _, err = s.svc.CreateWithdrawal(context.Background(), 1, request); err != nil {
		t.Fatal("replay", err)
	}
	available, err = s.svc.Withdrawable(context.Background(), 1)
	if err != nil || available.Available != 0 || available.Reserved != 76 {
		t.Fatalf("reservation double counted: %+v %v", available, err)
	}
	request.RequestID = "b834830b-de2b-4c55-a6c7-a5670bafae8a"
	request.Diamonds = 1
	if _, err = s.svc.CreateWithdrawal(context.Background(), 1, request); err == nil {
		t.Fatal("reserved income withdrawn twice")
	}
}
