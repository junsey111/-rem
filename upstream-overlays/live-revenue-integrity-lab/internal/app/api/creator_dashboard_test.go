package apiserver

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestDashboardIncomeFen(t *testing.T) {
	rules := &service.FinanceRules{DiamondUnits: 10, FiatFen: 100}
	for _, test := range []struct {
		diamonds int64
		rules    *service.FinanceRules
		want     *int64
	}{
		{0, nil, ptrInt64(0)},
		{30, rules, ptrInt64(300)},
		{1, &service.FinanceRules{DiamondUnits: 3, FiatFen: 100}, ptrInt64(33)},
		{30, nil, nil},
		{-1, rules, nil},
		{1 << 62, rules, nil},
	} {
		got := dashboardIncomeFen(test.diamonds, test.rules)
		if (got == nil) != (test.want == nil) || got != nil && *got != *test.want {
			t.Errorf("diamonds=%d: got %v, want %v", test.diamonds, got, test.want)
		}
	}
}

func ptrInt64(value int64) *int64 { return &value }

func TestCreatorDashboardCountsAllSessionsAndUsesConfiguredRate(t *testing.T) {
	db := newOperationsTestDB(t)
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type)
		VALUES(1,25,'CN','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL'),(2,25,'CN','SELF_REPORTED','GOOD',FALSE,FALSE,'NORMAL')`); err != nil {
		t.Fatal(err)
	}
	now := time.Now().UTC()
	monthStart := time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, time.UTC)
	priorStart := monthStart.Add(-24 * time.Hour)
	for id := 1; id <= 36; id++ {
		start := time.Now().UTC().Add(-10 * time.Minute)
		if id == 36 {
			start = priorStart
		}
		if _, err := db.Exec(`INSERT INTO live_sessions(live_session_id,creator_id,status,started_at,closed_at) VALUES(?,1,'CLOSED',?,?)`,
			id, start, start.Add(5*time.Minute)); err != nil {
			t.Fatal(err)
		}
	}
	insertGift := func(request string, diamonds int, created time.Time) {
		t.Helper()
		if _, err := db.Exec(`INSERT INTO gift_orders(viewer_id,creator_id,live_session_id,gift_id,quantity,charged_coins,match_points_added,diamond_reward,status,request_id,body_hash,created_at)
			VALUES(2,1,1,'ROSE',1,10,10,?,'ACCEPTED',?,UNHEX(REPEAT('00',32)),?)`, diamonds, request, created); err != nil {
			t.Fatal(err)
		}
	}
	insertGift("dashboard-current", 30, time.Now().UTC())
	insertGift("dashboard-prior", 20, priorStart)
	s := &Server{db: db, svc: service.New(db)}
	call := func() map[string]any {
		t.Helper()
		r := httptest.NewRequest(http.MethodGet, "/v1/creators/me/dashboard", nil)
		r = r.WithContext(context.WithValue(r.Context(), authUserIDKey, uint64(1)))
		w := httptest.NewRecorder()
		s.handleCreatorDashboard(w, r)
		if w.Code != http.StatusOK {
			t.Fatalf("dashboard: %d %s", w.Code, w.Body.String())
		}
		var result map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &result); err != nil {
			t.Fatal(err)
		}
		return result
	}
	if before := call(); before["monthly_income_fen"] != nil || before["total_income_fen"] != nil {
		t.Fatalf("unconfigured finance rules should not produce money: %v", before)
	}
	if _, err := db.Exec(`UPDATE finance_settings SET rules_json='{"diamond_units":10,"fiat_fen":100}' WHERE singleton=1`); err != nil {
		t.Fatal(err)
	}
	result := call()
	for key, want := range map[string]float64{
		"monthly_income_fen": 300, "total_income_fen": 500,
		"monthly_sessions": 35, "total_sessions": 36,
		"monthly_minutes": 175, "total_minutes": 180,
	} {
		if result[key] != want {
			t.Errorf("%s = %v, want %v", key, result[key], want)
		}
	}
}
