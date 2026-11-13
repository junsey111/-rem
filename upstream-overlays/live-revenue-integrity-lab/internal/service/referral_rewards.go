package service

import (
	"context"
	"database/sql"
	"fmt"
	"math"
	"strconv"
)

type ReferralRewardRecord struct {
	ID           string `json:"id"`
	Source       string `json:"source"`
	InviteeID    uint64 `json:"invitee_id"`
	SourceAmount int64  `json:"source_amount"`
	RateBPS      int64  `json:"rate_bps"`
	RewardCoins  int64  `json:"reward_coins"`
	CreatedAt    string `json:"created_at"`
}

func (s *Service) postReferralReward(ctx context.Context, tx *sql.Tx, kind string, sourceID, inviteeID, inviterID uint64, sourceAmount, inviterBalance int64) error {
	if kind != "RECHARGE" && kind != "WITHDRAWAL" {
		return fmt.Errorf("invalid referral reward source")
	}
	var configured bool
	var policy ReferralPolicy
	if err := tx.QueryRowContext(ctx, `SELECT configured,enabled,inviter_coins,invitee_coins,recharge_bps,withdrawal_bps
		FROM referral_settings WHERE singleton=1 LOCK IN SHARE MODE`).Scan(
		&configured, &policy.Enabled, &policy.InviterCoins, &policy.InviteeCoins,
		&policy.RechargeBPS, &policy.WithdrawalBPS); err != nil {
		return err
	}
	if !configured {
		policy = LoadReferralPolicy()
	}
	if !policy.usable() {
		return nil
	}
	rate := policy.RechargeBPS
	if kind == "WITHDRAWAL" {
		rate = policy.WithdrawalBPS
	}
	reward := sourceAmount * rate / 10000
	if reward <= 0 {
		return nil
	}
	if sourceAmount < 0 || inviterBalance > math.MaxInt64-reward {
		return fmt.Errorf("referral reward amount overflow")
	}
	requestID := fmt.Sprintf("referral:%s:%d", kind, sourceID)
	_, hash, err := BodyHashHex(struct {
		Kind     string
		SourceID uint64
		Inviter  uint64
		Invitee  uint64
		Amount   int64
		Rate     int64
	}{kind, sourceID, inviterID, inviteeID, sourceAmount, rate})
	if err != nil {
		return err
	}
	nextBalance := inviterBalance + reward
	txID, err := postLedger(ctx, tx, txTypeReferralReward, inviterID, requestID, hash, nextBalance,
		[]LedgerEntry{
			{AccountCode: fmt.Sprintf("USER_COINS:%d", inviterID), Amount: reward},
			{AccountCode: referralSourceAccount, Amount: -reward},
		})
	if err != nil {
		return err
	}
	if err := setCoinBalance(ctx, tx, inviterID, nextBalance); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `INSERT INTO referral_reward_events
		(source_kind,source_id,inviter_id,invitee_id,source_amount,rate_bps,reward_coins,wallet_tx_id)
		VALUES(?,?,?,?,?,?,?,?)`,
		kind, sourceID, inviterID, inviteeID, sourceAmount, rate, reward, txID)
	return err
}

func (s *Service) ListReferralRewards(ctx context.Context, inviterID, before uint64) ([]ReferralRewardRecord, string, error) {
	if before == 0 {
		before = ^uint64(0)
	}
	rows, err := s.db.QueryContext(ctx, `SELECT reward_id,source_kind,invitee_id,source_amount,rate_bps,reward_coins,
		CAST(created_at AS CHAR) FROM referral_reward_events
		WHERE inviter_id=? AND reward_id<? ORDER BY reward_id DESC LIMIT 26`, inviterID, before)
	if err != nil {
		return nil, "", err
	}
	defer rows.Close()
	items := make([]ReferralRewardRecord, 0, 26)
	for rows.Next() {
		var item ReferralRewardRecord
		var id uint64
		if err := rows.Scan(&id, &item.Source, &item.InviteeID, &item.SourceAmount,
			&item.RateBPS, &item.RewardCoins, &item.CreatedAt); err != nil {
			return nil, "", err
		}
		item.ID = strconv.FormatUint(id, 10)
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, "", err
	}
	next := ""
	if len(items) > 25 {
		items = items[:25]
		next = items[24].ID
	}
	return items, next, nil
}
