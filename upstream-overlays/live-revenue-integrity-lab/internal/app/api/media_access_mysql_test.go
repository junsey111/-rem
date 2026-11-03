package apiserver

import (
	"encoding/json"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestMediaPreviewCannotBeResetOrBypassedWithoutHeartbeats(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	_, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,account_standing,can_go_live,live_gifts_enabled,account_type,profile_source) VALUES (11,25,'US','GOOD',1,1,'NORMAL','SELF_REPORTED'),(22,25,'US','GOOD',1,1,'NORMAL','SELF_REPORTED');
 INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(501,22,'OPEN',UTC_TIMESTAMP(6));
 INSERT INTO live_watch_prices(live_session_id,unlock_price_coins) VALUES(501,10);`)
	if err != nil {
		t.Fatal(err)
	}
	call := func(body string, want int) map[string]any {
		t.Helper()
		w := httptest.NewRecorder()
		r := httptest.NewRequest("POST", "/v1/internal/media-access", strings.NewReader(body))
		s.handleMediaAccess(w, r)
		if w.Code != want {
			t.Fatalf("media access: %d %s", w.Code, w.Body.String())
		}
		var out map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &out); err != nil {
			t.Fatal(err)
		}
		return out
	}
	body := `{"user_id":11,"live_session_id":501,"room":"501"}`
	if call(body, 200)["allowed"] != true {
		t.Fatal("preview should open")
	}
	if _, err = db.Exec(`UPDATE live_watch_progress SET preview_started_at=TIMESTAMPADD(SECOND,-61,UTC_TIMESTAMP(6)) WHERE user_id=11 AND live_session_id=501`); err != nil {
		t.Fatal(err)
	}
	if call(body, 200)["allowed"] != false {
		t.Fatal("no heartbeat must still expire")
	}
	if call(body, 200)["allowed"] != false {
		t.Fatal("reconnect must not restart preview")
	}
	call(`{"user_id":11,"live_session_id":501,"room":"pk-99"}`, 403)
	if _, err = db.Exec(`INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(502,22,'OPEN',UTC_TIMESTAMP(6));
    INSERT INTO live_matches(match_id,live_session_id,creator_id,opponent_live_session_id,opponent_creator_id,status,mode) VALUES(99,501,22,502,22,'OPEN','ALL_GIFTS');`); err != nil {
		t.Fatal(err)
	}
	if call(`{"user_id":11,"live_session_id":502,"room":"pk-99"}`, 200)["allowed"] != false {
		t.Fatal("free PK side must not bypass the paid side")
	}

	if _, err = db.Exec(`UPDATE live_watch_progress SET unlocked=1 WHERE user_id=11 AND live_session_id=501`); err != nil {
		t.Fatal(err)
	}
	if call(body, 200)["allowed"] != true {
		t.Fatal("paid access should open")
	}
	if _, err = db.Exec(`UPDATE users SET account_standing='BANNED' WHERE user_id=11`); err != nil {
		t.Fatal(err)
	}
	call(body, 403)
	if _, err = db.Exec(`UPDATE live_sessions SET status='CLOSED' WHERE live_session_id=501`); err != nil {
		t.Fatal(err)
	}
	call(`{"user_id":22,"live_session_id":501,"room":"501"}`, 409)
}
