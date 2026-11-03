package apiserver

import (
	"context"
	"encoding/json"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestCohostPresenceGraceRecoveryAndTerminalState(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db), internalKey: "worker-key"}
	exec := func(query string) {
		t.Helper()
		if _, err := db.Exec(query); err != nil {
			t.Fatal(err)
		}
	}
	exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),(2,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'); INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(11,1,'OPEN',UTC_TIMESTAMP(6)); INSERT INTO cohost_requests(cohost_id,request_id,live_session_id,user_id,mode,status) VALUES(1,'10000000-0000-4000-8000-000000000001',11,2,'VIDEO','ACCEPTED')`)
	call := func(method, path, body, key string, want int) map[string]any {
		t.Helper()
		r := httptest.NewRequest(method, path, strings.NewReader(body))
		r.Header.Set("X-Internal-Key", key)
		w := httptest.NewRecorder()
		s.requireInternalKey(s.handleCohostPresence)(w, r)
		if w.Code != want {
			t.Fatalf("%d %s", w.Code, w.Body.String())
		}
		var data map[string]any
		json.Unmarshal(w.Body.Bytes(), &data)
		return data
	}
	probe := func(online bool, want string) {
		t.Helper()
		body := `{"cohost_id":"1","online":false}`
		if online {
			body = `{"cohost_id":"1","online":true}`
		}
		data := call("POST", "/v1/internal/cohost-presence", body, "worker-key", 200)
		if data["status"] != want {
			t.Fatalf("%v want %s", data, want)
		}
	}
	call("POST", "/v1/internal/cohost-presence", `{"cohost_id":"1","online":false}`, "", http.StatusUnauthorized)
	call("POST", "/v1/internal/cohost-presence", `{"cohost_id":"1"}`, "worker-key", 400)

	authority := func() map[string]any {
		t.Helper()
		w := httptest.NewRecorder()
		s.handleMediaRoom(w, httptest.NewRequest("GET", "/v1/internal/media-rooms/11", nil))
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
		var data map[string]any
		json.Unmarshal(w.Body.Bytes(), &data)
		return data
	}
	if len(authority()["publishers"].([]any)) != 2 {
		t.Fatal("accepted seat has no publishing authority")
	}
	probe(false, "ACCEPTED") // First absent observation starts the grace period.
	exec(`UPDATE cohost_requests SET media_absent_since=UTC_TIMESTAMP(6)-INTERVAL 89 SECOND,media_observed_at=UTC_TIMESTAMP(6) WHERE cohost_id=1`)
	probe(false, "ACCEPTED")
	probe(true, "ACCEPTED") // Reconnect cancels the accumulated absence.
	var absent any
	if err := db.QueryRow(`SELECT media_absent_since FROM cohost_requests WHERE cohost_id=1`).Scan(&absent); err != nil || absent != nil {
		t.Fatalf("absence %v %v", absent, err)
	}
	exec(`UPDATE cohost_requests SET media_absent_since=UTC_TIMESTAMP(6)-INTERVAL 5 MINUTE,media_observed_at=UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE WHERE cohost_id=1`)
	probe(false, "ACCEPTED") // Worker/service outage resets the grace, never mass-evicts.
	exec(`UPDATE cohost_requests SET media_absent_since=UTC_TIMESTAMP(6)-INTERVAL 91 SECOND,media_observed_at=UTC_TIMESTAMP(6),status='MUTED' WHERE cohost_id=1`)
	probe(false, "REMOVED")
	probe(true, "REMOVED") // A delayed online result cannot resurrect the expired seat.
	if len(authority()["publishers"].([]any)) != 1 {
		t.Fatal("expired seat retains publishing authority")
	}
	r := httptest.NewRequest("POST", "/v1/cohosts/11", strings.NewReader(`{"request_id":"10000000-0000-4000-8000-000000000002","mode":"VIDEO"}`))
	r = r.WithContext(context.WithValue(r.Context(), authUserIDKey, uint64(2)))
	w := httptest.NewRecorder()
	s.handleCohost(w, r)
	if w.Code != 200 {
		t.Fatalf("expired seat still blocks reapply: %d %s", w.Code, w.Body.String())
	}

	data := call("GET", "/v1/internal/cohost-presence", "", "worker-key", 200)
	if len(data["seats"].([]any)) != 0 {
		t.Fatalf("terminal seat listed %v", data)
	}
	exec(`UPDATE cohost_requests SET status='ACCEPTED',media_absent_since=NULL,media_observed_at=NULL WHERE cohost_id=1; UPDATE live_sessions SET status='CLOSED' WHERE live_session_id=11`)
	probe(true, "REMOVED") // Closed rooms release seats even if an old participant lingers.
	call("GET", "/v1/internal/cohost-presence?after_id=01", "", "worker-key", 400)
}
