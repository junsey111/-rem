package service

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/events"
)

func lockCoinBalance(ctx context.Context, tx *sql.Tx, userID uint64) (int64, error) {
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO wallet_accounts (user_id, currency, available_balance)
		VALUES (?, 'COIN', 0)
		ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
	`, userID); err != nil {
		return 0, err
	}
	var balance int64
	err := tx.QueryRowContext(ctx, `
		SELECT available_balance FROM wallet_accounts
		WHERE user_id = ? AND currency = 'COIN'
		FOR UPDATE
	`, userID).Scan(&balance)
	return balance, err
}

func postLedger(ctx context.Context, tx *sql.Tx, txType string, actor uint64, requestID string, bodyHash []byte, balanceAfter int64, entries []LedgerEntry) (uint64, error) {
	if SumLedgerEntries(entries) != 0 {
		return 0, fmt.Errorf("ledger imbalance for %s", txType)
	}
	res, err := tx.ExecContext(ctx, `
		INSERT INTO wallet_transactions (tx_type, actor_user_id, request_id, body_hash, created_at)
		VALUES (?, ?, ?, ?, ?)
	`, txType, actor, requestID, bodyHash, time.Now().UTC())
	if err != nil {
		return 0, err
	}
	txID64, err := res.LastInsertId()
	if err != nil || txID64 < 1 {
		return 0, fmt.Errorf("invalid wallet tx id")
	}
	txID := uint64(txID64)
	var delta int64
	userAccount := fmt.Sprintf("USER_COINS:%d", actor)
	for _, entry := range entries {
		if _, err = tx.ExecContext(ctx, `INSERT INTO wallet_entries (tx_id, account_code, amount) VALUES (?, ?, ?)`, txID, entry.AccountCode, entry.Amount); err != nil {
			return 0, err
		}
		if entry.AccountCode == userAccount {
			delta = entry.Amount
		}
	}
	payload, err := events.MarshalJSONString(events.WalletTransactionPostedV1{
		TxID:         txID,
		TxType:       txType,
		ActorUserID:  actor,
		RequestID:    requestID,
		DeltaCoins:   delta,
		BalanceAfter: balanceAfter,
	})
	if err != nil {
		return 0, err
	}
	if _, err = tx.ExecContext(ctx, `
		INSERT INTO outbox_events (topic, event_key, payload_json, created_at)
		VALUES (?, ?, ?, ?)
	`, walletTopic, fmt.Sprintf("%d", actor), payload, time.Now().UTC()); err != nil {
		return 0, err
	}
	return txID, nil
}

func setCoinBalance(ctx context.Context, tx *sql.Tx, userID uint64, balance int64) error {
	_, err := tx.ExecContext(ctx, `
		UPDATE wallet_accounts SET available_balance = ? WHERE user_id = ? AND currency = 'COIN'
	`, balance, userID)
	return err
}
