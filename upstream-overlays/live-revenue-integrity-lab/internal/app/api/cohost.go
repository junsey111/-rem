package apiserver

import (
	"database/sql"
	"net/http"
	"strconv"
	"strings"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type cohostRequest struct {
	ID        uint64 `json:"cohost_id,string"`
	RequestID string `json:"request_id"`
	LiveID    uint64 `json:"live_session_id,string"`
	UserID    uint64 `json:"user_id,string"`
	Mode      string `json:"mode"`
	Status    string `json:"status"`
}

const cohostColumns = `SELECT cohost_id,request_id,live_session_id,user_id,mode,status FROM cohost_requests `

func scanCohost(row interface{ Scan(...any) error }) (cohostRequest, error) {
	var c cohostRequest
	err := row.Scan(&c.ID, &c.RequestID, &c.LiveID, &c.UserID, &c.Mode, &c.Status)
	return c, err
}
func cohostError(w http.ResponseWriter, status int, message string) {
	httpx.WriteError(w, status, "COHOST_REFUSED", message)
}
func (s *Server) handleCohost(w http.ResponseWriter, r *http.Request) {
	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/v1/cohosts/"), "/")
	if len(parts) < 1 || len(parts) > 2 {
		cohostError(w, 404, "接口不存在")
		return
	}
	liveID, err := strconv.ParseUint(parts[0], 10, 64)
	if err != nil || liveID == 0 || strconv.FormatUint(liveID, 10) != parts[0] {
		cohostError(w, 400, "直播场次无效")
		return
	}
	user := authenticatedUserID(r)
	var owner uint64
	var status string
	if err = s.db.QueryRowContext(r.Context(), `SELECT creator_id,status FROM live_sessions WHERE live_session_id=?`, liveID).Scan(&owner, &status); err != nil {
		if err == sql.ErrNoRows {
			cohostError(w, 404, "直播不存在")
		} else {
			cohostError(w, 503, "连麦暂不可用")
		}
		return
	}
	if r.Method == http.MethodGet && len(parts) == 1 {
		query := cohostColumns + `WHERE live_session_id=? `
		args := []any{liveID}
		if owner != user {
			query += `AND user_id=? `
			args = append(args, user)
		}
		query += `ORDER BY cohost_id DESC LIMIT 100`
		rows, e := s.db.QueryContext(r.Context(), query, args...)
		if e != nil {
			cohostError(w, 503, "读取连麦失败")
			return
		}
		defer rows.Close()
		items := []cohostRequest{}
		for rows.Next() {
			item, e := scanCohost(rows)
			if e != nil {
				cohostError(w, 503, "读取连麦失败")
				return
			}
			items = append(items, item)
		}
		if rows.Err() != nil {
			cohostError(w, 503, "读取连麦失败")
			return
		}
		httpx.WriteJSON(w, 200, map[string]any{"requests": items, "live_status": status})
		return
	}
	if r.Method != http.MethodPost {
		cohostError(w, 405, "仅支持 GET/POST")
		return
	}
	var c struct {
		RequestID string `json:"request_id"`
		Mode      string `json:"mode"`
		Action    string `json:"action"`
	}
	if !financeBody(w, r, &c) {
		return
	}
	if !operationRequestPattern.MatchString(c.RequestID) {
		cohostError(w, 400, "操作单号无效")
		return
	}
	if err = s.ensureUser(r.Context(), user); err != nil {
		cohostError(w, 503, "账号状态暂不可用")
		return
	}
	tx, err := s.db.BeginTx(r.Context(), &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		cohostError(w, 503, "连麦尚未确认，请用原单号重试")
		return
	}
	defer tx.Rollback()
	if err = tx.QueryRowContext(r.Context(), `SELECT creator_id,status FROM live_sessions WHERE live_session_id=? FOR UPDATE`, liveID).Scan(&owner, &status); err != nil {
		cohostError(w, 503, "连麦尚未确认")
		return
	}
	fail := func(e error) bool {
		if e != nil {
			cohostError(w, 503, "连麦尚未确认，请用原单号重试")
			return true
		}
		return false
	}
	if len(parts) == 1 {
		if c.Mode != "AUDIO" && c.Mode != "VIDEO" {
			cohostError(w, 400, "请选择语音或视频连麦")
			return
		}
		prior, e := scanCohost(tx.QueryRowContext(r.Context(), cohostColumns+`WHERE user_id=? AND request_id=?`, user, c.RequestID))
		if e == nil {
			if prior.LiveID != liveID || prior.Mode != c.Mode {
				cohostError(w, 409, "单号已用于其他申请")
				return
			}
			httpx.WriteJSON(w, 200, prior)
			return
		}
		if e != sql.ErrNoRows && fail(e) {
			return
		}
		if user == owner {
			cohostError(w, 409, "主播无需申请自己的连麦")
			return
		}
		if status != "OPEN" {
			cohostError(w, 409, "直播已结束")
			return
		}
		var restricted bool
		err = tx.QueryRowContext(r.Context(), `SELECT (account_standing<>'GOOD' OR profile_source<>'SELF_REPORTED' OR age_years IS NULL OR region_code IS NULL OR age_years<IF(UPPER(region_code)='KR',19,18) OR EXISTS(SELECT 1 FROM platform_account_state p WHERE p.user_id=users.user_id AND p.disabled=TRUE)) FROM users WHERE user_id=?`, user).Scan(&restricted)
		if fail(err) {
			return
		}
		if restricted {
			cohostError(w, 403, "账号当前不能连麦")
			return
		}
		var busy int
		if fail(tx.QueryRowContext(r.Context(), `SELECT COUNT(*) FROM cohost_requests WHERE live_session_id=? AND user_id=? AND status IN ('PENDING','ACCEPTED','MUTED')`, liveID, user).Scan(&busy)) {
			return
		}
		if busy > 0 {
			cohostError(w, 409, "已有连麦申请，请先查看或结束")
			return
		}
		if fail(tx.QueryRowContext(r.Context(), `SELECT COUNT(*) FROM cohost_requests WHERE live_session_id=? AND status='PENDING'`, liveID).Scan(&busy)) {
			return
		}
		if busy >= 100 {
			cohostError(w, 409, "申请队列已满，请稍后再试")
			return
		}
		res, e := tx.ExecContext(r.Context(), `INSERT INTO cohost_requests(request_id,live_session_id,user_id,mode) VALUES(?,?,?,?)`, c.RequestID, liveID, user, c.Mode)
		if fail(e) {
			return
		}
		id, _ := res.LastInsertId()
		if fail(tx.Commit()) {
			return
		}
		httpx.WriteJSON(w, 200, cohostRequest{uint64(id), c.RequestID, liveID, user, c.Mode, "PENDING"})
		return
	}
	id, e := strconv.ParseUint(parts[1], 10, 64)
	if e != nil || id == 0 {
		cohostError(w, 400, "申请编号无效")
		return
	}
	item, e := scanCohost(tx.QueryRowContext(r.Context(), cohostColumns+`WHERE cohost_id=? AND live_session_id=? FOR UPDATE`, id, liveID))
	if e == sql.ErrNoRows {
		cohostError(w, 404, "连麦申请不存在")
		return
	}
	if fail(e) {
		return
	}
	if (c.Action == "LEAVE" && item.UserID != user) || (c.Action != "LEAVE" && owner != user) {
		cohostError(w, 403, "没有此操作权限")
		return
	}
	var priorID, priorOperator uint64
	var priorAction string
	e = tx.QueryRowContext(r.Context(), `SELECT cohost_id,operator_id,action FROM cohost_actions WHERE request_id=?`, c.RequestID).Scan(&priorID, &priorOperator, &priorAction)
	if e == nil {
		if priorID != id || priorOperator != user || priorAction != c.Action {
			cohostError(w, 409, "单号已用于其他操作")
			return
		}
		httpx.WriteJSON(w, 200, item)
		return
	}
	if e != sql.ErrNoRows && fail(e) {
		return
	}
	next := ""
	switch c.Action {
	case "ACCEPT":
		if item.Status == "PENDING" {
			next = "ACCEPTED"
		}
	case "REJECT":
		if item.Status == "PENDING" {
			next = "REJECTED"
		}
	case "MUTE":
		if item.Status == "ACCEPTED" {
			next = "MUTED"
		}
	case "UNMUTE":
		if item.Status == "MUTED" {
			next = "ACCEPTED"
		}
	case "REMOVE":
		if item.Status == "ACCEPTED" || item.Status == "MUTED" {
			next = "REMOVED"
		}
	case "LEAVE":
		if item.Status == "PENDING" || item.Status == "ACCEPTED" || item.Status == "MUTED" {
			next = "LEFT"
		}
	}
	if next == "" {
		cohostError(w, 409, "当前连麦状态不支持此操作")
		return
	}
	if next == "ACCEPTED" {
		if status != "OPEN" {
			cohostError(w, 409, "直播已结束")
			return
		}
		var count int
		if fail(tx.QueryRowContext(r.Context(), `SELECT COUNT(*) FROM live_matches WHERE status='OPEN' AND (live_session_id=? OR opponent_live_session_id=?)`, liveID, liveID).Scan(&count)) {
			return
		}
		if count > 0 {
			cohostError(w, 409, "PK 期间不能接入普通连麦")
			return
		}
		if fail(tx.QueryRowContext(r.Context(), `SELECT COUNT(*) FROM cohost_requests WHERE live_session_id=? AND cohost_id<>? AND status IN ('ACCEPTED','MUTED')`, liveID, id).Scan(&count)) {
			return
		}
		if count >= 3 {
			cohostError(w, 409, "最多同时接入三位观众")
			return
		}
		var allowed bool
		if fail(tx.QueryRowContext(r.Context(), `SELECT (account_standing='GOOD' AND profile_source='SELF_REPORTED' AND age_years>=IF(UPPER(region_code)='KR',19,18) AND region_code IS NOT NULL AND NOT EXISTS(SELECT 1 FROM platform_account_state p WHERE p.user_id=users.user_id AND p.disabled=TRUE)) FROM users WHERE user_id=?`, item.UserID).Scan(&allowed)) {
			return
		}
		if !allowed {
			cohostError(w, 403, "该账号不可连麦")
			return
		}
	}
	if _, e = tx.ExecContext(r.Context(), `UPDATE cohost_requests SET status=? WHERE cohost_id=?`, next, id); fail(e) {
		return
	}
	if _, e = tx.ExecContext(r.Context(), `INSERT INTO cohost_actions(request_id,cohost_id,operator_id,action) VALUES(?,?,?,?)`, c.RequestID, id, user, c.Action); fail(e) {
		return
	}
	if fail(tx.Commit()) {
		return
	}
	item.Status = next
	httpx.WriteJSON(w, 200, item)
}
