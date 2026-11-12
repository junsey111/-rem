package apiserver

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strconv"
	"strings"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func financeBody(w http.ResponseWriter, r *http.Request, v any) bool {
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 16384))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(v); err != nil {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "请求格式无效")
		return false
	}
	if decoder.Decode(new(any)) != io.EOF {
		httpx.WriteError(w, 400, "INVALID_OPERATION", "请求只能包含一个 JSON 对象")
		return false
	}
	return true
}
func financeReply(w http.ResponseWriter, data any, err error) {
	if err == nil {
		httpx.WriteJSON(w, 200, data)
		return
	}
	var app *service.AppError
	if errors.As(err, &app) {
		httpx.WriteError(w, app.HTTPStatus, app.Code, app.Message)
		return
	}
	httpx.WriteError(w, 503, "FINANCE_UNAVAILABLE", "资金操作尚未确认，请用原单号查询或重试")
}
func (s *Server) handleFinanceAdmin(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/v1/internal/finance/")
	switch {
	case (strings.HasPrefix(path, "withdrawals/") || strings.HasPrefix(path, "refunds/")) && r.Method == http.MethodGet:
		parts := strings.Split(path, "/")
		if len(parts) != 2 {
			httpx.WriteError(w, 404, "NOT_FOUND", "route not found")
			return
		}
		id, err := strconv.ParseUint(parts[1], 10, 64)
		if err != nil || id == 0 {
			httpx.WriteError(w, 400, "INVALID_OPERATION", "单号无效")
			return
		}
		if parts[0] == "withdrawals" {
			result, e := s.svc.GetWithdrawal(r.Context(), id)
			financeReply(w, result, e)
		} else {
			result, e := s.svc.GetRefund(r.Context(), id)
			financeReply(w, result, e)
		}
	case path == "settings" && r.Method == http.MethodGet:
		result, err := s.svc.GetFinanceSettings(r.Context())
		financeReply(w, result, err)
	case path == "settings" && r.Method == http.MethodPut:
		var c service.SettingsCommand
		if !financeBody(w, r, &c) {
			return
		}
		result, err := s.svc.UpdateFinanceSettings(r.Context(), c)
		financeReply(w, result, err)
	case path == "audit" && r.Method == http.MethodGet:
		s.writeFinanceAudit(w, r)
	case path == "manual-recharges" && (r.Method == http.MethodGet || r.Method == http.MethodPost):
		s.handleManualRecharges(w, r)
	case path == "recharges" && r.Method == http.MethodGet:
		userID := uint64(0)
		if raw := r.URL.Query().Get("user_id"); raw != "" {
			id, err := strconv.ParseUint(raw, 10, 64)
			if err != nil || id == 0 {
				httpx.WriteError(w, 400, "INVALID_USER", "用户编号无效")
				return
			}
			userID = id
		}
		before, ok := rechargeCursor(r)
		if !ok {
			httpx.WriteError(w, 400, "INVALID_CURSOR", "分页无效")
			return
		}
		records, next, err := s.listRecharges(r.Context(), userID, false, before)
		financeReply(w, map[string]any{"records": records, "next_before_id": next}, err)
	case path == "balance-adjustments" && (r.Method == http.MethodGet || r.Method == http.MethodPost):
		s.handleAdminBalance(w, r)
	case path == "withdrawals" && r.Method == http.MethodGet:
		s.writeWithdrawals(w, r, 0)
	case path == "refunds" && r.Method == http.MethodGet:
		s.writeRefunds(w, r, 0)
	case path == "refunds" && r.Method == http.MethodPost:
		var c service.RefundRequest
		if !financeBody(w, r, &c) {
			return
		}
		result, err := s.svc.CreateRefund(r.Context(), c)
		financeReply(w, result, err)
	case strings.HasPrefix(path, "refunds/") && r.Method == http.MethodPost:
		id, err := strconv.ParseUint(strings.TrimPrefix(path, "refunds/"), 10, 64)
		if err != nil || id == 0 {
			httpx.WriteError(w, 400, "INVALID_OPERATION", "退款单号无效")
			return
		}
		var c service.WithdrawalAction
		if !financeBody(w, r, &c) {
			return
		}
		result, err := s.svc.ReviewRefund(r.Context(), id, c)
		financeReply(w, result, err)
	case strings.HasPrefix(path, "withdrawals/") && r.Method == http.MethodPost:
		id, err := strconv.ParseUint(strings.TrimPrefix(path, "withdrawals/"), 10, 64)
		if err != nil || id == 0 {
			httpx.WriteError(w, 400, "INVALID_OPERATION", "提现单号无效")
			return
		}
		var c service.WithdrawalAction
		if !financeBody(w, r, &c) {
			return
		}
		result, err := s.svc.ReviewWithdrawal(r.Context(), id, c)
		financeReply(w, result, err)
	default:
		httpx.WriteError(w, 404, "NOT_FOUND", "route not found")
	}
}

func (s *Server) writeRefunds(w http.ResponseWriter, r *http.Request, user uint64) {
	before, ok := operationCursor(r)
	if !ok {
		httpx.WriteError(w, 400, "INVALID_CURSOR", "分页无效")
		return
	}
	items, err := s.svc.ListRefunds(r.Context(), user, before)
	next := ""
	if len(items) > 25 {
		items = items[:25]
		next = strconv.FormatUint(items[24].ID, 10)
	}
	financeReply(w, map[string]any{"refunds": items, "next_before_id": next}, err)
}
func (s *Server) handleMyRefunds(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	s.writeRefunds(w, r, authenticatedUserID(r))
}
func (s *Server) writeWithdrawals(w http.ResponseWriter, r *http.Request, user uint64) {
	before, ok := operationCursor(r)
	if !ok {
		httpx.WriteError(w, 400, "INVALID_CURSOR", "分页无效")
		return
	}
	items, err := s.svc.ListWithdrawals(r.Context(), user, before)
	next := ""
	if len(items) > 25 {
		items = items[:25]
		next = strconv.FormatUint(items[24].ID, 10)
	}
	financeReply(w, map[string]any{"withdrawals": items, "next_before_id": next}, err)
}
func (s *Server) handleWithdrawals(w http.ResponseWriter, r *http.Request) {
	user := authenticatedUserID(r)
	if err := s.ensureUser(r.Context(), user); err != nil {
		financeReply(w, nil, err)
		return
	}
	switch r.Method {
	case http.MethodGet:
		s.writeWithdrawals(w, r, user)
	case http.MethodPost:
		var c service.WithdrawalRequest
		if !financeBody(w, r, &c) {
			return
		}
		result, err := s.svc.CreateWithdrawal(r.Context(), user, c)
		financeReply(w, result, err)
	default:
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET or POST required")
	}
}

func (s *Server) handleFinanceRules(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	result, err := s.svc.GetFinanceSettings(r.Context())
	financeReply(w, result, err)
}
func (s *Server) handleWithdrawable(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	result, err := s.svc.Withdrawable(r.Context(), authenticatedUserID(r))
	financeReply(w, result, err)
}
func (s *Server) writeFinanceAudit(w http.ResponseWriter, r *http.Request) {
	page := 1
	if raw := r.URL.Query().Get("page"); raw != "" {
		n, err := strconv.Atoi(raw)
		if err != nil || n < 1 || n > 1000000 {
			httpx.WriteError(w, 400, "INVALID_CURSOR", "页码无效")
			return
		}
		page = n
	}
	rows, err := s.db.QueryContext(r.Context(), `SELECT kind,request_id,operator_id,subject_id,action,reason,payment_ref,created_at FROM (
 SELECT 'SETTINGS' AS kind,request_id,operator_id,CAST(version AS CHAR) AS subject_id,'UPDATE' AS action,reason,'' AS payment_ref,CAST(created_at AS CHAR) AS created_at FROM finance_settings_history
 UNION ALL SELECT 'WITHDRAWAL',request_id,operator_id,CAST(withdrawal_id AS CHAR),action,reason,payment_ref,CAST(created_at AS CHAR) FROM withdrawal_actions
 UNION ALL SELECT 'REFUND',request_id,operator_id,CAST(refund_id AS CHAR),action,reason,payment_ref,CAST(created_at AS CHAR) FROM refund_actions
 ) a ORDER BY created_at DESC,request_id DESC LIMIT 26 OFFSET ?`, (page-1)*25)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer rows.Close()
	items := []map[string]any{}
	for rows.Next() {
		var kind, request, subject, action, reason, payment, created string
		var operator uint64
		if err = rows.Scan(&kind, &request, &operator, &subject, &action, &reason, &payment, &created); err != nil {
			financeReply(w, nil, err)
			return
		}
		items = append(items, map[string]any{"kind": kind, "request_id": request, "operator_id": strconv.FormatUint(operator, 10), "subject_id": subject, "action": action, "reason": reason, "payment_ref": payment, "created_at": created})
	}
	more := len(items) > 25
	if more {
		items = items[:25]
	}
	financeReply(w, map[string]any{"audit": items, "page": page, "has_more": more}, rows.Err())
}
