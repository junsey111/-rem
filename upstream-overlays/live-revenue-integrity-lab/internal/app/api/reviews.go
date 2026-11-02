package apiserver

import (
	"database/sql"
	"net/http"
	"strconv"
	"strings"
	"unicode/utf8"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type reviewRequest struct {
	ID             uint64 `json:"review_id,string"`
	UserID         uint64 `json:"user_id,string"`
	RequestID      string `json:"request_id"`
	Kind           string `json:"kind"`
	TargetType     string `json:"target_type"`
	TargetID       uint64 `json:"target_id,string"`
	Content        string `json:"content"`
	Evidence       string `json:"evidence_reference"`
	Status         string `json:"status"`
	CreatedAt      string `json:"created_at"`
	Decision       string `json:"decision"`
	DecisionReason string `json:"decision_reason"`
}

const reviewColumns = `SELECT review_id,user_id,request_id,kind,target_type,target_id,content,evidence_reference,status,CAST(created_at AS CHAR),COALESCE((SELECT decision FROM review_actions a WHERE a.review_id=q.review_id LIMIT 1),''),COALESCE((SELECT reason FROM review_actions a WHERE a.review_id=q.review_id LIMIT 1),'') FROM review_requests q `

func scanReview(row interface{ Scan(...any) error }) (reviewRequest, error) {
	var q reviewRequest
	err := row.Scan(&q.ID, &q.UserID, &q.RequestID, &q.Kind, &q.TargetType, &q.TargetID, &q.Content, &q.Evidence, &q.Status, &q.CreatedAt, &q.Decision, &q.DecisionReason)
	return q, err
}
func (s *Server) handleReviews(w http.ResponseWriter, r *http.Request) {
	kind := strings.TrimPrefix(r.URL.Path, "/v1/users/me/")
	if kind == "creator-applications" {
		kind = "CREATOR"
	} else if kind == "reports" {
		kind = "REPORT"
	} else {
		httpx.WriteError(w, 404, "NOT_FOUND", "route not found")
		return
	}
	user := authenticatedUserID(r)
	if r.Method == http.MethodGet {
		s.listReviews(w, r, user, kind)
		return
	}
	if r.Method != http.MethodPost {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET or POST required")
		return
	}
	if err := s.ensureUser(r.Context(), user); err != nil {
		financeReply(w, nil, err)
		return
	}
	var c struct {
		RequestID  string `json:"request_id"`
		TargetType string `json:"target_type"`
		TargetID   uint64 `json:"target_id"`
		Content    string `json:"content"`
		Evidence   string `json:"evidence_reference"`
	}
	if !financeBody(w, r, &c) {
		return
	}
	if !operationRequestPattern.MatchString(c.RequestID) || strings.TrimSpace(c.Content) == "" || utf8.RuneCountInString(c.Content) > 1000 || utf8.RuneCountInString(c.Evidence) > 300 {
		httpx.WriteError(w, 400, "INVALID_REVIEW_REQUEST", "请填写有效申请单号、内容和材料引用")
		return
	}
	if kind == "CREATOR" {
		c.TargetType = "USER"
		c.TargetID = user
		if strings.TrimSpace(c.Evidence) == "" {
			httpx.WriteError(w, 400, "EVIDENCE_REQUIRED", "请填写可供运营核实的材料编号或引用")
			return
		}
	}
	if c.TargetID == 0 || (c.TargetType != "USER" && c.TargetType != "LIVE") {
		httpx.WriteError(w, 400, "INVALID_TARGET", "请选择有效用户或直播")
		return
	}
	tx, err := s.db.BeginTx(r.Context(), &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer tx.Rollback()
	var standing string
	if err = tx.QueryRowContext(r.Context(), `SELECT account_standing FROM users WHERE user_id=? FOR UPDATE`, user).Scan(&standing); err != nil {
		financeReply(w, nil, err)
		return
	}
	prior, err := scanReview(tx.QueryRowContext(r.Context(), reviewColumns+`WHERE user_id=? AND request_id=?`, user, c.RequestID))
	if err == nil {
		if prior.Kind != kind || prior.TargetType != c.TargetType || prior.TargetID != c.TargetID || prior.Content != c.Content || prior.Evidence != c.Evidence {
			httpx.WriteError(w, 409, "OPERATION_CONFLICT", "单号已用于其他申请")
			return
		}
		financeReply(w, prior, nil)
		return
	}
	if err != sql.ErrNoRows {
		financeReply(w, nil, err)
		return
	}
	if standing != "GOOD" {
		httpx.WriteError(w, 403, "ACCOUNT_RESTRICTED", "账号当前不能提交申请")
		return
	}
	var target int
	query := `SELECT COUNT(*) FROM users WHERE user_id=?`
	if c.TargetType == "LIVE" {
		query = `SELECT COUNT(*) FROM live_sessions WHERE live_session_id=?`
	}
	if err = tx.QueryRowContext(r.Context(), query, c.TargetID).Scan(&target); err != nil {
		financeReply(w, nil, err)
		return
	}
	if target == 0 {
		httpx.WriteError(w, 404, "TARGET_NOT_FOUND", "目标不存在")
		return
	}
	if kind == "CREATOR" {
		var existing int
		if err = tx.QueryRowContext(r.Context(), `SELECT COUNT(*) FROM review_requests WHERE user_id=? AND kind='CREATOR' AND status IN ('PENDING','APPROVED')`, user).Scan(&existing); err != nil {
			financeReply(w, nil, err)
			return
		}
		if existing > 0 {
			httpx.WriteError(w, 409, "APPLICATION_EXISTS", "已有待审核或通过的入驻申请，请查看记录")
			return
		}
		// Submission requires a fresh review. Review does not attest legal identity or change self-reported age.
		if _, err = tx.ExecContext(r.Context(), `UPDATE users SET can_go_live=FALSE WHERE user_id=?`, user); err != nil {
			financeReply(w, nil, err)
			return
		}
	}
	result, err := tx.ExecContext(r.Context(), `INSERT INTO review_requests(user_id,request_id,kind,target_type,target_id,content,evidence_reference) VALUES(?,?,?,?,?,?,?)`, user, c.RequestID, kind, c.TargetType, c.TargetID, c.Content, c.Evidence)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	id, _ := result.LastInsertId()
	record, err := scanReview(tx.QueryRowContext(r.Context(), reviewColumns+`WHERE review_id=?`, id))
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	financeReply(w, record, tx.Commit())
}
func (s *Server) listReviews(w http.ResponseWriter, r *http.Request, user uint64, kind string) {
	before, ok := operationCursor(r)
	if !ok {
		httpx.WriteError(w, 400, "INVALID_CURSOR", "分页无效")
		return
	}
	query := reviewColumns + `WHERE kind=?`
	args := []any{kind}
	if user > 0 {
		query += ` AND user_id=?`
		args = append(args, user)
	}
	if before > 0 {
		query += ` AND review_id<?`
		args = append(args, before)
	}
	rows, err := s.db.QueryContext(r.Context(), query+` ORDER BY review_id DESC LIMIT 26`, args...)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer rows.Close()
	items := []reviewRequest{}
	for rows.Next() {
		q, err := scanReview(rows)
		if err != nil {
			financeReply(w, nil, err)
			return
		}
		items = append(items, q)
	}
	next := ""
	if len(items) > 25 {
		items = items[:25]
		next = strconv.FormatUint(items[24].ID, 10)
	}
	financeReply(w, map[string]any{"reviews": items, "next_before_id": next}, rows.Err())
}
func (s *Server) handleAdminReviews(w http.ResponseWriter, r *http.Request) {
	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/v1/internal/reviews/"), "/")
	kind := ""
	if parts[0] == "creators" {
		kind = "CREATOR"
	}
	if parts[0] == "reports" {
		kind = "REPORT"
	}
	if kind == "" {
		httpx.WriteError(w, 404, "NOT_FOUND", "route not found")
		return
	}
	if len(parts) == 1 && r.Method == http.MethodGet {
		s.listReviews(w, r, 0, kind)
		return
	}
	if len(parts) != 2 || r.Method != http.MethodPost {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "POST required")
		return
	}
	id, err := strconv.ParseUint(parts[1], 10, 64)
	if err != nil || id == 0 {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "审核单号无效")
		return
	}
	var c struct {
		RequestID  string `json:"request_id"`
		OperatorID uint64 `json:"operator_id"`
		Decision   string `json:"decision"`
		Reason     string `json:"reason"`
	}
	if !financeBody(w, r, &c) {
		return
	}
	valid := kind == "CREATOR" && (c.Decision == "APPROVED" || c.Decision == "REJECTED") || kind == "REPORT" && (c.Decision == "RESOLVED" || c.Decision == "DISMISSED")
	if !valid || !operationRequestPattern.MatchString(c.RequestID) || c.OperatorID == 0 || strings.TrimSpace(c.Reason) == "" || utf8.RuneCountInString(c.Reason) > 200 {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "请填写有效审核结论和原因")
		return
	}
	tx, err := s.db.BeginTx(r.Context(), nil)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer tx.Rollback()
	var user uint64
	if err = tx.QueryRowContext(r.Context(), `SELECT user_id FROM review_requests WHERE review_id=? AND kind=?`, id, kind).Scan(&user); err == sql.ErrNoRows {
		httpx.WriteError(w, 404, "REVIEW_NOT_FOUND", "申请不存在")
		return
	}
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	if kind == "CREATOR" {
		var lock uint64
		if err = tx.QueryRowContext(r.Context(), `SELECT user_id FROM users WHERE user_id=? FOR UPDATE`, user).Scan(&lock); err != nil {
			financeReply(w, nil, err)
			return
		}
	}
	record, err := scanReview(tx.QueryRowContext(r.Context(), reviewColumns+`WHERE review_id=? FOR UPDATE`, id))
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	var priorID, priorOperator uint64
	var priorDecision, priorReason string
	err = tx.QueryRowContext(r.Context(), `SELECT review_id,operator_id,decision,reason FROM review_actions WHERE request_id=?`, c.RequestID).Scan(&priorID, &priorOperator, &priorDecision, &priorReason)
	if err == nil {
		if priorID != id || priorOperator != c.OperatorID || priorDecision != c.Decision || priorReason != c.Reason {
			httpx.WriteError(w, 409, "OPERATION_CONFLICT", "单号已用于其他审核")
			return
		}
		financeReply(w, record, nil)
		return
	}
	if err != sql.ErrNoRows {
		financeReply(w, nil, err)
		return
	}
	if record.Status != "PENDING" {
		httpx.WriteError(w, 409, "REVIEW_STATE_CONFLICT", "该申请已审核，请刷新")
		return
	}
	if kind == "CREATOR" {
		if _, err = tx.ExecContext(r.Context(), `UPDATE users SET can_go_live=? WHERE user_id=?`, c.Decision == "APPROVED", user); err != nil {
			financeReply(w, nil, err)
			return
		}
	}
	if _, err = tx.ExecContext(r.Context(), `INSERT INTO review_actions(request_id,review_id,operator_id,decision,reason) VALUES(?,?,?,?,?)`, c.RequestID, id, c.OperatorID, c.Decision, c.Reason); err != nil {
		financeReply(w, nil, err)
		return
	}
	if _, err = tx.ExecContext(r.Context(), `UPDATE review_requests SET status=? WHERE review_id=?`, c.Decision, id); err != nil {
		financeReply(w, nil, err)
		return
	}
	record.Status = c.Decision
	record.Decision = c.Decision
	record.DecisionReason = c.Reason
	financeReply(w, record, tx.Commit())
}
