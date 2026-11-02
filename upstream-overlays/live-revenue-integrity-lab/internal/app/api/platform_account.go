package apiserver

import (
	"encoding/json"
	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http"
	"strconv"
	"time"
)

// Revisions come from the account database's durable action sequence. Delayed retries cannot restore an older state.
func (s *Server) handlePlatformAccount(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPut {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "PUT required")
		return
	}
	var c struct {
		UserID   uint64 `json:"user_id"`
		Revision uint64 `json:"revision"`
		Disabled bool   `json:"disabled"`
	}
	if !financeBody(w, r, &c) {
		return
	}
	if c.UserID == 0 || c.Revision == 0 {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "用户和状态版本无效")
		return
	}
	tx, err := s.db.BeginTx(r.Context(), nil)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer tx.Rollback()
	_, err = tx.ExecContext(r.Context(), `INSERT INTO platform_account_state(user_id,revision,disabled) VALUES(?,?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)`, c.UserID, c.Revision, c.Disabled)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	var revision uint64
	var disabled bool
	if err = tx.QueryRowContext(r.Context(), `SELECT revision,disabled FROM platform_account_state WHERE user_id=? FOR UPDATE`, c.UserID).Scan(&revision, &disabled); err != nil {
		financeReply(w, nil, err)
		return
	}
	if c.Revision == revision && c.Disabled != disabled {
		financeReply(w, nil, &service.AppError{HTTPStatus: 409, Code: "OPERATION_CONFLICT", Message: "同一版本状态不一致"})
		return
	}
	if c.Revision > revision {
		if _, err = tx.ExecContext(r.Context(), `UPDATE platform_account_state SET revision=?,disabled=? WHERE user_id=?`, c.Revision, c.Disabled, c.UserID); err != nil {
			financeReply(w, nil, err)
			return
		}
		revision = c.Revision
		disabled = c.Disabled
	}
	if disabled {
		rows, e := tx.QueryContext(r.Context(), `SELECT live_session_id FROM live_sessions WHERE creator_id=? AND status='OPEN' FOR UPDATE`, c.UserID)
		if e != nil {
			financeReply(w, nil, e)
			return
		}
		ids := []uint64{}
		for rows.Next() {
			var id uint64
			if err = rows.Scan(&id); err != nil {
				break
			}
			ids = append(ids, id)
		}
		rows.Close()
		if err == nil {
			err = rows.Err()
		}
		if err != nil {
			financeReply(w, nil, err)
			return
		}
		for _, id := range ids {
			if _, err = tx.ExecContext(r.Context(), `UPDATE live_sessions SET status='CLOSED',closed_at=UTC_TIMESTAMP(6) WHERE live_session_id=?`, id); err != nil {
				financeReply(w, nil, err)
				return
			}
			if _, err = tx.ExecContext(r.Context(), `UPDATE live_matches SET status='CLOSED' WHERE status='OPEN' AND (live_session_id=? OR opponent_live_session_id=?)`, id, id); err != nil {
				financeReply(w, nil, err)
				return
			}
			payload, _ := json.Marshal(map[string]any{"live_session_id": id, "closed_at_ms": time.Now().UTC().UnixMilli()})
			if _, err = tx.ExecContext(r.Context(), `INSERT INTO outbox_events(topic,event_key,payload_json,created_at) VALUES('live.session.closed.v1',?,?,UTC_TIMESTAMP(6))`, strconv.FormatUint(id, 10), string(payload)); err != nil {
				financeReply(w, nil, err)
				return
			}
		}
	}
	if err = tx.Commit(); err != nil {
		financeReply(w, nil, err)
		return
	}
	financeReply(w, map[string]any{"revision": revision, "disabled": disabled}, nil)
}
