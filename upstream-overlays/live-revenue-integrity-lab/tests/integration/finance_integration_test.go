package integration

import (
	"context"
	"os"
	"sync"
	"testing"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestFinanceRulesReservationRefundAndAudit(t *testing.T) {
	dsn := os.Getenv("MYSQL_TEST_DSN")
	if dsn == "" {
		t.Skip("isolated MySQL required")
	}
	ctx := context.Background()
	db, _ := startIsolatedMySQL(ctx, t, dsn, false)
	svc := service.New(db)
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','GOOD',1,1,'NORMAL'); INSERT INTO live_sessions(live_session_id,creator_id,status,started_at,closed_at) VALUES(1,1,'CLOSED',UTC_TIMESTAMP(),UTC_TIMESTAMP()); INSERT INTO stream_settlements VALUES(1,1000,1,1000,UTC_TIMESTAMP()); INSERT INTO reconciliation_results VALUES(1,'PASS',1000,1000,0,'{}',UTC_TIMESTAMP())`); err != nil {
		t.Fatal(err)
	}
	settings, err := svc.GetFinanceSettings(ctx)
	if err != nil || settings.Configured {
		t.Fatalf("unconfigured: %+v %v", settings, err)
	}
	cmd := service.SettingsCommand{RequestID: "10000000-0000-4000-8000-000000000001", OperatorID: 9, Reason: "initial setup", Rules: service.FinanceRules{WithdrawalsEnabled: true, DiamondUnits: 100, FiatFen: 100, PlatformFeeBPS: 2000, MinimumDiamonds: 100, PayoutMethods: []string{"bank"}}}
	settings, err = svc.UpdateFinanceSettings(ctx, cmd)
	if err != nil || settings.Version != 1 {
		t.Fatalf("settings: %+v %v", settings, err)
	}
	if _, err = svc.UpdateFinanceSettings(ctx, cmd); err != nil {
		t.Fatal("settings retry", err)
	}
	stale := cmd
	stale.RequestID = "10000000-0000-4000-8000-000000000002"
	if _, err = svc.UpdateFinanceSettings(ctx, stale); err == nil {
		t.Fatal("stale version accepted")
	}
	requests := []service.WithdrawalRequest{{RequestID: "20000000-0000-4000-8000-000000000001", Diamonds: 700, PayoutMethod: "bank", PayoutAccount: "test-account", PayoutName: "test"}, {RequestID: "20000000-0000-4000-8000-000000000002", Diamonds: 700, PayoutMethod: "bank", PayoutAccount: "test-account", PayoutName: "test"}}
	var wg sync.WaitGroup
	records := make(chan service.Withdrawal, 2)
	for _, request := range requests {
		wg.Add(1)
		go func(c service.WithdrawalRequest) {
			defer wg.Done()
			w, e := svc.CreateWithdrawal(ctx, 1, c)
			if e == nil {
				records <- w
			}
		}(request)
	}
	wg.Wait()
	close(records)
	var winner service.Withdrawal
	count := 0
	for w := range records {
		winner = w
		count++
	}
	if count != 1 {
		t.Fatalf("concurrent successes=%d", count)
	}
	if winner.PayoutFen != 560 || winner.SettingsVersion != 1 {
		t.Fatalf("wrong snapshot: %+v", winner)
	}
	if _, err = svc.CreateWithdrawal(ctx, 1, winner.WithdrawalRequest); err != nil {
		t.Fatal("withdraw retry", err)
	}
	reject := service.WithdrawalAction{RequestID: "30000000-0000-4000-8000-000000000001", OperatorID: 9, Action: "REJECT", Reason: "test rejection"}
	if _, err = svc.ReviewWithdrawal(ctx, winner.ID, reject); err != nil {
		t.Fatal(err)
	}
	if _, err = svc.ReviewWithdrawal(ctx, winner.ID, reject); err != nil {
		t.Fatal("repeated rejection", err)
	}
	next := requests[0]
	next.RequestID = "20000000-0000-4000-8000-000000000003"
	if _, err = svc.CreateWithdrawal(ctx, 1, next); err != nil {
		t.Fatal("rejected reservation not released", err)
	}
	_, app, err := svc.Recharge(ctx, service.RechargeRequest{RequestID: "original-recharge", ViewerID: 1, Coins: 100, PaymentRef: "original-provider-trade"})
	if err != nil || app != nil {
		t.Fatalf("recharge: %v %v", err, app)
	}
	refund := service.RefundRequest{RequestID: "40000000-0000-4000-8000-000000000001", UserID: 1, RechargeRequestID: "original-recharge", AmountFen: 100, Reason: "test refund"}
	r, err := svc.CreateRefund(ctx, refund)
	if err != nil {
		t.Fatal(err)
	}
	if _, err = svc.CreateRefund(ctx, refund); err != nil {
		t.Fatal("refund retry", err)
	}
	var balance int64
	if err = db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=1`).Scan(&balance); err != nil || balance != 0 {
		t.Fatalf("duplicate debit %d %v", balance, err)
	}
	reject.RequestID = "30000000-0000-4000-8000-000000000002"
	if _, err = svc.ReviewRefund(ctx, r.ID, reject); err != nil {
		t.Fatal(err)
	}
	if _, err = svc.ReviewRefund(ctx, r.ID, reject); err != nil {
		t.Fatal(err)
	}
	if err = db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=1`).Scan(&balance); err != nil || balance != 100 {
		t.Fatalf("refund release %d %v", balance, err)
	}
	var sum int64
	// A rejected application remains audited, while a new one can reserve the
	// original recharge again. Only one concurrent non-rejected request wins.
	results := make(chan service.Refund, 2)
	var reapply sync.WaitGroup
	for _, key := range []string{"40000000-0000-4000-8000-000000000010", "40000000-0000-4000-8000-000000000011"} {
		reapply.Add(1)
		go func(key string) {
			defer reapply.Done()
			c := refund
			c.RequestID = key
			if item, e := svc.CreateRefund(ctx, c); e == nil {
				results <- item
			}
		}(key)
	}
	reapply.Wait()
	close(results)
	if len(results) != 1 {
		t.Fatalf("concurrent refund reapplications: %d winners", len(results))
	}
	won := <-results
	if original, e := svc.CreateRefund(ctx, refund); e != nil || original.Status != "REJECTED" {
		t.Fatalf("old request replay changed: %v %v", original, e)
	}
	reject.RequestID = "30000000-0000-4000-8000-000000000010"
	if _, err = svc.ReviewRefund(ctx, won.ID, reject); err != nil {
		t.Fatal(err)
	}
	if err = db.QueryRow(`SELECT SUM(amount) FROM wallet_entries`).Scan(&sum); err != nil || sum != 0 {
		t.Fatalf("unbalanced ledger %d %v", sum, err)
	}
	_, app, err = svc.Recharge(ctx, service.RechargeRequest{RequestID: "consumed-recharge", ViewerID: 1, Coins: 100, PaymentRef: "second-provider-trade"})
	if err != nil || app != nil {
		t.Fatal(err, app)
	}
	if _, err = db.Exec(`UPDATE wallet_accounts SET available_balance=0 WHERE user_id=1`); err != nil {
		t.Fatal(err)
	}
	refund.RequestID = "40000000-0000-4000-8000-000000000002"
	refund.RechargeRequestID = "consumed-recharge"
	if _, err = svc.CreateRefund(ctx, refund); err == nil {
		t.Fatal("consumed refund allowed despite disabled policy")
	}
	cmd.RequestID = "10000000-0000-4000-8000-000000000003"
	cmd.ExpectedVersion = 1
	cmd.Rules.RefundConsumedCoinsAllowed = true
	if _, err = svc.UpdateFinanceSettings(ctx, cmd); err != nil {
		t.Fatal(err)
	}
	if _, err = svc.CreateRefund(ctx, refund); err != nil {
		t.Fatal(err)
	}
	if err = db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=1`).Scan(&balance); err != nil || balance != -100 {
		t.Fatalf("consumed refund debt %d %v", balance, err)
	}
}
