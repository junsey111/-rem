package apiserver

import (
	"database/sql"
	"net/http"
	"strconv"
	"strings"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

// Internal only: Billd has already checked the current account session.
func (s *Server) handleMediaAccess(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "POST required")
		return
	}
	var body struct {
		UserID uint64 `json:"user_id"`
		LiveID uint64 `json:"live_session_id"`
		Room   string `json:"room"`
	}
	if !decodeWatchBody(w, r, &body) {
		return
	}
	if body.UserID == 0 || body.LiveID == 0 {
		httpx.WriteError(w, 400, "INVALID_MEDIA_ID", "invalid media identity")
		return
	}
	var otherLive, firstHost, secondHost uint64
	expected := strconv.FormatUint(body.LiveID, 10)
	if body.Room != expected {
		id, err := strconv.ParseUint(strings.TrimPrefix(body.Room, "pk-"), 10, 64)
		if err != nil || body.Room != "pk-"+strconv.FormatUint(id, 10) || id == 0 {
			httpx.WriteError(w, 403, "INVALID_MEDIA_ROOM", "invalid room")
			return
		}
		err = s.db.QueryRowContext(r.Context(), `SELECT IF(m.live_session_id=?,m.opponent_live_session_id,m.live_session_id),a.creator_id,b.creator_id FROM live_matches m JOIN live_sessions a ON a.live_session_id=m.live_session_id JOIN live_sessions b ON b.live_session_id=m.opponent_live_session_id WHERE m.match_id=? AND m.status='OPEN' AND a.status='OPEN' AND b.status='OPEN' AND (m.live_session_id=? OR m.opponent_live_session_id=?)`, body.LiveID, id, body.LiveID, body.LiveID).Scan(&otherLive, &firstHost, &secondHost)
		if err == sql.ErrNoRows {
			httpx.WriteError(w, 403, "INVALID_MEDIA_ROOM", "unrelated room")
			return
		}
		if err != nil {
			httpx.WriteError(w, 503, "MEDIA_UNAVAILABLE", "media state unavailable")
			return
		}
	}
	var enabled bool
	err := s.db.QueryRowContext(r.Context(), `SELECT EXISTS(SELECT 1 FROM users u WHERE u.user_id=? AND u.account_standing='GOOD' AND NOT EXISTS(SELECT 1 FROM platform_account_state p WHERE p.user_id=u.user_id AND p.disabled=TRUE))`, body.UserID).Scan(&enabled)
	if err != nil {
		httpx.WriteError(w, 503, "MEDIA_UNAVAILABLE", "account state unavailable")
		return
	}
	if !enabled {
		httpx.WriteError(w, 403, "ACCOUNT_DISABLED", "account unavailable")
		return
	}
	if otherLive != 0 && body.UserID != firstHost && body.UserID != secondHost {
		other, err := s.svc.BeginMediaPreview(r.Context(), body.UserID, otherLive)
		if err != nil {
			writeWatch(w, nil, err)
			return
		}
		if other.Status == "LOCKED" {
			httpx.WriteJSON(w, 200, map[string]any{"allowed": false, "access": other})
			return
		}
	}
	access, err := s.svc.BeginMediaPreview(r.Context(), body.UserID, body.LiveID)
	if err != nil {
		writeWatch(w, nil, err)
		return
	}
	httpx.WriteJSON(w, 200, map[string]any{"allowed": access.Status == "FREE" || access.Status == "UNLOCKED" || access.Status == "PREVIEW" && access.SecondsRemaining > 0, "access": access})
}
