package apiserver

import (
	"context"
	"fmt"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestAdmissionPolicyVersionReplayAndPublishing(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL')`); err != nil {
		t.Fatal(err)
	}
	allowed := func(want bool) {
		t.Helper()
		got, err := s.svc.CanStartLive(context.Background(), 1)
		if err != nil || got != want {
			t.Fatalf("allowed %v want %v: %v", got, want, err)
		}
	}
	call := func(n, version int, required bool, want int) {
		t.Helper()
		r := httptest.NewRequest("PUT", "/v1/internal/admission", strings.NewReader(fmt.Sprintf(`{"request_id":"10000000-0000-4000-8000-%012d","operator_id":9,"expected_version":%d,"require_approval":%v,"reason":"policy"}`, n, version, required)))
		w := httptest.NewRecorder()
		s.handleAdmission(w, r)
		if w.Code != want {
			t.Fatalf("%d %s", w.Code, w.Body.String())
		}
	}
	allowed(true)
	call(1, 0, true, 200)
	allowed(false)
	call(1, 0, true, 200)
	allowed(false)
	call(1, 0, false, 409)
	call(2, 0, false, 409)
	call(2, 1, false, 200)
	allowed(true)
	call(1, 0, true, 200)
	allowed(true) // replay returns original result without restoring stale policy
}
