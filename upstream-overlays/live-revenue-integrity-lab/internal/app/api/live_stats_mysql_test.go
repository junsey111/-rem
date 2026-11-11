package apiserver

import (
	"context"
	"encoding/json"
	"net/http/httptest"
	"testing"
)

func TestLiveStatsAreOwnerOnlyAndCountAcceptedSessionGifts(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db}
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','GOOD',1,1,'NORMAL');
 INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(11,1,'OPEN',UTC_TIMESTAMP(6)),(12,1,'OPEN',UTC_TIMESTAMP(6));
 INSERT INTO gift_orders(viewer_id,creator_id,live_session_id,gift_id,quantity,charged_coins,match_points_added,diamond_reward,status,request_id,body_hash,created_at) VALUES
 (2,1,11,'rose',2,100,0,20,'ACCEPTED','a',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6)),
 (2,1,11,'rose',1,50,0,10,'ACCEPTED','b',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6)),
 (2,1,11,'rose',9,900,0,180,'REJECTED','c',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6)),
 (2,1,12,'rose',8,800,0,160,'ACCEPTED','d',UNHEX(REPEAT('00',32)),UTC_TIMESTAMP(6));`); err != nil {
		t.Fatal(err)
	}
	call := func(user uint64, room string, want int) map[string]any {
		t.Helper()
		req := httptest.NewRequest("GET", "/v1/lives/"+room+"/stats", nil)
		req = req.WithContext(context.WithValue(req.Context(), authUserIDKey, user))
		res := httptest.NewRecorder()
		s.handleLivesPrefix(res, req)
		if res.Code != want {
			t.Fatalf("stats: %d %s", res.Code, res.Body.String())
		}
		var data map[string]any
		json.Unmarshal(res.Body.Bytes(), &data)
		return data
	}
	call(2, "11", 403)
	call(1, "99", 404)
	stats := call(1, "11", 200)
	if stats["gift_coins"] != float64(150) || stats["reward_diamonds"] != float64(30) || stats["gift_count"] != float64(3) {
		t.Fatalf("wrong session totals: %+v", stats)
	}
}
