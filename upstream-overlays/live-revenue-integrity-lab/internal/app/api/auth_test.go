package apiserver

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"
)

func makeTestToken(secret string, exp int64) string {
	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"HS256","typ":"JWT"}`))
	payload := base64.RawURLEncoding.EncodeToString([]byte(fmt.Sprintf(`{"exp":%d,"userInfo":{"id":42}}`, exp)))
	message := header + "." + payload
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(message))
	return message + "." + base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func TestRequestedPeriod(t *testing.T) {
	for _, period := range []string{"daily", "monthly"} {
		req := httptest.NewRequest("GET", "/v1/rankings?period="+url.QueryEscape(period), nil)
		start, got, ok := requestedPeriod(req)
		if !ok || got != period || start.Location() != time.UTC {
			t.Fatalf("period %q not accepted: start=%v got=%q ok=%v", period, start, got, ok)
		}
		if period == "daily" && (start.Hour() != 0 || start.Day() != time.Now().UTC().Day()) {
			t.Fatalf("daily period not aligned to UTC midnight: %v", start)
		}
		if period == "monthly" && (start.Day() != 1 || start.Hour() != 0) {
			t.Fatalf("monthly period not aligned to first UTC day: %v", start)
		}
	}
	req := httptest.NewRequest("GET", "/v1/rankings?period=yearly", nil)
	if _, _, ok := requestedPeriod(req); ok {
		t.Fatal("unsupported period accepted")
	}
}

func TestVerifyBilldJWT(t *testing.T) {
	secret := "test-secret"
	req := httptest.NewRequest("POST", "/v1/gifts", nil)
	req.Header.Set("Authorization", "Bearer "+makeTestToken(secret, time.Now().Unix()+60))
	userID, err := verifyBilldJWT(req, secret)
	if err != nil || userID != 42 {
		t.Fatalf("expected user 42, got user=%d err=%v", userID, err)
	}
	if _, err := verifyBilldJWT(req, "wrong-secret"); err == nil {
		t.Fatal("expected signature validation failure")
	}
	req.Header.Set("Authorization", "Bearer "+makeTestToken(secret, time.Now().Unix()-1))
	if _, err := verifyBilldJWT(req, secret); err == nil {
		t.Fatal("expected expired token rejection")
	}
}

func TestVerifyPaymentSignature(t *testing.T) {
	const secret = "callback-secret"
	const payload = "req-1:42:100:provider-ref"
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(payload))
	req := httptest.NewRequest("POST", "/v1/wallets/recharges", nil)
	req.Header.Set("X-Payment-Signature", base64.RawURLEncoding.EncodeToString(mac.Sum(nil)))
	if !verifyPaymentSignature(req, secret, payload) {
		t.Fatal("expected valid payment signature")
	}
	if verifyPaymentSignature(req, secret, payload+":tampered") {
		t.Fatal("expected modified payment payload rejection")
	}
}
