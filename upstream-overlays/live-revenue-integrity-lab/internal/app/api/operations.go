package apiserver

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

var operationRequestPattern = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)

type moderationAction struct {
	ID         uint64    `json:"action_id,string"`
	RequestID  string    `json:"request_id"`
	OperatorID uint64    `json:"operator_id,string"`
	LiveID     uint64    `json:"live_session_id,string"`
	Reason     string    `json:"reason"`
	CreatedAt  time.Time `json:"created_at"`
}

const moderationColumns = `SELECT action_id,request_id,operator_id,live_session_id,reason,created_at FROM moderation_actions `

func scanModeration(row receiptScanner) (moderationAction, error) {
	var a moderationAction
	err := row.Scan(&a.ID, &a.RequestID, &a.OperatorID, &a.LiveID, &a.Reason, &a.CreatedAt)
	return a, err
}

func operationCursor(r *http.Request) (uint64, bool) {
	values, ok := r.URL.Query()["before_id"]
	if !ok {
		return 0, true
	}
	if len(values) != 1 {
		return 0, false
	}
	id, err := strconv.ParseUint(values[0], 10, 64)
	return id, err == nil && id > 0
}

func (s *Server) handleInternalOperations(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/v1/internal/operations/")
	if path == "lives" || path == "actions" {
		if r.Method != http.MethodGet {
			httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET required")
			return
		}
		s.listOperations(w, r, path)
		return
	}
	if strings.HasPrefix(path, "actions/") && r.Method == http.MethodGet {
		s.writeModeration(w, r, strings.TrimPrefix(path, "actions/"))
		return
	}
	parts := strings.Split(path, "/")
	if len(parts) != 3 || parts[0] != "lives" || parts[2] != "close" {
		httpx.WriteError(w, 404, "NOT_FOUND", "route not found")
		return
	}
	if r.Method != http.MethodPost {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "POST required")
		return
	}
	id, err := strconv.ParseUint(parts[1], 10, 64)
	var body struct {
		RequestID  string `json:"request_id"`
		OperatorID uint64 `json:"operator_id"`
		Reason     string `json:"reason"`
	}
	if err != nil || id == 0 || json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&body) != nil {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "invalid operation")
		return
	}
	if !operationRequestPattern.MatchString(body.RequestID) || body.OperatorID == 0 || strings.TrimSpace(body.Reason) == "" || utf8.RuneCountInString(body.Reason) > 200 {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "valid request id, operator and reason required")
		return
	}
	if appErr := s.svc.ForceCloseLiveSession(r.Context(), id, service.OperatorClose{RequestID: body.RequestID, OperatorID: body.OperatorID, Reason: body.Reason}); appErr != nil {
		message := appErr.Message
		if appErr.HTTPStatus >= 500 {
			message = "operation could not be confirmed"
		}
		httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, message)
		return
	}
	s.writeModeration(w, r, body.RequestID)
}

func (s *Server) writeModeration(w http.ResponseWriter, r *http.Request, request string) {
	if !operationRequestPattern.MatchString(request) {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "invalid request id")
		return
	}
	record, err := scanModeration(s.db.QueryRowContext(r.Context(), moderationColumns+`WHERE request_id=? AND applied=TRUE`, request))
	if err == sql.ErrNoRows {
		httpx.WriteError(w, 404, "OPERATION_NOT_FOUND", "no confirmed operation")
		return
	}
	if err != nil {
		httpx.WriteError(w, 503, "OPERATIONS_UNAVAILABLE", "operation unavailable")
		return
	}
	httpx.WriteJSON(w, 200, record)
}

func (s *Server) listOperations(w http.ResponseWriter, r *http.Request, kind string) {
	before, ok := operationCursor(r)
	if !ok {
		httpx.WriteError(w, 400, "INVALID_CURSOR", "positive cursor required")
		return
	}
	query, args := moderationColumns+`WHERE applied=TRUE`, []any{}
	idColumn := "action_id"
	if kind == "lives" {
		query = `SELECT live_session_id,creator_id,started_at FROM live_sessions WHERE status='OPEN'`
		idColumn = "live_session_id"
	}
	if before > 0 {
		query += ` AND ` + idColumn + `<?`
		args = append(args, before)
	}
	rows, err := s.db.QueryContext(r.Context(), query+` ORDER BY `+idColumn+` DESC LIMIT 26`, args...)
	if err != nil {
		httpx.WriteError(w, 503, "OPERATIONS_UNAVAILABLE", "operations unavailable")
		return
	}
	defer rows.Close()
	items := make([]any, 0, 26)
	ids := make([]uint64, 0, 26)
	for rows.Next() {
		if kind == "actions" {
			a, scanErr := scanModeration(rows)
			err = scanErr
			items = append(items, a)
			ids = append(ids, a.ID)
		} else {
			var live struct {
				ID        uint64    `json:"live_session_id,string"`
				CreatorID uint64    `json:"creator_id,string"`
				StartedAt time.Time `json:"started_at"`
			}
			err = rows.Scan(&live.ID, &live.CreatorID, &live.StartedAt)
			items = append(items, live)
			ids = append(ids, live.ID)
		}
		if err != nil {
			httpx.WriteError(w, 503, "OPERATIONS_UNAVAILABLE", "operations unavailable")
			return
		}
	}
	if rows.Err() != nil {
		httpx.WriteError(w, 503, "OPERATIONS_UNAVAILABLE", "operations unavailable")
		return
	}
	next := ""
	if len(items) > 25 {
		items = items[:25]
		next = strconv.FormatUint(ids[24], 10)
	}
	httpx.WriteJSON(w, 200, map[string]any{kind: items, "next_before_id": next})
}
