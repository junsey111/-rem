package apiserver

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/go-sql-driver/mysql"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
	"github.com/pressly/goose/v3"
)

func newOperationsTestDB(t *testing.T) *sql.DB {
	t.Helper()
	dsn := os.Getenv("MYSQL_TEST_DSN")
	if dsn == "" {
		t.Skip("isolated MySQL required")
	}
	config, err := mysql.ParseDSN(dsn)
	if err != nil {
		t.Fatal(err)
	}
	config.DBName = ""
	config.ParseTime = true
	admin, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		t.Fatal(err)
	}
	name := fmt.Sprintf("live_test_operations_%d", time.Now().UnixNano())
	if _, err = admin.Exec("CREATE DATABASE `" + name + "`"); err != nil {
		t.Fatal(err)
	}
	config.DBName = name
	db, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close(); admin.Exec("DROP DATABASE `" + name + "`"); admin.Close() })
	p, err := goose.NewProvider(goose.DialectMySQL, db, os.DirFS("../../../migrations"), goose.WithExcludeNames([]string{"00002_seed.sql"}))
	if err != nil {
		t.Fatal(err)
	}
	if _, err = p.Up(context.Background()); err != nil {
		t.Fatal(err)
	}
	return db
}
func TestCreatorReviewsReportsAndCatalogPersistIdempotently(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db, svc: service.New(db)}
	if _, err := db.Exec(`INSERT INTO users(user_id,age_years,region_code,profile_source,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES(1,25,'US','SELF_REPORTED','GOOD',TRUE,TRUE,'NORMAL')`); err != nil {
		t.Fatal(err)
	}
	call := func(handler http.HandlerFunc, path, method string, body any, want int) map[string]any {
		t.Helper()
		raw, _ := json.Marshal(body)
		r := httptest.NewRequest(method, path, strings.NewReader(string(raw)))
		r = r.WithContext(context.WithValue(r.Context(), authUserIDKey, uint64(1)))
		w := httptest.NewRecorder()
		handler(w, r)
		if w.Code != want {
			t.Fatalf("%s %s: %d %s", method, path, w.Code, w.Body.String())
		}
		var result map[string]any
		if json.Unmarshal(w.Body.Bytes(), &result) != nil {
			t.Fatal(w.Body.String())
		}
		return result
	}
	application := map[string]any{"request_id": "10000000-0000-4000-8000-000000000001", "content": "Creator introduction", "evidence_reference": "Operator-held material TEST-1"}
	result := call(s.handleReviews, "/v1/users/me/creator-applications", "POST", application, 200)
	id := result["review_id"].(string)
	call(s.handleReviews, "/v1/users/me/creator-applications", "POST", application, 200)
	if allowed, err := s.svc.CanStartLive(context.Background(), 1); err != nil || allowed {
		t.Fatal("pending applicant may publish", err)
	}
	approval := map[string]any{"request_id": "20000000-0000-4000-8000-000000000001", "operator_id": 9, "decision": "APPROVED", "reason": "Material checked by operator"}
	call(s.handleAdminReviews, "/v1/internal/reviews/creators/"+id, "POST", approval, 200)
	call(s.handleAdminReviews, "/v1/internal/reviews/creators/"+id, "POST", approval, 200)
	if allowed, err := s.svc.CanStartLive(context.Background(), 1); err != nil || !allowed {
		t.Fatal("approved applicant blocked", err)
	}
	approval["decision"] = "REJECTED"
	call(s.handleAdminReviews, "/v1/internal/reviews/creators/"+id, "POST", approval, 409)
	report := map[string]any{"request_id": "10000000-0000-4000-8000-000000000002", "target_type": "USER", "target_id": 1, "content": "Test report", "evidence_reference": "test reference"}
	result = call(s.handleReviews, "/v1/users/me/reports", "POST", report, 200)
	reportID := result["review_id"].(string)
	call(s.handleAdminReviews, "/v1/internal/reviews/reports/"+reportID, "POST", map[string]any{"request_id": "20000000-0000-4000-8000-000000000002", "operator_id": 9, "decision": "DISMISSED", "reason": "No violation"}, 200)
	result = call(s.handleReviews, "/v1/users/me/reports", "GET", nil, 200)
	if len(result["reviews"].([]any)) != 1 {
		t.Fatal("missing report history")
	}
	gift := map[string]any{"request_id": "30000000-0000-4000-8000-000000000001", "operator_id": 9, "gift_id": "testgift", "display_name": "Test gift", "coin_price": 10, "diamond_reward": 5, "match_points": 10, "enabled": true, "reason": "test create"}
	call(s.handleAdminCatalog, "/v1/internal/catalog", "PUT", gift, 200)
	gift["request_id"] = "30000000-0000-4000-8000-000000000002"
	gift["coin_price"] = 20
	call(s.handleAdminCatalog, "/v1/internal/catalog", "PUT", gift, 200)
	gift["request_id"] = "30000000-0000-4000-8000-000000000001"
	gift["coin_price"] = 10
	call(s.handleAdminCatalog, "/v1/internal/catalog", "PUT", gift, 200)
	var price int
	if err := db.QueryRow(`SELECT coin_price FROM gift_catalog WHERE gift_id='testgift'`).Scan(&price); err != nil || price != 20 {
		t.Fatal("old retry changed price", price, err)
	}
	gift["coin_price"] = 11
	call(s.handleAdminCatalog, "/v1/internal/catalog", "PUT", gift, 409)
}
