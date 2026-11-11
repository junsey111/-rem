package apiserver

import (
	"context"
	"fmt"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestLevelSettingsVersionAndCalculation(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db}
	initial, err := s.readLevelSettings(context.Background())
	if err != nil || initial.Version != 0 || service.LevelForPointsWithThresholds(1_000_000, initial.ViewerThresholds) != 6 {
		t.Fatalf("initial level settings: %+v %v", initial, err)
	}
	call := func(body string, want int) {
		t.Helper()
		w := httptest.NewRecorder()
		s.handleBusinessSettings(w, httptest.NewRequest("PUT", "/v1/internal/business/levels", strings.NewReader(body)))
		if w.Code != want {
			t.Fatalf("save levels: %d %s", w.Code, w.Body.String())
		}
	}
	command := func(id, version int, viewer string) string {
		return fmt.Sprintf(`{"request_id":"50000000-0000-4000-8000-%012d","operator_id":9,"expected_version":%d,"reason":"test levels","viewer_thresholds":%s,"creator_thresholds":[30,60,90,120,150]}`,
			id, version, viewer)
	}
	valid := command(1, 0, `[20,40,80,160,320]`)
	call(valid, 200)
	call(valid, 200)
	call(command(2, 0, `[20,40,80,160,320]`), 409)
	call(command(3, 1, `[20,40,40,160,320]`), 400)
	saved, err := s.readLevelSettings(context.Background())
	if err != nil || saved.Version != 1 || !saved.Configured ||
		service.LevelForPointsWithThresholds(640, saved.ViewerThresholds) != 6 ||
		service.LevelForPointsWithThresholds(150, saved.CreatorThresholds) != 6 {
		t.Fatalf("saved level settings: %+v %v", saved, err)
	}
}
