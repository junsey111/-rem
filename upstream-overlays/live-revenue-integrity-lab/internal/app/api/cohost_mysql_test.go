package apiserver

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestCohostQueuePositionAdvances(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),(2,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'),(3,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'); INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(11,1,'OPEN',UTC_TIMESTAMP(6));`); err != nil {
		t.Fatal(err)
	}
	call := func(user uint64, method, path, body string) map[string]any {
		t.Helper()
		req := httptest.NewRequest(method, path, strings.NewReader(body))
		req = req.WithContext(context.WithValue(req.Context(), authUserIDKey, user))
		res := httptest.NewRecorder()
		s.handleCohost(res, req)
		if res.Code != 200 {
			t.Fatalf("queue: %d %s", res.Code, res.Body.String())
		}
		var data map[string]any
		json.Unmarshal(res.Body.Bytes(), &data)
		return data
	}
	first := call(2, "POST", "/v1/cohosts/11", `{"request_id":"10000000-0000-4000-8000-000000000002","mode":"AUDIO"}`)
	call(3, "POST", "/v1/cohosts/11", `{"request_id":"10000000-0000-4000-8000-000000000003","mode":"VIDEO"}`)
	own := call(3, "GET", "/v1/cohosts/11", "")["requests"].([]any)
	if len(own) != 1 || own[0].(map[string]any)["queue_position"] != float64(2) {
		t.Fatalf("viewer queue: %+v", own)
	}
	call(2, "POST", "/v1/cohosts/11/"+first["cohost_id"].(string), `{"request_id":"20000000-0000-4000-8000-000000000002","action":"LEAVE"}`)
	own = call(3, "GET", "/v1/cohosts/11", "")["requests"].([]any)
	if own[0].(map[string]any)["queue_position"] != float64(1) {
		t.Fatal("queue did not advance")
	}
}

func TestCohostPermissionsAndLifecycle(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	for _, id := range []int{1, 2, 3} {
		if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(?,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL')`, id); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := db.Exec(`INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(11,1,'OPEN',UTC_TIMESTAMP(6))`); err != nil {
		t.Fatal(err)
	}
	call := func(user uint64, path, body string, want int) map[string]any {
		t.Helper()
		r := httptest.NewRequest(http.MethodPost, path, strings.NewReader(body))
		r = r.WithContext(context.WithValue(r.Context(), authUserIDKey, user))
		w := httptest.NewRecorder()
		s.handleCohost(w, r)
		if w.Code != want {
			t.Fatalf("%s: %d %s", path, w.Code, w.Body.String())
		}
		result := map[string]any{}
		json.Unmarshal(w.Body.Bytes(), &result)
		return result
	}
	request := `{"request_id":"10000000-0000-4000-8000-000000000001","mode":"AUDIO"}`
	if _, err := db.Exec(`UPDATE users SET age_years=17 WHERE user_id=3`); err != nil {
		t.Fatal(err)
	}
	call(3, "/v1/cohosts/11", request, 403)
	first := call(2, "/v1/cohosts/11", request, 200)
	id := first["cohost_id"].(string)
	if call(2, "/v1/cohosts/11", request, 200)["cohost_id"] != id {
		t.Fatal("duplicate request")
	}
	action := func(user uint64, n int, action string, want int) {
		call(user, "/v1/cohosts/11/"+id, fmt.Sprintf(`{"request_id":"20000000-0000-4000-8000-%012d","action":"%s"}`, n, action), want)
	}
	authority := func() map[string]any {
		r := httptest.NewRequest("GET", "/v1/internal/media-rooms/11", nil)
		w := httptest.NewRecorder()
		s.handleMediaRoom(w, r)
		if w.Code != 200 {
			t.Fatal(w.Body.String())
		}
		var m map[string]any
		json.Unmarshal(w.Body.Bytes(), &m)
		return m
	}
	if len(authority()["publishers"].([]any)) != 1 {
		t.Fatal("pending viewer may publish")
	}
	action(3, 1, "ACCEPT", 403)
	action(1, 1, "ACCEPT", 200)
	action(1, 1, "ACCEPT", 200)
	state := authority()
	if len(state["publishers"].([]any)) != 2 {
		t.Fatal(state)
	}
	if fmt.Sprint(state["publish_sources"].(map[string]any)["2"]) != "[microphone]" {
		t.Fatal(state)
	}
	action(1, 2, "MUTE", 200)
	if len(authority()["publishers"].([]any)) != 1 {
		t.Fatal("mute failed")
	}
	action(1, 3, "UNMUTE", 200)
	action(2, 4, "REMOVE", 403)
	action(1, 4, "REMOVE", 200)
	if len(authority()["publishers"].([]any)) != 1 {
		t.Fatal("remove failed")
	}
	action(1, 5, "ACCEPT", 409)
	if _, err := db.Exec(`UPDATE live_sessions SET status='CLOSED' WHERE live_session_id=11`); err != nil {
		t.Fatal(err)
	}
	call(3, "/v1/cohosts/11", `{"request_id":"10000000-0000-4000-8000-000000000002","mode":"VIDEO"}`, 409)
}
