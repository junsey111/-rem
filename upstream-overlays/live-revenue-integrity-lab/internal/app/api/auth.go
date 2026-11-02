package apiserver

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type authContextKey string

const authUserIDKey authContextKey = "live-revenue.user-id"

var accountClient = &http.Client{
	Timeout:       3 * time.Second,
	CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
}

// No positive-result cache: logout, disable and session replacement apply to the next request.
func (s *Server) accountStatus(r *http.Request, userID uint64) int {
	u, err := url.Parse(s.accountURL)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" || u.User != nil || u.Fragment != "" {
		return http.StatusServiceUnavailable
	}
	request, err := http.NewRequestWithContext(r.Context(), http.MethodGet, s.accountURL, nil)
	if err != nil {
		return http.StatusServiceUnavailable
	}
	request.Header.Set("Authorization", r.Header.Get("Authorization"))
	response, err := accountClient.Do(request)
	if err != nil {
		return http.StatusServiceUnavailable
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusUnauthorized || response.StatusCode == http.StatusForbidden {
		return http.StatusUnauthorized
	}
	if response.StatusCode != http.StatusOK {
		return http.StatusServiceUnavailable
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, 4097))
	if err != nil || len(data) > 4096 {
		return http.StatusServiceUnavailable
	}
	var identity struct {
		Code int `json:"code"`
		Data struct {
			ID     uint64 `json:"id"`
			Status *int   `json:"status"`
		} `json:"data"`
	}
	if json.Unmarshal(data, &identity) != nil || identity.Code != 200 || identity.Data.ID != userID || identity.Data.Status == nil {
		return http.StatusServiceUnavailable
	}
	if *identity.Data.Status != 1 {
		return http.StatusUnauthorized
	}
	return http.StatusOK
}

func (s *Server) requireAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, err := verifyBilldJWT(r, s.jwtSecret)
		if err != nil {
			httpx.WriteError(w, http.StatusUnauthorized, "UNAUTHORIZED", "valid Billd bearer token required")
			return
		}
		if status := s.accountStatus(r, userID); status != http.StatusOK {
			if status == http.StatusUnauthorized {
				httpx.WriteError(w, status, "UNAUTHORIZED", "current app session required")
			} else {
				httpx.WriteError(w, status, "ACCOUNT_AUTHORITY_UNAVAILABLE", "account verification temporarily unavailable")
			}
			return
		}
		ctx := context.WithValue(r.Context(), authUserIDKey, userID)
		next(w, r.WithContext(ctx))
	}
}

func authenticatedUserID(r *http.Request) uint64 {
	userID, _ := r.Context().Value(authUserIDKey).(uint64)
	return userID
}

func verifyPaymentSignature(r *http.Request, secret, payload string) bool {
	if strings.TrimSpace(secret) == "" {
		return false
	}
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(payload))
	provided, err := base64.RawURLEncoding.DecodeString(strings.TrimSpace(r.Header.Get("X-Payment-Signature")))
	return err == nil && hmac.Equal(provided, mac.Sum(nil))
}

func verifyBilldJWT(r *http.Request, secret string) (uint64, error) {
	if strings.TrimSpace(secret) == "" {
		return 0, errors.New("JWT_SECRET is not configured")
	}
	parts := strings.Split(strings.TrimSpace(r.Header.Get("Authorization")), " ")
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
		return 0, errors.New("missing bearer token")
	}
	segments := strings.Split(parts[1], ".")
	if len(segments) != 3 {
		return 0, errors.New("invalid JWT")
	}
	decode := func(value string, target any) error {
		data, err := base64.RawURLEncoding.DecodeString(value)
		if err != nil {
			return err
		}
		return json.Unmarshal(data, target)
	}
	var header struct {
		Alg string `json:"alg"`
	}
	if err := decode(segments[0], &header); err != nil || header.Alg != "HS256" {
		return 0, errors.New("unsupported JWT algorithm")
	}
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(segments[0] + "." + segments[1]))
	expected, err := base64.RawURLEncoding.DecodeString(segments[2])
	if err != nil || !hmac.Equal(expected, mac.Sum(nil)) {
		return 0, errors.New("invalid JWT signature")
	}
	var claims struct {
		Exp      float64 `json:"exp"`
		Iat      float64 `json:"iat"`
		UserInfo struct {
			ID json.Number `json:"id"`
		} `json:"userInfo"`
	}
	if err := decode(segments[1], &claims); err != nil || claims.Exp <= float64(time.Now().Unix()) || claims.Iat <= 0 || claims.Iat > float64(time.Now().Unix()+30) || claims.Exp <= claims.Iat || claims.Exp-claims.Iat > 86400 {
		return 0, errors.New("expired JWT")
	}
	userID, err := strconv.ParseUint(claims.UserInfo.ID.String(), 10, 64)
	if err != nil || userID == 0 {
		return 0, errors.New("JWT has no user id")
	}
	return userID, nil
}
