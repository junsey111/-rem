package apiserver

import (
	"database/sql"
	"net/http"
	"strconv"
	"strings"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

// handleUserDiscovery returns only account ids. Billd applies its own account
// visibility and block policy before exposing profiles to the client.
func (s *Server) handleUserDiscovery(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	userID := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), userID); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	var recommendationsEnabled bool
	if err := s.db.QueryRowContext(r.Context(), `SELECT personalized_recommendations FROM users WHERE user_id=?`, userID).Scan(&recommendationsEnabled); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if !recommendationsEnabled {
		httpx.WriteJSON(w, http.StatusOK, map[string]any{"user_ids": []uint64{}})
		return
	}
	gender := strings.ToUpper(strings.TrimSpace(r.URL.Query().Get("gender")))
	if gender != "" && gender != "MALE" && gender != "FEMALE" && gender != "UNSPECIFIED" {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_GENDER", "gender filter is invalid")
		return
	}
	parseAge := func(name string) (*int, bool) {
		raw := strings.TrimSpace(r.URL.Query().Get(name))
		if raw == "" {
			return nil, true
		}
		value, err := strconv.Atoi(raw)
		if err != nil || value < 18 || value > 120 {
			return nil, false
		}
		return &value, true
	}
	ageMin, ok := parseAge("age_min")
	if !ok {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_AGE", "age filter must be between 18 and 120")
		return
	}
	ageMax, ok := parseAge("age_max")
	if !ok || ageMin != nil && ageMax != nil && *ageMax < *ageMin {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_AGE", "age filter range is invalid")
		return
	}
	region := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("region")))
	if region != "" && region != "same" {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_REGION", "region filter is invalid")
		return
	}

	where := []string{
		"u.user_id <> ?",
		"u.account_standing = 'GOOD'",
		"NOT EXISTS (SELECT 1 FROM platform_account_state p WHERE p.user_id=u.user_id AND p.disabled=TRUE)",
	}
	args := []any{userID}
	if gender != "" {
		where = append(where, "u.gender = ?")
		args = append(args, gender)
	}
	if ageMin != nil {
		where = append(where, "u.profile_source='SELF_REPORTED' AND u.age_years >= ?")
		args = append(args, *ageMin)
	}
	if ageMax != nil {
		where = append(where, "u.profile_source='SELF_REPORTED' AND u.age_years <= ?")
		args = append(args, *ageMax)
	}
	if region == "same" {
		var ownRegion sql.NullString
		if err := s.db.QueryRowContext(r.Context(), `SELECT region_code FROM users WHERE user_id=? AND profile_source='SELF_REPORTED'`, userID).Scan(&ownRegion); err != nil && err != sql.ErrNoRows {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		if !ownRegion.Valid || len(ownRegion.String) != 2 {
			httpx.WriteJSON(w, http.StatusOK, map[string]any{"user_ids": []uint64{}})
			return
		}
		where = append(where, "u.profile_source='SELF_REPORTED' AND u.region_code = ?")
		args = append(args, strings.ToUpper(ownRegion.String))
	}

	query := `SELECT u.user_id FROM users u WHERE ` + strings.Join(where, " AND ") + ` ORDER BY u.user_id DESC LIMIT 1000`
	rows, err := s.db.QueryContext(r.Context(), query, args...)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	ids := make([]uint64, 0)
	for rows.Next() {
		var id uint64
		if err := rows.Scan(&id); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		ids = append(ids, id)
	}
	if err := rows.Err(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"user_ids": ids})
}
