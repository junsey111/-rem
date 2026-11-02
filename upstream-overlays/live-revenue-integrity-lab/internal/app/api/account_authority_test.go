package apiserver

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestAccountAuthorityFailuresNeverReachBusinessHandler(t *testing.T) {
	const secret = "account-authority-test"
	token := makeTestToken(secret, time.Now().Unix()+60)
	for _, scenario := range []struct {
		name   string
		status int
		body   string
		want   int
	}{
		{"current", 200, `{"code":200,"data":{"id":42,"status":1}}`, 204},
		{"revoked", 401, `{"private":"must not leak"}`, 401},
		{"disabled", 403, `{}`, 401},
		{"disabled-identity", 200, `{"code":200,"data":{"id":42,"status":2}}`, 401},
		{"unavailable", 503, `{"private":"SQL credentials"}`, 503},
		{"throttled", 429, `{}`, 503},
		{"redirect", 302, `{}`, 503},
		{"invalid-json", 200, `not json`, 503},
		{"wrong-identity", 200, `{"code":200,"data":{"id":99,"status":1}}`, 503},
		{"missing-status", 200, `{"code":200,"data":{"id":42}}`, 503},
		{"missing-code", 200, `{"data":{"id":42,"status":1}}`, 503},
		{"oversize", 200, strings.Repeat(" ", 4097), 503},
		{"trailing-data", 200, `{"code":200,"data":{"id":42,"status":1}}{}`, 503},
	} {
		t.Run(scenario.name, func(t *testing.T) {
			calls := 0
			authority := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				calls++
				if r.Method != "GET" || r.Header.Get("Authorization") != "Bearer "+token {
					t.Error("identity request did not preserve bearer")
				}
				w.Header().Set("Location", "http://127.0.0.1:1/do-not-follow")
				w.WriteHeader(scenario.status)
				_, _ = w.Write([]byte(scenario.body))
			}))
			defer authority.Close()
			server := &Server{jwtSecret: secret, accountURL: authority.URL + "/user/me"}
			reached := false
			handler := server.requireAuth(func(w http.ResponseWriter, r *http.Request) {
				reached = true
				if authenticatedUserID(r) != 42 {
					t.Error("wrong downstream identity")
				}
				w.WriteHeader(204)
			})
			req := httptest.NewRequest("POST", "/v1/gifts", strings.NewReader(`{}`))
			req.Header.Set("Authorization", "Bearer "+token)
			result := httptest.NewRecorder()
			handler(result, req)
			if result.Code != scenario.want || reached != (scenario.want == 204) || calls != 1 {
				t.Fatalf("status=%d reached=%v calls=%d", result.Code, reached, calls)
			}
			if strings.Contains(result.Body.String(), "private") || strings.Contains(result.Body.String(), "SQL") {
				t.Fatal("authority details leaked")
			}
		})
	}
}

func TestAccountAuthorityNoCacheAndNoFallback(t *testing.T) {
	const secret = "authority-no-cache"
	status := 200
	calls := 0
	authority := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		w.WriteHeader(status)
		_, _ = w.Write([]byte(`{"code":200,"data":{"id":42,"status":1}}`))
	}))
	defer authority.Close()
	server := &Server{jwtSecret: secret, accountURL: authority.URL}
	handler := server.requireAuth(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(204) })
	call := func(token string, ctx context.Context) int {
		r := httptest.NewRequest("GET", "/v1/wallets/me", nil).WithContext(ctx)
		r.Header.Set("Authorization", "Bearer "+token)
		w := httptest.NewRecorder()
		handler(w, r)
		return w.Code
	}
	token := makeTestToken(secret, time.Now().Unix()+60)
	if call(token, context.Background()) != 204 {
		t.Fatal("current account refused")
	}
	status = 401
	if call(token, context.Background()) != 401 || calls != 2 {
		t.Fatal("positive authority result cached")
	}
	if call("invalid", context.Background()) != 401 || calls != 2 {
		t.Fatal("malformed token reached authority")
	}
	if call(makeTestToken(secret, time.Now().Unix()+90000), context.Background()) != 401 || calls != 2 {
		t.Fatal("overlong token reached authority")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if call(token, ctx) != 503 {
		t.Fatal("cancelled authority call fell back to JWT")
	}
	for _, url := range []string{"", "file:///secret", "http://user:password@127.0.0.1/", "http://127.0.0.1:1/"} {
		server.accountURL = url
		if call(token, context.Background()) != 503 {
			t.Fatal("bad authority configuration allowed")
		}
	}
}
