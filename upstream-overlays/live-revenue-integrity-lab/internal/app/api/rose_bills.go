package apiserver

import (
	"net/http"
	"strconv"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type roseBill struct {
	GiftOrderID   uint64    `json:"gift_order_id,string"`
	ViewerID      uint64    `json:"viewer_id,string"`
	LiveSessionID uint64    `json:"live_session_id,string"`
	GiftID        string    `json:"gift_id"`
	GiftName      string    `json:"gift_name"`
	Quantity      uint16    `json:"quantity"`
	DiamondReward uint32    `json:"diamond_reward"`
	Status        string    `json:"status"`
	CreatedAt     time.Time `json:"created_at"`
}

// handleRoseBills returns confirmed gift income for the authenticated creator.
// It reads the gift ledger only; no balance or settlement is changed here.
func (s *Server) handleRoseBills(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	before := uint64(0)
	if raw, ok := r.URL.Query()["before_id"]; ok {
		if len(raw) != 1 {
			httpx.WriteError(w, http.StatusBadRequest, "INVALID_CURSOR", "one positive cursor required")
			return
		}
		var err error
		before, err = strconv.ParseUint(raw[0], 10, 64)
		if err != nil || before == 0 {
			httpx.WriteError(w, http.StatusBadRequest, "INVALID_CURSOR", "positive cursor required")
			return
		}
	}

	query := `SELECT o.gift_order_id,o.viewer_id,o.live_session_id,o.gift_id,
		COALESCE(g.display_name,o.gift_id),o.quantity,o.diamond_reward,o.status,o.created_at
		FROM gift_orders o LEFT JOIN gift_catalog g ON g.gift_id=o.gift_id
		WHERE o.creator_id=? AND o.status='ACCEPTED' AND o.diamond_reward>0`
	args := []any{authenticatedUserID(r)}
	if before > 0 {
		query += ` AND o.gift_order_id<?`
		args = append(args, before)
	}
	query += ` ORDER BY o.gift_order_id DESC LIMIT 26`

	rows, err := s.db.QueryContext(r.Context(), query, args...)
	if err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "rose bills unavailable")
		return
	}
	defer rows.Close()
	bills := make([]roseBill, 0, 26)
	for rows.Next() {
		var bill roseBill
		if err := rows.Scan(&bill.GiftOrderID, &bill.ViewerID, &bill.LiveSessionID, &bill.GiftID,
			&bill.GiftName, &bill.Quantity, &bill.DiamondReward, &bill.Status, &bill.CreatedAt); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "rose bills unavailable")
			return
		}
		bills = append(bills, bill)
	}
	if err := rows.Err(); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "rose bills unavailable")
		return
	}
	next := ""
	if len(bills) > 25 {
		bills = bills[:25]
		next = strconv.FormatUint(bills[24].GiftOrderID, 10)
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"bills": bills, "next_before_id": next})
}
