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
	accountURL    string
	referrals     service.ReferralPolicy
	reqCounter    atomic.Uint64
}

// New creates a new API server.
func New(addr string, db *sql.DB, redisClient *redis.Client, jwtSecret, paymentSecret, internalKey, accountURL string) *Server {
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
		accountURL:    accountURL,
		referrals:     service.LoadReferralPolicy(),
	}
	mux.Handle("/metrics", promhttp.Handler())
	mux.HandleFunc("/healthz", s.wrap("healthz", s.handleHealth))
	mux.HandleFunc("/v1/internal/users", s.requireInternalKey(s.wrap("internal_user_sync", s.handleInternalUserSync)))
	mux.HandleFunc("/v1/internal/lives", s.requireInternalKey(s.wrap("internal_live_sync", s.handleInternalLiveSync)))
	mux.HandleFunc("/v1/internal/matches", s.requireInternalKey(s.wrap("internal_match_sync", s.handleInternalMatchSync)))
	mux.HandleFunc("/v1/internal/media-access", s.requireInternalKey(s.wrap("internal_media_access", s.handleMediaAccess)))
	mux.HandleFunc("/v1/internal/media-rooms/", s.requireInternalKey(s.wrap("internal_media_room", s.handleMediaRoom)))
	mux.HandleFunc("/v1/internal/operations/", s.requireInternalKey(s.wrap("internal_operations", s.handleInternalOperations)))
	mux.HandleFunc("/v1/internal/finance/", s.requireInternalKey(s.wrap("internal_finance", s.handleFinanceAdmin)))
	mux.HandleFunc("/v1/internal/platform-account", s.requireInternalKey(s.wrap("platform_account", s.handlePlatformAccount)))
	mux.HandleFunc("/v1/creators/me/withdrawals", s.requireAuth(s.wrap("withdrawals", s.handleWithdrawals)))
	mux.HandleFunc("/v1/users/me/refunds", s.requireAuth(s.wrap("refunds", s.handleMyRefunds)))
	mux.HandleFunc("/v1/finance/rules", s.requireAuth(s.wrap("finance_rules", s.handleFinanceRules)))
	mux.HandleFunc("/v1/creators/me/withdrawable", s.requireAuth(s.wrap("withdrawable", s.handleWithdrawable)))
	mux.HandleFunc("/v1/gifts/catalog", s.wrap("gift_catalog", s.handleGiftCatalog))
	mux.HandleFunc("/v1/internal/catalog", s.requireInternalKey(s.wrap("admin_catalog", s.handleAdminCatalog)))
	mux.HandleFunc("/v1/internal/reviews/", s.requireInternalKey(s.wrap("admin_reviews", s.handleAdminReviews)))
	mux.HandleFunc("/v1/users/me/creator-applications", s.requireAuth(s.wrap("creator_applications", s.handleReviews)))
	mux.HandleFunc("/v1/users/me/reports", s.requireAuth(s.wrap("reports", s.handleReviews)))
	mux.HandleFunc("/v1/internal/cohost-presence", s.requireInternalKey(s.wrap("cohost_presence", s.handleCohostPresence)))
	mux.HandleFunc("/v1/cohosts/", s.requireAuth(s.wrap("cohosts", s.handleCohost)))
	mux.HandleFunc("/v1/internal/business/", s.requireInternalKey(s.wrap("business_settings", s.handleBusinessSettings)))
	mux.HandleFunc("/v1/internal/admission", s.requireInternalKey(s.wrap("admission", s.handleAdmission)))
	mux.HandleFunc("/v1/wallets/me", s.requireAuth(s.wrap("wallet_balance", s.handleWalletBalance)))
	mux.HandleFunc("/v1/users/me/profile", s.requireAuth(s.wrap("user_profile", s.handleUserProfile)))
	mux.HandleFunc("/v1/users/discovery", s.requireAuth(s.wrap("user_discovery", s.handleUserDiscovery)))
	mux.HandleFunc("/v1/creators/me/earnings", s.requireAuth(s.wrap("creator_earnings", s.handleCreatorEarnings)))
	mux.HandleFunc("/v1/creators/me/sessions", s.requireAuth(s.wrap("creator_sessions", s.handleCreatorSessions)))
	mux.HandleFunc("/v1/creators/me/supporters", s.requireAuth(s.wrap("creator_supporters", s.handleCreatorSupporters)))
	mux.HandleFunc("/v1/users/me/level", s.requireAuth(s.wrap("user_level", s.handleUserLevel)))
	mux.HandleFunc("/v1/referrals/me", s.requireAuth(s.wrap("referral_me", s.handleReferralMe)))
	mux.HandleFunc("/v1/referrals/redeem", s.requireAuth(s.wrap("referral_redeem", s.handleReferralRedeem)))
	mux.HandleFunc("/v1/rankings/creators", s.requireAuth(s.wrap("creator_rankings", s.handleCreatorRankings)))
	mux.HandleFunc("/v1/rankings/contributors", s.requireAuth(s.wrap("contributor_rankings", s.handleContributorRankings)))
	mux.HandleFunc("/v1/wallets/recharges", s.wrap("wallet_recharge", s.handleRecharge))
	mux.HandleFunc("/v1/gifts", s.requireAuth(s.wrap("gift_send", s.handleGift)))
	mux.HandleFunc("/v1/dm/gifts", s.requireAuth(s.wrap("dm_gifts", s.handleDmGifts)))
	mux.HandleFunc("/v1/dm/gifts/", s.requireAuth(s.wrap("dm_gift_read", s.handleDmGiftRead)))
	mux.HandleFunc("/v1/dm/video-unlocks", s.requireAuth(s.wrap("dm_video_unlock", s.handleDmVideoUnlock)))
	mux.HandleFunc("/v1/dm/videos/", s.requireAuth(s.wrap("dm_video_access", s.handleDmVideoAccess)))
	mux.HandleFunc("/v1/users/me/gifts", s.requireAuth(s.wrap("gift_history", s.handleGiftHistory)))
	mux.HandleFunc("/v1/users/me/gifts/", s.requireAuth(s.wrap("gift_receipt", s.handleGiftReceipt)))
	mux.HandleFunc("/v1/internal/comments", s.requireAuth(s.wrap("internal_comments", s.handleInternalComments)))
	mux.HandleFunc("/v1/internal/watch-minutes", s.requireAuth(s.wrap("internal_watch_minutes", s.handleInternalWatchMinutes)))
	mux.HandleFunc("/v1/lives/", s.requireAuth(s.wrap("lives_prefix", s.handleLivesPrefix)))
	mux.HandleFunc("/v1/matches/", s.requireAuth(s.wrap("match_score", s.handleMatchScore)))
	mux.HandleFunc("/v1/match-invitations", s.requireAuth(s.wrap("match_invitations", s.handleMatchInvitations)))
	mux.HandleFunc("/v1/match-invitations/", s.requireAuth(s.wrap("match_invitation_response", s.handleMatchInvitationResponse)))
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
	rows, err := s.db.QueryContext(r.Context(), `SELECT gift_id, display_name, coin_price, match_points, diamond_reward, effect_type, effect_url, effect_video_mode, effect_md5, icon_url FROM gift_catalog WHERE enabled = TRUE ORDER BY coin_price`)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	type gift struct {
		GiftID          string `json:"gift_id"`
		Name            string `json:"display_name"`
		Coins           uint32 `json:"coin_price"`
		MatchPoints     uint32 `json:"match_points"`
		Diamonds        uint32 `json:"diamond_reward"`
		EffectType      string `json:"effect_type"`
		EffectURL       string `json:"effect_url"`
		EffectVideoMode string `json:"effect_video_mode"`
		EffectMD5       string `json:"effect_md5"`
		IconURL         string `json:"icon_url"`
	}
	items := []gift{}
	for rows.Next() {
		var item gift
		if err := rows.Scan(&item.GiftID, &item.Name, &item.Coins, &item.MatchPoints, &item.Diamonds, &item.EffectType, &item.EffectURL, &item.EffectVideoMode, &item.EffectMD5, &item.IconURL); err != nil {
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
	userID := authenticatedUserID(r)
	var diamonds, gifts int64
	err := s.db.QueryRowContext(r.Context(), `SELECT COALESCE(SUM(diamond_reward),0), COUNT(*) FROM gift_orders WHERE creator_id=? AND status='ACCEPTED' AND created_at>=?`, userID, start).Scan(&diamonds, &gifts)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	_, dmDiamonds, dmGifts, err := s.dmPointTotals(r.Context(), userID, &start)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"creator_id": userID, "period": period, "diamonds": diamonds + dmDiamonds, "gifts": gifts + dmGifts})
}

func (s *Server) handleCreatorSessions(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	userID := authenticatedUserID(r)
	rows, err := s.db.QueryContext(r.Context(), `
		SELECT live_session_id, status, started_at,
			GREATEST(0, TIMESTAMPDIFF(SECOND, started_at, COALESCE(closed_at, UTC_TIMESTAMP(6))))
		FROM live_sessions WHERE creator_id=? ORDER BY started_at DESC LIMIT 30`, userID)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	sessions := make([]map[string]any, 0)
	for rows.Next() {
		var id uint64
		var status string
		var startedAt time.Time
		var duration int64
		if err := rows.Scan(&id, &status, &startedAt, &duration); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		sessions = append(sessions, map[string]any{"live_session_id": strconv.FormatUint(id, 10), "status": status,
			"started_at": startedAt.UTC().Format(time.RFC3339), "duration_seconds": duration})
	}
	if err := rows.Err(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"sessions": sessions})
}

func (s *Server) handleCreatorSupporters(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	userID := authenticatedUserID(r)
	rows, err := s.db.QueryContext(r.Context(), `
		SELECT viewer_id, COUNT(*), COALESCE(SUM(charged_coins),0)
		FROM gift_orders
		WHERE creator_id=? AND status='ACCEPTED' AND created_at>=UTC_TIMESTAMP(6)-INTERVAL 7 DAY
		GROUP BY viewer_id ORDER BY 3 DESC, viewer_id ASC LIMIT 50`, userID)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()
	type supporter struct {
		UserID uint64 `json:"user_id"`
		Gifts  int64  `json:"gift_count"`
		Coins  int64  `json:"coins"`
	}
	supporters := make([]supporter, 0)
	for rows.Next() {
		var item supporter
		if err := rows.Scan(&item.UserID, &item.Gifts, &item.Coins); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		supporters = append(supporters, item)
	}
	if err := rows.Err(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"period_days": 7, "supporters": supporters})
}

func (s *Server) handleUserLevel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	userID := authenticatedUserID(r)
	var spent, earned int64
	err := s.db.QueryRowContext(r.Context(), `
		SELECT
			COALESCE(SUM(CASE WHEN viewer_id = ? AND status = 'ACCEPTED' THEN charged_coins ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN creator_id = ? AND status = 'ACCEPTED' THEN diamond_reward ELSE 0 END), 0)
		FROM gift_orders
	`, userID, userID).Scan(&spent, &earned)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	dmSpent, dmEarned, _, err := s.dmPointTotals(r.Context(), userID, nil)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	spent += dmSpent
	earned += dmEarned
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"user_id":                   userID,
		"viewer_points":             spent,
		"viewer_level":              service.LevelForPoints(spent),
		"viewer_next_level_points":  service.NextLevelPoints(spent),
		"creator_points":            earned,
		"creator_level":             service.LevelForPoints(earned),
		"creator_next_level_points": service.NextLevelPoints(earned),
	})
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
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) == 4 && parts[0] == "v1" && parts[1] == "matches" && parts[3] == "close" {
		s.handleMatchClose(w, r, parts[2])
		return
	}
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only GET is allowed")
		return
	}
	if len(parts) == 4 && parts[0] == "v1" && parts[1] == "matches" && parts[3] == "score" {
		matchID, err := strconv.ParseUint(parts[2], 10, 64)
		if err != nil {
			httpx.WriteError(w, http.StatusBadRequest, "BAD_PATH", "invalid match id")
			return
		}
		var creatorID, opponentCreatorID sql.NullInt64
		if err := s.db.QueryRowContext(r.Context(), `SELECT creator_id,opponent_creator_id FROM live_matches WHERE match_id=?`, matchID).Scan(&creatorID, &opponentCreatorID); err != nil {
			if err == sql.ErrNoRows {
				httpx.WriteError(w, http.StatusNotFound, "MATCH_NOT_FOUND", "match not found")
				return
			}
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		rows, err := s.db.QueryContext(r.Context(), `SELECT creator_id,COALESCE(SUM(match_points_added),0) FROM gift_orders WHERE match_id=? AND status='ACCEPTED' GROUP BY creator_id ORDER BY creator_id`, matchID)
		if err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		defer rows.Close()
		type score struct {
			CreatorID uint64 `json:"creator_id"`
			Points    int64  `json:"points"`
		}
		result := []score{{CreatorID: uint64(creatorID.Int64)}}
		if opponentCreatorID.Valid && opponentCreatorID.Int64 != creatorID.Int64 {
			result = append(result, score{CreatorID: uint64(opponentCreatorID.Int64)})
		}
		for rows.Next() {
			var item score
			if err := rows.Scan(&item.CreatorID, &item.Points); err != nil {
				httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
				return
			}
			for i := range result {
				if result[i].CreatorID == item.CreatorID {
					result[i].Points = item.Points
				}
			}
		}
		if err := rows.Err(); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		httpx.WriteJSON(w, http.StatusOK, map[string]any{"match_id": matchID, "scores": result})
		return
	}
	if len(parts) == 3 && parts[0] == "v1" && parts[1] == "matches" {
		matchID, err := strconv.ParseUint(parts[2], 10, 64)
		if err != nil {
			httpx.WriteError(w, http.StatusBadRequest, "BAD_PATH", "invalid match id")
			return
		}
		var result struct {
			MatchID               uint64        `json:"match_id"`
			LiveSessionID         uint64        `json:"live_session_id"`
			CreatorID             uint64        `json:"creator_id"`
			OpponentLiveSessionID sql.NullInt64 `json:"-"`
			OpponentSessionID     uint64        `json:"opponent_live_session_id"`
			OpponentCreatorID     sql.NullInt64 `json:"-"`
			OpponentID            uint64        `json:"opponent_creator_id"`
			Status                string        `json:"status"`
		}
		if err := s.db.QueryRowContext(r.Context(), `SELECT match_id,live_session_id,creator_id,opponent_live_session_id,opponent_creator_id,status FROM live_matches WHERE match_id=?`, matchID).Scan(&result.MatchID, &result.LiveSessionID, &result.CreatorID, &result.OpponentLiveSessionID, &result.OpponentCreatorID, &result.Status); err != nil {
			if err == sql.ErrNoRows {
				httpx.WriteError(w, http.StatusNotFound, "MATCH_NOT_FOUND", "match not found")
				return
			}
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		if result.OpponentLiveSessionID.Valid {
			result.OpponentSessionID = uint64(result.OpponentLiveSessionID.Int64)
		}
		if result.OpponentCreatorID.Valid {
			result.OpponentID = uint64(result.OpponentCreatorID.Int64)
		}
		httpx.WriteJSON(w, http.StatusOK, result)
		return
	}
	httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
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
		CanGoLive        bool   `json:"can_go_live"`
		LiveGiftsEnabled bool   `json:"live_gifts_enabled"`
		AccountType      string `json:"account_type"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.UserID == 0 {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid user payload")
		return
	}
	if req.AccountType == "" {
		req.AccountType = "NORMAL"
	}
	_, err := s.db.ExecContext(r.Context(), `
		INSERT INTO users (user_id, age_years, region_code, account_standing, can_go_live, live_gifts_enabled, account_type)
		VALUES (?, NULL, NULL, 'GOOD', ?, ?, ?)
		ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)
	`, req.UserID, req.CanGoLive, req.LiveGiftsEnabled, req.AccountType)
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
	eligible, err := s.svc.CanStartLive(r.Context(), req.CreatorID)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if !eligible {
		httpx.WriteError(w, http.StatusForbidden, "LIVE_PROFILE_REQUIRED", "complete your real profile and check live permissions before starting")
		return
	}
	_, err = s.db.ExecContext(r.Context(), `
		INSERT INTO live_sessions (live_session_id, creator_id, campaign_id, status, started_at, closed_at)
		VALUES (?, ?, ?, 'OPEN', UTC_TIMESTAMP(6), NULL)
		ON DUPLICATE KEY UPDATE creator_id=VALUES(creator_id), campaign_id=VALUES(campaign_id), status='OPEN', closed_at=NULL
	`, req.LiveSessionID, req.CreatorID, req.CampaignID)
	if err == nil && req.MatchID != nil {
		_, err = s.db.ExecContext(r.Context(), `
		INSERT INTO live_matches (match_id, live_session_id, creator_id, mode, specific_gift_id, status)
		SELECT ?, ?, creator_id, ?, ?, 'OPEN' FROM live_sessions WHERE live_session_id=?
		ON DUPLICATE KEY UPDATE live_session_id=VALUES(live_session_id), creator_id=VALUES(creator_id), mode=VALUES(mode), specific_gift_id=VALUES(specific_gift_id), status='OPEN'
		`, *req.MatchID, req.LiveSessionID, req.Mode, req.SpecificGiftID, req.LiveSessionID)
	}
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusCreated, map[string]any{"live_session_id": req.LiveSessionID, "match_id": req.MatchID})
}

func (s *Server) handleInternalMatchSync(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	var req struct {
		MatchID        uint64  `json:"match_id"`
		LiveSessionID  uint64  `json:"live_session_id"`
		Mode           string  `json:"mode"`
		SpecificGiftID *string `json:"specific_gift_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.MatchID == 0 || req.LiveSessionID == 0 {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "match_id and live_session_id are required")
		return
	}
	if req.Mode == "" {
		req.Mode = "ALL_GIFTS"
	}
	if req.Mode != "ALL_GIFTS" && req.Mode != "SPECIFIC_GIFT" {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_MODE", "mode must be ALL_GIFTS or SPECIFIC_GIFT")
		return
	}
	var status string
	if err := s.db.QueryRowContext(r.Context(), `SELECT status FROM live_sessions WHERE live_session_id=?`, req.LiveSessionID).Scan(&status); err != nil {
		if err == sql.ErrNoRows {
			httpx.WriteError(w, http.StatusNotFound, "LIVE_SESSION_NOT_FOUND", "live session not found")
			return
		}
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if status != "OPEN" {
		httpx.WriteError(w, http.StatusConflict, "LIVE_SESSION_CLOSED", "live session is closed")
		return
	}
	_, err := s.db.ExecContext(r.Context(), `
		INSERT INTO live_matches (match_id, live_session_id, creator_id, mode, specific_gift_id, status)
		SELECT ?, ?, creator_id, ?, ?, 'OPEN' FROM live_sessions WHERE live_session_id=?
		ON DUPLICATE KEY UPDATE live_session_id=VALUES(live_session_id), creator_id=VALUES(creator_id), mode=VALUES(mode), specific_gift_id=VALUES(specific_gift_id), status='OPEN'
	`, req.MatchID, req.LiveSessionID, req.Mode, req.SpecificGiftID, req.LiveSessionID)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusCreated, map[string]any{"match_id": req.MatchID, "live_session_id": req.LiveSessionID, "status": "OPEN"})
}

func (s *Server) handleMatchInvitations(w http.ResponseWriter, r *http.Request) {
	userID := authenticatedUserID(r)
	if r.Method == http.MethodGet {
		rows, err := s.db.QueryContext(r.Context(), `
			SELECT invitation_id, match_id, live_session_id, creator_id, opponent_live_session_id, opponent_creator_id, mode, status, created_at
			FROM live_match_invitations
			WHERE opponent_creator_id=? AND status='PENDING'
			AND EXISTS (SELECT 1 FROM live_sessions s WHERE s.live_session_id=live_match_invitations.live_session_id AND s.status='OPEN')
			AND EXISTS (SELECT 1 FROM live_sessions s WHERE s.live_session_id=live_match_invitations.opponent_live_session_id AND s.status='OPEN')
			ORDER BY created_at DESC LIMIT 50
		`, userID)
		if err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		defer rows.Close()
		type invitation struct {
			InvitationID          uint64    `json:"invitation_id"`
			MatchID               uint64    `json:"match_id"`
			LiveSessionID         uint64    `json:"live_session_id"`
			CreatorID             uint64    `json:"creator_id"`
			OpponentLiveSessionID uint64    `json:"opponent_live_session_id"`
			OpponentCreatorID     uint64    `json:"opponent_creator_id"`
			Mode                  string    `json:"mode"`
			Status                string    `json:"status"`
			CreatedAt             time.Time `json:"created_at"`
		}
		items := []invitation{}
		for rows.Next() {
			var item invitation
			if err := rows.Scan(&item.InvitationID, &item.MatchID, &item.LiveSessionID, &item.CreatorID, &item.OpponentLiveSessionID, &item.OpponentCreatorID, &item.Mode, &item.Status, &item.CreatedAt); err != nil {
				httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
				return
			}
			items = append(items, item)
		}
		if err := rows.Err(); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		httpx.WriteJSON(w, http.StatusOK, map[string]any{"invitations": items})
		return
	}
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET or POST required")
		return
	}
	var req struct {
		LiveSessionID         uint64  `json:"live_session_id"`
		OpponentLiveSessionID uint64  `json:"opponent_live_session_id"`
		Mode                  string  `json:"mode"`
		SpecificGiftID        *string `json:"specific_gift_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.LiveSessionID == 0 || req.OpponentLiveSessionID == 0 || req.LiveSessionID == req.OpponentLiveSessionID {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "two distinct live sessions are required")
		return
	}
	if req.Mode == "" {
		req.Mode = "ALL_GIFTS"
	}
	if req.Mode != "ALL_GIFTS" && req.Mode != "SPECIFIC_GIFT" {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_MODE", "mode must be ALL_GIFTS or SPECIFIC_GIFT")
		return
	}
	if req.Mode == "SPECIFIC_GIFT" {
		var enabled bool
		if req.SpecificGiftID == nil || s.db.QueryRowContext(r.Context(), `SELECT enabled FROM gift_catalog WHERE gift_id=?`, req.SpecificGiftID).Scan(&enabled) != nil || !enabled {
			httpx.WriteError(w, http.StatusBadRequest, "INVALID_GIFT", "an enabled gift is required")
			return
		}
	}
	var creatorID, opponentCreatorID uint64
	var ownStatus, opponentStatus string
	if err := s.db.QueryRowContext(r.Context(), `SELECT creator_id,status FROM live_sessions WHERE live_session_id=?`, req.LiveSessionID).Scan(&creatorID, &ownStatus); err != nil || creatorID != userID || ownStatus != "OPEN" {
		httpx.WriteError(w, http.StatusForbidden, "FORBIDDEN", "the caller must own an open live session")
		return
	}
	if err := s.db.QueryRowContext(r.Context(), `SELECT creator_id,status FROM live_sessions WHERE live_session_id=?`, req.OpponentLiveSessionID).Scan(&opponentCreatorID, &opponentStatus); err != nil || opponentStatus != "OPEN" || opponentCreatorID == userID {
		httpx.WriteError(w, http.StatusBadRequest, "OPPONENT_UNAVAILABLE", "opponent live session is unavailable")
		return
	}
	baseID := uint64(time.Now().UnixMilli())*1000 + uint64(time.Now().Nanosecond()%1000)
	_, err := s.db.ExecContext(r.Context(), `
		INSERT INTO live_match_invitations (invitation_id,match_id,live_session_id,creator_id,opponent_live_session_id,opponent_creator_id,mode,specific_gift_id,status,created_at)
		VALUES (?,?,?,?,?,?,?,?, 'PENDING', UTC_TIMESTAMP(6))
	`, baseID, baseID, req.LiveSessionID, creatorID, req.OpponentLiveSessionID, opponentCreatorID, req.Mode, req.SpecificGiftID)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusCreated, map[string]any{"invitation_id": baseID, "match_id": baseID, "status": "PENDING"})
}

func (s *Server) handleMatchInvitationResponse(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed")
		return
	}
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) != 4 || parts[0] != "v1" || parts[1] != "match-invitations" || parts[3] != "respond" {
		httpx.WriteError(w, http.StatusNotFound, "NOT_FOUND", "route not found")
		return
	}
	id, err := strconv.ParseUint(parts[2], 10, 64)
	if err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_PATH", "invalid invitation id")
		return
	}
	var req struct {
		Accept *bool `json:"accept"`
	}
	if json.NewDecoder(r.Body).Decode(&req) != nil || req.Accept == nil {
		httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "accept is required")
		return
	}
	tx, err := s.db.BeginTx(r.Context(), &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer tx.Rollback()
	var matchID, liveID, creatorID, opponentLiveID, opponentID uint64
	var mode, status string
	var specificGift sql.NullString
	err = tx.QueryRowContext(r.Context(), `SELECT match_id,live_session_id,creator_id,opponent_live_session_id,opponent_creator_id,mode,specific_gift_id,status FROM live_match_invitations WHERE invitation_id=? FOR UPDATE`, id).Scan(&matchID, &liveID, &creatorID, &opponentLiveID, &opponentID, &mode, &specificGift, &status)
	if err == sql.ErrNoRows {
		httpx.WriteError(w, http.StatusNotFound, "INVITATION_NOT_FOUND", "invitation not found")
		return
	}
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if opponentID != authenticatedUserID(r) {
		httpx.WriteError(w, http.StatusForbidden, "FORBIDDEN", "only the invited creator may respond")
		return
	}
	if status != "PENDING" {
		httpx.WriteError(w, http.StatusConflict, "INVITATION_RESOLVED", "invitation is no longer pending")
		return
	}
	newStatus := "REJECTED"
	if *req.Accept {
		// Serialize accepts per creator, even when a creator has multiple sessions.
		creators := []uint64{creatorID, opponentID}
		if creators[0] > creators[1] {
			creators[0], creators[1] = creators[1], creators[0]
		}
		for _, creator := range creators {
			if _, err := tx.ExecContext(r.Context(), `INSERT INTO live_match_creator_locks(creator_id) VALUES (?) ON DUPLICATE KEY UPDATE creator_id=VALUES(creator_id)`, creator); err != nil {
				httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
				return
			}
		}
		lives := []uint64{liveID, opponentLiveID}
		if lives[0] > lives[1] {
			lives[0], lives[1] = lives[1], lives[0]
		}
		for _, live := range lives {
			var liveStatus string
			if err := tx.QueryRowContext(r.Context(), `SELECT status FROM live_sessions WHERE live_session_id=? FOR UPDATE`, live).Scan(&liveStatus); err != nil || liveStatus != "OPEN" {
				httpx.WriteError(w, http.StatusConflict, "LIVE_SESSION_CLOSED", "a live session is unavailable")
				return
			}
		}
		var active int
		if err := tx.QueryRowContext(r.Context(), `SELECT COUNT(*) FROM live_matches WHERE status='OPEN' AND (creator_id IN (?,?) OR opponent_creator_id IN (?,?))`, creatorID, opponentID, creatorID, opponentID).Scan(&active); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		if active != 0 {
			httpx.WriteError(w, http.StatusConflict, "CREATOR_IN_MATCH", "a creator is already in a match")
			return
		}
		if _, err := tx.ExecContext(r.Context(), `INSERT INTO live_matches (match_id,live_session_id,creator_id,opponent_live_session_id,opponent_creator_id,mode,specific_gift_id,status) VALUES (?,?,?,?,?,?,?, 'OPEN')`, matchID, liveID, creatorID, opponentLiveID, opponentID, mode, specificGift); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		newStatus = "ACCEPTED"
	}
	if _, err := tx.ExecContext(r.Context(), `UPDATE live_match_invitations SET status=?,responded_at=UTC_TIMESTAMP(6) WHERE invitation_id=?`, newStatus, id); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if err := tx.Commit(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"invitation_id": id, "match_id": matchID, "status": newStatus, "live_session_id": liveID, "opponent_live_session_id": opponentLiveID, "creator_id": creatorID, "opponent_creator_id": opponentID})
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
		VALUES (?, NULL, NULL, 'GOOD', TRUE, TRUE, 'NORMAL')
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
	case r.Method == http.MethodGet && parts[3] == "watch-access":
		s.handleWatchAccess(w, r, liveSessionID)
		return
	case r.Method == http.MethodPost && parts[3] == "watch-heartbeat":
		s.handleWatchHeartbeat(w, r, liveSessionID)
		return
	case r.Method == http.MethodPost && parts[3] == "watch-unlock":
		s.handleWatchUnlock(w, r, liveSessionID)
		return
	case r.Method == http.MethodPut && parts[3] == "watch-price":
		s.handleWatchPrice(w, r, liveSessionID)
		return
	case r.Method == http.MethodGet && parts[3] == "match":
		var matchID uint64
		err := s.db.QueryRowContext(r.Context(), `SELECT match_id FROM live_matches WHERE status='OPEN' AND (live_session_id=? OR opponent_live_session_id=?) ORDER BY match_id DESC LIMIT 1`, liveSessionID, liveSessionID).Scan(&matchID)
		if err != nil && err != sql.ErrNoRows {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		httpx.WriteJSON(w, http.StatusOK, map[string]any{"match_id": matchID})
		return
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
	v := s.reqCounter.Add(1)
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
