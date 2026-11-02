package apiserver

import (
	"database/sql"
	"net/http"
	"strconv"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

// Closing is idempotent and shares the match row lock with gift acceptance.
func (s *Server) handleMatchClose(w http.ResponseWriter, r *http.Request, rawID string) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "POST required")
		return
	}
	id, err := strconv.ParseUint(rawID, 10, 64)
	if err != nil || id == 0 {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_PATH", "invalid match id")
		return
	}
	tx, err := s.db.BeginTx(r.Context(), nil)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer tx.Rollback()
	var creator uint64
	var opponent sql.NullInt64
	err = tx.QueryRowContext(r.Context(), `SELECT creator_id,opponent_creator_id FROM live_matches WHERE match_id=? FOR UPDATE`, id).Scan(&creator, &opponent)
	if err == sql.ErrNoRows {
		httpx.WriteError(w, http.StatusNotFound, "MATCH_NOT_FOUND", "match not found")
		return
	}
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	user := authenticatedUserID(r)
	if creator != user && (!opponent.Valid || uint64(opponent.Int64) != user) {
		httpx.WriteError(w, http.StatusForbidden, "FORBIDDEN", "only a participant may end the match")
		return
	}
	if _, err := tx.ExecContext(r.Context(), `UPDATE live_matches SET status='CLOSED' WHERE match_id=?`, id); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if err := tx.Commit(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"match_id": id, "status": "CLOSED"})
}
