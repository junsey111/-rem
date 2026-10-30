// Package apiserver hosts the HTTP ingress handlers.
package apiserver

import (
	"context"
	"crypto/hmac"
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/redis/go-redis/v9"
)

// Server is the HTTP API runtime.
type Server struct {
	httpServer    *http.Server
	svc           *service.Service
	db            *sql.DB
	redis         *redis.Client
	jwtSecret     string
	paymentSecret string
	internalKey   string
	reqCounter    uint64
}

// New creates a new API server.
func New(addr string, db *sql.DB, redisClient *redis.Client, jwtSecret, paymentSecret, internalKey string) *Server {
	registerMetrics()

	svc := service.New(db)
	mux := http.NewServeMux()
	s := &Server{
		httpServer: &http.Server{
			Addr:              addr,
			Handler:           mux,
			ReadHeaderTimeout: 5 * time.Second,
		},
		svc:           svc,
		db:            db,
		redis:         redisClient,
		jwtSecret:     jwtSecret,
		paymentSecret: paymentSecret,
		internalKey:   internalKey,
	}
	mux.Handle("/metrics", promhttp.Handler())
	mux.HandleFunc("/healthz", s.wrap("healthz", s.handleHealth))
	mux.HandleFunc("/v1/internal/users", s.requireInternalKey(s.wrap("internal_user_sync", s.handleInternalUserSync)))
	mux.HandleFunc("/v1/internal/lives", s.requireInternalKey(s.wrap("internal_live_sync", s.handleInternalLiveSync)))
	mux.HandleFunc("/v1/gifts/catalog", s.wrap("gift_catalog", s.handleGiftCatalog))
	mux.HandleFunc("/v1/wallets/me", s.requireAuth(s.wrap("wallet_balance", s.handleWalletBalance)))
	mux.HandleFunc("/v1/creators/me/earnings", s.requireAuth(s.wrap("creator_earnings", s.handleCreatorEarnings)))
	mux.HandleFunc("/v1/rankings/creators", s.requireAuth(s.wrap("creator_rankings", s.handleCreatorRankings)))
	mux.HandleFunc("/v1/rankings/contributors", s.requireAuth(s.wrap("contributor_rankings", s.handleContributorRankings)))
	mux.HandleFunc("/v1/wallets/recharges", s.wrap("wallet_recharge", s.handleRecharge))
	mux.HandleFunc("/v1/gifts", s.requireAuth(s.wrap("gift_send", s.handleGift)))
	mux.HandleFunc("/v1/internal/comments", s.requireAuth(s.wrap("internal_comments", s.handleInternalComments)))
	mux.HandleFunc("/v1/internal/watch-minutes", s.requireAuth(s.wrap("internal_watch_minutes", s.handleInternalWatchMinutes)))
	mux.HandleFunc("/v1/lives/", s.requireAuth(s.wrap("lives_prefix", s.handleLivesPrefix)))
	mux.HandleFunc("/v1/matches/", s.requireAuth(s.wrap("match_score", s.handleMatchScore)))
	mux.HandleFunc("/v1/campaigns/", s.wrap("campaigns_prefix", s.handleCampaignPrefix))
	mux.HandleFunc("/v1/settlements/", s.requireAuth(s.wrap("settlement_get", s.handleSettlementGet)))
	return s
}

// Start runs the HTTP server.
func (s *Server) Start() error {
	log.Printf("api listening on %s", s.httpServer.Addr)
	return s.httpServer.ListenAndServe()
}

// Shutdown gracefully stops the server.
func (s *Server) Shutdown(ctx context.Context) error {
	return s.httpServer.Shutdown(ctx)
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (s *Server) handleGiftCatalog(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	rows, err := s.db.QueryContext(r.Context(), `SELECT gift_id, display_name, coin_price, match_points, diamond_reward FROM gift_catalog WHERE enabled = TRUE ORDER BY coin_price`)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	type gift struct {
		GiftID      string `json:"gift_id"`
		Name        string `json:"display_name"`
		Coins       uint32 `json:"coin_price"`
		MatchPoints uint32 `json:"match_points"`
		Diamonds    uint32 `json:"diamond_reward"`
	}
	items := []gift{}
	for rows.Next() {
		var item gift
		if err := rows.Scan(&item.GiftID, &item.Name, &item.Coins, &item.MatchPoints, &item.Diamonds); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"gifts": items})
}

func (s *Server) handleWalletBalance(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	userID := authenticatedUserID(r)
	var balance int64
	err := s.db.QueryRowContext(r.Context(), `SELECT available_balance FROM wallet_accounts WHERE user_id = ? AND currency = 'COIN'`, userID).Scan(&balance)
	if err != nil && err != sql.ErrNoRows {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"user_id": userID, "balance": balance})
}

func requestedPeriod(r *http.Request) (time.Time, string, bool) {
	period := r.URL.Query().Get("period")
	if period == "" {
		period = "monthly"
	}
	now := time.Now().UTC()
	switch period {
	case "daily":
		return time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC), period, true
	case "monthly":
		return time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, time.UTC), period, true
	default:
		return time.Time{}, period, false
	}
}

func (s *Server) handleCreatorEarnings(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	start, period, ok := requestedPeriod(r)
	if !ok {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_PERIOD", "period must be daily or monthly")
		return
	}
	var diamonds, gifts int64
	err := s.db.QueryRowContext(r.Context(), `SELECT COALESCE(SUM(diamond_reward),0), COUNT(*) FROM gift_orders WHERE creator_id=? AND status='ACCEPTED' AND created_at>=?`, authenticatedUserID(r), start).Scan(&diamonds, &gifts)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"creator_id": authenticatedUserID(r), "period": period, "diamonds": diamonds, "gifts": gifts})
}

func (s *Server) handleCreatorRankings(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	start, period, ok := requestedPeriod(r)
	if !ok {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_PERIOD", "period must be daily or monthly")
		return
	}
	limit := parseLimit(r.URL.Query().Get("limit"))
	rows, err := s.db.QueryContext(r.Context(), `SELECT creator_id, COALESCE(SUM(diamond_reward),0), COUNT(*) FROM gift_orders WHERE status='ACCEPTED' AND created_at>=? GROUP BY creator_id ORDER BY 2 DESC, creator_id ASC LIMIT ?`, start, limit)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	type row struct {
		CreatorID uint64 `json:"creator_id"`
		Diamonds  int64  `json:"diamonds"`
		Gifts     int64  `json:"gifts"`
	}
	result := []row{}
	for rows.Next() {
		var item row
		if err := rows.Scan(&item.CreatorID, &item.Diamonds, &item.Gifts); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		result = append(result, item)
	}
	if err := rows.Err(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"period": period, "rankings": result})
}

func (s *Server) handleContributorRankings(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	start, period, ok := requestedPeriod(r)
	if !ok {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_PERIOD", "period must be daily or monthly")
		return
	}
	limit := parseLimit(r.URL.Query().Get("limit"))
	rows, err := s.db.QueryContext(r.Context(), `SELECT viewer_id, COALESCE(SUM(charged_coins),0), COUNT(*) FROM gift_orders WHERE status='ACCEPTED' AND created_at>=? GROUP BY viewer_id ORDER BY 2 DESC, viewer_id ASC LIMIT ?`, start, limit)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	type row struct {
		ViewerID uint64 `json:"viewer_id"`
		Coins    int64  `json:"coins"`
		Gifts    int64  `json:"gifts"`
	}
	result := []row{}
	for rows.Next() {
		var item row
		if err := rows.Scan(&item.ViewerID, &item.Coins, &item.Gifts); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		result = append(result, item)
	}
	if err := rows.Err(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"period": period, "rankings": result})
}

func (s *Server) handleMatchScore(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) != 3 || parts[2] != "score" {
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
		return
	}
	matchID, err := strconv.ParseUint(parts[1], 10, 64)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_PATH", "invalid match id")
		return
	}
	rows, err := s.db.QueryContext(r.Context(), `SELECT creator_id, COALESCE(SUM(match_points_added),0) FROM gift_orders WHERE match_id=? AND status='ACCEPTED' GROUP BY creator_id ORDER BY creator_id`, matchID)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	type score struct {
		CreatorID uint64 `json:"creator_id"`
		Points    int64  `json:"points"`
	}
	result := []score{}
	for rows.Next() {
		var item score
		if err := rows.Scan(&item.CreatorID, &item.Points); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		result = append(result, item)
	}
	if err := rows.Err(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"match_id": matchID, "scores": result})
}

func (s *Server) requireInternalKey(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		provided := []byte(r.Header.Get("X-Internal-Key"))
		expected := []byte(s.internalKey)
		if len(expected) == 0 || !hmac.Equal(provided, expected) {
			httpx.WriteError(w, http.StatusUnauthorized, "UNAUTHORIZED", "internal service key required")
			return
		}
		next(w, r)
	}
}

func (s *Server) handleInternalUserSync(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	var req struct {
		UserID           uint64 `json:"user_id"`
		AgeYears         uint8  `json:"age_years"`
		RegionCode       string `json:"region_code"`
		CanGoLive        bool   `json:"can_go_live"`
		LiveGiftsEnabled bool   `json:"live_gifts_enabled"`
		AccountType      string `json:"account_type"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.UserID == 0 {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid user payload")
		return
	}
	if req.AgeYears == 0 {
		req.AgeYears = 18
	}
	if req.RegionCode == "" {
		req.RegionCode = "US"
	}
	if req.AccountType == "" {
		req.AccountType = "NORMAL"
	}
	_, err := s.db.ExecContext(r.Context(), `
		INSERT INTO users (user_id, age_years, region_code, account_standing, can_go_live, live_gifts_enabled, account_type)
		VALUES (?, ?, ?, 'GOOD', ?, ?, ?)
		ON DUPLICATE KEY UPDATE age_years=VALUES(age_years), region_code=VALUES(region_code), can_go_live=VALUES(can_go_live), live_gifts_enabled=VALUES(live_gifts_enabled), account_type=VALUES(account_type)
	`, req.UserID, req.AgeYears, req.RegionCode, req.CanGoLive, req.LiveGiftsEnabled, req.AccountType)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusCreated, map[string]any{"user_id": req.UserID})
}

func (s *Server) handleInternalLiveSync(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	var req struct {
		LiveSessionID  uint64  `json:"live_session_id"`
		CreatorID      uint64  `json:"creator_id"`
		CampaignID     *uint64 `json:"campaign_id"`
		MatchID        *uint64 `json:"match_id"`
		Mode           string  `json:"mode"`
		SpecificGiftID *string `json:"specific_gift_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.LiveSessionID == 0 || req.CreatorID == 0 {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid live session payload")
		return
	}
	if req.Mode == "" {
		req.Mode = "ALL_GIFTS"
	}
	_, err := s.db.ExecContext(r.Context(), `
		INSERT INTO live_sessions (live_session_id, creator_id, campaign_id, status, started_at, closed_at)
		VALUES (?, ?, ?, 'OPEN', UTC_TIMESTAMP(6), NULL)
		ON DUPLICATE KEY UPDATE creator_id=VALUES(creator_id), campaign_id=VALUES(campaign_id), status='OPEN', closed_at=NULL
	`, req.LiveSessionID, req.CreatorID, req.CampaignID)
	if err == nil && req.MatchID != nil {
		_, err = s.db.ExecContext(r.Context(), `
			INSERT INTO live_matches (match_id, live_session_id, mode, specific_gift_id, status)
			VALUES (?, ?, ?, ?, 'OPEN')
			ON DUPLICATE KEY UPDATE live_session_id=VALUES(live_session_id), mode=VALUES(mode), specific_gift_id=VALUES(specific_gift_id), status='OPEN'
		`, *req.MatchID, req.LiveSessionID, req.Mode, req.SpecificGiftID)
	}
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusCreated, map[string]any{"live_session_id": req.LiveSessionID, "match_id": req.MatchID})
}

func (s *Server) handleRecharge(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	defer func() {
		_ = r.Body.Close()
	}()

	var req service.RechargeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid JSON body")
		return
	}
	if !verifyPaymentSignature(r, s.paymentSecret, req.RequestID+":"+strconv.FormatUint(uint64(req.ViewerID), 10)+":"+strconv.FormatUint(uint64(req.Coins), 10)+":"+req.PaymentRef) {
		httpx.WriteError(w, http.StatusUnauthorized, "INVALID_PAYMENT_SIGNATURE", "verified payment callback required")
		return
	}
	if err := s.ensureUser(r.Context(), req.ViewerID); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	res, appErr, err := s.svc.Recharge(r.Context(), req)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}

	httpx.WriteJSON(w, http.StatusCreated, res)
}

func (s *Server) handleGift(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	defer func() {
		_ = r.Body.Close()
	}()

	var req service.GiftRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid JSON body")
		return
	}
	req.ViewerID = authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), req.ViewerID); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	res, appErr, err := s.svc.SendGift(r.Context(), req)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	httpx.WriteJSON(w, http.StatusCreated, res)
}

func (s *Server) ensureUser(ctx context.Context, userID uint64) error {
	if userID == 0 {
		return fmt.Errorf("invalid user id")
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO users (user_id, age_years, region_code, account_standing, can_go_live, live_gifts_enabled, account_type)
		VALUES (?, 18, 'US', 'GOOD', TRUE, TRUE, 'NORMAL')
		ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
	`, userID)
	return err
}

func (s *Server) handleLivesPrefix(w http.ResponseWriter, r *http.Request) {
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if (len(parts) != 3 && len(parts) != 4) || parts[0] != "v1" || parts[1] != "lives" {
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
		return
	}
	liveSessionID, err := strconv.ParseUint(parts[2], 10, 64)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_PATH", "invalid live session id")
		return
	}
	if len(parts) == 3 && r.Method == http.MethodGet {
		var creatorID uint64
		var status string
		if err := s.db.QueryRowContext(r.Context(), `SELECT creator_id, status FROM live_sessions WHERE live_session_id = ?`, liveSessionID).Scan(&creatorID, &status); err != nil {
			if err == sql.ErrNoRows {
				httpx.WriteError(w, http.StatusNotFound, "LIVE_SESSION_NOT_FOUND", "live session not found")
				return
			}
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		httpx.WriteJSON(w, http.StatusOK, map[string]any{"live_session_id": liveSessionID, "creator_id": creatorID, "status": status})
		return
	}
	if len(parts) != 4 {
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
		return
	}

	switch {
	case r.Method == http.MethodPost && parts[3] == "close":
		var creatorID uint64
		err := s.db.QueryRowContext(r.Context(), `SELECT creator_id FROM live_sessions WHERE live_session_id = ?`, liveSessionID).Scan(&creatorID)
		if err == sql.ErrNoRows {
			httpx.WriteError(w, http.StatusNotFound, "LIVE_SESSION_NOT_FOUND", "live session not found")
			return
		}
		if err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		if creatorID != authenticatedUserID(r) {
			httpx.WriteError(w, http.StatusForbidden, "FORBIDDEN", "only the live host may close this session")
			return
		}
		appErr := s.svc.CloseLiveSession(r.Context(), liveSessionID)
		if appErr != nil {
			httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
			return
		}
		httpx.WriteJSON(w, http.StatusAccepted, map[string]any{
			"live_session_id": liveSessionID,
			"status":          "CLOSED",
		})
		return
	case r.Method == http.MethodGet && parts[3] == "contributors":
		limit := parseLimit(r.URL.Query().Get("limit"))
		rows, err := s.getLiveContributors(r.Context(), liveSessionID, limit)
		if err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		httpx.WriteJSON(w, http.StatusOK, map[string]any{
			"live_session_id": liveSessionID,
			"contributors":    rows,
		})
		return
	default:
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
	}
}

func (s *Server) handleCampaignPrefix(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) != 4 || parts[0] != "v1" || parts[1] != "campaigns" || parts[3] != "leaderboard" {
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
		return
	}
	campaignID, err := strconv.ParseUint(parts[2], 10, 64)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_PATH", "invalid campaign id")
		return
	}
	limit := parseLimit(r.URL.Query().Get("limit"))
	rows, err := s.getCampaignLeaderboard(r.Context(), campaignID, limit)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"campaign_id": campaignID,
		"leaderboard": rows,
	})
}

func (s *Server) handleSettlementGet(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) != 3 || parts[0] != "v1" || parts[1] != "settlements" {
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
		return
	}
	liveSessionID, err := strconv.ParseUint(parts[2], 10, 64)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_PATH", "invalid live session id")
		return
	}
	var creatorID uint64
	if err := s.db.QueryRowContext(r.Context(), `SELECT creator_id FROM live_sessions WHERE live_session_id=?`, liveSessionID).Scan(&creatorID); err != nil {
		httpx.WriteError(w, http.StatusNotFound, "LIVE_SESSION_NOT_FOUND", "live session not found")
		return
	}
	if creatorID != authenticatedUserID(r) {
		httpx.WriteError(w, http.StatusForbidden, "FORBIDDEN", "only the live host may view settlement")
		return
	}

	res, appErr, err := s.svc.GetSettlement(r.Context(), liveSessionID)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, res)
}

func (s *Server) handleInternalComments(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	defer func() {
		_ = r.Body.Close()
	}()

	var req service.InternalCommentRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid JSON body")
		return
	}
	if appErr := s.svc.EnqueueCommentEvent(r.Context(), req); appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	httpx.WriteJSON(w, http.StatusAccepted, map[string]any{"queued": 1})
}

func (s *Server) handleInternalWatchMinutes(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	defer func() {
		_ = r.Body.Close()
	}()

	var req service.InternalWatchMinutesRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid JSON body")
		return
	}
	queued, appErr := s.svc.EnqueueWatchMinuteEvents(r.Context(), req)
	if appErr != nil {
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	httpx.WriteJSON(w, http.StatusAccepted, map[string]any{"queued": queued})
}

// WithTimeout is used by tests and shutdown hooks.
func WithTimeout(ctx context.Context, d time.Duration) (context.Context, context.CancelFunc) {
	return context.WithTimeout(ctx, d)
}

func parseLimit(raw string) int {
	if strings.TrimSpace(raw) == "" {
		return 20
	}
	n, err := strconv.Atoi(raw)
	if err != nil || n <= 0 {
		return 20
	}
	if n > 100 {
		return 100
	}
	return n
}

func (s *Server) getLiveContributors(ctx context.Context, liveSessionID uint64, limit int) ([]service.ContributorSummary, error) {
	if s.redis != nil {
		key := fmt.Sprintf("lb:contributors:%d", liveSessionID)
		zs, err := s.redis.ZRevRangeWithScores(ctx, key, 0, int64(limit-1)).Result()
		if err == nil {
			out := make([]service.ContributorSummary, 0, len(zs))
			for _, z := range zs {
				member, ok := z.Member.(string)
				if !ok {
					continue
				}
				viewerID, err := strconv.ParseUint(member, 10, 64)
				if err != nil {
					continue
				}
				out = append(out, service.ContributorSummary{ViewerID: viewerID, Coins: int64(z.Score)})
			}
			return out, nil
		}
	}
	return s.svc.GetLiveContributors(ctx, liveSessionID, limit)
}

func (s *Server) getCampaignLeaderboard(ctx context.Context, campaignID uint64, limit int) ([]service.CampaignRankRow, error) {
	if s.redis != nil {
		key := fmt.Sprintf("lb:campaign:%d", campaignID)
		zs, err := s.redis.ZRevRangeWithScores(ctx, key, 0, int64(limit-1)).Result()
		if err == nil {
			out := make([]service.CampaignRankRow, 0, len(zs))
			for _, z := range zs {
				member, ok := z.Member.(string)
				if !ok {
					continue
				}
				creatorID, err := strconv.ParseUint(member, 10, 64)
				if err != nil {
					continue
				}
				out = append(out, service.CampaignRankRow{CreatorID: creatorID, Points: int64(z.Score)})
			}
			return out, nil
		}
	}
	return s.svc.GetCampaignLeaderboard(ctx, campaignID, limit)
}

func (s *Server) wrap(route string, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		requestID := r.Header.Get("X-Request-ID")
		if requestID == "" {
			requestID = s.newRequestID()
		}
		w.Header().Set("X-Request-ID", requestID)

		sw := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		start := time.Now()
		next(sw, r)
		durationSec := time.Since(start).Seconds()

		statusCode := strconv.Itoa(sw.status)
		apiRequestsTotal.WithLabelValues(route, r.Method, statusCode).Inc()
		apiRequestDurationSeconds.WithLabelValues(route, r.Method).Observe(durationSec)
		if sw.status >= 400 {
			apiRejectsTotal.WithLabelValues(route, statusCode).Inc()
		}

		log.Printf(
			`{"route":%q,"status":%d,"duration_ms":%.2f}`,
			route,
			sw.status,
			durationSec*1000,
		)
	}
}

func (s *Server) newRequestID() string {
	v := atomic.AddUint64(&s.reqCounter, 1)
	return fmt.Sprintf("req-%d", v)
}

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (w *statusWriter) WriteHeader(code int) {
	w.status = code
	w.ResponseWriter.WriteHeader(code)
}

var (
	metricsOnce = sync.Once{}

	apiRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "live_revenue_api_requests_total",
			Help: "Total API requests by route/method/status.",
		},
		[]string{"route", "method", "status"},
	)
	apiRequestDurationSeconds = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "live_revenue_api_request_duration_seconds",
			Help:    "API request latency in seconds by route and method.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"route", "method"},
	)
	apiRejectsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "live_revenue_api_rejects_total",
			Help: "Total rejected API requests by route/status.",
		},
		[]string{"route", "status"},
	)
)

func registerMetrics() {
	metricsOnce.Do(func() {
		prometheus.MustRegister(apiRequestsTotal, apiRequestDurationSeconds, apiRejectsTotal)
	})
}
