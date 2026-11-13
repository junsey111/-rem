package apiserver

import (
	"database/sql"
	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"net/http"
)

func (s *Server) handleLiveStats(w http.ResponseWriter, r *http.Request, liveID uint64) {
	var owner uint64
	err := s.db.QueryRowContext(r.Context(), `SELECT creator_id FROM live_sessions WHERE live_session_id=?`, liveID).Scan(&owner)
	if err == sql.ErrNoRows {
		httpx.WriteError(w, 404, "LIVE_SESSION_NOT_FOUND", "直播不存在")
		return
	}
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	if owner != authenticatedUserID(r) {
		httpx.WriteError(w, 403, "FORBIDDEN", "只有主播可以查看本场收益")
		return
	}
	var coins, diamonds, gifts int64
	err = s.db.QueryRowContext(r.Context(), `SELECT COALESCE(SUM(charged_coins),0),COALESCE(SUM(diamond_reward),0),COALESCE(SUM(quantity),0) FROM gift_orders WHERE live_session_id=? AND creator_id=? AND status='ACCEPTED'`, liveID, owner).Scan(&coins, &diamonds, &gifts)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	httpx.WriteJSON(w, 200, map[string]any{"live_session_id": liveID, "gift_coins": coins, "reward_diamonds": diamonds, "gift_count": gifts})
}
