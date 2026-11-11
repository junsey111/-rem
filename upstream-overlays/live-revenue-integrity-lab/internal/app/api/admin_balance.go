package apiserver

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func (s *Server) handleAdminBalance(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodPost {
		var c service.AdminBalanceCommand
		if !financeBody(w, r, &c) {
			return
		}
		if err := s.ensureUser(r.Context(), c.UserID); err != nil {
			financeReply(w, nil, err)
			return
		}
		result, err := s.svc.SetAdminBalance(r.Context(), c)
		financeReply(w, result, err)
		return
	}
	id, err := strconv.ParseUint(r.URL.Query().Get("user_id"), 10, 64)
	if err != nil || id == 0 {
		httpx.WriteError(w, 400, "INVALID_USER", "用户编号无效")
		return
	}
	rows, err := s.db.QueryContext(r.Context(), `SELECT id,operator_id,request_id,old_balance,new_balance,reason,CAST(created_at AS CHAR)
		FROM admin_balance_adjustments WHERE user_id=? ORDER BY id DESC LIMIT 50`, id)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer rows.Close()
	items := []map[string]any{}
	for rows.Next() {
		var itemID, operatorID uint64
		var requestID, reason, created string
		var oldBalance, newBalance int64
		if err := rows.Scan(&itemID, &operatorID, &requestID, &oldBalance, &newBalance, &reason, &created); err != nil {
			financeReply(w, nil, err)
			return
		}
		items = append(items, map[string]any{"id": strconv.FormatUint(itemID, 10), "operator_id": strconv.FormatUint(operatorID, 10),
			"request_id": requestID, "old_balance": oldBalance, "new_balance": newBalance,
			"reason": strings.TrimSpace(reason), "created_at": created})
	}
	if err := rows.Err(); err != nil {
		financeReply(w, nil, err)
		return
	}
	financeReply(w, map[string]any{"adjustments": items}, nil)
}
