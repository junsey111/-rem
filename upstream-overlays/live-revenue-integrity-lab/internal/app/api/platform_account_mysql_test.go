package apiserver

import (
	"database/sql"
	"fmt"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/go-sql-driver/mysql"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func TestPlatformAccountRevisionAndLiveClosureMySQL(t *testing.T) {
	dsn := os.Getenv("MYSQL_TEST_DSN")
	if dsn == "" {
		t.Skip("isolated MySQL required")
	}
	config, err := mysql.ParseDSN(dsn)
	if err != nil {
		t.Fatal(err)
	}
	config.DBName = ""
	config.MultiStatements = true
	admin, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		t.Fatal(err)
	}
	defer admin.Close()
	name := fmt.Sprintf("live_test_platform_%d", time.Now().UnixNano())
	if _, err = admin.Exec("CREATE DATABASE `" + name + "`"); err != nil {
		t.Fatal(err)
	}
	defer admin.Exec("DROP DATABASE `" + name + "`")
	config.DBName = name
	db, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	for _, file := range []string{"00001_init.sql", "00004_pk_match_participants.sql", "00007_user_profile_provenance.sql", "00012_platform_moderation.sql"} {
		raw, err := os.ReadFile("../../../migrations/" + file)
		if err != nil {
			t.Fatal(err)
		}
		if _, err = db.Exec(strings.Split(string(raw), "-- +goose Down")[0]); err != nil {
			t.Fatal(file, err)
		}
	}
	if _, err = db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','SELF_REPORTED','GOOD',1,1,'NORMAL'); INSERT INTO live_sessions(live_session_id,creator_id,status,started_at) VALUES(1,1,'OPEN',UTC_TIMESTAMP());`); err != nil {
		t.Fatal(err)
	}
	server := &Server{db: db, svc: service.New(db)}
	call := func(revision int, disabled bool, intended int) {
		t.Helper()
		r := httptest.NewRequest("PUT", "/v1/internal/platform-account", strings.NewReader(fmt.Sprintf(`{"user_id":1,"revision":%d,"disabled":%t}`, revision, disabled)))
		w := httptest.NewRecorder()
		server.handlePlatformAccount(w, r)
		if w.Code != intended {
			t.Fatalf("status %d: %s", w.Code, w.Body.String())
		}
	}
	call(1, true, 200)
	call(1, true, 200)
	var events int
	var status string
	db.QueryRow(`SELECT COUNT(*) FROM outbox_events`).Scan(&events)
	db.QueryRow(`SELECT status FROM live_sessions WHERE live_session_id=1`).Scan(&status)
	if events != 1 || status != "CLOSED" {
		t.Fatalf("events=%d status=%s", events, status)
	}
	call(2, false, 200)
	call(1, true, 200)
	var revision int
	var disabled bool
	if err = db.QueryRow(`SELECT revision,disabled FROM platform_account_state WHERE user_id=1`).Scan(&revision, &disabled); err != nil {
		t.Fatal(err)
	}
	if revision != 2 || disabled {
		t.Fatal("stale disable overwrote restore")
	}
	call(2, true, 409)
	var standing string
	var enabled bool
	db.QueryRow(`SELECT account_standing,can_go_live FROM users WHERE user_id=1`).Scan(&standing, &enabled)
	if standing != "GOOD" || !enabled {
		t.Fatal("moderation overwrote independent permissions")
	}
}
