package apiserver

import (
	"database/sql"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type giftReceipt struct {
	ID            uint64    `json:"gift_order_id,string"`
	RequestID     string    `json:"request_id"`
	CreatorID     uint64    `json:"creator_id,string"`
	LiveSessionID uint64    `json:"live_session_id,string"`
	MatchID       uint64    `json:"match_id,string"`
	GiftID        string    `json:"gift_id"`
	GiftName      string    `json:"gift_name"`
	Quantity      uint16    `json:"quantity"`
	ChargedCoins  uint32    `json:"charged_coins"`
	Status        string    `json:"status"`
	RejectCode    string    `json:"reject_code"`
	CreatedAt     time.Time `json:"created_at"`
}

const giftReceiptColumns = `SELECT o.gift_order_id,o.request_id,o.creator_id,o.live_session_id,COALESCE(o.match_id,0),o.gift_id,COALESCE(g.display_name,o.gift_id),o.quantity,o.charged_coins,o.status,COALESCE(o.reject_code,''),o.created_at FROM gift_orders o LEFT JOIN gift_catalog g ON g.gift_id=o.gift_id `

type receiptScanner interface{ Scan(...any) error }

func scanGiftReceipt(row receiptScanner) (giftReceipt, error) {
	var record giftReceipt
	err := row.Scan(&record.ID, &record.RequestID, &record.CreatorID, &record.LiveSessionID, &record.MatchID, &record.GiftID, &record.GiftName, &record.Quantity, &record.ChargedCoins, &record.Status, &record.RejectCode, &record.CreatedAt)
	return record, err
}

func (s *Server) handleGiftHistory(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	before := uint64(0)
	if raw, ok := r.URL.Query()["before_id"]; ok {
		if len(raw) != 1 {
			httpx.WriteError(w, 400, "INVALID_CURSOR", "one positive cursor required")
			return
		}
		var err error
		before, err = strconv.ParseUint(raw[0], 10, 64)
		if err != nil || before == 0 {
			httpx.WriteError(w, 400, "INVALID_CURSOR", "positive cursor required")
			return
		}
	}
	query := giftReceiptColumns + `WHERE o.viewer_id=?`
	args := []any{authenticatedUserID(r)}
	if before > 0 {
		query += ` AND o.gift_order_id<?`
		args = append(args, before)
	}
	rows, err := s.db.QueryContext(r.Context(), query+` ORDER BY o.gift_order_id DESC LIMIT 26`, args...)
	if err != nil {
		httpx.WriteError(w, 500, "INTERNAL_ERROR", "gift records unavailable")
		return
	}
	defer rows.Close()
	records := make([]giftReceipt, 0, 26)
	for rows.Next() {
		record, err := scanGiftReceipt(rows)
		if err != nil {
			httpx.WriteError(w, 500, "INTERNAL_ERROR", "gift records unavailable")
			return
		}
		records = append(records, record)
	}
	if rows.Err() != nil {
		httpx.WriteError(w, 500, "INTERNAL_ERROR", "gift records unavailable")
		return
	}
	next := ""
	if len(records) > 25 {
		records = records[:25]
		next = strconv.FormatUint(records[24].ID, 10)
	}
	httpx.WriteJSON(w, 200, map[string]any{"orders": records, "next_before_id": next})
}

var giftRequestIDPattern = regexp.MustCompile(`^[A-Za-z0-9._:-]{1,64}$`)

func (s *Server) handleGiftReceipt(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET required")
		return
	}
	requestID := strings.TrimPrefix(r.URL.Path, "/v1/users/me/gifts/")
	if !giftRequestIDPattern.MatchString(requestID) {
		httpx.WriteError(w, 400, "INVALID_REQUEST_ID", "invalid request id")
		return
	}
	record, err := scanGiftReceipt(s.db.QueryRowContext(r.Context(), giftReceiptColumns+`WHERE o.viewer_id=? AND o.request_id=?`, authenticatedUserID(r), requestID))
	if err == sql.ErrNoRows {
		httpx.WriteError(w, 404, "GIFT_RECORD_NOT_FOUND", "no confirmed record for this account")
		return
	}
	if err != nil {
		httpx.WriteError(w, 500, "INTERNAL_ERROR", "gift record unavailable")
		return
	}
	httpx.WriteJSON(w, 200, record)
}
