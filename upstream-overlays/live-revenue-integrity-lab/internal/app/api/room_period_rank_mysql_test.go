package apiserver

import (
	"encoding/json"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http/httptest"
	"testing"
)

func TestRoomPeriodRankIsHostScopedAndExcludesRejectedAndOldGifts(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	_, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','GOOD',1,1,'NORMAL'),(4,25,'US','GOOD',1,1,'NORMAL');
        INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(11,1,'OPEN',UTC_TIMESTAMP(6)),(12,1,'CLOSED',UTC_TIMESTAMP(6)),(14,4,'OPEN',UTC_TIMESTAMP(6));
        INSERT INTO gift_orders(viewer_id,creator_id,live_session_id,gift_id,quantity,charged_coins,match_points_added,diamond_reward,status,request_id,body_hash,created_at) VALUES
        (2,1,11,'rose',1,100,0,1,'ACCEPTED','rank-a',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6)),
        (2,1,12,'rose',1,50,0,1,'ACCEPTED','rank-b',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6)),
        (3,1,11,'rose',1,70,0,1,'ACCEPTED','rank-c',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6)),
        (2,1,11,'rose',1,800,0,1,'REJECTED','rank-d',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6)),
        (2,4,14,'rose',1,900,0,1,'ACCEPTED','rank-e',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6)),
        (2,1,11,'rose',1,700,0,1,'ACCEPTED','rank-f',UNHEX(REPEAT('00',32)),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 40 DAY));`)
	if err != nil {
		t.Fatal(err)
	}
	for _, period := range []string{"daily", "weekly", "monthly"} {
		w := httptest.NewRecorder()
		s.handleLivesPrefix(w, httptest.NewRequest("GET", "/v1/lives/11/contributors?period="+period, nil))
		if w.Code != 200 {
			t.Fatalf("%s: %d %s", period, w.Code, w.Body.String())
		}
		var response struct {
			Contributors []service.ContributorSummary `json:"contributors"`
		}
		if err := json.Unmarshal(w.Body.Bytes(), &response); err != nil {
			t.Fatal(err)
		}
		if len(response.Contributors) != 2 || response.Contributors[0].ViewerID != 2 || response.Contributors[0].Coins != 150 || response.Contributors[1].Coins != 70 {
			t.Fatalf("%s: %s", period, w.Body.String())
		}
	}
	w := httptest.NewRecorder()
	s.handleLivesPrefix(w, httptest.NewRequest("GET", "/v1/lives/11/contributors?period=hourly", nil))
	if w.Code != 400 {
		t.Fatalf("hourly should be unsupported: %d", w.Code)
	}
}
