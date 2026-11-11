package apiserver

import (
	"encoding/base64"
	"encoding/json"
	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"net/http"
	"time"
)

type incomeCursor struct {
	At  time.Time `json:"at"`
	Key string    `json:"key"`
}

// Lists already settled source records without adding any balance or ledger entries.
func (s *Server) handleCreatorIncome(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	userID := authenticatedUserID(r)
	var approved bool
	if err := s.db.QueryRowContext(r.Context(), `SELECT EXISTS(SELECT 1 FROM review_requests WHERE user_id=? AND kind='CREATOR' AND status='APPROVED')`, userID).Scan(&approved); err != nil {
		httpx.WriteError(w, 503, "INTERNAL_ERROR", "暂时无法确认主播认证状态")
		return
	}
	if !approved {
		httpx.WriteError(w, 403, "CREATOR_REQUIRED", "请先完成主播认证")
		return
	}
	source := r.URL.Query().Get("source")
	valid := map[string]bool{"": true, "LIVE_GIFT": true, "PRIVATE_GIFT": true, "PAID_VIDEO": true, "VIDEO_CALL": true, "LIVE_WATCH": true}
	if !valid[source] {
		httpx.WriteError(w, 400, "INVALID_SOURCE", "invalid income source")
		return
	}
	cursor := incomeCursor{}
	if raw := r.URL.Query().Get("before"); raw != "" {
		bytes, err := base64.RawURLEncoding.DecodeString(raw)
		if len(raw) > 512 || err != nil || json.Unmarshal(bytes, &cursor) != nil || cursor.At.IsZero() || len(cursor.Key) < 1 || len(cursor.Key) > 100 {
			httpx.WriteError(w, 400, "INVALID_CURSOR", "invalid income cursor")
			return
		}
	}
	settings, err := s.svc.GetFinanceSettings(r.Context())
	if err != nil {
		httpx.WriteError(w, 500, "INTERNAL_ERROR", err.Error())
		return
	}
	query := `SELECT record_key,source,actor_id,label,reward,created_at FROM (
      SELECT CONCAT('LIVE_GIFT:',gift_order_id) record_key,'LIVE_GIFT' source,viewer_id actor_id,CONCAT('直播礼物 · ',gift_id,' ×',quantity) label,diamond_reward reward,created_at FROM gift_orders WHERE creator_id=? AND status='ACCEPTED'
      UNION ALL SELECT CONCAT('PRIVATE_GIFT:',gift_order_id),'PRIVATE_GIFT',viewer_id,CONCAT('私信礼物 · ',gift_name,' ×',quantity),diamond_reward,created_at FROM dm_gift_orders WHERE recipient_id=? AND status='ACCEPTED'
      UNION ALL SELECT CONCAT('PAID_VIDEO:',unlock_id),'PAID_VIDEO',viewer_id,'付费视频解锁',diamond_reward,created_at FROM dm_video_unlocks WHERE owner_id=? AND charged_coins>0
      UNION ALL SELECT CONCAT('VIDEO_CALL:',call_id),'VIDEO_CALL',payer_id,'付费视频通话',price_coins,created_at FROM dm_call_charges WHERE owner_id=? AND price_coins>0
      UNION ALL SELECT CONCAT('LIVE_WATCH:',user_id,':',request_id),'LIVE_WATCH',user_id,'付费直播观看',diamond_reward,created_at FROM live_watch_unlocks WHERE creator_id=?
    ) incomes WHERE reward>0`
	args := []any{userID, userID, userID, userID, userID}
	if source != "" {
		query += " AND source=?"
		args = append(args, source)
	}
	if !cursor.At.IsZero() {
		query += " AND (created_at<? OR (created_at=? AND record_key<?))"
		args = append(args, cursor.At, cursor.At, cursor.Key)
	}
	query += " ORDER BY created_at DESC,record_key DESC LIMIT 31"
	rows, err := s.db.QueryContext(r.Context(), query, args...)
	if err != nil {
		httpx.WriteError(w, 500, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	records := make([]map[string]any, 0)
	var last incomeCursor
	more := false
	for rows.Next() {
		var key, kind, label string
		var actor uint64
		var reward int64
		var at time.Time
		if err = rows.Scan(&key, &kind, &actor, &label, &reward, &at); err != nil {
			httpx.WriteError(w, 500, "INTERNAL_ERROR", err.Error())
			return
		}
		if len(records) == 30 {
			more = true
			break
		}
		records = append(records, map[string]any{"id": key, "source": kind, "user_id": actor, "label": label, "income_fen": dashboardIncomeFen(reward, settings.Rules), "created_at": at})
		last = incomeCursor{at, key}
	}
	if err = rows.Err(); err != nil {
		httpx.WriteError(w, 500, "INTERNAL_ERROR", err.Error())
		return
	}
	next := ""
	if more {
		bytes, _ := json.Marshal(last)
		next = base64.RawURLEncoding.EncodeToString(bytes)
	}
	httpx.WriteJSON(w, 200, map[string]any{"records": records, "next_before": next})
}
