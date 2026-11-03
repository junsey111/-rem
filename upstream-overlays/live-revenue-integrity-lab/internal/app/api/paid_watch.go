package apiserver

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func writeWatch(w http.ResponseWriter, data any, err error) {
	if err == nil {
		httpx.WriteJSON(w, http.StatusOK, data)
		return
	}
	var app *service.AppError
	if errors.As(err, &app) {
		httpx.WriteError(w, app.HTTPStatus, app.Code, app.Message)
		return
	}
	httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
}

func decodeWatchBody(w http.ResponseWriter, r *http.Request, dest any) bool {
	decoder := json.NewDecoder(io.LimitReader(r.Body, 4096))
	if err := decoder.Decode(dest); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "请求格式无效")
		return false
	}
	return true
}

func (s *Server) handleWatchAccess(w http.ResponseWriter, r *http.Request, liveID uint64) {
	userID := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), userID); err != nil {
		writeWatch(w, nil, err)
		return
	}
	access, err := s.svc.WatchAccess(r.Context(), userID, liveID)
	writeWatch(w, access, err)
}

func (s *Server) handleWatchHeartbeat(w http.ResponseWriter, r *http.Request, liveID uint64) {
	var body struct {
		RequestID    string `json:"request_id"`
		DeltaSeconds *int   `json:"delta_seconds"`
	}
	if !decodeWatchBody(w, r, &body) {
		return
	}
	if body.DeltaSeconds == nil {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_DELTA", "请提供观看心跳秒数")
		return
	}
	userID := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), userID); err != nil {
		writeWatch(w, nil, err)
		return
	}
	access, err := s.svc.WatchHeartbeat(r.Context(), userID, liveID, body.RequestID, *body.DeltaSeconds)
	writeWatch(w, access, err)
}

func (s *Server) handleWatchUnlock(w http.ResponseWriter, r *http.Request, liveID uint64) {
	var body struct {
		RequestID string `json:"request_id"`
	}
	if !decodeWatchBody(w, r, &body) {
		return
	}
	userID := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), userID); err != nil {
		writeWatch(w, nil, err)
		return
	}
	access, err := s.svc.WatchUnlock(r.Context(), userID, liveID, body.RequestID)
	writeWatch(w, access, err)
}

func (s *Server) handleWatchPrice(w http.ResponseWriter, r *http.Request, liveID uint64) {
	var body struct {
		UnlockPriceCoins *int `json:"unlock_price_coins"`
	}
	if !decodeWatchBody(w, r, &body) {
		return
	}
	if body.UnlockPriceCoins == nil {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_PRICE", "请填写付费观看价格")
		return
	}
	userID := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), userID); err != nil {
		writeWatch(w, nil, err)
		return
	}
	err := s.svc.SetWatchPrice(r.Context(), userID, liveID, *body.UnlockPriceCoins)
	if err != nil {
		writeWatch(w, nil, err)
		return
	}
	access, err := s.svc.WatchAccess(r.Context(), userID, liveID)
	writeWatch(w, access, err)
}
