package apiserver

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestReferralRewardsHTTP(t *testing.T) {
	db := newOperationsTestDB(t)
	const secret = "referral-test-secret"
	s := New("", db, nil, secret, "", "internal-test-secret", testAccountAuthority(t, secret))
	policy := service.ReferralPolicy{InviterCoins: 100, InviteeCoins: 40}
	s.referrals = policy
	ts := httptest.NewServer(s.httpServer.Handler)
	t.Cleanup(ts.Close)

	call := func(user uint64, method, path, body string) (int, map[string]any, []byte) {
		t.Helper()
		header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"HS256"}`))
		payload := base64.RawURLEncoding.EncodeToString([]byte(fmt.Sprintf(`{"userInfo":{"id":%d},"exp":%d,"iat":%d}`, user, time.Now().Unix()+60, time.Now().Unix())))
		mac := hmac.New(sha256.New, []byte(secret))
		_, _ = mac.Write([]byte(header + "." + payload))
		req, err := http.NewRequest(method, ts.URL+path, bytes.NewBufferString(body))
		if err != nil {
			t.Fatal(err)
		}
		req.Header.Set("Authorization", "Bearer "+header+"."+payload+"."+base64.RawURLEncoding.EncodeToString(mac.Sum(nil)))
		req.Header.Set("Content-Type", "application/json")
		res, err := ts.Client().Do(req)
		if err != nil {
			t.Fatal(err)
		}
		defer res.Body.Close()
		raw, err := io.ReadAll(res.Body)
		if err != nil {
			t.Fatal(err)
		}
		decoded := map[string]any{}
		if len(bytes.TrimSpace(raw)) > 0 {
			if err := json.Unmarshal(raw, &decoded); err != nil {
				t.Fatalf("status %d body %s: %v", res.StatusCode, raw, err)
			}
		}
		return res.StatusCode, decoded, raw
	}
	requireStatus := func(user uint64, method, path, body string, want int) map[string]any {
		t.Helper()
		status, decoded, raw := call(user, method, path, body)
		if status != want {
			t.Fatalf("%s %s user %d: want %d got %d %s", method, path, user, want, status, raw)
		}
		return decoded
	}
	number := func(body map[string]any, key string) int64 {
		t.Helper()
		value, ok := body[key].(float64)
		if !ok {
			t.Fatalf("%s = %#v", key, body[key])
		}
		return int64(value)
	}
	ledger := func() (txs int, sum int64, redemptions int, gifts int) {
		t.Helper()
		if err := db.QueryRow(`SELECT COUNT(DISTINCT t.tx_id), COALESCE(SUM(e.amount),0) FROM wallet_transactions t LEFT JOIN wallet_entries e ON e.tx_id=t.tx_id WHERE t.tx_type='REFERRAL_REWARD'`).Scan(&txs, &sum); err != nil {
			t.Fatal(err)
		}
		if err := db.QueryRow(`SELECT COUNT(*) FROM referral_redemptions`).Scan(&redemptions); err != nil {
			t.Fatal(err)
		}
		if err := db.QueryRow(`SELECT COUNT(*) FROM gift_orders`).Scan(&gifts); err != nil {
			t.Fatal(err)
		}
		return
	}
	balance := func(user uint64) int64 {
		t.Helper()
		var value int64
		err := db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=? AND currency='COIN'`, user).Scan(&value)
		if err != nil {
			t.Fatalf("balance %d: %v", user, err)
		}
		return value
	}

	disabled := requireStatus(1, http.MethodGet, "/v1/referrals/me", "", 200)
	if disabled["enabled"] != false || len(disabled) != 1 {
		t.Fatalf("disabled profile invented fields: %v", disabled)
	}
	refused := requireStatus(2, http.MethodPost, "/v1/referrals/redeem", `{"request_id":"10000000-0000-4000-8000-000000000001","code":"ABCDEFGH"}`, 403)
	if refused["code"] != "REFERRALS_DISABLED" || refused["message"] == "" {
		t.Fatalf("disabled redeem: %v", refused)
	}
	if txs, sum, redemptions, gifts := ledger(); txs != 0 || sum != 0 || redemptions != 0 || gifts != 0 {
		t.Fatalf("disabled path wrote ledger txs=%d sum=%d redemptions=%d gifts=%d", txs, sum, redemptions, gifts)
	}
	var codes int
	if err := db.QueryRow(`SELECT COUNT(*) FROM referral_codes`).Scan(&codes); err != nil || codes != 0 {
		t.Fatalf("disabled profile allocated a code: %d %v", codes, err)
	}

	s.referrals.Enabled = true
	first := requireStatus(1, http.MethodGet, "/v1/referrals/me", "", 200)
	code, _ := first["code"].(string)
	if first["enabled"] != true || len(code) != 8 || number(first, "invited_count") != 0 || number(first, "rewarded_coins") != 0 {
		t.Fatalf("enabled profile: %v", first)
	}
	if again := requireStatus(1, http.MethodGet, "/v1/referrals/me", "", 200); again["code"] != code {
		t.Fatalf("code changed: %v then %v", code, again["code"])
	}

	request := `{"request_id":"10000000-0000-4000-8000-000000000010","code":"` + code + `"}`
	created := requireStatus(2, http.MethodPost, "/v1/referrals/redeem", request, 200)
	if created["accepted"] != true || created["idempotency"] != "created" || created["code"] != code || number(created, "inviter_coins") != 100 || number(created, "invitee_coins") != 40 || number(created, "invitee_balance") != 40 || number(created, "inviter_id") != 1 {
		t.Fatalf("redeem receipt: %v", created)
	}
	if balance(1) != 100 || balance(2) != 40 {
		t.Fatalf("balances after redeem inviter=%d invitee=%d", balance(1), balance(2))
	}
	replayed := requireStatus(2, http.MethodPost, "/v1/referrals/redeem", strings.Replace(request, code, strings.ToLower(code), 1), 200)
	if replayed["idempotency"] != "replayed" || replayed["invitee_balance"] != created["invitee_balance"] {
		t.Fatalf("replay: %v", replayed)
	}
	if balance(1) != 100 || balance(2) != 40 {
		t.Fatal("replay changed balances")
	}
	mismatch := requireStatus(2, http.MethodPost, "/v1/referrals/redeem", `{"request_id":"10000000-0000-4000-8000-000000000010","code":"ZZZZZZZZ"}`, 409)
	if mismatch["code"] != "IDEMPOTENCY_PAYLOAD_MISMATCH" {
		t.Fatalf("mismatch: %v", mismatch)
	}

	own := requireStatus(3, http.MethodGet, "/v1/referrals/me", "", 200)
	selfCode, _ := own["code"].(string)
	self := requireStatus(3, http.MethodPost, "/v1/referrals/redeem", `{"request_id":"10000000-0000-4000-8000-000000000011","code":"`+selfCode+`"}`, 409)
	if self["code"] != "SELF_REFERRAL" {
		t.Fatalf("self: %v", self)
	}
	unknown := requireStatus(6, http.MethodPost, "/v1/referrals/redeem", `{"request_id":"10000000-0000-4000-8000-000000000012","code":"ZZZZZZZZ"}`, 404)
	if unknown["code"] != "REFERRAL_CODE_NOT_FOUND" {
		t.Fatalf("unknown: %v", unknown)
	}

	second := requireStatus(4, http.MethodPost, "/v1/referrals/redeem", `{"request_id":"10000000-0000-4000-8000-000000000013","code":"`+code+`"}`, 200)
	if second["idempotency"] != "created" || number(second, "invitee_balance") != 40 {
		t.Fatalf("second invitee: %v", second)
	}
	inviteeCode := requireStatus(2, http.MethodGet, "/v1/referrals/me", "", 200)["code"].(string)
	double := requireStatus(4, http.MethodPost, "/v1/referrals/redeem", `{"request_id":"10000000-0000-4000-8000-000000000014","code":"`+inviteeCode+`"}`, 409)
	if double["code"] != "REFERRAL_ALREADY_REDEEMED" {
		t.Fatalf("double redeem: %v", double)
	}
	if balance(1) != 200 || balance(2) != 40 || balance(4) != 40 {
		t.Fatalf("balances inviter=%d invitee=%d second=%d", balance(1), balance(2), balance(4))
	}
	inviter := requireStatus(1, http.MethodGet, "/v1/referrals/me", "", 200)
	if number(inviter, "invited_count") != 2 || number(inviter, "rewarded_coins") != 200 {
		t.Fatalf("inviter counters: %v", inviter)
	}
	if number(requireStatus(2, http.MethodGet, "/v1/referrals/me", "", 200), "rewarded_coins") != 0 {
		t.Fatal("invitee reward was counted as inviter reward")
	}

	s.referrals.Enabled = false
	if again := requireStatus(1, http.MethodGet, "/v1/referrals/me", "", 200); again["enabled"] != false || len(again) != 1 {
		t.Fatalf("disabled again: %v", again)
	}
	if status, decoded, raw := call(5, http.MethodPost, "/v1/referrals/redeem", `{"request_id":"10000000-0000-4000-8000-000000000015","code":"`+code+`"}`); status != 403 || decoded["code"] != "REFERRALS_DISABLED" {
		t.Fatalf("redeem while disabled: %d %s", status, raw)
	}
	kept := requireStatus(2, http.MethodPost, "/v1/referrals/redeem", request, 200)
	if kept["idempotency"] != "replayed" || number(kept, "invitee_balance") != 40 {
		t.Fatalf("replay after disable: %v", kept)
	}
	if txs, sum, redemptions, gifts := ledger(); txs != 2 || sum != 0 || redemptions != 2 || gifts != 0 {
		t.Fatalf("ledger txs=%d sum=%d redemptions=%d gifts=%d", txs, sum, redemptions, gifts)
	}
	var user3Wallet int
	if err := db.QueryRow(`SELECT COUNT(*) FROM wallet_accounts WHERE user_id=3`).Scan(&user3Wallet); err != nil || user3Wallet != 0 {
		t.Fatalf("self-redeem created a wallet: %d %v", user3Wallet, err)
	}
}
