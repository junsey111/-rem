package apiserver

import (
	"net/http"
	"strconv"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

func (s *Server) handleUserDiscovery(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	query := r.URL.Query()
	if query.Get("gender") != "" {
		httpx.WriteError(w, http.StatusBadRequest, "GENDER_UNAVAILABLE", "性别资料尚未开放筛选")
		return
	}
	min, max := 18, 120
	if raw := query.Get("age_min"); raw != "" {
		parsed, err := strconv.Atoi(raw)
		if err != nil || parsed < 18 || parsed > 120 {
			httpx.WriteError(w, 400, "INVALID_AGE", "年龄范围无效")
			return
		}
		min = parsed
	}
	if raw := query.Get("age_max"); raw != "" {
		parsed, err := strconv.Atoi(raw)
		if err != nil || parsed < min || parsed > 120 {
			httpx.WriteError(w, 400, "INVALID_AGE", "年龄范围无效")
			return
		}
		max = parsed
	}
	region := query.Get("region")
	if region != "" && region != "same" {
		httpx.WriteError(w, 400, "INVALID_REGION", "地区条件无效")
		return
	}
	regionCode := ""
	if region == "same" {
		_ = s.db.QueryRowContext(r.Context(), `SELECT region_code FROM users WHERE user_id=? AND profile_source='SELF_REPORTED'`, authenticatedUserID(r)).Scan(&regionCode)
		if regionCode == "" {
			httpx.WriteJSON(w, 200, map[string]any{"user_ids": []uint64{}})
			return
		}
	}
	rows, err := s.db.QueryContext(r.Context(), `SELECT user_id FROM users WHERE user_id<>? AND profile_source='SELF_REPORTED' AND age_years BETWEEN ? AND ? AND (?='' OR region_code=?) ORDER BY user_id DESC LIMIT 500`, authenticatedUserID(r), min, max, regionCode, regionCode)
	if err != nil {
		httpx.WriteError(w, 503, "DISCOVERY_UNAVAILABLE", "用户筛选暂时不可用")
		return
	}
	defer rows.Close()
	ids := []uint64{}
	for rows.Next() {
		var id uint64
		if err := rows.Scan(&id); err != nil {
			httpx.WriteError(w, 503, "DISCOVERY_UNAVAILABLE", "用户筛选暂时不可用")
			return
		}
		ids = append(ids, id)
	}
	if rows.Err() != nil {
		httpx.WriteError(w, 503, "DISCOVERY_UNAVAILABLE", "用户筛选暂时不可用")
		return
	}
	httpx.WriteJSON(w, 200, map[string]any{"user_ids": ids})
}
