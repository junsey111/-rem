package apiserver

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type taskProgressRequest struct {
	UserID   uint64 `json:"user_id"`
	TaskKey  string `json:"task_key"`
	TaskDate string `json:"task_date"`
}

func (s *Server) handleInternalTaskProgress(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	defer r.Body.Close()
	var req taskProgressRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 16*1024)).Decode(&req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid task progress payload")
		return
	}
	date, err := time.Parse("2006-01-02", req.TaskDate)
	if req.UserID == 0 || (req.TaskKey != "send_gift" && req.TaskKey != "start_live") || err != nil || date.Format("2006-01-02") != req.TaskDate {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_TASK", "unsupported task progress request")
		return
	}
	var progress int64
	if req.TaskKey == "send_gift" {
		err = s.db.QueryRowContext(r.Context(), `
			SELECT COALESCE(SUM(quantity), 0)
			FROM gift_orders
			WHERE viewer_id=? AND status='ACCEPTED' AND created_at>=? AND created_at<?
		`, req.UserID, date, date.AddDate(0, 0, 1)).Scan(&progress)
	} else {
		err = s.db.QueryRowContext(r.Context(), `
			SELECT COUNT(*) FROM live_sessions
			WHERE creator_id=? AND started_at>=? AND started_at<?
		`, req.UserID, date, date.AddDate(0, 0, 1)).Scan(&progress)
	}
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "could not load task progress")
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]int64{"progress": progress})
}
