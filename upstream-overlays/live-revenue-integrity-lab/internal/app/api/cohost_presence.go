package apiserver

import (
	"database/sql"
	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"net/http"
	"strconv"
	"time"
)

// Only the trusted media worker reports actual LiveKit presence. Client polling
// cannot keep a disconnected seat alive. Gaps in observations reset the grace
// period, so an unavailable worker or media service cannot evict everyone.
func (s *Server) handleCohostPresence(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		var before uint64
		if raw := r.URL.Query().Get("after_id"); raw != "" {
			var err error
			before, err = strconv.ParseUint(raw, 10, 64)
			if err != nil || before == 0 || strconv.FormatUint(before, 10) != raw {
				cohostError(w, 400, "分页无效")
				return
			}
		}
		rows, err := s.db.QueryContext(r.Context(), `SELECT cohost_id,live_session_id,user_id FROM cohost_requests WHERE status IN ('ACCEPTED','MUTED') AND cohost_id>? ORDER BY cohost_id LIMIT 100`, before)
		if err != nil {
			cohostError(w, 503, "读取连麦席位失败")
			return
		}
		defer rows.Close()
		items := []map[string]string{}
		var last uint64
		for rows.Next() {
			var id, live, user uint64
			if err = rows.Scan(&id, &live, &user); err != nil {
				cohostError(w, 503, "读取连麦席位失败")
				return
			}
			last = id
			items = append(items, map[string]string{"cohost_id": strconv.FormatUint(id, 10), "live_session_id": strconv.FormatUint(live, 10), "user_id": strconv.FormatUint(user, 10)})
		}
		if rows.Err() != nil {
			cohostError(w, 503, "读取连麦席位失败")
			return
		}
		next := ""
		if len(items) == 100 {
			next = strconv.FormatUint(last, 10)
		}
		httpx.WriteJSON(w, 200, map[string]any{"seats": items, "next_after_id": next})
		return
	}
	if r.Method != http.MethodPost {
		cohostError(w, 405, "仅支持 GET/POST")
		return
	}
	var c struct {
		ID     uint64 `json:"cohost_id,string"`
		Online *bool  `json:"online"`
	}
	if !financeBody(w, r, &c) {
		return
	}
	if c.ID == 0 || c.Online == nil {
		cohostError(w, 400, "连麦在线状态无效")
		return
	}
	// Lock the parent first, matching accept/leave/close lock order.
	var live uint64
	err := s.db.QueryRowContext(r.Context(), `SELECT live_session_id FROM cohost_requests WHERE cohost_id=?`, c.ID).Scan(&live)
	if err == sql.ErrNoRows {
		cohostError(w, 404, "席位不存在")
		return
	}
	if err != nil {
		cohostError(w, 503, "席位核对失败")
		return
	}
	tx, err := s.db.BeginTx(r.Context(), &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		cohostError(w, 503, "席位核对失败")
		return
	}
	defer tx.Rollback()
	var liveStatus, status string
	var absent, observed sql.NullTime
	var now time.Time
	err = tx.QueryRowContext(r.Context(), `SELECT status FROM live_sessions WHERE live_session_id=? FOR UPDATE`, live).Scan(&liveStatus)
	if err == nil {
		err = tx.QueryRowContext(r.Context(), `SELECT status,media_absent_since,media_observed_at,UTC_TIMESTAMP(6) FROM cohost_requests WHERE cohost_id=? FOR UPDATE`, c.ID).Scan(&status, &absent, &observed, &now)
	}
	if err != nil {
		cohostError(w, 503, "席位核对失败")
		return
	}
	if status == "ACCEPTED" || status == "MUTED" {
		if liveStatus != "OPEN" {
			status = "REMOVED"
		} else if *c.Online {
			absent = sql.NullTime{}
		} else {
			if !observed.Valid || now.Sub(observed.Time) > 20*time.Second || !absent.Valid {
				absent = sql.NullTime{Time: now, Valid: true}
			}
			if now.Sub(absent.Time) >= 90*time.Second {
				status = "REMOVED"
			}
		}
		_, err = tx.ExecContext(r.Context(), `UPDATE cohost_requests SET status=?,media_absent_since=?,media_observed_at=? WHERE cohost_id=?`, status, absent, now, c.ID)
	}
	if err == nil {
		err = tx.Commit()
	}
	if err != nil {
		cohostError(w, 503, "席位核对失败")
		return
	}
	httpx.WriteJSON(w, 200, map[string]any{"cohost_id": strconv.FormatUint(c.ID, 10), "status": status})
}
