package apiserver

import (
	"context"
	"fmt"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"unicode/utf8"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

var manualRechargeID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)

type manualRechargeCommand struct {
	RequestID  string `json:"request_id"`
	UserID     uint64 `json:"user_id"`
	OperatorID uint64 `json:"operator_id"`
	Coins      uint32 `json:"coins"`
	Reason     string `json:"reason"`
}

func (c manualRechargeCommand) paymentRef() (string, bool) {
	reason := strings.TrimSpace(c.Reason)
	if !manualRechargeID.MatchString(c.RequestID) || c.UserID == 0 || c.OperatorID == 0 || c.Coins < 1 || c.Coins > 100000 ||
		utf8.RuneCountInString(reason) < 1 || utf8.RuneCountInString(reason) > 40 {
		return "", false
	}
	ref := fmt.Sprintf("manual:%d:%d:%s:%s", c.UserID, c.OperatorID, c.RequestID, reason)
	return ref, utf8.RuneCountInString(ref) <= 128
}

type rechargeRecord struct {
	TxID         uint64 `json:"tx_id,string"`
	RequestID    string `json:"request_id"`
	UserID       uint64 `json:"user_id,string"`
	Coins        uint32 `json:"coins"`
	BalanceAfter int64  `json:"balance_after"`
	CreatedAt    string `json:"created_at"`
	Source       string `json:"source"`
	OperatorID   string `json:"operator_id,omitempty"`
	Reason       string `json:"reason,omitempty"`
}

func rechargeCursor(r *http.Request) (uint64, bool) {
	raw := r.URL.Query().Get("before_id")
	if raw == "" {
		return ^uint64(0), true
	}
	id, err := strconv.ParseUint(raw, 10, 64)
	return id, err == nil && id > 0
}

func (s *Server) listRecharges(ctx context.Context, userID uint64, manualOnly bool, before uint64) ([]rechargeRecord, string, error) {
	where := "tx_id < ?"
	args := []any{before}
	if userID > 0 {
		where += " AND viewer_id = ?"
		args = append(args, userID)
	}
	if manualOnly {
		where += " AND payment_ref LIKE 'manual:%'"
	}
	rows, err := s.db.QueryContext(ctx, `SELECT tx_id, request_id, viewer_id, coins, resulting_balance, payment_ref, CAST(created_at AS CHAR)
		FROM recharge_requests WHERE `+where+` ORDER BY tx_id DESC LIMIT 26`, args...)
	if err != nil {
		return nil, "", err
	}
	defer rows.Close()
	records := make([]rechargeRecord, 0, 26)
	for rows.Next() {
		var record rechargeRecord
		var ref string
		if err := rows.Scan(&record.TxID, &record.RequestID, &record.UserID, &record.Coins, &record.BalanceAfter, &ref, &record.CreatedAt); err != nil {
			return nil, "", err
		}
		record.Source = "ONLINE"
		if strings.HasPrefix(ref, "manual:") {
			record.Source = "MANUAL"
			if manualOnly {
				parts := strings.SplitN(ref, ":", 5)
				if len(parts) == 5 {
					record.OperatorID = parts[2]
					record.Reason = parts[4]
				}
			}
		}
		records = append(records, record)
	}
	if err := rows.Err(); err != nil {
		return nil, "", err
	}
	next := ""
	if len(records) > 25 {
		records = records[:25]
		next = strconv.FormatUint(records[len(records)-1].TxID, 10)
	}
	return records, next, nil
}

func (s *Server) handleMyRecharges(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	before, ok := rechargeCursor(r)
	if !ok {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_CURSOR", "分页无效")
		return
	}
	records, next, err := s.listRecharges(r.Context(), authenticatedUserID(r), false, before)
	financeReply(w, map[string]any{"records": records, "next_before_id": next}, err)
}

func (s *Server) handleManualRecharges(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		before, ok := rechargeCursor(r)
		if !ok {
			httpx.WriteError(w, http.StatusBadRequest, "INVALID_CURSOR", "分页无效")
			return
		}
		records, next, err := s.listRecharges(r.Context(), 0, true, before)
		financeReply(w, map[string]any{"records": records, "next_before_id": next}, err)
		return
	}
	var command manualRechargeCommand
	if !financeBody(w, r, &command) {
		return
	}
	ref, ok := command.paymentRef()
	if !ok {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_OPERATION", "请填写有效的用户、金币、单号和 1 至 40 字原因")
		return
	}
	if err := s.ensureUser(r.Context(), command.UserID); err != nil {
		financeReply(w, nil, err)
		return
	}
	result, appErr, err := s.svc.Recharge(r.Context(), service.RechargeRequest{
		RequestID: command.RequestID, ViewerID: command.UserID, Coins: command.Coins, PaymentRef: ref,
	})
	if appErr != nil {
		financeReply(w, nil, appErr)
		return
	}
	financeReply(w, result, err)
}
