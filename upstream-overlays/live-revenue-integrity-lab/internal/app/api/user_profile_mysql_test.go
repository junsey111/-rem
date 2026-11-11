package apiserver

import (
	"context"
	"encoding/json"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestProfileSavesGenderWithoutRegionMySQL(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	call := func(method, body string, want int) map[string]any {
		t.Helper()
		r := httptest.NewRequest(method, "/v1/users/me/profile", strings.NewReader(body))
		r = r.WithContext(context.WithValue(r.Context(), authUserIDKey, uint64(1)))
		w := httptest.NewRecorder()
		s.handleUserProfile(w, r)
		if w.Code != want {
			t.Fatalf("%s profile: status %d, body %s", method, w.Code, w.Body.String())
		}
		var result map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &result); err != nil {
			t.Fatal(err)
		}
		return result
	}
	result := call("PUT", `{"age_years":25,"gender":"FEMALE"}`, 200)
	if result["gender"] != "FEMALE" || result["age_years"] != float64(25) || result["region_code"] != nil {
		t.Fatalf("saved profile: %#v", result)
	}
	result = call("GET", "", 200)
	if result["gender"] != "FEMALE" || result["age_years"] != float64(25) {
		t.Fatalf("reloaded profile: %#v", result)
	}
	if allowed, err := s.svc.CanStartLive(context.Background(), 1); err != nil || !allowed {
		t.Fatalf("age-only live eligibility: allowed=%v err=%v", allowed, err)
	}
	call("PUT", `{"age_years":25,"gender":"OTHER"}`, 400)
	if result = call("GET", "", 200); result["gender"] != "FEMALE" {
		t.Fatalf("invalid gender changed profile: %#v", result)
	}
}
