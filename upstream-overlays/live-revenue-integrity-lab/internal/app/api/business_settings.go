package apiserver

import (
	"database/sql"
	"encoding/json"
	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http"
	"strconv"
	"strings"
	"unicode/utf8"
)

func (s *Server) handleBusinessSettings(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/v1/internal/business/")
	if path == "actions" && r.Method == http.MethodGet {
		s.listBusinessActions(w, r)
		return
	}
	var liveID uint64
	var err error
	kind := "REFERRALS"
	if path != "referrals" {
		if !strings.HasPrefix(path, "watch-prices/") {
			httpx.WriteError(w, 404, "NOT_FOUND", "route not found")
			return
		}
		raw := strings.TrimPrefix(path, "watch-prices/")
		liveID, err = strconv.ParseUint(raw, 10, 64)
		if err != nil || liveID == 0 || raw != strconv.FormatUint(liveID, 10) {
			httpx.WriteError(w, 400, "INVALID_OPERATION", "直播编号无效")
			return
		}
		kind = "WATCH_PRICE"
	}
	if r.Method == http.MethodGet {
		if kind == "REFERRALS" {
			data, err := s.svc.ReadReferralSettings(r.Context(), s.referrals)
			financeReply(w, data, err)
			return
		}
		var status string
		var price int
		var version uint64
		err = s.db.QueryRowContext(r.Context(), `SELECT l.status,COALESCE(p.unlock_price_coins,0),COALESCE(p.version,0) FROM live_sessions l LEFT JOIN live_watch_prices p ON p.live_session_id=l.live_session_id WHERE l.live_session_id=?`, liveID).Scan(&status, &price, &version)
		if err == sql.ErrNoRows {
			httpx.WriteError(w, 404, "LIVE_SESSION_NOT_FOUND", "直播不存在")
			return
		}
		financeReply(w, map[string]any{"live_session_id": strconv.FormatUint(liveID, 10), "status": status, "unlock_price_coins": price, "version": version}, err)
		return
	}
	if r.Method != http.MethodPut {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET/PUT required")
		return
	}
	var c struct {
		RequestID       string  `json:"request_id"`
		OperatorID      uint64  `json:"operator_id"`
		ExpectedVersion *uint64 `json:"expected_version"`
		Reason          string  `json:"reason"`
		Enabled         *bool   `json:"enabled"`
		InviterCoins    *int64  `json:"inviter_coins"`
		InviteeCoins    *int64  `json:"invitee_coins"`
		Price           *int    `json:"unlock_price_coins"`
	}
	if !financeBody(w, r, &c) {
		return
	}
	if !operationRequestPattern.MatchString(c.RequestID) || c.OperatorID == 0 || c.ExpectedVersion == nil || strings.TrimSpace(c.Reason) == "" || utf8.RuneCountInString(c.Reason) > 200 {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "请填写操作单号、版本及原因")
		return
	}
	if kind == "REFERRALS" && (c.Enabled == nil || c.InviterCoins == nil || c.InviteeCoins == nil || c.Price != nil || *c.InviterCoins < 0 || *c.InviterCoins > 100000 || *c.InviteeCoins < 0 || *c.InviteeCoins > 100000) || kind == "WATCH_PRICE" && (c.Price == nil || *c.Price < 0 || *c.Price > 1000000 || c.Enabled != nil || c.InviterCoins != nil || c.InviteeCoins != nil) {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "奖励或价格超出允许范围")
		return
	}
	_, hash, err := service.BodyHashHex(struct {
		Kind    string
		Live    uint64
		Command any
	}{kind, liveID, c})
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	tx, err := s.db.BeginTx(r.Context(), &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer tx.Rollback()
	var version uint64
	var status string
	if kind == "REFERRALS" {
		err = tx.QueryRowContext(r.Context(), `SELECT version FROM referral_settings WHERE singleton=1 FOR UPDATE`).Scan(&version)
	} else {
		err = tx.QueryRowContext(r.Context(), `SELECT status FROM live_sessions WHERE live_session_id=? FOR UPDATE`, liveID).Scan(&status)
		if err == sql.ErrNoRows {
			httpx.WriteError(w, 404, "LIVE_SESSION_NOT_FOUND", "直播不存在")
			return
		}
		if err == nil {
			err = tx.QueryRowContext(r.Context(), `SELECT version FROM live_watch_prices WHERE live_session_id=? FOR UPDATE`, liveID).Scan(&version)
			if err == sql.ErrNoRows {
				err = nil
			}
		}
	}
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	var priorHash []byte
	var snapshot []byte
	err = tx.QueryRowContext(r.Context(), `SELECT body_hash,settings_json FROM business_setting_actions WHERE request_id=?`, c.RequestID).Scan(&priorHash, &snapshot)
	if err == nil {
		if string(hash) != string(priorHash) {
			httpx.WriteError(w, 409, "OPERATION_CONFLICT", "该单号已用于其他设置变更")
			return
		}
		var data any
		if json.Unmarshal(snapshot, &data) != nil {
			financeReply(w, nil, sql.ErrNoRows)
			return
		}
		httpx.WriteJSON(w, 200, data)
		return
	}
	if err != sql.ErrNoRows {
		financeReply(w, nil, err)
		return
	}
	if version != *c.ExpectedVersion {
		httpx.WriteError(w, 409, "VERSION_CONFLICT", "设置已被修改，请刷新后重新确认")
		return
	}
	if kind == "WATCH_PRICE" && status != "OPEN" {
		httpx.WriteError(w, 409, "LIVE_SESSION_CLOSED", "直播已结束，不能改价")
		return
	}
	version++
	var data map[string]any
	if kind == "REFERRALS" {
		_, err = tx.ExecContext(r.Context(), `UPDATE referral_settings SET version=?,configured=TRUE,enabled=?,inviter_coins=?,invitee_coins=? WHERE singleton=1`, version, *c.Enabled, *c.InviterCoins, *c.InviteeCoins)
		data = map[string]any{"version": version, "configured": true, "enabled": *c.Enabled, "inviter_coins": *c.InviterCoins, "invitee_coins": *c.InviteeCoins}
	} else {
		_, err = tx.ExecContext(r.Context(), `INSERT INTO live_watch_prices(live_session_id,unlock_price_coins,version) VALUES(?,?,?) ON DUPLICATE KEY UPDATE unlock_price_coins=VALUES(unlock_price_coins),version=VALUES(version)`, liveID, *c.Price, version)
		data = map[string]any{"live_session_id": strconv.FormatUint(liveID, 10), "status": status, "unlock_price_coins": *c.Price, "version": version}
	}
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	snapshot, err = json.Marshal(data)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	_, err = tx.ExecContext(r.Context(), `INSERT INTO business_setting_actions(request_id,operator_id,kind,target_id,body_hash,settings_json,reason) VALUES(?,?,?,?,?,?,?)`, c.RequestID, c.OperatorID, kind, liveID, hash, snapshot, c.Reason)
	if err == nil {
		err = tx.Commit()
	}
	financeReply(w, data, err)
}

func (s *Server) listBusinessActions(w http.ResponseWriter, r *http.Request) {
	before, ok := operationCursor(r)
	if !ok {
		httpx.WriteError(w, 400, "INVALID_CURSOR", "分页无效")
		return
	}
	query := `SELECT action_id,request_id,operator_id,kind,target_id,settings_json,reason,created_at FROM business_setting_actions`
	args := []any{}
	if before > 0 {
		query += ` WHERE action_id<?`
		args = append(args, before)
	}
	rows, err := s.db.QueryContext(r.Context(), query+` ORDER BY action_id DESC LIMIT 26`, args...)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer rows.Close()
	items := make([]map[string]any, 0, 26)
	ids := make([]uint64, 0, 26)
	for rows.Next() {
		var id, operator, target uint64
		var request, kind, reason string
		var snapshot []byte
		var created any
		if err = rows.Scan(&id, &request, &operator, &kind, &target, &snapshot, &reason, &created); err != nil {
			financeReply(w, nil, err)
			return
		}
		var settings any
		if err = json.Unmarshal(snapshot, &settings); err != nil {
			financeReply(w, nil, err)
			return
		}
		items = append(items, map[string]any{"action_id": strconv.FormatUint(id, 10), "request_id": request, "operator_id": strconv.FormatUint(operator, 10), "kind": kind, "target_id": strconv.FormatUint(target, 10), "settings": settings, "reason": reason, "created_at": created})
		ids = append(ids, id)
	}
	if rows.Err() != nil {
		financeReply(w, nil, rows.Err())
		return
	}
	next := ""
	if len(items) > 25 {
		items = items[:25]
		next = strconv.FormatUint(ids[24], 10)
	}
	httpx.WriteJSON(w, 200, map[string]any{"actions": items, "next_before_id": next})
}
