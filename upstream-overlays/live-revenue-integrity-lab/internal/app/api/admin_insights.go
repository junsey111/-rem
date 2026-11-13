package apiserver

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

func (s *Server) handleAdminInsights(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	if strings.HasPrefix(r.URL.Path, "/v1/internal/admin-insights/wallet/") {
		id, err := strconv.ParseUint(strings.TrimPrefix(r.URL.Path, "/v1/internal/admin-insights/wallet/"), 10, 64)
		if err != nil || id == 0 { httpx.WriteError(w, 400, "INVALID_USER", "invalid user"); return }
		var balance int64
		err = s.db.QueryRowContext(r.Context(), `SELECT COALESCE((SELECT available_balance FROM wallet_accounts WHERE user_id=? AND currency='COIN'),0)`, id).Scan(&balance)
		if err != nil { httpx.WriteError(w, 503, "REPORT_UNAVAILABLE", "wallet report is unavailable"); return }
		httpx.WriteJSON(w, 200, map[string]any{"user_id": id, "balance_coins": balance})
		return
	}
	if r.URL.Path == "/v1/internal/admin-insights/transactions" {
		s.adminTransactions(w, r)
		return
	}
	if r.URL.Path == "/v1/internal/admin-insights/creators" {
		s.adminCreators(w, r)
		return
	}
	if r.URL.Path != "/v1/internal/admin-insights/dashboard" {
		httpx.WriteError(w, 404, "NOT_FOUND", "route not found")
		return
	}
	from, first := time.Parse(time.RFC3339, r.URL.Query().Get("from"))
	until, second := time.Parse(time.RFC3339, r.URL.Query().Get("until"))
	if first != nil || second != nil || !from.Before(until) || until.Sub(from) > 366*24*time.Hour {
		httpx.WriteError(w, 400, "INVALID_RANGE", "date range is invalid")
		return
	}
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	now := time.Now().In(shanghai)
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, shanghai)
	week := today.AddDate(0, 0, -((int(today.Weekday())+6)%7))
	var daily, weekly, total, selected int64
	err := s.db.QueryRowContext(r.Context(), `SELECT
		COALESCE(SUM(CASE WHEN created_at>=? THEN charged_coins ELSE 0 END),0),
		COALESCE(SUM(CASE WHEN created_at>=? THEN charged_coins ELSE 0 END),0),
		COALESCE(SUM(charged_coins),0),
		COALESCE(SUM(CASE WHEN created_at>=? AND created_at<? THEN charged_coins ELSE 0 END),0)
		FROM gift_orders WHERE status='ACCEPTED'`, today.UTC(), week.UTC(), from.UTC(), until.UTC()).Scan(&daily, &weekly, &total, &selected)
	if err != nil {
		httpx.WriteError(w, 503, "REPORT_UNAVAILABLE", "gift report is unavailable")
		return
	}
	httpx.WriteJSON(w, 200, map[string]any{"gift_coins": map[string]int64{"today": daily, "week": weekly, "total": total, "selected": selected}})
}

func (s *Server) adminTransactions(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("user_id")
	var userID uint64
	if id != "" {
		var err error
		userID, err = strconv.ParseUint(id, 10, 64)
		if err != nil || userID == 0 { httpx.WriteError(w, 400, "INVALID_USER", "invalid user"); return }
	}
	rows, err := s.db.QueryContext(r.Context(), `SELECT t.tx_id,t.tx_type,t.actor_user_id,t.request_id,CAST(t.created_at AS CHAR),
		COALESCE((SELECT SUM(e.amount) FROM wallet_entries e WHERE e.tx_id=t.tx_id AND e.account_code=CONCAT('USER_COINS:',t.actor_user_id)),0)
		FROM wallet_transactions t WHERE (?=0 OR t.actor_user_id=?) ORDER BY t.tx_id DESC LIMIT 100`, userID, userID)
	if err != nil { httpx.WriteError(w, 503, "REPORT_UNAVAILABLE", "transaction report is unavailable"); return }
	defer rows.Close()
	items := []map[string]any{}
	for rows.Next() {
		var txID, actor uint64
		var amount int64
		var kind, requestID, created string
		if err = rows.Scan(&txID, &kind, &actor, &requestID, &created, &amount); err != nil { break }
		items = append(items, map[string]any{"tx_id": txID, "type": kind, "user_id": actor, "request_id": requestID, "created_at": created, "coin_change": amount})
	}
	if err == nil { err = rows.Err() }
	if err != nil { httpx.WriteError(w, 503, "REPORT_UNAVAILABLE", "transaction report is unavailable"); return }
	httpx.WriteJSON(w, 200, map[string]any{"transactions": items})
}

func (s *Server) adminCreators(w http.ResponseWriter, r *http.Request) {
	from, first := time.Parse(time.RFC3339, r.URL.Query().Get("from"))
	until, second := time.Parse(time.RFC3339, r.URL.Query().Get("until"))
	if first != nil || second != nil || !from.Before(until) || until.Sub(from) > 366*24*time.Hour {
		httpx.WriteError(w, 400, "INVALID_RANGE", "date range is invalid")
		return
	}
	rows, err := s.db.QueryContext(r.Context(), `SELECT q.user_id,CAST(MAX(a.created_at) AS CHAR),
		COALESCE((SELECT SUM(g.charged_coins) FROM gift_orders g WHERE g.creator_id=q.user_id AND g.status='ACCEPTED' AND g.created_at>=? AND g.created_at<?),0),
		COALESCE((SELECT SUM(g.diamond_reward) FROM gift_orders g WHERE g.creator_id=q.user_id AND g.status='ACCEPTED' AND g.created_at>=? AND g.created_at<?),0)
		FROM review_requests q JOIN review_actions a ON a.review_id=q.review_id AND a.decision='APPROVED'
		WHERE q.kind='CREATOR' AND q.status='APPROVED' GROUP BY q.user_id ORDER BY q.user_id DESC LIMIT 200`, from.UTC(), until.UTC(), from.UTC(), until.UTC())
	if err != nil { httpx.WriteError(w, 503, "REPORT_UNAVAILABLE", "creator report is unavailable"); return }
	defer rows.Close()
	items := []map[string]any{}
	for rows.Next() {
		var id uint64
		var approved string
		var coins, diamonds int64
		if err = rows.Scan(&id, &approved, &coins, &diamonds); err != nil { break }
		items = append(items, map[string]any{"user_id": id, "approved_at": approved, "gift_coins": coins, "reward_diamonds": diamonds})
	}
	if err == nil { err = rows.Err() }
	if err != nil { httpx.WriteError(w, 503, "REPORT_UNAVAILABLE", "creator report is unavailable"); return }
	httpx.WriteJSON(w, 200, map[string]any{"creators": items})
}
