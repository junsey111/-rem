package apiserver

import (
	"database/sql"
	"net/http"
	"strconv"
	"strings"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

// Room names belong to the application: <liveID> or pk-<matchID>.
// This internal control-plane view never treats a missing row as an open room.
func (s *Server) handleMediaRoom(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	name := strings.TrimPrefix(r.URL.Path, "/v1/internal/media-rooms/")
	pk := strings.HasPrefix(name, "pk-")
	id, err := strconv.ParseUint(strings.TrimPrefix(name, "pk-"), 10, 64)
	canonical := strconv.FormatUint(id, 10)
	if pk {
		canonical = "pk-" + canonical
	}
	if err != nil || id == 0 || name != canonical {
		httpx.WriteError(w, 400, "INVALID_ROOM", "invalid managed room")
		return
	}
	var creators []uint64
	if pk {
		var first, second uint64
		err = s.db.QueryRowContext(r.Context(), `SELECT a.creator_id,b.creator_id FROM live_matches m JOIN live_sessions a ON a.live_session_id=m.live_session_id JOIN live_sessions b ON b.live_session_id=m.opponent_live_session_id WHERE m.match_id=? AND m.status='OPEN' AND a.status='OPEN' AND b.status='OPEN'`, id).Scan(&first, &second)
		creators = []uint64{first, second}
	} else {
		var creator uint64
		err = s.db.QueryRowContext(r.Context(), `SELECT creator_id FROM live_sessions WHERE live_session_id=? AND status='OPEN'`, id).Scan(&creator)
		creators = []uint64{creator}
	}
	open := err == nil
	if err != nil && err != sql.ErrNoRows {
		httpx.WriteError(w, 503, "MEDIA_STATE_UNAVAILABLE", "media authority unavailable")
		return
	}
	publishers := make([]string, 0, len(creators))
	if open {
		for _, creator := range creators {
			allowed, checkErr := s.svc.CanStartLive(r.Context(), creator)
			if checkErr != nil {
				httpx.WriteError(w, 503, "MEDIA_STATE_UNAVAILABLE", "media authority unavailable")
				return
			}
			if !allowed {
				open = false
				break
			}
			publishers = append(publishers, strconv.FormatUint(creator, 10))
		}
	}
	directoryOpen := open && !pk
	if !open {
		publishers = []string{}
	}
	if open && !pk {
		var inMatch bool
		if err := s.db.QueryRowContext(r.Context(), `SELECT EXISTS(SELECT 1 FROM live_matches WHERE status='OPEN' AND (live_session_id=? OR opponent_live_session_id=?))`, id, id).Scan(&inMatch); err != nil {
			httpx.WriteError(w, 503, "MEDIA_STATE_UNAVAILABLE", "media authority unavailable")
			return
		}
		open = !inMatch
	}
	result := map[string]any{"room": name, "open": open, "directory_open": directoryOpen, "publishers": publishers}
	if open && !pk {
		rows, err := s.db.QueryContext(r.Context(), `SELECT c.user_id,c.mode FROM cohost_requests c JOIN users u ON u.user_id=c.user_id WHERE c.live_session_id=? AND c.status='ACCEPTED' AND u.account_standing='GOOD' AND u.profile_source='SELF_REPORTED' AND u.age_years>=IF(UPPER(u.region_code)='KR',19,18) AND u.region_code IS NOT NULL AND NOT EXISTS(SELECT 1 FROM platform_account_state p WHERE p.user_id=u.user_id AND p.disabled=TRUE) ORDER BY c.cohost_id`, id)
		if err != nil {
			httpx.WriteError(w, 503, "MEDIA_STATE_UNAVAILABLE", "media authority unavailable")
			return
		}
		sources := map[string][]string{}
		for rows.Next() {
			var user uint64
			var mode string
			if err = rows.Scan(&user, &mode); err != nil {
				rows.Close()
				httpx.WriteError(w, 503, "MEDIA_STATE_UNAVAILABLE", "media authority unavailable")
				return
			}
			identity := strconv.FormatUint(user, 10)
			publishers = append(publishers, identity)
			sources[identity] = []string{"microphone"}
			if mode == "VIDEO" {
				sources[identity] = append(sources[identity], "camera")
			}
		}
		err = rows.Err()
		rows.Close()
		if err != nil {
			httpx.WriteError(w, 503, "MEDIA_STATE_UNAVAILABLE", "media authority unavailable")
			return
		}
		result["publishers"] = publishers
		if len(sources) > 0 {
			result["publish_sources"] = sources
		}
	}
	httpx.WriteJSON(w, 200, result)
}
