package apiserver

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func (s *Server) handleDmGifts(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	defer func() { _ = r.Body.Close() }()
	var req service.DmGiftRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid JSON body")
		return
	}
	// The Billd JWT is the payer. A body viewer_id cannot charge another wallet.
	req.ViewerID = authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), req.ViewerID); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	res, appErr, err := s.svc.SendDmGift(r.Context(), req)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	status := http.StatusCreated
	if res.Idempotency == "replayed" {
		status = http.StatusOK
	}
	httpx.WriteJSON(w, status, res)
}

func (s *Server) handleDmGiftRead(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	requestID := strings.TrimPrefix(r.URL.Path, "/v1/dm/gifts/")
	if requestID == "" || strings.Contains(requestID, "/") {
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
		return
	}
	res, appErr, err := s.svc.GetDmGift(r.Context(), authenticatedUserID(r), requestID)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, res)
}

func (s *Server) handleDmVideoUnlock(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	defer func() { _ = r.Body.Close() }()
	var req service.DmUnlockRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid JSON body")
		return
	}
	req.ViewerID = authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), req.ViewerID); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	res, appErr, err := s.svc.UnlockDmVideo(r.Context(), req)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	status := http.StatusCreated
	if res.Idempotency == "replayed" {
		status = http.StatusOK
	}
	httpx.WriteJSON(w, status, res)
}

func (s *Server) handleDmVideoAccess(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) != 5 || parts[0] != "v1" || parts[1] != "dm" || parts[2] != "videos" || parts[4] != "access" || parts[3] == "" {
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
		return
	}
	res, appErr, err := s.svc.DmVideoAccess(r.Context(), authenticatedUserID(r), parts[3])
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, res)
}

// dmPointTotals adds private gifts, video unlocks and connected-call charges to earnings.
// Coins themselves stay in the shared COIN wallet; this only reports the diamond amounts already stored.
func (s *Server) dmPointTotals(ctx context.Context, userID uint64, start *time.Time) (spent, diamonds, gifts int64, err error) {
	since := func(query string, args []any) (string, []any) {
		if start == nil {
			return query, args
		}
		return query + " AND created_at>=?", append(args, *start)
	}
	var giftSpent, unlockSpent, callSpent, giftDiamonds, giftCount, unlockDiamonds, unlockCount, callDiamonds int64
	query, args := since(`SELECT COALESCE(SUM(charged_coins),0) FROM dm_gift_orders WHERE viewer_id=? AND status='ACCEPTED'`, []any{userID})
	if err = s.db.QueryRowContext(ctx, query, args...).Scan(&giftSpent); err != nil {
		return 0, 0, 0, err
	}
	query, args = since(`SELECT COALESCE(SUM(diamond_reward),0), COUNT(*) FROM dm_gift_orders WHERE recipient_id=? AND status='ACCEPTED'`, []any{userID})
	if err = s.db.QueryRowContext(ctx, query, args...).Scan(&giftDiamonds, &giftCount); err != nil {
		return 0, 0, 0, err
	}
	query, args = since(`SELECT COALESCE(SUM(charged_coins),0) FROM dm_video_unlocks WHERE viewer_id=?`, []any{userID})
	if err = s.db.QueryRowContext(ctx, query, args...).Scan(&unlockSpent); err != nil {
		return 0, 0, 0, err
	}
	query, args = since(`SELECT COALESCE(SUM(diamond_reward),0), COUNT(CASE WHEN diamond_reward>0 THEN 1 END) FROM dm_video_unlocks WHERE owner_id=?`, []any{userID})
	if err = s.db.QueryRowContext(ctx, query, args...).Scan(&unlockDiamonds, &unlockCount); err != nil {
		return 0, 0, 0, err
	}
	query, args = since(`SELECT COALESCE(SUM(price_coins),0) FROM dm_call_charges WHERE payer_id=?`, []any{userID})
	if err = s.db.QueryRowContext(ctx, query, args...).Scan(&callSpent); err != nil {
		return 0, 0, 0, err
	}
	query, args = since(`SELECT COALESCE(SUM(price_coins),0) FROM dm_call_charges WHERE owner_id=?`, []any{userID})
	if err = s.db.QueryRowContext(ctx, query, args...).Scan(&callDiamonds); err != nil {
		return 0, 0, 0, err
	}
	return giftSpent + unlockSpent + callSpent, giftDiamonds + unlockDiamonds + callDiamonds, giftCount + unlockCount, nil
}
