package service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"
)

const (
	txTypeNoblePurchase       = "NOBLE_PURCHASE"
	txTypeCostumePurchase     = "COSTUME_PURCHASE"
	txTypeCatalogPurchaseBack = "CATALOG_PURCHASE_REVERSE"
)

var catalogItemID = regexp.MustCompile(`^[1-9][0-9]{0,19}$`)

type CatalogPurchaseRequest struct {
	RequestID  string `json:"request_id"`
	UserID     uint64 `json:"user_id"`
	ItemType   string `json:"item_type"`
	ItemID     string `json:"item_id"`
	PriceCoins int64  `json:"price_coins"`
}

type CatalogPurchaseResponse struct {
	PurchaseID  uint64 `json:"purchase_id,string"`
	RequestID   string `json:"request_id"`
	UserID      uint64 `json:"user_id,string"`
	ItemType    string `json:"item_type"`
	ItemID      string `json:"item_id"`
	PriceCoins  int64  `json:"price_coins"`
	NewBalance  int64  `json:"new_balance"`
	Status      string `json:"status"`
	Idempotency string `json:"idempotency"`
}

func (s *Service) PurchaseCatalog(ctx context.Context, req CatalogPurchaseRequest) (CatalogPurchaseResponse, *AppError, error) {
	if !financeRequestID.MatchString(strings.ToLower(req.RequestID)) || req.UserID == 0 ||
		(req.ItemType != "NOBLE" && req.ItemType != "COSTUME") || !catalogItemID.MatchString(req.ItemID) ||
		req.PriceCoins < 0 || req.PriceCoins > 1000000000 {
		return CatalogPurchaseResponse{}, &AppError{HTTPStatus: 400, Code: "INVALID_CATALOG_PURCHASE", Message: "catalog purchase is invalid"}, nil
	}
	req.RequestID = strings.ToLower(req.RequestID)
	_, bodyHash, err := BodyHashHex(req)
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}

	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	defer tx.Rollback()
	// Serialize same-user purchases before checking idempotency so concurrent retries replay the committed purchase.
	balance, err := lockCoinBalance(ctx, tx, req.UserID)
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}

	var existing CatalogPurchaseResponse
	var existingHash []byte
	err = tx.QueryRowContext(ctx, `
		SELECT purchase_id,request_id,user_id,item_type,item_id,price_coins,new_balance,status,body_hash
		FROM social_catalog_purchases WHERE user_id=? AND request_id=? FOR UPDATE`,
		req.UserID, req.RequestID).Scan(&existing.PurchaseID, &existing.RequestID, &existing.UserID,
		&existing.ItemType, &existing.ItemID, &existing.PriceCoins, &existing.NewBalance, &existing.Status, &existingHash)
	if err == nil {
		if !bytesEqual(existingHash, bodyHash) {
			return CatalogPurchaseResponse{}, &AppError{HTTPStatus: 409, Code: "IDEMPOTENCY_PAYLOAD_MISMATCH", Message: "catalog purchase was requested with different data"}, nil
		}
		if existing.Status == "REVERSED" {
			return CatalogPurchaseResponse{}, &AppError{HTTPStatus: 409, Code: "PURCHASE_REVERSED", Message: "this purchase was reversed; create a new request"}, nil
		}
		if err := tx.Commit(); err != nil {
			return CatalogPurchaseResponse{}, nil, err
		}
		existing.Idempotency = "replayed"
		return existing, nil, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return CatalogPurchaseResponse{}, nil, err
	}

	if balance < req.PriceCoins {
		return CatalogPurchaseResponse{}, &AppError{HTTPStatus: 409, Code: "INSUFFICIENT_BALANCE", Message: "insufficient coin balance"}, nil
	}
	newBalance := balance - req.PriceCoins
	txType := txTypeNoblePurchase
	if req.ItemType == "COSTUME" {
		txType = txTypeCostumePurchase
	}
	txID, err := postLedger(ctx, tx, txType, req.UserID, req.RequestID, bodyHash, newBalance, []LedgerEntry{
		{AccountCode: fmt.Sprintf("USER_COINS:%d", req.UserID), Amount: -req.PriceCoins},
		{AccountCode: "PLATFORM_CATALOG_REVENUE", Amount: req.PriceCoins},
	})
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	if err = setCoinBalance(ctx, tx, req.UserID, newBalance); err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	result, err := tx.ExecContext(ctx, `
		INSERT INTO social_catalog_purchases(user_id,request_id,item_type,item_id,price_coins,body_hash,tx_id,new_balance,status,created_at)
		VALUES(?,?,?,?,?,?,?,?, 'DEBITED',?)`, req.UserID, req.RequestID, req.ItemType, req.ItemID, req.PriceCoins, bodyHash, txID, newBalance, time.Now().UTC())
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	purchaseID, err := result.LastInsertId()
	if err != nil || purchaseID < 1 {
		return CatalogPurchaseResponse{}, nil, fmt.Errorf("invalid catalog purchase id")
	}
	if err = tx.Commit(); err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	return CatalogPurchaseResponse{PurchaseID: uint64(purchaseID), RequestID: req.RequestID, UserID: req.UserID,
		ItemType: req.ItemType, ItemID: req.ItemID, PriceCoins: req.PriceCoins, NewBalance: newBalance, Status: "DEBITED", Idempotency: "created"}, nil, nil
}

type CatalogPurchaseReverseRequest struct {
	RequestID string `json:"request_id"`
	UserID    uint64 `json:"user_id"`
	Reason    string `json:"reason"`
}

func (s *Service) ReverseCatalogPurchase(ctx context.Context, req CatalogPurchaseReverseRequest) (CatalogPurchaseResponse, *AppError, error) {
	if !financeRequestID.MatchString(strings.ToLower(req.RequestID)) || req.UserID == 0 || strings.TrimSpace(req.Reason) == "" || len([]rune(req.Reason)) > 200 {
		return CatalogPurchaseResponse{}, &AppError{HTTPStatus: 400, Code: "INVALID_CATALOG_REVERSE", Message: "catalog reversal is invalid"}, nil
	}
	req.RequestID = strings.ToLower(req.RequestID)
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	defer tx.Rollback()
	// Keep the same wallet -> purchase lock order as PurchaseCatalog.
	balance, err := lockCoinBalance(ctx, tx, req.UserID)
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	var result CatalogPurchaseResponse
	err = tx.QueryRowContext(ctx, `
		SELECT purchase_id,request_id,user_id,item_type,item_id,price_coins,new_balance,status
		FROM social_catalog_purchases WHERE user_id=? AND request_id=? FOR UPDATE`, req.UserID, req.RequestID).Scan(
		&result.PurchaseID, &result.RequestID, &result.UserID, &result.ItemType, &result.ItemID, &result.PriceCoins, &result.NewBalance, &result.Status)
	if errors.Is(err, sql.ErrNoRows) {
		return CatalogPurchaseResponse{}, &AppError{HTTPStatus: 404, Code: "PURCHASE_NOT_FOUND", Message: "catalog purchase not found"}, nil
	}
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	if result.Status == "REVERSED" {
		if err := tx.Commit(); err != nil {
			return CatalogPurchaseResponse{}, nil, err
		}
		result.Idempotency = "replayed"
		return result, nil, nil
	}
	newBalance := balance + result.PriceCoins
	if newBalance < balance {
		return CatalogPurchaseResponse{}, &AppError{HTTPStatus: 409, Code: "BALANCE_LIMIT_EXCEEDED", Message: "reversal exceeds balance limit"}, nil
	}
	_, bodyHash, err := BodyHashHex(req)
	if err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	if _, err = postLedger(ctx, tx, txTypeCatalogPurchaseBack, req.UserID, req.RequestID, bodyHash, newBalance, []LedgerEntry{
		{AccountCode: fmt.Sprintf("USER_COINS:%d", req.UserID), Amount: result.PriceCoins},
		{AccountCode: "PLATFORM_CATALOG_REVENUE", Amount: -result.PriceCoins},
	}); err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	if err = setCoinBalance(ctx, tx, req.UserID, newBalance); err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	if _, err = tx.ExecContext(ctx, `UPDATE social_catalog_purchases SET status='REVERSED',new_balance=?,updated_at=? WHERE user_id=? AND request_id=?`, newBalance, time.Now().UTC(), req.UserID, req.RequestID); err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	result.NewBalance, result.Status, result.Idempotency = newBalance, "REVERSED", "created"
	if err = tx.Commit(); err != nil {
		return CatalogPurchaseResponse{}, nil, err
	}
	return result, nil, nil
}
