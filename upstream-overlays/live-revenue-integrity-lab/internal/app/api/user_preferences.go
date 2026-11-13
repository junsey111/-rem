package apiserver

import (
	"encoding/json"
	"net/http"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

func (s *Server) handleUserPreferences(w http.ResponseWriter, r *http.Request) {
	userID := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), userID); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if r.Method == http.MethodPut {
		var req struct {
			PersonalizedRecommendations *bool `json:"personalized_recommendations"`
			HideGuardian                 *bool `json:"hide_guardian"`
		}
		if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&req); err != nil ||
			req.PersonalizedRecommendations == nil && req.HideGuardian == nil {
			httpx.WriteError(w, http.StatusBadRequest, "INVALID_PREFERENCES", "at least one supported preference is required")
			return
		}
		if req.PersonalizedRecommendations != nil {
			if _, err := s.db.ExecContext(r.Context(), `UPDATE users SET personalized_recommendations=? WHERE user_id=?`, *req.PersonalizedRecommendations, userID); err != nil {
				httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
				return
			}
		}
		if req.HideGuardian != nil {
			if _, err := s.db.ExecContext(r.Context(), `UPDATE users SET hide_guardian=? WHERE user_id=?`, *req.HideGuardian, userID); err != nil {
				httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
				return
			}
		}
	} else if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET or PUT required")
		return
	}

	var personalized, hideGuardian bool
	if err := s.db.QueryRowContext(r.Context(), `SELECT personalized_recommendations,hide_guardian FROM users WHERE user_id=?`, userID).Scan(&personalized, &hideGuardian); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"personalized_recommendations": personalized,
		"hide_guardian":                 hideGuardian,
	})
}
