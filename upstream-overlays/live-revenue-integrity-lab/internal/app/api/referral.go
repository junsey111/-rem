package apiserver

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func (s *Server) handleReferralMe(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	userID := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), userID); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "referral profile unavailable")
		return
	}
	profile, err := s.svc.ReferralProfile(r.Context(), userID, s.referrals)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "referral profile unavailable")
		return
	}
	httpx.WriteJSON(w, http.StatusOK, profile)
}

func (s *Server) handleReferralRewards(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	var before uint64
	if raw := r.URL.Query().Get("before_id"); raw != "" {
		var err error
		before, err = strconv.ParseUint(raw, 10, 64)
		if err != nil || before == 0 {
			httpx.WriteError(w, http.StatusBadRequest, "INVALID_CURSOR", "分页无效")
			return
		}
	}
	rewards, next, err := s.svc.ListReferralRewards(r.Context(), authenticatedUserID(r), before)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "referral rewards unavailable")
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"rewards": rewards, "next_before_id": next})
}

func (s *Server) handleReferralRedeem(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	var req service.ReferralRedeemRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid JSON body")
		return
	}
	userID := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), userID); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "referral redeem unavailable")
		return
	}
	res, appErr, err := s.svc.RedeemReferral(r.Context(), userID, req, s.referrals)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "referral redeem unavailable")
		return
	}
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, res)
}
