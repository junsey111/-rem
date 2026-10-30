package apiserver

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type authContextKey string

const authUserIDKey authContextKey = "live-revenue.user-id"

func (s *Server) requireAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, err := verifyBilldJWT(r, s.jwtSecret)
		if err != nil {
			httpx.WriteError(w, http.StatusUnauthorized, "UNAUTHORIZED", "valid Billd bearer token required")
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
		UserInfo struct {
			ID json.Number `json:"id"`
		} `json:"userInfo"`
	}
	if err := decode(segments[1], &claims); err != nil || claims.Exp <= float64(time.Now().Unix()) {
		return 0, errors.New("expired JWT")
	}
	userID, err := strconv.ParseUint(claims.UserInfo.ID.String(), 10, 64)
	if err != nil || userID == 0 {
		return 0, errors.New("JWT has no user id")
	}
	return userID, nil
}
