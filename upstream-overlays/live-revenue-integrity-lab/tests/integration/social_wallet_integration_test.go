package integration

import (
	"context"
	"fmt"
	"os"
	"sync"
	"testing"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestSocialWalletConcurrentReplay(t *testing.T) {
	dsn := os.Getenv("MYSQL_TEST_DSN")
	if dsn == "" {
		t.Skip("isolated MySQL required")
	}
	ctx := context.Background()
	db, _ := startIsolatedMySQL(ctx, t, dsn, false)
	svc := service.New(db)
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','GOOD',TRUE,TRUE,'NORMAL'); INSERT INTO wallet_accounts(user_id,currency,available_balance) VALUES(1,'COIN',1000)`); err != nil {
		t.Fatal(err)
	}
	// Simultaneous retries must return one committed result, without duplicate credits/debits.
	parallel := func(run func() (uint64, string, *service.AppError, error)) uint64 {
		t.Helper()
		const attempts = 8
		start := make(chan struct{})
		type outcome struct {
			id     uint64
			mode   string
			appErr *service.AppError
			err    error
		}
		results := make(chan outcome, attempts)
		var wg sync.WaitGroup
		for i := 0; i < attempts; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				<-start
				id, mode, appErr, err := run()
				results <- outcome{id, mode, appErr, err}
			}()
		}
		close(start)
		wg.Wait()
		close(results)
		var id uint64
		created := 0
		for result := range results {
			if result.err != nil || result.appErr != nil {
				t.Fatalf("concurrent replay: %v %+v", result.err, result.appErr)
			}
			if result.id == 0 || (id != 0 && result.id != id) {
				t.Fatalf("different receipts: %d / %d", id, result.id)
			}
			id = result.id
			if result.mode == "created" {
				created++
			} else if result.mode != "replayed" {
				t.Fatalf("unknown mode: %s", result.mode)
			}
		}
		if created != 1 {
			t.Fatalf("created %d times", created)
		}
		return id
	}
	claim := service.TaskRewardRequest{RequestID: "71000000-0000-4000-8000-000000000001", UserID: 1, TaskKey: "daily_checkin", TaskDate: "2026-11-13", RewardCoins: 100}
	parallel(func() (uint64, string, *service.AppError, error) {
		result, appErr, err := svc.GrantTaskReward(ctx, claim)
		return result.ClaimID, result.Idempotency, appErr, err
	})
	claim.RewardCoins++
	if _, appErr, err := svc.GrantTaskReward(ctx, claim); err != nil || appErr == nil || appErr.Code != "IDEMPOTENCY_PAYLOAD_MISMATCH" {
		t.Fatalf("reward mismatch: %v %+v", err, appErr)
	}
	claim.TaskKey = "publish_moment"
	if _, appErr, err := svc.GrantTaskReward(ctx, claim); err != nil || appErr == nil || appErr.Code != "IDEMPOTENCY_PAYLOAD_MISMATCH" {
		t.Fatalf("reward request reused across tasks: %v %+v", err, appErr)
	}
	purchase := service.CatalogPurchaseRequest{RequestID: "72000000-0000-4000-8000-000000000001", UserID: 1, ItemType: "COSTUME", ItemID: "1", PriceCoins: 200}
	parallel(func() (uint64, string, *service.AppError, error) {
		result, appErr, err := svc.PurchaseCatalog(ctx, purchase)
		return result.PurchaseID, result.Idempotency, appErr, err
	})
	purchase.PriceCoins++
	if _, appErr, err := svc.PurchaseCatalog(ctx, purchase); err != nil || appErr == nil || appErr.Code != "IDEMPOTENCY_PAYLOAD_MISMATCH" {
		t.Fatalf("purchase mismatch: %v %+v", err, appErr)
	}
	parallel(func() (uint64, string, *service.AppError, error) {
		result, appErr, err := svc.ReverseCatalogPurchase(ctx, service.CatalogPurchaseReverseRequest{RequestID: purchase.RequestID, UserID: 1, Reason: "isolated test"})
		return result.PurchaseID, result.Idempotency, appErr, err
	})
	if _, appErr, err := svc.PurchaseCatalog(ctx, purchase); err != nil || appErr == nil || appErr.Code != "IDEMPOTENCY_PAYLOAD_MISMATCH" {
		t.Fatalf("changed reversed purchase: %v %+v", err, appErr)
	}
	purchase.PriceCoins--
	if _, appErr, err := svc.PurchaseCatalog(ctx, purchase); err != nil || appErr == nil || appErr.Code != "PURCHASE_REVERSED" {
		t.Fatalf("reversed purchase replay: %v %+v", err, appErr)
	}
	var balance, transactions, claims, purchases, delta int64
	for query, target := range map[string]*int64{
		`SELECT available_balance FROM wallet_accounts WHERE user_id=1 AND currency='COIN'`: &balance,
		`SELECT COUNT(*) FROM wallet_transactions`:                                          &transactions,
		`SELECT COUNT(*) FROM task_reward_claims`:                                           &claims,
		`SELECT COUNT(*) FROM social_catalog_purchases`:                                     &purchases,
		`SELECT COALESCE(SUM(amount),0) FROM wallet_entries`:                                &delta,
	} {
		if err := db.QueryRow(query).Scan(target); err != nil {
			t.Fatal(err)
		}
	}
	if balance != 1100 || transactions != 3 || claims != 1 || purchases != 1 || delta != 0 {
		t.Fatal(fmt.Sprintf("balance=%d transactions=%d claims=%d purchases=%d ledger_delta=%d", balance, transactions, claims, purchases, delta))
	}
}
