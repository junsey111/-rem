package service

import (
	"context"
	"crypto/rand"
	"database/sql"
	"errors"
	"fmt"
	"math"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/events"
)

const (
	txTypeReferralReward   = "REFERRAL_REWARD"
	referralSourceAccount  = "SYSTEM_REFERRAL_SOURCE"
	referralCodeAlphabet   = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	maxReferralRewardCoins = 100000
)

// ReferralPolicy is the process config for invite rewards.
// Coins outside 0..100000 disable the feature so a bad deploy cannot mint them.
type ReferralPolicy struct {
	Enabled      bool  `json:"enabled"`
	InviterCoins int64 `json:"inviter_coins"`
	InviteeCoins int64 `json:"invitee_coins"`
}

func (p ReferralPolicy) usable() bool {
	return p.Enabled && p.InviterCoins >= 0 && p.InviteeCoins >= 0 && p.InviterCoins <= maxReferralRewardCoins && p.InviteeCoins <= maxReferralRewardCoins
}

// LoadReferralPolicy reads REFERRALS_ENABLED, REFERRAL_INVITER_COINS, and
// REFERRAL_INVITEE_COINS. The feature is off unless explicitly enabled.
// Defaults once enabled are 100 coins for the inviter and 50 for the invitee.
func LoadReferralPolicy() ReferralPolicy {
	inviter := referralCoinConfig(os.Getenv("REFERRAL_INVITER_COINS"), 100)
	invitee := referralCoinConfig(os.Getenv("REFERRAL_INVITEE_COINS"), 50)
	enabled := referralFlagEnabled(os.Getenv("REFERRALS_ENABLED"))
	if inviter < 0 || invitee < 0 {
		enabled = false
	}
	if inviter < 0 {
		inviter = 0
	}
	if invitee < 0 {
		invitee = 0
	}
	return ReferralPolicy{Enabled: enabled, InviterCoins: inviter, InviteeCoins: invitee}
}

func referralFlagEnabled(value string) bool {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}

func referralCoinConfig(value string, fallback int64) int64 {
	value = strings.TrimSpace(value)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed < 0 || parsed > maxReferralRewardCoins {
		return -1
	}
	return parsed
}

// ReferralProfile is GET /v1/referrals/me.
// Code and counters are omitted while invites are disabled.
type ReferralProfile struct {
	Enabled       bool   `json:"enabled"`
	Code          string `json:"code,omitempty"`
	InvitedCount  *int64 `json:"invited_count,omitempty"`
	RewardedCoins *int64 `json:"rewarded_coins,omitempty"`
}

// ReferralRedeemRequest is the only payload that participates in idempotency.
type ReferralRedeemRequest struct {
	RequestID string `json:"request_id"`
	Code      string `json:"code"`
}

// ReferralRedeemResponse is the committed receipt. invitee_balance is the
// invitee's coin balance immediately after this acceptance, not a later read.
type ReferralRedeemResponse struct {
	Accepted       bool   `json:"accepted"`
	RequestID      string `json:"request_id"`
	Code           string `json:"code"`
	Idempotency    string `json:"idempotency"`
	InviterID      uint64 `json:"inviter_id"`
	InviterCoins   int64  `json:"inviter_coins"`
	InviteeCoins   int64  `json:"invitee_coins"`
	InviteeBalance int64  `json:"invitee_balance"`
}

type referralBody struct {
	RequestID string `json:"request_id"`
	Code      string `json:"code"`
}

type storedReferral struct {
	InviterID      uint64
	RequestID      string
	Code           string
	InviterCoins   int64
	InviteeCoins   int64
	InviteeBalance int64
	BodyHash       []byte
}

var errReferralBusy = errors.New("referral redeem busy")

// ReferralProfile returns the caller's invite code when the feature is on.
// rewarded_coins is what this user earned as an inviter, not as an invitee.
func (s *Service) ReferralProfile(ctx context.Context, userID uint64, policy ReferralPolicy) (ReferralProfile, error) {
	settings, err := s.ReadReferralSettings(ctx, policy)
	if err != nil {
		return ReferralProfile{}, err
	}
	policy = settings.ReferralPolicy
	if userID == 0 || !policy.usable() {
		return ReferralProfile{Enabled: false}, nil
	}
	code, err := s.ensureReferralCode(ctx, userID)
	if err != nil {
		return ReferralProfile{}, err
	}
	var invited, rewarded int64
	if err := s.db.QueryRowContext(ctx, `
		SELECT COUNT(*), COALESCE(SUM(inviter_coins), 0)
		FROM referral_redemptions WHERE inviter_id = ?
	`, userID).Scan(&invited, &rewarded); err != nil {
		return ReferralProfile{}, err
	}
	return ReferralProfile{Enabled: true, Code: code, InvitedCount: &invited, RewardedCoins: &rewarded}, nil
}

func (s *Service) ensureReferralCode(ctx context.Context, userID uint64) (string, error) {
	var existing string
	err := s.db.QueryRowContext(ctx, `SELECT code FROM referral_codes WHERE user_id = ?`, userID).Scan(&existing)
	if err == nil {
		return existing, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return "", err
	}
	for range 5 {
		code, genErr := randomReferralCode()
		if genErr != nil {
			return "", genErr
		}
		_, execErr := s.db.ExecContext(ctx, `
			INSERT INTO referral_codes (user_id, code, created_at) VALUES (?, ?, ?)
		`, userID, code, time.Now().UTC())
		if execErr == nil {
			return code, nil
		}
		if !isDupKey(execErr) {
			return "", execErr
		}
		err = s.db.QueryRowContext(ctx, `SELECT code FROM referral_codes WHERE user_id = ?`, userID).Scan(&existing)
		if err == nil {
			return existing, nil
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return "", err
		}
	}
	return "", fmt.Errorf("referral code was not allocated")
}

func randomReferralCode() (string, error) {
	buf := make([]byte, len(referralCodeAlphabet))
	out := make([]byte, 8)
	filled := 0
	for filled < len(out) {
		if _, err := rand.Read(buf); err != nil {
			return "", err
		}
		for _, b := range buf {
			// 32 divides 256, so this modulo has no bias.
			out[filled] = referralCodeAlphabet[int(b)%len(referralCodeAlphabet)]
			filled++
			if filled == len(out) {
				break
			}
		}
	}
	return string(out), nil
}

// RedeemReferral grants configured coins to the inviter and invitee.
// The same invitee and request_id returns the original receipt. A different
// code for that request_id is a conflict. Failed attempts are not stored, so
// the same request_id can succeed after the code or feature flag is fixed.
// An invitee has at most one successful redemption.
func (s *Service) RedeemReferral(ctx context.Context, inviteeID uint64, req ReferralRedeemRequest, policy ReferralPolicy) (ReferralRedeemResponse, *AppError, error) {
	requestID := strings.ToLower(strings.TrimSpace(req.RequestID))
	code := strings.ToUpper(strings.TrimSpace(req.Code))
	if inviteeID == 0 || !financeRequestID.MatchString(requestID) || code == "" {
		return ReferralRedeemResponse{}, errValidation, nil
	}
	_, bodyHash, err := BodyHashHex(referralBody{RequestID: requestID, Code: code})
	if err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	res, appErr, err := s.redeemReferralOnce(ctx, inviteeID, requestID, code, bodyHash, policy)
	if errors.Is(err, errReferralBusy) {
		return s.referralReceipt(ctx, inviteeID, requestID, bodyHash)
	}
	return res, appErr, err
}

func (s *Service) redeemReferralOnce(ctx context.Context, inviteeID uint64, requestID, code string, bodyHash []byte, policy ReferralPolicy) (ReferralRedeemResponse, *AppError, error) {
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	defer func() { _ = tx.Rollback() }()

	stored, found, err := loadReferral(ctx, tx, inviteeID, true)
	if err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	if found {
		if appErr := referralReplayError(stored, requestID, bodyHash); appErr != nil {
			return ReferralRedeemResponse{}, appErr, nil
		}
		if err := tx.Commit(); err != nil {
			return ReferralRedeemResponse{}, nil, err
		}
		return referralResponse(stored, "replayed"), nil, nil
	}
	// Shared row lock lets redemptions run together but serializes an operator's
	// policy change with committed rewards. Replays above keep their old receipt.
	var configured bool
	var storedPolicy ReferralPolicy
	if err = tx.QueryRowContext(ctx, `SELECT configured,enabled,inviter_coins,invitee_coins FROM referral_settings WHERE singleton=1 FOR SHARE`).Scan(&configured, &storedPolicy.Enabled, &storedPolicy.InviterCoins, &storedPolicy.InviteeCoins); err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	if configured {
		policy = storedPolicy
	}
	if !policy.usable() {
		return ReferralRedeemResponse{}, &AppError{HTTPStatus: 403, Code: "REFERRALS_DISABLED", Message: "referral rewards are disabled"}, nil
	}

	var inviterID uint64
	err = tx.QueryRowContext(ctx, `SELECT user_id FROM referral_codes WHERE code = ? FOR UPDATE`, code).Scan(&inviterID)
	if errors.Is(err, sql.ErrNoRows) {
		return ReferralRedeemResponse{}, &AppError{HTTPStatus: 404, Code: "REFERRAL_CODE_NOT_FOUND", Message: "invite code not found"}, nil
	}
	if err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	if inviterID == inviteeID {
		return ReferralRedeemResponse{}, &AppError{HTTPStatus: 409, Code: "SELF_REFERRAL", Message: "cannot redeem your own invite code"}, nil
	}

	balances, err := lockCoinAccounts(ctx, tx, []uint64{inviteeID, inviterID})
	if err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	walletTx, inviteeBalance, err := postReferralRewards(ctx, tx, inviteeID, inviterID, requestID, bodyHash, policy, balances)
	if isDupKey(err) {
		return ReferralRedeemResponse{}, nil, errReferralBusy
	}
	if err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	_, err = tx.ExecContext(ctx, `
		INSERT INTO referral_redemptions (
			invitee_id, inviter_id, request_id, code, inviter_coins, invitee_coins,
			invitee_balance, body_hash, tx_id, created_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, inviteeID, inviterID, requestID, code, policy.InviterCoins, policy.InviteeCoins, inviteeBalance, bodyHash, nullableTxID(walletTx), time.Now().UTC())
	if err != nil {
		if isDupKey(err) {
			return ReferralRedeemResponse{}, nil, errReferralBusy
		}
		return ReferralRedeemResponse{}, nil, err
	}
	if err := tx.Commit(); err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	return ReferralRedeemResponse{
		Accepted: true, RequestID: requestID, Code: code, Idempotency: "created",
		InviterID: inviterID, InviterCoins: policy.InviterCoins, InviteeCoins: policy.InviteeCoins, InviteeBalance: inviteeBalance,
	}, nil, nil
}

func (s *Service) referralReceipt(ctx context.Context, inviteeID uint64, requestID string, bodyHash []byte) (ReferralRedeemResponse, *AppError, error) {
	stored, found, err := loadReferral(ctx, s.db, inviteeID, false)
	if err != nil {
		return ReferralRedeemResponse{}, nil, err
	}
	if !found {
		return ReferralRedeemResponse{}, &AppError{HTTPStatus: 503, Code: "REFERRAL_IN_PROGRESS", Message: "referral redeem is not confirmed yet; retry the same request_id"}, nil
	}
	if appErr := referralReplayError(stored, requestID, bodyHash); appErr != nil {
		return ReferralRedeemResponse{}, appErr, nil
	}
	return referralResponse(stored, "replayed"), nil, nil
}

func referralReplayError(stored storedReferral, requestID string, bodyHash []byte) *AppError {
	if stored.RequestID != requestID {
		return &AppError{HTTPStatus: 409, Code: "REFERRAL_ALREADY_REDEEMED", Message: "invite code already redeemed"}
	}
	if !bytesEqual(stored.BodyHash, bodyHash) {
		return &AppError{HTTPStatus: 409, Code: "IDEMPOTENCY_PAYLOAD_MISMATCH", Message: "same request_id with different payload"}
	}
	return nil
}

func referralResponse(stored storedReferral, idempotency string) ReferralRedeemResponse {
	return ReferralRedeemResponse{
		Accepted: true, RequestID: stored.RequestID, Code: stored.Code, Idempotency: idempotency,
		InviterID: stored.InviterID, InviterCoins: stored.InviterCoins, InviteeCoins: stored.InviteeCoins, InviteeBalance: stored.InviteeBalance,
	}
}

type rowQuery interface {
	QueryRowContext(context.Context, string, ...any) *sql.Row
}

func loadReferral(ctx context.Context, q rowQuery, inviteeID uint64, lock bool) (storedReferral, bool, error) {
	var stored storedReferral
	query := `
		SELECT inviter_id, request_id, code, inviter_coins, invitee_coins, invitee_balance, body_hash
		FROM referral_redemptions WHERE invitee_id = ?`
	if lock {
		query += ` FOR UPDATE`
	}
	err := q.QueryRowContext(ctx, query, inviteeID).Scan(&stored.InviterID, &stored.RequestID, &stored.Code, &stored.InviterCoins, &stored.InviteeCoins, &stored.InviteeBalance, &stored.BodyHash)
	if errors.Is(err, sql.ErrNoRows) {
		return storedReferral{}, false, nil
	}
	if err != nil {
		return storedReferral{}, false, err
	}
	return stored, true, nil
}

func lockCoinAccounts(ctx context.Context, tx *sql.Tx, users []uint64) (map[uint64]int64, error) {
	ordered := append([]uint64(nil), users...)
	sort.Slice(ordered, func(i, j int) bool { return ordered[i] < ordered[j] })
	balances := make(map[uint64]int64, len(ordered))
	for _, userID := range ordered {
		if _, ok := balances[userID]; ok {
			continue
		}
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO wallet_accounts (user_id, currency, available_balance)
			VALUES (?, 'COIN', 0)
			ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
		`, userID); err != nil {
			return nil, err
		}
		var balance int64
		if err := tx.QueryRowContext(ctx, `
			SELECT available_balance FROM wallet_accounts
			WHERE user_id = ? AND currency = 'COIN' FOR UPDATE
		`, userID).Scan(&balance); err != nil {
			return nil, err
		}
		balances[userID] = balance
	}
	return balances, nil
}

func postReferralRewards(ctx context.Context, tx *sql.Tx, inviteeID, inviterID uint64, requestID string, bodyHash []byte, policy ReferralPolicy, balances map[uint64]int64) (uint64, int64, error) {
	inviteeBalance := balances[inviteeID]
	if policy.InviteeCoins == 0 && policy.InviterCoins == 0 {
		return 0, inviteeBalance, nil
	}
	if policy.InviteeCoins < 0 || policy.InviterCoins < 0 || inviteeBalance > math.MaxInt64-policy.InviteeCoins || balances[inviterID] > math.MaxInt64-policy.InviterCoins {
		return 0, inviteeBalance, fmt.Errorf("referral credit overflow")
	}
	entries := make([]LedgerEntry, 0, 3)
	var total int64
	if policy.InviteeCoins > 0 {
		entries = append(entries, LedgerEntry{AccountCode: fmt.Sprintf("USER_COINS:%d", inviteeID), Amount: policy.InviteeCoins})
		total += policy.InviteeCoins
		inviteeBalance += policy.InviteeCoins
	}
	if policy.InviterCoins > 0 {
		entries = append(entries, LedgerEntry{AccountCode: fmt.Sprintf("USER_COINS:%d", inviterID), Amount: policy.InviterCoins})
		total += policy.InviterCoins
	}
	entries = append(entries, LedgerEntry{AccountCode: referralSourceAccount, Amount: -total})
	if SumLedgerEntries(entries) != 0 {
		return 0, inviteeBalance, fmt.Errorf("ledger imbalance for referral")
	}
	res, err := tx.ExecContext(ctx, `
		INSERT INTO wallet_transactions (tx_type, actor_user_id, request_id, body_hash, created_at)
		VALUES (?, ?, ?, ?, ?)
	`, txTypeReferralReward, inviteeID, requestID, bodyHash, time.Now().UTC())
	if err != nil {
		return 0, inviteeBalance, err
	}
	txID64, err := res.LastInsertId()
	if err != nil || txID64 < 1 {
		return 0, inviteeBalance, fmt.Errorf("invalid wallet tx id")
	}
	txID := uint64(txID64)
	for _, entry := range entries {
		if _, err = tx.ExecContext(ctx, `INSERT INTO wallet_entries (tx_id, account_code, amount) VALUES (?, ?, ?)`, txID, entry.AccountCode, entry.Amount); err != nil {
			return 0, inviteeBalance, err
		}
	}
	for userID, amount := range map[uint64]int64{inviteeID: policy.InviteeCoins, inviterID: policy.InviterCoins} {
		if amount == 0 {
			continue
		}
		newBalance := balances[userID] + amount
		if _, err = tx.ExecContext(ctx, `
			UPDATE wallet_accounts SET available_balance = ? WHERE user_id = ? AND currency = 'COIN'
		`, newBalance, userID); err != nil {
			return 0, inviteeBalance, err
		}
		payload, marshalErr := events.MarshalJSONString(events.WalletTransactionPostedV1{
			TxID: txID, TxType: txTypeReferralReward, ActorUserID: userID, RequestID: requestID, DeltaCoins: amount, BalanceAfter: newBalance,
		})
		if marshalErr != nil {
			return 0, inviteeBalance, marshalErr
		}
		if _, err = tx.ExecContext(ctx, `
			INSERT INTO outbox_events (topic, event_key, payload_json, created_at) VALUES (?, ?, ?, ?)
		`, walletTopic, fmt.Sprintf("%d", userID), payload, time.Now().UTC()); err != nil {
			return 0, inviteeBalance, err
		}
	}
	return txID, inviteeBalance, nil
}

func nullableTxID(id uint64) any {
	if id == 0 {
		return nil
	}
	return id
}

// Env policy remains a migration fallback until an operator explicitly saves rules.
type ReferralSettings struct {
	Version    uint64 `json:"version"`
	Configured bool   `json:"configured"`
	ReferralPolicy
}

func (s *Service) ReadReferralSettings(ctx context.Context, fallback ReferralPolicy) (ReferralSettings, error) {
	var settings ReferralSettings
	err := s.db.QueryRowContext(ctx, `SELECT version,configured,enabled,inviter_coins,invitee_coins FROM referral_settings WHERE singleton=1`).Scan(&settings.Version, &settings.Configured, &settings.Enabled, &settings.InviterCoins, &settings.InviteeCoins)
	if !settings.Configured {
		settings.ReferralPolicy = fallback
	}
	return settings, err
}
