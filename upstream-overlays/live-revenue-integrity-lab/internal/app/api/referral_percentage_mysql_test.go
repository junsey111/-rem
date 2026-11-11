package apiserver

import (
	"context"
	"testing"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestReferralPercentageRewardsCreditSpendableBalanceOnce(t *testing.T) {
	db := newOperationsTestDB(t)
	ctx := context.Background()
	svc := service.New(db)
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type)
		VALUES(1,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),
		      (2,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL')`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(`UPDATE referral_settings SET configured=TRUE,enabled=TRUE,
		inviter_coins=0,invitee_coins=0,recharge_bps=500,withdrawal_bps=800 WHERE singleton=1`); err != nil {
		t.Fatal(err)
	}
	profile, err := svc.ReferralProfile(ctx, 1, service.ReferralPolicy{})
	if err != nil || profile.Code == "" || profile.RechargeBPS != 500 || profile.WithdrawalBPS != 800 {
		t.Fatalf("profile %+v: %v", profile, err)
	}
	receipt, appErr, err := svc.RedeemReferral(ctx, 2, service.ReferralRedeemRequest{
		RequestID: "50000000-0000-4000-8000-000000000001", Code: profile.Code,
	}, service.ReferralPolicy{})
	if err != nil || appErr != nil || !receipt.Accepted || receipt.InviterCoins != 0 || receipt.InviteeCoins != 0 {
		t.Fatalf("redeem %+v %v %v", receipt, appErr, err)
	}
	topUp := service.RechargeRequest{
		RequestID: "recharge-for-invitee", ViewerID: 2, Coins: 10000, PaymentRef: "manual:2:test",
	}
	first, appErr, err := svc.Recharge(ctx, topUp)
	if err != nil || appErr != nil || first.Idempotency != "created" {
		t.Fatalf("recharge %+v %v %v", first, appErr, err)
	}
	again, appErr, err := svc.Recharge(ctx, topUp)
	if err != nil || appErr != nil || again.Idempotency != "replayed" {
		t.Fatalf("replay %+v %v %v", again, appErr, err)
	}
	insert, err := db.Exec(`INSERT INTO withdrawals(user_id,request_id,diamonds,gross_fen,fee_fen,payout_fen,
		settings_version,rules_json,payout_method,payout_account,payout_name,status)
		VALUES(2,'50000000-0000-4000-8000-000000000002',20000,20000,0,20000,1,'{}',
		'bank','test-account','test-user','APPROVED')`)
	if err != nil {
		t.Fatal(err)
	}
	rawID, err := insert.LastInsertId()
	if err != nil {
		t.Fatal(err)
	}
	action := service.WithdrawalAction{
		RequestID:  "50000000-0000-4000-8000-000000000003",
		OperatorID: 9, Action: "PAID", Reason: "test payment", PaymentRef: "test-payment-1",
	}
	paid, err := svc.ReviewWithdrawal(ctx, uint64(rawID), action)
	if err != nil || paid.Status != "PAID" {
		t.Fatalf("paid %+v %v", paid, err)
	}
	if _, err := svc.ReviewWithdrawal(ctx, uint64(rawID), action); err != nil {
		t.Fatal(err)
	}
	var balance, eventCount, ledgerTotal int64
	if err := db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=1 AND currency='COIN'`).Scan(&balance); err != nil {
		t.Fatal(err)
	}
	if err := db.QueryRow(`SELECT COUNT(*) FROM referral_reward_events`).Scan(&eventCount); err != nil {
		t.Fatal(err)
	}
	if err := db.QueryRow(`SELECT COALESCE(SUM(e.amount),0) FROM wallet_entries e
		JOIN wallet_transactions t ON t.tx_id=e.tx_id WHERE t.tx_type='REFERRAL_REWARD'`).Scan(&ledgerTotal); err != nil {
		t.Fatal(err)
	}
	if balance != 2100 || eventCount != 2 || ledgerTotal != 0 {
		t.Fatalf("balance=%d events=%d ledger=%d", balance, eventCount, ledgerTotal)
	}
	profile, err = svc.ReferralProfile(ctx, 1, service.ReferralPolicy{})
	if err != nil || profile.TotalRechargeRewardCoins != 500 ||
		profile.TotalWithdrawalRewardCoins != 1600 || profile.InvitedCount == nil || *profile.InvitedCount != 1 {
		t.Fatalf("profile after rewards %+v %v", profile, err)
	}
	rewards, next, err := svc.ListReferralRewards(ctx, 1, 0)
	if err != nil || len(rewards) != 2 || next != "" || rewards[0].Source != "WITHDRAWAL" || rewards[0].RewardCoins != 1600 {
		t.Fatalf("rewards %+v next=%q err=%v", rewards, next, err)
	}
}
