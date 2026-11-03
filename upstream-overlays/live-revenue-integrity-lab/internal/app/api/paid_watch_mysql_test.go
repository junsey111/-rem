package apiserver

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestGiftCatalogEffectsAndPaidWatch(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	if _, err := db.Exec(`
		INSERT INTO users (user_id, age_years, region_code, account_standing, can_go_live, live_gifts_enabled, account_type, profile_source)
		VALUES (11, 25, 'US', 'GOOD', 1, 1, 'NORMAL', 'SELF_REPORTED'),
		       (22, 25, 'US', 'GOOD', 1, 1, 'NORMAL', 'SELF_REPORTED');
		INSERT INTO live_sessions (live_session_id, creator_id, status, started_at)
		VALUES (501, 22, 'OPEN', UTC_TIMESTAMP(6)), (502, 22, 'OPEN', UTC_TIMESTAMP(6));
		INSERT INTO wallet_accounts (user_id, currency, available_balance) VALUES (11, 'COIN', 9);
	`); err != nil {
		t.Fatal(err)
	}

	catalogReq := httptest.NewRequest(http.MethodGet, "/v1/gifts/catalog", nil)
	catalogRes := httptest.NewRecorder()
	s.handleGiftCatalog(catalogRes, catalogReq)
	if catalogRes.Code != 200 {
		t.Fatalf("catalog: %d %s", catalogRes.Code, catalogRes.Body.String())
	}
	var catalog struct {
		Gifts []map[string]any `json:"gifts"`
	}
	if err := json.Unmarshal(catalogRes.Body.Bytes(), &catalog); err != nil {
		t.Fatal(err)
	}
	var rose map[string]any
	for _, gift := range catalog.Gifts {
		if gift["gift_id"] == "ROSE" {
			rose = gift
		}
		for _, key := range []string{"effect_type", "effect_url", "effect_video_mode", "effect_md5", "icon_url", "coin_price"} {
			if _, ok := gift[key]; !ok {
				t.Fatalf("gift %v missing %s", gift["gift_id"], key)
			}
		}
	}
	if rose == nil || rose["effect_type"] != "none" || rose["effect_url"] != "" || rose["coin_price"] != float64(1) {
		t.Fatalf("rose defaults: %#v", rose)
	}
	var price int
	if err := db.QueryRow(`SELECT coin_price FROM gift_catalog WHERE gift_id='ROSE'`).Scan(&price); err != nil || price != 1 {
		t.Fatalf("rose price changed: %d %v", price, err)
	}

	call := func(user uint64, method, path, body string, want int) map[string]any {
		t.Helper()
		req := httptest.NewRequest(method, path, strings.NewReader(body))
		req = req.WithContext(context.WithValue(req.Context(), authUserIDKey, user))
		res := httptest.NewRecorder()
		s.handleLivesPrefix(res, req)
		if res.Code != want {
			t.Fatalf("%s %s: %d %s", method, path, res.Code, res.Body.String())
		}
		var payload map[string]any
		if res.Body.Len() > 0 && json.Unmarshal(res.Body.Bytes(), &payload) != nil {
			t.Fatalf("json: %s", res.Body.String())
		}
		return payload
	}
	host := call(22, http.MethodPut, "/v1/lives/501/watch-price", `{"unlock_price_coins":10}`, 200)
	if host["unlock_price_coins"] != float64(10) || host["status"] != "FREE" && host["live_id"] != "501" {
		// Host viewing their own paid room is still PREVIEW until they would need to unlock; price is set.
	}
	if host["live_id"] != "501" || host["unlock_price_coins"] != float64(10) || host["free_preview_seconds"] != float64(60) {
		t.Fatalf("price set: %#v", host)
	}

	first := call(11, http.MethodPost, "/v1/lives/501/watch-heartbeat", `{"request_id":"11111111-1111-4111-8111-111111111111","delta_seconds":30}`, 200)
	if first["status"] != "PREVIEW" || first["watched_unpaid_seconds"] != float64(30) || first["seconds_remaining"] != float64(30) || first["unlocked"] != false {
		t.Fatalf("preview: %#v", first)
	}
	replay := call(11, http.MethodPost, "/v1/lives/501/watch-heartbeat", `{"request_id":"11111111-1111-4111-8111-111111111111","delta_seconds":30}`, 200)
	if replay["watched_unpaid_seconds"] != float64(30) {
		t.Fatalf("heartbeat replay incremented: %#v", replay)
	}
	call(11, http.MethodPost, "/v1/lives/501/watch-heartbeat", `{"request_id":"11111111-1111-4111-8111-111111111111","delta_seconds":5}`, 409)
	second := call(11, http.MethodPost, "/v1/lives/501/watch-heartbeat", `{"request_id":"22222222-2222-4222-8222-222222222222","delta_seconds":100}`, 200)
	if second["status"] != "LOCKED" || second["watched_unpaid_seconds"] != float64(60) || second["seconds_remaining"] != float64(0) {
		t.Fatalf("locked: %#v", second)
	}
	third := call(11, http.MethodPost, "/v1/lives/501/watch-heartbeat", `{"request_id":"33333333-3333-4333-8333-333333333333","delta_seconds":10}`, 200)
	if third["watched_unpaid_seconds"] != float64(60) || third["status"] != "LOCKED" {
		t.Fatalf("locked heartbeat changed progress: %#v", third)
	}

	refused := call(11, http.MethodPost, "/v1/lives/501/watch-unlock", `{"request_id":"44444444-4444-4444-8444-444444444444"}`, 402)
	if refused["code"] != "INSUFFICIENT_BALANCE" || !strings.Contains(refused["message"].(string), "未扣币") {
		t.Fatalf("refusal: %#v", refused)
	}
	var balance int64
	var txs int
	if err := db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=11 AND currency='COIN'`).Scan(&balance); err != nil || balance != 9 {
		t.Fatalf("balance changed on refusal: %d %v", balance, err)
	}
	if err := db.QueryRow(`SELECT COUNT(*) FROM wallet_transactions WHERE actor_user_id=11 AND tx_type='WATCH_UNLOCK'`).Scan(&txs); err != nil || txs != 0 {
		t.Fatalf("partial debit: %d %v", txs, err)
	}
	if _, err := db.Exec(`UPDATE wallet_accounts SET available_balance=100 WHERE user_id=11`); err != nil {
		t.Fatal(err)
	}
	unlocked := call(11, http.MethodPost, "/v1/lives/501/watch-unlock", `{"request_id":"44444444-4444-4444-8444-444444444444"}`, 200)
	if unlocked["unlocked"] != true || unlocked["status"] != "UNLOCKED" || unlocked["live_id"] != "501" {
		t.Fatalf("unlock: %#v", unlocked)
	}
	again := call(11, http.MethodPost, "/v1/lives/501/watch-unlock", `{"request_id":"44444444-4444-4444-8444-444444444444"}`, 200)
	other := call(11, http.MethodPost, "/v1/lives/501/watch-unlock", `{"request_id":"55555555-5555-4555-8555-555555555555"}`, 200)
	if again["status"] != "UNLOCKED" || other["status"] != "UNLOCKED" {
		t.Fatalf("replay unlock: %#v %#v", again, other)
	}
	if err := db.QueryRow(`SELECT available_balance FROM wallet_accounts WHERE user_id=11`).Scan(&balance); err != nil || balance != 90 {
		t.Fatalf("expected one debit to 90, got %d %v", balance, err)
	}
	var ledger int64
	if err := db.QueryRow(`SELECT COALESCE(SUM(amount),0) FROM wallet_entries WHERE tx_id IN (SELECT tx_id FROM wallet_transactions WHERE actor_user_id=11 AND tx_type='WATCH_UNLOCK')`).Scan(&ledger); err != nil || ledger != 0 {
		t.Fatalf("ledger not balanced: %d %v", ledger, err)
	}

	free := call(11, http.MethodGet, "/v1/lives/502/watch-access", "", 200)
	if free["status"] != "FREE" || free["unlock_price_coins"] != float64(0) || free["free_preview_seconds"] != float64(60) {
		t.Fatalf("free room: %#v", free)
	}
	freeBeat := call(11, http.MethodPost, "/v1/lives/502/watch-heartbeat", `{"request_id":"66666666-6666-4666-8666-666666666666","delta_seconds":30}`, 200)
	if freeBeat["status"] != "FREE" || freeBeat["watched_unpaid_seconds"] != float64(0) {
		t.Fatalf("free heartbeat counted: %#v", freeBeat)
	}

	effect := map[string]any{"request_id": "30000000-0000-4000-8000-0000000000aa", "operator_id": 9, "gift_id": "ROSE", "display_name": "Rose", "coin_price": 1, "diamond_reward": 1, "match_points": 1, "enabled": true, "effect_type": "YYEVA", "effect_url": "https://cdn.example.com/rose.mp4", "reason": "bad case"}
	raw, _ := json.Marshal(effect)
	admin := httptest.NewRequest(http.MethodPut, "/v1/internal/catalog", strings.NewReader(string(raw)))
	adminRes := httptest.NewRecorder()
	s.handleAdminCatalog(adminRes, admin)
	if adminRes.Code != 400 {
		t.Fatalf("uppercase effect accepted: %d %s", adminRes.Code, adminRes.Body.String())
	}
	effect["effect_type"] = "yyeva"
	effect["effect_url"] = "https://cdn.example.com/rose.svga"
	raw, _ = json.Marshal(effect)
	admin = httptest.NewRequest(http.MethodPut, "/v1/internal/catalog", strings.NewReader(string(raw)))
	adminRes = httptest.NewRecorder()
	s.handleAdminCatalog(adminRes, admin)
	if adminRes.Code != 400 {
		t.Fatalf("non-mp4 yyeva accepted: %d %s", adminRes.Code, adminRes.Body.String())
	}
	effect["request_id"] = "30000000-0000-4000-8000-0000000000ab"
	effect["effect_url"] = "https://cdn.example.com/effects/rose.mp4?x=1"
	effect["effect_video_mode"] = "alpha"
	effect["effect_md5"] = "0123456789abcdef0123456789abcdef"
	effect["icon_url"] = "https://cdn.example.com/rose.png"
	raw, _ = json.Marshal(effect)
	admin = httptest.NewRequest(http.MethodPut, "/v1/internal/catalog", strings.NewReader(string(raw)))
	adminRes = httptest.NewRecorder()
	s.handleAdminCatalog(adminRes, admin)
	if adminRes.Code != 200 {
		t.Fatalf("yyeva update: %d %s", adminRes.Code, adminRes.Body.String())
	}
	catalogRes = httptest.NewRecorder()
	s.handleGiftCatalog(catalogRes, httptest.NewRequest(http.MethodGet, "/v1/gifts/catalog", nil))
	if err := json.Unmarshal(catalogRes.Body.Bytes(), &catalog); err != nil {
		t.Fatal(err)
	}
	for _, gift := range catalog.Gifts {
		if gift["gift_id"] == "ROSE" {
			rose = gift
		}
	}
	if rose["effect_type"] != "yyeva" || rose["effect_url"] != "https://cdn.example.com/effects/rose.mp4?x=1" || rose["effect_video_mode"] != "alpha" || rose["effect_md5"] != "0123456789abcdef0123456789abcdef" || rose["icon_url"] != "https://cdn.example.com/rose.png" || rose["coin_price"] != float64(1) {
		t.Fatalf("yyeva catalog: %#v", rose)
	}
}
