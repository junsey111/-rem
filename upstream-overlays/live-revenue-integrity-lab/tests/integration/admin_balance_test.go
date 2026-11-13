package integration

import (
	"context"
	"os"
	"testing"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestAdminBalanceAdjustment(t *testing.T) {
	dsn := os.Getenv("MYSQL_TEST_DSN")
	if dsn == "" {
		t.Skip("MYSQL_TEST_DSN is required")
	}
	ctx := context.Background()
	db, _ := startIsolatedMySQL(ctx, t, dsn, false)
	_, err := db.ExecContext(ctx, `INSERT INTO users(user_id,age_years,region_code,account_standing,can_go_live,live_gifts_enabled,account_type)
		VALUES (42,25,'US','GOOD',TRUE,TRUE,'NORMAL')`)
	if err != nil {
		t.Fatal(err)
	}
	svc := service.New(db)
	command := service.AdminBalanceCommand{RequestID: "adjust-1", UserID: 42, OperatorID: 9,
		ExpectedBalance: 0, TargetBalance: 75, Reason: "test adjustment"}
	first, err := svc.SetAdminBalance(ctx, command)
	if err != nil {
		t.Fatal(err)
	}
	if first.OldBalance != 0 || first.NewBalance != 75 || first.DeltaCoins != 75 {
		t.Fatalf("unexpected first result: %+v", first)
	}
	replay, err := svc.SetAdminBalance(ctx, command)
	if err != nil || replay.TxID != first.TxID || replay.Idempotency != "replayed" {
		t.Fatalf("bad replay: %+v, %v", replay, err)
	}
	changed := command
	changed.TargetBalance = 90
	if _, err := svc.SetAdminBalance(ctx, changed); err == nil {
		t.Fatal("accepted changed payload under existing request ID")
	}
	stale := command
	stale.RequestID = "adjust-2"
	stale.TargetBalance = 20
	if _, err := svc.SetAdminBalance(ctx, stale); err == nil {
		t.Fatal("accepted stale expected balance")
	}
	stale.ExpectedBalance = 75
	second, err := svc.SetAdminBalance(ctx, stale)
	if err != nil || second.DeltaCoins != -55 {
		t.Fatalf("bad debit adjustment: %+v, %v", second, err)
	}
	var balance, sum int64
	if err := db.QueryRowContext(ctx, `SELECT available_balance FROM wallet_accounts WHERE user_id=42 AND currency='COIN'`).Scan(&balance); err != nil {
		t.Fatal(err)
	}
	if err := db.QueryRowContext(ctx, `SELECT COALESCE(SUM(e.amount),0) FROM wallet_entries e JOIN wallet_transactions t ON t.tx_id=e.tx_id WHERE t.tx_type='ADMIN_ADJUST'`).Scan(&sum); err != nil {
		t.Fatal(err)
	}
	if balance != 20 || sum != 0 {
		t.Fatalf("balance=%d ledger sum=%d", balance, sum)
	}
}
