package apiserver

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// Missing matches must reach the database, rather than fail URL parsing.
type missingMatchDriver struct{}
type missingMatchConn struct{}

func (missingMatchDriver) Open(string) (driver.Conn, error)  { return missingMatchConn{}, nil }
func (missingMatchConn) Prepare(string) (driver.Stmt, error) { return nil, driver.ErrSkip }
func (missingMatchConn) Close() error                        { return nil }
func (missingMatchConn) Begin() (driver.Tx, error)           { return nil, driver.ErrSkip }
func (missingMatchConn) QueryContext(context.Context, string, []driver.NamedValue) (driver.Rows, error) {
	return nil, sql.ErrNoRows
}

func init() { sql.Register("missing-match-test", missingMatchDriver{}) }

func TestMatchRoutes(t *testing.T) {
	db, err := sql.Open("missing-match-test", "")
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	s := &Server{db: db}
	for _, tc := range []struct {
		path   string
		status int
		code   string
	}{
		{"/v1/matches/42", http.StatusNotFound, "MATCH_NOT_FOUND"},
		{"/v1/matches/42/score", http.StatusNotFound, "MATCH_NOT_FOUND"},
		{"/v1/matches/invalid", http.StatusBadRequest, "BAD_PATH"},
		{"/v1/matches/invalid/score", http.StatusBadRequest, "BAD_PATH"},
		{"/v1/matches/42/score/extra", http.StatusNotFound, "NOT_FOUND"},
		{"/v1/other/42/score", http.StatusNotFound, "NOT_FOUND"},
	} {
		t.Run(tc.path, func(t *testing.T) {
			recorder := httptest.NewRecorder()
			s.handleMatchScore(recorder, httptest.NewRequest(http.MethodGet, tc.path, nil))
			if recorder.Code != tc.status || !strings.Contains(recorder.Body.String(), `"code":"`+tc.code+`"`) {
				t.Fatalf("got %d %s; want %d %s", recorder.Code, recorder.Body.String(), tc.status, tc.code)
			}
		})
	}
}
