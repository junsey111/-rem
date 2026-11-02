package service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

const (
	txTypeDmGiftDebit   = "DM_GIFT_DEBIT"
	txTypeDmVideoUnlock = "DM_VIDEO_UNLOCK"
)

// DmGiftRequest is the Billd private-message gift body.
type DmGiftRequest struct {
	RequestID   string `json:"request_id"`
	ViewerID    uint64 `json:"viewer_id"`
	RecipientID uint64 `json:"recipient_id"`
	GiftID      string `json:"gift_id"`
	Quantity    uint16 `json:"quantity"`
	SentAtMS    int64  `json:"sent_at_ms"`
}

// DmGiftReceipt is the JSON body Billd interpretDmGift reads directly.
type DmGiftReceipt struct {
	Accepted     bool   `json:"accepted"`
	Status       string `json:"status"`
	GiftOrderID  string `json:"gift_order_id"`
	GiftID       string `json:"gift_id"`
	GiftName     string `json:"gift_name"`
	Quantity     uint16 `json:"quantity"`
	RequestID    string `json:"request_id"`
	ChargedCoins uint32 `json:"charged_coins"`
	NewBalance   int64  `json:"new_balance"`
	Idempotency  string `json:"idempotency,omitempty"`
}

type dmGiftIdentity struct {
	RequestID   string `json:"request_id"`
	ViewerID    uint64 `json:"viewer_id"`
	RecipientID uint64 `json:"recipient_id"`
	GiftID      string `json:"gift_id"`
	Quantity    uint16 `json:"quantity"`
	SentAtMS    int64  `json:"sent_at_ms"`
}

// DmUnlockRequest is the Billd paid-video settlement body. video_id is opaque.
type DmUnlockRequest struct {
	RequestID  string `json:"request_id"`
	ViewerID   uint64 `json:"viewer_id"`
	VideoID    string `json:"video_id"`
	OwnerID    uint64 `json:"owner_id"`
	PriceCoins int64  `json:"price_coins"`
	SentAtMS   int64  `json:"sent_at_ms"`
}

// DmUnlockReceipt is the JSON body Billd interpretUnlock reads directly.
type DmUnlockReceipt struct {
	Accepted     bool   `json:"accepted"`
	Unlocked     bool   `json:"unlocked"`
	VideoID      string `json:"video_id"`
	RequestID    string `json:"request_id"`
	ChargedCoins uint32 `json:"charged_coins"`
	NewBalance   int64  `json:"new_balance"`
	Idempotency  string `json:"idempotency,omitempty"`
}

type dmUnlockIdentity struct {
	RequestID  string `json:"request_id"`
	ViewerID   uint64 `json:"viewer_id"`
	VideoID    string `json:"video_id"`
	OwnerID    uint64 `json:"owner_id"`
	PriceCoins int64  `json:"price_coins"`
	SentAtMS   int64  `json:"sent_at_ms"`
}

// DmAccess is GET /v1/dm/videos/:id/access.
type DmAccess struct {
	Unlocked bool   `json:"unlocked"`
	VideoID  string `json:"video_id"`
}

func dmGiftName(displayName, giftID string) string {
	name := strings.TrimSpace(displayName)
	if name == "" {
		name = strings.TrimSpace(giftID)
	}
	if utf8.RuneCountInString(name) > 40 {
		runes := []rune(name)
		name = string(runes[:40])
	}
	if name == "" {
		return "gift"
	}
	return name
}

func dmGiftReject(code string) *AppError {
	switch code {
	case "SELF_GIFT":
		return &AppError{HTTPStatus: 400, Code: code, Message: "cannot send a gift to yourself"}
	case "RECIPIENT_NOT_ELIGIBLE", "CREATOR_NOT_ELIGIBLE":
		return &AppError{HTTPStatus: 403, Code: code, Message: "recipient is not eligible to receive gifts"}
	case "GIFT_NOT_FOUND":
		return &AppError{HTTPStatus: 404, Code: code, Message: "gift not found"}
	case "INSUFFICIENT_BALANCE":
		return &AppError{HTTPStatus: 409, Code: code, Message: "insufficient balance"}
	default:
		return &AppError{HTTPStatus: 409, Code: code, Message: "gift rejected"}
	}
}

func dmMismatch() *AppError {
	return &AppError{HTTPStatus: 409, Code: "IDEMPOTENCY_PAYLOAD_MISMATCH", Message: "same request_id with different payload"}
}

// SendDmGift debits the viewer coin wallet and records recipient diamonds.
// Coins move from USER_COINS to PLATFORM_RESERVED_COINS, the same accounts live gifts use.
func (s *Service) SendDmGift(ctx context.Context, req DmGiftRequest) (DmGiftReceipt, *AppError, error) {
	req.RequestID = strings.ToLower(strings.TrimSpace(req.RequestID))
	req.GiftID = strings.TrimSpace(req.GiftID)
	if !financeRequestID.MatchString(req.RequestID) || req.ViewerID == 0 || req.RecipientID == 0 || req.GiftID == "" || len(req.GiftID) > 64 {
		return DmGiftReceipt{}, errValidation, nil
	}
	if req.Quantity < 1 || req.Quantity > 99 {
		return DmGiftReceipt{}, &AppError{HTTPStatus: 400, Code: "VALIDATION_ERROR", Message: "quantity must be in [1,99]"}, nil
	}
	if req.SentAtMS < 0 {
		return DmGiftReceipt{}, errValidation, nil
	}
	_, bodyHash, err := BodyHashHex(dmGiftIdentity{
		RequestID: req.RequestID, ViewerID: req.ViewerID, RecipientID: req.RecipientID,
		GiftID: req.GiftID, Quantity: req.Quantity, SentAtMS: req.SentAtMS,
	})
	if err != nil {
		return DmGiftReceipt{}, nil, err
	}

	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return DmGiftReceipt{}, nil, err
	}
	defer func() { _ = tx.Rollback() }()

	receipt, appErr, err := loadDmGift(ctx, tx, req.ViewerID, req.RequestID, bodyHash, true)
	if err != nil || appErr != nil || receipt != nil {
		if receipt != nil && appErr == nil && err == nil {
			if commitErr := tx.Commit(); commitErr != nil {
				return DmGiftReceipt{}, nil, commitErr
			}
			return *receipt, nil, nil
		}
		return DmGiftReceipt{}, appErr, err
	}

	if req.ViewerID == req.RecipientID {
		return s.finishDmGiftReject(ctx, tx, req, bodyHash, "SELF_GIFT", "")
	}
	var recipientExists int
	err = tx.QueryRowContext(ctx, `SELECT 1 FROM users WHERE user_id=?`, req.RecipientID).Scan(&recipientExists)
	if errors.Is(err, sql.ErrNoRows) {
		return DmGiftReceipt{}, dmGiftReject("RECIPIENT_NOT_ELIGIBLE"), nil
	}
	if err != nil {
		return DmGiftReceipt{}, nil, err
	}

	var price, diamonds int64
	var enabled bool
	var displayName string
	err = tx.QueryRowContext(ctx, `
		SELECT coin_price, diamond_reward, enabled, display_name
		FROM gift_catalog WHERE gift_id=?
	`, req.GiftID).Scan(&price, &diamonds, &enabled, &displayName)
	if errors.Is(err, sql.ErrNoRows) || (err == nil && (!enabled || price <= 0)) {
		return s.finishDmGiftReject(ctx, tx, req, bodyHash, "GIFT_NOT_FOUND", req.GiftID)
	}
	if err != nil {
		return DmGiftReceipt{}, nil, err
	}
	name := dmGiftName(displayName, req.GiftID)
	charged := price * int64(req.Quantity)
	diamondTotal := diamonds * int64(req.Quantity)
	if charged < 0 || diamondTotal < 0 || charged > int64(^uint32(0)) || diamondTotal > int64(^uint32(0)) {
		return DmGiftReceipt{}, nil, fmt.Errorf("dm gift amount overflow")
	}

	eligible, err := s.isCreatorEligible(ctx, tx, req.RecipientID)
	if err != nil {
		return DmGiftReceipt{}, nil, err
	}
	if !eligible {
		return s.finishDmGiftReject(ctx, tx, req, bodyHash, "RECIPIENT_NOT_ELIGIBLE", name)
	}

	balance, err := lockCoinBalance(ctx, tx, req.ViewerID)
	if err != nil {
		return DmGiftReceipt{}, nil, err
	}
	if balance < charged {
		// Leave no row so the same request_id can be paid after a top-up.
		return DmGiftReceipt{}, dmGiftReject("INSUFFICIENT_BALANCE"), nil
	}
	newBalance := balance - charged
	txID, err := postLedger(ctx, tx, txTypeDmGiftDebit, req.ViewerID, req.RequestID, bodyHash, newBalance, []LedgerEntry{
		{AccountCode: fmt.Sprintf("USER_COINS:%d", req.ViewerID), Amount: -charged},
		{AccountCode: "PLATFORM_RESERVED_COINS", Amount: charged},
	})
	if err != nil {
		if isDupKey(err) {
			again, appErr, replayErr := loadDmGift(ctx, tx, req.ViewerID, req.RequestID, bodyHash, true)
			if replayErr != nil || appErr != nil || again == nil {
				return DmGiftReceipt{}, appErr, replayErr
			}
			if commitErr := tx.Commit(); commitErr != nil {
				return DmGiftReceipt{}, nil, commitErr
			}
			return *again, nil, nil
		}
		return DmGiftReceipt{}, nil, err
	}
	if err = setCoinBalance(ctx, tx, req.ViewerID, newBalance); err != nil {
		return DmGiftReceipt{}, nil, err
	}
	res, err := tx.ExecContext(ctx, `
		INSERT INTO dm_gift_orders (
			viewer_id, recipient_id, request_id, gift_id, gift_name, quantity,
			charged_coins, diamond_reward, post_balance, status, reject_code,
			sent_at_ms, body_hash, tx_id, created_at
		) VALUES (?,?,?,?,?,?,?,?,?,'ACCEPTED',NULL,?,?,?,?)
	`, req.ViewerID, req.RecipientID, req.RequestID, req.GiftID, name, req.Quantity,
		charged, diamondTotal, newBalance, req.SentAtMS, bodyHash, txID, time.Now().UTC())
	if err != nil {
		if isDupKey(err) {
			again, appErr, replayErr := loadDmGift(ctx, tx, req.ViewerID, req.RequestID, bodyHash, true)
			if replayErr != nil || appErr != nil || again == nil {
				return DmGiftReceipt{}, appErr, replayErr
			}
			if commitErr := tx.Commit(); commitErr != nil {
				return DmGiftReceipt{}, nil, commitErr
			}
			return *again, nil, nil
		}
		return DmGiftReceipt{}, nil, err
	}
	orderID, err := res.LastInsertId()
	if err != nil || orderID < 1 {
		return DmGiftReceipt{}, nil, fmt.Errorf("invalid dm gift order id")
	}
	if err = tx.Commit(); err != nil {
		return DmGiftReceipt{}, nil, err
	}
	return DmGiftReceipt{
		Accepted: true, Status: "ACCEPTED", GiftOrderID: strconv.FormatInt(orderID, 10),
		GiftID: req.GiftID, GiftName: name, Quantity: req.Quantity, RequestID: req.RequestID,
		ChargedCoins: uint32(charged), NewBalance: newBalance, Idempotency: "created",
	}, nil, nil
}

func (s *Service) finishDmGiftReject(ctx context.Context, tx *sql.Tx, req DmGiftRequest, bodyHash []byte, code, name string) (DmGiftReceipt, *AppError, error) {
	if name == "" {
		name = dmGiftName("", req.GiftID)
	}
	_, err := tx.ExecContext(ctx, `
		INSERT INTO dm_gift_orders (
			viewer_id, recipient_id, request_id, gift_id, gift_name, quantity,
			charged_coins, diamond_reward, post_balance, status, reject_code,
			sent_at_ms, body_hash, tx_id, created_at
		) VALUES (?,?,?,?,?,?,0,0,NULL,'REJECTED',?,?,?,NULL,?)
	`, req.ViewerID, req.RecipientID, req.RequestID, req.GiftID, name, req.Quantity, code, req.SentAtMS, bodyHash, time.Now().UTC())
	if err != nil {
		if isDupKey(err) {
			again, appErr, replayErr := loadDmGift(ctx, tx, req.ViewerID, req.RequestID, bodyHash, false)
			if replayErr != nil {
				return DmGiftReceipt{}, nil, replayErr
			}
			if again != nil {
				if commitErr := tx.Commit(); commitErr != nil {
					return DmGiftReceipt{}, nil, commitErr
				}
				return *again, nil, nil
			}
			if appErr != nil {
				if commitErr := tx.Commit(); commitErr != nil {
					return DmGiftReceipt{}, nil, commitErr
				}
				return DmGiftReceipt{}, appErr, nil
			}
		}
		return DmGiftReceipt{}, nil, err
	}
	if err = tx.Commit(); err != nil {
		return DmGiftReceipt{}, nil, err
	}
	return DmGiftReceipt{}, dmGiftReject(code), nil
}

func loadDmGift(ctx context.Context, tx *sql.Tx, viewer uint64, requestID string, bodyHash []byte, markReplay bool) (*DmGiftReceipt, *AppError, error) {
	var (
		orderID      uint64
		giftID       string
		name         string
		quantity     uint16
		charged      uint32
		balance      sql.NullInt64
		status       string
		reject       sql.NullString
		existingHash []byte
		storedReq    string
	)
	err := tx.QueryRowContext(ctx, `
		SELECT gift_order_id, request_id, gift_id, gift_name, quantity, charged_coins, post_balance, status, reject_code, body_hash
		FROM dm_gift_orders WHERE viewer_id=? AND request_id=? FOR UPDATE
	`, viewer, requestID).Scan(&orderID, &storedReq, &giftID, &name, &quantity, &charged, &balance, &status, &reject, &existingHash)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil, nil
	}
	if err != nil {
		return nil, nil, err
	}
	if bodyHash != nil && !bytesEqual(existingHash, bodyHash) {
		return nil, dmMismatch(), nil
	}
	if status != "ACCEPTED" {
		code := "REJECTED"
		if reject.Valid && reject.String != "" {
			code = reject.String
		}
		return nil, dmGiftReject(code), nil
	}
	idempotency := ""
	if markReplay {
		idempotency = "replayed"
	}
	post := int64(0)
	if balance.Valid {
		post = balance.Int64
	}
	return &DmGiftReceipt{
		Accepted: true, Status: "ACCEPTED", GiftOrderID: strconv.FormatUint(orderID, 10),
		GiftID: giftID, GiftName: name, Quantity: quantity, RequestID: storedReq,
		ChargedCoins: charged, NewBalance: post, Idempotency: idempotency,
	}, nil, nil
}

// GetDmGift returns the caller's accepted DM gift receipt.
func (s *Service) GetDmGift(ctx context.Context, viewer uint64, requestID string) (DmGiftReceipt, *AppError, error) {
	requestID = strings.ToLower(strings.TrimSpace(requestID))
	if viewer == 0 || !financeRequestID.MatchString(requestID) {
		return DmGiftReceipt{}, &AppError{HTTPStatus: 400, Code: "INVALID_REQUEST_ID", Message: "invalid request id"}, nil
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return DmGiftReceipt{}, nil, err
	}
	defer func() { _ = tx.Rollback() }()
	receipt, appErr, err := loadDmGift(ctx, tx, viewer, requestID, nil, true)
	if err != nil {
		return DmGiftReceipt{}, nil, err
	}
	if appErr != nil || receipt == nil {
		return DmGiftReceipt{}, &AppError{HTTPStatus: 404, Code: "GIFT_RECORD_NOT_FOUND", Message: "no confirmed dm gift for this account"}, nil
	}
	return *receipt, nil, nil
}

// UnlockDmVideo settles a private video. The bytes stay on Billd.
func (s *Service) UnlockDmVideo(ctx context.Context, req DmUnlockRequest) (DmUnlockReceipt, *AppError, error) {
	req.RequestID = strings.ToLower(strings.TrimSpace(req.RequestID))
	req.VideoID = strings.TrimSpace(req.VideoID)
	if !financeRequestID.MatchString(req.RequestID) || req.ViewerID == 0 {
		return DmUnlockReceipt{}, errValidation, nil
	}
	if req.SentAtMS < 0 || req.PriceCoins < 0 || req.PriceCoins > 1000000 {
		return DmUnlockReceipt{}, &AppError{HTTPStatus: 400, Code: "VALIDATION_ERROR", Message: "invalid unlock price"}, nil
	}
	if req.VideoID == "" || len(req.VideoID) > 64 || strings.ContainsAny(req.VideoID, " /\\") || req.OwnerID == 0 {
		return DmUnlockReceipt{}, &AppError{HTTPStatus: 404, Code: "VIDEO_NOT_FOUND", Message: "video not found"}, nil
	}
	_, bodyHash, err := BodyHashHex(dmUnlockIdentity{
		RequestID: req.RequestID, ViewerID: req.ViewerID, VideoID: req.VideoID,
		OwnerID: req.OwnerID, PriceCoins: req.PriceCoins, SentAtMS: req.SentAtMS,
	})
	if err != nil {
		return DmUnlockReceipt{}, nil, err
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return DmUnlockReceipt{}, nil, err
	}
	defer func() { _ = tx.Rollback() }()

	existing, appErr, err := loadDmUnlock(ctx, tx, req.ViewerID, req.RequestID, bodyHash)
	if err != nil || appErr != nil {
		return DmUnlockReceipt{}, appErr, err
	}
	if existing != nil {
		if err = tx.Commit(); err != nil {
			return DmUnlockReceipt{}, nil, err
		}
		return *existing, nil, nil
	}

	var ownerKnown int
	err = tx.QueryRowContext(ctx, `SELECT 1 FROM users WHERE user_id=?`, req.OwnerID).Scan(&ownerKnown)
	if errors.Is(err, sql.ErrNoRows) {
		return DmUnlockReceipt{}, &AppError{HTTPStatus: 404, Code: "VIDEO_NOT_FOUND", Message: "video not found"}, nil
	}
	if err != nil {
		return DmUnlockReceipt{}, nil, err
	}

	_, err = tx.ExecContext(ctx, `
		INSERT INTO dm_video_quotes (video_id, owner_id, price_coins, created_at) VALUES (?,?,?,?)
	`, req.VideoID, req.OwnerID, req.PriceCoins, time.Now().UTC())
	if err != nil {
		if !isDupKey(err) {
			return DmUnlockReceipt{}, nil, err
		}
		var owner uint64
		var price int64
		err = tx.QueryRowContext(ctx, `SELECT owner_id, price_coins FROM dm_video_quotes WHERE video_id=? FOR UPDATE`, req.VideoID).Scan(&owner, &price)
		if err != nil {
			return DmUnlockReceipt{}, nil, err
		}
		if price != req.PriceCoins {
			return DmUnlockReceipt{}, &AppError{HTTPStatus: 409, Code: "PRICE_MISMATCH", Message: "unlock price does not match the settled price"}, nil
		}
		if owner != req.OwnerID {
			return DmUnlockReceipt{}, &AppError{HTTPStatus: 404, Code: "VIDEO_NOT_FOUND", Message: "video not found"}, nil
		}
	} else if _, err = tx.ExecContext(ctx, `SELECT video_id FROM dm_video_quotes WHERE video_id=? FOR UPDATE`, req.VideoID); err != nil {
		return DmUnlockReceipt{}, nil, err
	}

	var prior int
	err = tx.QueryRowContext(ctx, `
		SELECT 1 FROM dm_video_unlocks WHERE viewer_id=? AND video_id=? LIMIT 1 FOR UPDATE
	`, req.ViewerID, req.VideoID).Scan(&prior)
	already := err == nil
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return DmUnlockReceipt{}, nil, err
	}

	charge := req.PriceCoins
	idempotency := "created"
	if already || req.ViewerID == req.OwnerID {
		charge = 0
		idempotency = "replayed"
	} else if req.PriceCoins == 0 {
		charge = 0
	}
	var newBalance int64
	var txID any
	if charge > 0 {
		balance, lockErr := lockCoinBalance(ctx, tx, req.ViewerID)
		if lockErr != nil {
			return DmUnlockReceipt{}, nil, lockErr
		}
		if balance < charge {
			return DmUnlockReceipt{}, &AppError{HTTPStatus: 409, Code: "INSUFFICIENT_BALANCE", Message: "insufficient balance"}, nil
		}
		newBalance = balance - charge
		posted, postErr := postLedger(ctx, tx, txTypeDmVideoUnlock, req.ViewerID, req.RequestID, bodyHash, newBalance, []LedgerEntry{
			{AccountCode: fmt.Sprintf("USER_COINS:%d", req.ViewerID), Amount: -charge},
			{AccountCode: "PLATFORM_RESERVED_COINS", Amount: charge},
		})
		if postErr != nil {
			return DmUnlockReceipt{}, nil, postErr
		}
		if err = setCoinBalance(ctx, tx, req.ViewerID, newBalance); err != nil {
			return DmUnlockReceipt{}, nil, err
		}
		txID = posted
	} else if err = tx.QueryRowContext(ctx, `
		SELECT COALESCE(available_balance,0) FROM wallet_accounts WHERE user_id=? AND currency='COIN'
	`, req.ViewerID).Scan(&newBalance); err != nil && !errors.Is(err, sql.ErrNoRows) {
		return DmUnlockReceipt{}, nil, err
	}

	diamond := charge
	_, err = tx.ExecContext(ctx, `
		INSERT INTO dm_video_unlocks (
			viewer_id, owner_id, video_id, request_id, price_coins, charged_coins, diamond_reward,
			post_balance, body_hash, tx_id, created_at
		) VALUES (?,?,?,?,?,?,?,?,?,?,?)
	`, req.ViewerID, req.OwnerID, req.VideoID, req.RequestID, req.PriceCoins, charge, diamond, newBalance, bodyHash, txID, time.Now().UTC())
	if err != nil {
		if isDupKey(err) {
			again, appErr, replayErr := loadDmUnlock(ctx, tx, req.ViewerID, req.RequestID, bodyHash)
			if replayErr != nil || appErr != nil || again == nil {
				return DmUnlockReceipt{}, appErr, replayErr
			}
			if commitErr := tx.Commit(); commitErr != nil {
				return DmUnlockReceipt{}, nil, commitErr
			}
			return *again, nil, nil
		}
		return DmUnlockReceipt{}, nil, err
	}
	if err = tx.Commit(); err != nil {
		return DmUnlockReceipt{}, nil, err
	}
	return DmUnlockReceipt{
		Accepted: true, Unlocked: true, VideoID: req.VideoID, RequestID: req.RequestID,
		ChargedCoins: uint32(charge), NewBalance: newBalance, Idempotency: idempotency,
	}, nil, nil
}

func loadDmUnlock(ctx context.Context, tx *sql.Tx, viewer uint64, requestID string, bodyHash []byte) (*DmUnlockReceipt, *AppError, error) {
	var videoID, storedReq string
	var charged uint32
	var balance sql.NullInt64
	var existing []byte
	err := tx.QueryRowContext(ctx, `
		SELECT video_id, request_id, charged_coins, post_balance, body_hash
		FROM dm_video_unlocks WHERE viewer_id=? AND request_id=? FOR UPDATE
	`, viewer, requestID).Scan(&videoID, &storedReq, &charged, &balance, &existing)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil, nil
	}
	if err != nil {
		return nil, nil, err
	}
	if bodyHash != nil && !bytesEqual(existing, bodyHash) {
		return nil, dmMismatch(), nil
	}
	post := int64(0)
	if balance.Valid {
		post = balance.Int64
	}
	return &DmUnlockReceipt{
		Accepted: true, Unlocked: true, VideoID: videoID, RequestID: storedReq,
		ChargedCoins: charged, NewBalance: post, Idempotency: "replayed",
	}, nil, nil
}

// DmVideoAccess reports whether the caller may play a settled private video.
func (s *Service) DmVideoAccess(ctx context.Context, viewer uint64, videoID string) (DmAccess, *AppError, error) {
	videoID = strings.TrimSpace(videoID)
	if viewer == 0 || videoID == "" || len(videoID) > 64 {
		return DmAccess{}, &AppError{HTTPStatus: 400, Code: "VALIDATION_ERROR", Message: "invalid video id"}, nil
	}
	var unlocked int
	err := s.db.QueryRowContext(ctx, `
		SELECT 1 FROM dm_video_unlocks WHERE viewer_id=? AND video_id=?
		UNION
		SELECT 1 FROM dm_video_quotes WHERE video_id=? AND owner_id=?
		LIMIT 1
	`, viewer, videoID, videoID, viewer).Scan(&unlocked)
	if errors.Is(err, sql.ErrNoRows) {
		return DmAccess{Unlocked: false, VideoID: videoID}, nil, nil
	}
	if err != nil {
		return DmAccess{}, nil, err
	}
	return DmAccess{Unlocked: true, VideoID: videoID}, nil, nil
}
