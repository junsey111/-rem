package service

import (
	"context"
	"database/sql"
	"errors"
	"strconv"
)

const (
	FreePreviewSeconds = 60
	watchDeltaMin      = 1
	watchDeltaMax      = 30
	txTypeWatchUnlock  = "WATCH_UNLOCK"
)

// WatchAccess is the Android poll contract for paid viewing.
type WatchAccess struct {
	LiveID               string `json:"live_id"`
	FreePreviewSeconds   int    `json:"free_preview_seconds"`
	WatchedUnpaidSeconds int    `json:"watched_unpaid_seconds"`
	SecondsRemaining     int    `json:"seconds_remaining"`
	Unlocked             bool   `json:"unlocked"`
	UnlockPriceCoins     int    `json:"unlock_price_coins"`
	Status               string `json:"status"`
}

// ClampWatchDelta forces a heartbeat delta into 1..30.
func ClampWatchDelta(delta int) int {
	if delta < watchDeltaMin {
		return watchDeltaMin
	}
	if delta > watchDeltaMax {
		return watchDeltaMax
	}
	return delta
}

// DeriveWatchAccess maps stored progress to the status Android polls.
// seconds_remaining is max(0, 60-watched) and is forced to 0 when LOCKED.
func DeriveWatchAccess(liveID string, price, watched int, unlocked bool) WatchAccess {
	if watched < 0 {
		watched = 0
	}
	remaining := FreePreviewSeconds - watched
	if remaining < 0 {
		remaining = 0
	}
	status := "PREVIEW"
	switch {
	case price <= 0:
		status = "FREE"
	case unlocked:
		status = "UNLOCKED"
	case watched >= FreePreviewSeconds:
		status = "LOCKED"
		remaining = 0
	}
	return WatchAccess{
		LiveID:               liveID,
		FreePreviewSeconds:   FreePreviewSeconds,
		WatchedUnpaidSeconds: watched,
		SecondsRemaining:     remaining,
		Unlocked:             unlocked,
		UnlockPriceCoins:     price,
		Status:               status,
	}
}

type watchIdentity struct {
	RequestID     string `json:"request_id"`
	LiveSessionID uint64 `json:"live_session_id"`
	DeltaSeconds  *int   `json:"delta_seconds,omitempty"`
}

func (s *Service) loadWatchState(ctx context.Context, tx *sql.Tx, userID, liveID uint64) (creatorID uint64, status string, price, watched int, unlocked bool, err error) {
	err = tx.QueryRowContext(ctx, `SELECT creator_id, status FROM live_sessions WHERE live_session_id=?`, liveID).Scan(&creatorID, &status)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, "", 0, 0, false, errNotFound("LIVE_SESSION_NOT_FOUND", "直播间不存在")
	}
	if err != nil {
		return 0, "", 0, 0, false, err
	}
	err = tx.QueryRowContext(ctx, `SELECT unlock_price_coins FROM live_watch_prices WHERE live_session_id=?`, liveID).Scan(&price)
	if errors.Is(err, sql.ErrNoRows) {
		price = 0
		err = nil
	}
	if err != nil {
		return 0, "", 0, 0, false, err
	}
	if _, err = tx.ExecContext(ctx, `
		INSERT INTO live_watch_progress (user_id, live_session_id, watched_unpaid_seconds, unlocked)
		VALUES (?, ?, 0, 0)
		ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
	`, userID, liveID); err != nil {
		return 0, "", 0, 0, false, err
	}
	// A media-started deadline is authoritative even when the client omits heartbeats.
	var unlockedBit int
	err = tx.QueryRowContext(ctx, `
		SELECT GREATEST(watched_unpaid_seconds, IFNULL(TIMESTAMPDIFF(SECOND, preview_started_at, UTC_TIMESTAMP(6)), 0)), unlocked FROM live_watch_progress
		WHERE user_id=? AND live_session_id=? FOR UPDATE
	`, userID, liveID).Scan(&watched, &unlockedBit)
	unlocked = unlockedBit != 0
	return creatorID, status, price, watched, unlocked, err
}

func errNotFound(code, message string) error {
	return &AppError{HTTPStatus: 404, Code: code, Message: message}
}

func (s *Service) WatchAccess(ctx context.Context, userID, liveID uint64) (WatchAccess, error) {
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return WatchAccess{}, err
	}
	defer tx.Rollback()
	creatorID, status, price, watched, unlocked, err := s.loadWatchState(ctx, tx, userID, liveID)
	if err != nil {
		return WatchAccess{}, err
	}
	if status != "OPEN" {
		return WatchAccess{}, &AppError{HTTPStatus: 409, Code: "LIVE_SESSION_CLOSED", Message: "直播已结束"}
	}
	if err = tx.Commit(); err != nil {
		return WatchAccess{}, err
	}
	return DeriveWatchAccess(strconv.FormatUint(liveID, 10), price, watched, unlocked || creatorID == userID), nil
}

func (s *Service) SetWatchPrice(ctx context.Context, userID, liveID uint64, price int) error {
	if price < 0 || price > 1000000 {
		return &AppError{HTTPStatus: 400, Code: "INVALID_PRICE", Message: "付费观看价格需为 0 至 1000000 金币"}
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var creatorID uint64
	var status string
	err = tx.QueryRowContext(ctx, `SELECT creator_id, status FROM live_sessions WHERE live_session_id=? FOR UPDATE`, liveID).Scan(&creatorID, &status)
	if errors.Is(err, sql.ErrNoRows) {
		return errNotFound("LIVE_SESSION_NOT_FOUND", "直播间不存在")
	}
	if err != nil {
		return err
	}
	if creatorID != userID {
		return &AppError{HTTPStatus: 403, Code: "FORBIDDEN", Message: "只有主播可以设置付费观看价格"}
	}
	if status != "OPEN" {
		return &AppError{HTTPStatus: 409, Code: "LIVE_SESSION_CLOSED", Message: "直播已结束，不能修改观看价格"}
	}
	if _, err = tx.ExecContext(ctx, `
		INSERT INTO live_watch_prices (live_session_id, unlock_price_coins, version) VALUES (?, ?, 1)
		ON DUPLICATE KEY UPDATE unlock_price_coins=VALUES(unlock_price_coins),version=version+1
	`, liveID, price); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Service) WatchHeartbeat(ctx context.Context, userID, liveID uint64, requestID string, delta int) (WatchAccess, error) {
	if !financeRequestID.MatchString(requestID) {
		return WatchAccess{}, &AppError{HTTPStatus: 400, Code: "INVALID_REQUEST_ID", Message: "观看心跳单号无效，请使用 UUID v4"}
	}
	deltaCopy := delta
	_, bodyHash, err := BodyHashHex(watchIdentity{RequestID: requestID, LiveSessionID: liveID, DeltaSeconds: &deltaCopy})
	if err != nil {
		return WatchAccess{}, err
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return WatchAccess{}, err
	}
	defer tx.Rollback()
	creatorID, status, price, watched, unlocked, err := s.loadWatchState(ctx, tx, userID, liveID)
	if err != nil {
		return WatchAccess{}, err
	}
	if creatorID == userID {
		unlocked = true
	}
	var existingHash []byte
	var existingLive uint64
	err = tx.QueryRowContext(ctx, `SELECT live_session_id, body_hash FROM live_watch_heartbeats WHERE user_id=? AND request_id=? FOR UPDATE`, userID, requestID).Scan(&existingLive, &existingHash)
	if err == nil {
		if existingLive != liveID || !bytesEqual(existingHash, bodyHash) {
			return WatchAccess{}, &AppError{HTTPStatus: 409, Code: "IDEMPOTENCY_PAYLOAD_MISMATCH", Message: "同一心跳单号不能用于不同的观看进度"}
		}
		if err = tx.Commit(); err != nil {
			return WatchAccess{}, err
		}
		return DeriveWatchAccess(strconv.FormatUint(liveID, 10), price, watched, unlocked), nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return WatchAccess{}, err
	}
	if status != "OPEN" {
		return WatchAccess{}, &AppError{HTTPStatus: 409, Code: "LIVE_SESSION_CLOSED", Message: "直播已结束，无法继续计时"}
	}
	access := DeriveWatchAccess(strconv.FormatUint(liveID, 10), price, watched, unlocked)
	applied := 0
	if access.Status == "PREVIEW" {
		var reported int
		if err = tx.QueryRowContext(ctx, `SELECT watched_unpaid_seconds FROM live_watch_progress WHERE user_id=? AND live_session_id=?`, userID, liveID).Scan(&reported); err != nil {
			return WatchAccess{}, err
		}
		applied = ClampWatchDelta(delta)
		room := FreePreviewSeconds - reported
		if room < applied {
			applied = room
		}
		if applied < 0 {
			applied = 0
		}
		reported += applied
		if reported > watched {
			watched = reported
		}
		if _, err = tx.ExecContext(ctx, `UPDATE live_watch_progress SET watched_unpaid_seconds=? WHERE user_id=? AND live_session_id=?`, reported, userID, liveID); err != nil {
			return WatchAccess{}, err
		}
	}
	if _, err = tx.ExecContext(ctx, `
		INSERT INTO live_watch_heartbeats (user_id, request_id, live_session_id, delta_seconds, applied_seconds, body_hash, created_at)
		VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
	`, userID, requestID, liveID, delta, applied, bodyHash); err != nil {
		return WatchAccess{}, err
	}
	if err = tx.Commit(); err != nil {
		return WatchAccess{}, err
	}
	return DeriveWatchAccess(strconv.FormatUint(liveID, 10), price, watched, unlocked), nil
}

func (s *Service) WatchUnlock(ctx context.Context, userID, liveID uint64, requestID string) (WatchAccess, error) {
	if !financeRequestID.MatchString(requestID) {
		return WatchAccess{}, &AppError{HTTPStatus: 400, Code: "INVALID_REQUEST_ID", Message: "解锁单号无效，请使用 UUID v4"}
	}
	_, bodyHash, err := BodyHashHex(watchIdentity{RequestID: requestID, LiveSessionID: liveID})
	if err != nil {
		return WatchAccess{}, err
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return WatchAccess{}, err
	}
	defer tx.Rollback()
	creatorID, status, price, watched, unlocked, err := s.loadWatchState(ctx, tx, userID, liveID)
	if err != nil {
		return WatchAccess{}, err
	}
	var existingHash []byte
	var existingLive uint64
	err = tx.QueryRowContext(ctx, `SELECT live_session_id, body_hash FROM live_watch_unlocks WHERE user_id=? AND request_id=?`, userID, requestID).Scan(&existingLive, &existingHash)
	if err == nil {
		if existingLive != liveID || !bytesEqual(existingHash, bodyHash) {
			return WatchAccess{}, &AppError{HTTPStatus: 409, Code: "IDEMPOTENCY_PAYLOAD_MISMATCH", Message: "同一解锁单号不能用于其他直播间"}
		}
		if err = tx.Commit(); err != nil {
			return WatchAccess{}, err
		}
		return DeriveWatchAccess(strconv.FormatUint(liveID, 10), price, watched, true), nil
	}
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return WatchAccess{}, err
	}
	if price <= 0 || unlocked || userID == creatorID {
		if err = tx.Commit(); err != nil {
			return WatchAccess{}, err
		}
		return DeriveWatchAccess(strconv.FormatUint(liveID, 10), price, watched, unlocked || userID == creatorID), nil
	}
	if status != "OPEN" {
		return WatchAccess{}, &AppError{HTTPStatus: 409, Code: "LIVE_SESSION_CLOSED", Message: "直播已结束，无法解锁"}
	}
	balance, err := lockCoinBalance(ctx, tx, userID)
	if err != nil {
		return WatchAccess{}, err
	}
	if balance < int64(price) {
		return WatchAccess{}, &AppError{HTTPStatus: 402, Code: "INSUFFICIENT_BALANCE", Message: "余额不足，无法解锁付费观看，本次未扣币"}
	}
	newBalance := balance - int64(price)
	txID, err := postLedger(ctx, tx, txTypeWatchUnlock, userID, requestID, bodyHash, newBalance, []LedgerEntry{
		{AccountCode: "USER_COINS:" + strconv.FormatUint(userID, 10), Amount: -int64(price)},
		{AccountCode: "PLATFORM_RESERVED_COINS", Amount: int64(price)},
	})
	if err != nil {
		return WatchAccess{}, err
	}
	if err = setCoinBalance(ctx, tx, userID, newBalance); err != nil {
		return WatchAccess{}, err
	}
	if _, err = tx.ExecContext(ctx, `UPDATE live_watch_progress SET unlocked=1 WHERE user_id=? AND live_session_id=?`, userID, liveID); err != nil {
		return WatchAccess{}, err
	}
	if _, err = tx.ExecContext(ctx, `
		INSERT INTO live_watch_unlocks (user_id, request_id, live_session_id, creator_id, charged_coins, diamond_reward, post_balance, tx_id, body_hash, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
	`, userID, requestID, liveID, creatorID, price, price, newBalance, txID, bodyHash); err != nil {
		return WatchAccess{}, err
	}
	if err = tx.Commit(); err != nil {
		return WatchAccess{}, err
	}
	return DeriveWatchAccess(strconv.FormatUint(liveID, 10), price, watched, true), nil
}

// BeginMediaPreview starts one continuous 60-second window on first media access.
// Reconnects, restarts and omitted client heartbeats cannot reset that window.
func (s *Service) BeginMediaPreview(ctx context.Context, userID, liveID uint64) (WatchAccess, error) {
	access, err := s.WatchAccess(ctx, userID, liveID)
	if err != nil || access.Status != "PREVIEW" {
		return access, err
	}
	_, err = s.db.ExecContext(ctx, `UPDATE live_watch_progress SET preview_started_at=COALESCE(preview_started_at,TIMESTAMPADD(SECOND,-watched_unpaid_seconds,UTC_TIMESTAMP(6))) WHERE user_id=? AND live_session_id=?`, userID, liveID)
	if err != nil {
		return WatchAccess{}, err
	}
	return s.WatchAccess(ctx, userID, liveID)
}
