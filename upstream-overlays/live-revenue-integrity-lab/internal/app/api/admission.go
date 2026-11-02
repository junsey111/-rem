package apiserver

import (
	"database/sql"
	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"net/http"
	"strings"
	"unicode/utf8"
)

func (s *Server) handleAdmission(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		var version uint64
		var required bool
		if s.db.QueryRowContext(r.Context(), `SELECT version,require_approval FROM creator_admission_settings WHERE singleton=1`).Scan(&version, &required) != nil {
			cohostError(w, 503, "准入设置暂不可用")
			return
		}
		httpx.WriteJSON(w, 200, map[string]any{"version": version, "require_approval": required})
		return
	}
	if r.Method != http.MethodPut {
		cohostError(w, 405, "GET/PUT required")
		return
	}
	var c struct {
		RequestID       string `json:"request_id"`
		OperatorID      uint64 `json:"operator_id"`
		ExpectedVersion uint64 `json:"expected_version"`
		RequireApproval bool   `json:"require_approval"`
		Reason          string `json:"reason"`
	}
	if !financeBody(w, r, &c) {
		return
	}
	if !operationRequestPattern.MatchString(c.RequestID) || c.OperatorID == 0 || strings.TrimSpace(c.Reason) == "" || utf8.RuneCountInString(c.Reason) > 200 {
		cohostError(w, 400, "操作单号和原因无效")
		return
	}
	tx, e := s.db.BeginTx(r.Context(), nil)
	if e != nil {
		cohostError(w, 503, "设置尚未确认")
		return
	}
	defer tx.Rollback()
	var version uint64
	var required bool
	if tx.QueryRowContext(r.Context(), `SELECT version,require_approval FROM creator_admission_settings WHERE singleton=1 FOR UPDATE`).Scan(&version, &required) != nil {
		cohostError(w, 503, "设置尚未确认")
		return
	}
	var operator, oldVersion uint64
	var oldRequired bool
	var reason string
	e = tx.QueryRowContext(r.Context(), `SELECT operator_id,require_approval,reason,version FROM creator_admission_actions WHERE request_id=?`, c.RequestID).Scan(&operator, &oldRequired, &reason, &oldVersion)
	if e == nil {
		if operator != c.OperatorID || oldRequired != c.RequireApproval || reason != c.Reason {
			cohostError(w, 409, "单号冲突")
			return
		}
		httpx.WriteJSON(w, 200, map[string]any{"version": oldVersion, "require_approval": oldRequired})
		return
	}
	if e != sql.ErrNoRows {
		cohostError(w, 503, "设置尚未确认")
		return
	}
	if version != c.ExpectedVersion {
		cohostError(w, 409, "设置已变更，请刷新")
		return
	}
	version++
	if _, e = tx.ExecContext(r.Context(), `INSERT INTO creator_admission_actions(request_id,operator_id,require_approval,reason,version) VALUES(?,?,?,?,?)`, c.RequestID, c.OperatorID, c.RequireApproval, c.Reason, version); e != nil {
		cohostError(w, 503, "设置尚未确认")
		return
	}
	if _, e = tx.ExecContext(r.Context(), `UPDATE creator_admission_settings SET version=?,require_approval=? WHERE singleton=1`, version, c.RequireApproval); e != nil {
		cohostError(w, 503, "设置尚未确认")
		return
	}
	if e = tx.Commit(); e != nil {
		cohostError(w, 503, "设置尚未确认")
		return
	}
	httpx.WriteJSON(w, 200, map[string]any{"version": version, "require_approval": c.RequireApproval})
}
