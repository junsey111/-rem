package service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"
)

type AdminBalanceCommand struct {
	RequestID       string `json:"request_id"`
	UserID          uint64 `json:"user_id"`
	OperatorID      uint64 `json:"operator_id"`
	ExpectedBalance int64  `json:"expected_balance"`
	TargetBalance   int64  `json:"target_balance"`
	Reason          string `json:"reason"`
}

type AdminBalanceResult struct {
	TxID        uint64 `json:"tx_id,string"`
	UserID      uint64 `json:"user_id,string"`
	OldBalance  int64  `json:"old_balance"`
	NewBalance  int64  `json:"new_balance"`
	DeltaCoins  int64  `json:"delta_coins"`
	Idempotency string `json:"idempotency"`
}

func (s *Service) SetAdminBalance(ctx context.Context, c AdminBalanceCommand) (AdminBalanceResult, error) {
	c.Reason = strings.TrimSpace(c.Reason)
	if c.RequestID == "" || c.UserID == 0 || c.OperatorID == 0 || c.ExpectedBalance < 0 ||
		c.TargetBalance < 0 || c.TargetBalance > 1_000_000_000 || len([]rune(c.Reason)) == 0 || len([]rune(c.Reason)) > 200 {
		return AdminBalanceResult{}, &AppError{HTTPStatus: 400, Code: "INVALID_ADJUSTMENT", Message: "余额调整参数无效"}
	}
	_, hash, err := BodyHashHex(c)
	if err != nil {
		return AdminBalanceResult{}, err
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return AdminBalanceResult{}, err
	}
	defer tx.Rollback()
	current, err := lockCoinBalance(ctx, tx, c.UserID)
	if err != nil {
		return AdminBalanceResult{}, err
	}
	var savedHash []byte
	var existing AdminBalanceResult
	err = tx.QueryRowContext(ctx, `SELECT body_hash, old_balance, new_balance, wallet_tx_id
		FROM admin_balance_adjustments WHERE user_id=? AND request_id=?`, c.UserID, c.RequestID).
		Scan(&savedHash, &existing.OldBalance, &existing.NewBalance, &existing.TxID)
	if err == nil {
		if !bytesEqual(savedHash, hash) {
			return AdminBalanceResult{}, &AppError{HTTPStatus: 409, Code: "IDEMPOTENCY_PAYLOAD_MISMATCH", Message: "操作单号已用于其他余额调整"}
		}
		existing.UserID = c.UserID
		existing.DeltaCoins = existing.NewBalance - existing.OldBalance
		existing.Idempotency = "replayed"
		if err := tx.Commit(); err != nil {
			return AdminBalanceResult{}, err
		}
		return existing, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return AdminBalanceResult{}, err
	}
	if current != c.ExpectedBalance {
		return AdminBalanceResult{}, &AppError{HTTPStatus: 409, Code: "BALANCE_CHANGED", Message: "用户余额已变化，请刷新后重新确认"}
	}
	if current == c.TargetBalance {
		return AdminBalanceResult{}, &AppError{HTTPStatus: 400, Code: "BALANCE_UNCHANGED", Message: "目标余额与当前余额相同"}
	}
	delta := c.TargetBalance - current
	txID, err := postLedger(ctx, tx, "ADMIN_ADJUST", c.UserID, c.RequestID, hash, c.TargetBalance, []LedgerEntry{
		{AccountCode: fmt.Sprintf("USER_COINS:%d", c.UserID), Amount: delta},
		{AccountCode: "SYSTEM_ADMIN_ADJUST", Amount: -delta},
	})
	if err != nil {
		return AdminBalanceResult{}, err
	}
	if err := setCoinBalance(ctx, tx, c.UserID, c.TargetBalance); err != nil {
		return AdminBalanceResult{}, err
	}
	_, err = tx.ExecContext(ctx, `INSERT INTO admin_balance_adjustments
		(user_id,operator_id,request_id,body_hash,old_balance,new_balance,reason,wallet_tx_id,created_at)
		VALUES (?,?,?,?,?,?,?,?,?)`, c.UserID, c.OperatorID, c.RequestID, hash, current, c.TargetBalance, c.Reason, txID, time.Now().UTC())
	if err != nil {
		return AdminBalanceResult{}, err
	}
	if err := tx.Commit(); err != nil {
		return AdminBalanceResult{}, err
	}
	return AdminBalanceResult{TxID: txID, UserID: c.UserID, OldBalance: current, NewBalance: c.TargetBalance, DeltaCoins: delta, Idempotency: "created"}, nil
}
