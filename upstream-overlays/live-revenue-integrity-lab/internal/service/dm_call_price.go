package service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"regexp"
	"time"
)

var dmCallID = regexp.MustCompile(`^[0-9a-f]{32}$`)

func (s *Service) DmCallPrice(ctx context.Context, owner uint64) (int, error) {
	var price int
	err := s.db.QueryRowContext(ctx, `SELECT price_coins FROM dm_call_prices WHERE owner_id=?`, owner).Scan(&price)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, nil
	}
	return price, err
}

func (s *Service) SetDmCallPrice(ctx context.Context, owner uint64, price int) *AppError {
	if owner == 0 || price < 0 || price > 1_000_000 {
		return &AppError{HTTPStatus: 400, Code: "INVALID_PRICE", Message: "视频通话价格需为 0 至 1000000 金币"}
	}
	if price > 0 {
		var approved bool
		if err := s.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM review_requests WHERE user_id=? AND kind='CREATOR' AND status='APPROVED')`, owner).Scan(&approved); err != nil {
			return &AppError{HTTPStatus: 503, Code: "PRICE_UNAVAILABLE", Message: "暂时无法确认主播认证状态"}
		}
		if !approved {
			return &AppError{HTTPStatus: 403, Code: "CREATOR_REQUIRED", Message: "只有认证通过的主播才能设置付费视频通话"}
		}
	}
	_, err := s.db.ExecContext(ctx, `INSERT INTO dm_call_prices(owner_id,price_coins) VALUES(?,?)
        ON DUPLICATE KEY UPDATE price_coins=VALUES(price_coins)`, owner, price)
	if err != nil {
		return &AppError{HTTPStatus: 503, Code: "PRICE_UNAVAILABLE", Message: "视频通话价格暂时无法保存"}
	}
	return nil
}

// SettleDmCall is called after the gateway verifies both participants in LiveKit.
// The call id is the idempotency key, so reconnects cannot charge twice.
func (s *Service) SettleDmCall(ctx context.Context, callID string, payer, owner uint64, quotedPrice int) (int, *AppError, error) {
	if !dmCallID.MatchString(callID) || payer == 0 || owner == 0 || payer == owner || quotedPrice < 0 || quotedPrice > 1_000_000 {
		return 0, errValidation, nil
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return 0, nil, err
	}
	defer tx.Rollback()
	// Serializing on the payer's wallet also serializes first-time charges for one call ID.
	balance, err := lockCoinBalance(ctx, tx, payer)
	if err != nil {
		return 0, nil, err
	}
	var existingPrice int
	var existingPayer, existingOwner uint64
	err = tx.QueryRowContext(ctx, `SELECT payer_id,owner_id,price_coins FROM dm_call_charges WHERE call_id=? FOR UPDATE`, callID).Scan(&existingPayer, &existingOwner, &existingPrice)
	if err == nil {
		if existingPayer != payer || existingOwner != owner || existingPrice != quotedPrice {
			return 0, dmMismatch(), nil
		}
		return existingPrice, nil, tx.Commit()
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return 0, nil, err
	}
	var currentPrice int
	err = tx.QueryRowContext(ctx, `SELECT price_coins FROM dm_call_prices WHERE owner_id=? FOR UPDATE`, owner).Scan(&currentPrice)
	if errors.Is(err, sql.ErrNoRows) {
		currentPrice = 0
	} else if err != nil {
		return 0, nil, err
	}
	if currentPrice != quotedPrice {
		return 0, &AppError{HTTPStatus: 409, Code: "PRICE_CHANGED", Message: "视频通话价格已变化，请重新发起"}, nil
	}
	if balance < int64(quotedPrice) {
		return 0, &AppError{HTTPStatus: 409, Code: "INSUFFICIENT_BALANCE", Message: "金币余额不足"}, nil
	}
	var ledgerID any
	newBalance := balance - int64(quotedPrice)
	if quotedPrice > 0 {
		_, bodyHash, hashErr := BodyHashHex(struct {
			CallID string `json:"call_id"`
			Payer  uint64 `json:"payer_id"`
			Owner  uint64 `json:"owner_id"`
			Price  int    `json:"price_coins"`
		}{callID, payer, owner, quotedPrice})
		if hashErr != nil {
			return 0, nil, hashErr
		}
		posted, postErr := postLedger(ctx, tx, "DM_VIDEO_CALL", payer, callID, bodyHash, newBalance, []LedgerEntry{
			{AccountCode: fmt.Sprintf("USER_COINS:%d", payer), Amount: -int64(quotedPrice)},
			{AccountCode: "PLATFORM_RESERVED_COINS", Amount: int64(quotedPrice)},
		})
		if postErr != nil {
			return 0, nil, postErr
		}
		if err = setCoinBalance(ctx, tx, payer, newBalance); err != nil {
			return 0, nil, err
		}
		ledgerID = posted
	}
	_, err = tx.ExecContext(ctx, `INSERT INTO dm_call_charges(call_id,payer_id,owner_id,price_coins,post_balance,tx_id,created_at)
        VALUES(?,?,?,?,?,?,?)`, callID, payer, owner, quotedPrice, newBalance, ledgerID, time.Now().UTC())
	if err != nil {
		return 0, nil, err
	}
	if err = tx.Commit(); err != nil {
		return 0, nil, err
	}
	return quotedPrice, nil, nil
}
