package apiserver

import (
	"encoding/json"
	"net/http"
	"reflect"
	"regexp"
	"strings"
	"unicode/utf8"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type catalogCommand struct {
	RequestID     string `json:"request_id"`
	OperatorID    uint64 `json:"operator_id"`
	GiftID        string `json:"gift_id"`
	Name          string `json:"display_name"`
	CoinPrice     uint32 `json:"coin_price"`
	MatchPoints   uint32 `json:"match_points"`
	DiamondReward uint32 `json:"diamond_reward"`
	Enabled       bool   `json:"enabled"`
	Reason        string `json:"reason"`
}

func (s *Server) handleAdminCatalog(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		rows, err := s.db.QueryContext(r.Context(), `SELECT gift_id,display_name,coin_price,match_points,diamond_reward,enabled FROM gift_catalog ORDER BY coin_price,gift_id`)
		if err != nil {
			financeReply(w, nil, err)
			return
		}
		defer rows.Close()
		items := []map[string]any{}
		for rows.Next() {
			var c catalogCommand
			if err = rows.Scan(&c.GiftID, &c.Name, &c.CoinPrice, &c.MatchPoints, &c.DiamondReward, &c.Enabled); err != nil {
				financeReply(w, nil, err)
				return
			}
			items = append(items, map[string]any{"gift_id": c.GiftID, "display_name": c.Name, "coin_price": c.CoinPrice, "match_points": c.MatchPoints, "diamond_reward": c.DiamondReward, "enabled": c.Enabled})
		}
		financeReply(w, map[string]any{"gifts": items}, rows.Err())
		return
	}
	if r.Method != http.MethodPut {
		httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "GET or PUT required")
		return
	}
	var c catalogCommand
	if !financeBody(w, r, &c) {
		return
	}
	if !operationRequestPattern.MatchString(c.RequestID) || c.OperatorID == 0 || !regexp.MustCompile(`^[a-zA-Z0-9_-]{1,32}$`).MatchString(c.GiftID) || strings.TrimSpace(c.Name) == "" || utf8.RuneCountInString(c.Name) > 64 || c.CoinPrice < 1 || c.CoinPrice > 1000000 || c.MatchPoints > 1000000 || c.DiamondReward > 1000000 || strings.TrimSpace(c.Reason) == "" || utf8.RuneCountInString(c.Reason) > 200 {
		httpx.WriteError(w, 400, "INVALID_GIFT", "请填写有效的礼物标识、数值、名称和原因")
		return
	}
	tx, err := s.db.BeginTx(r.Context(), nil)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer tx.Rollback()
	raw, _ := json.Marshal(c)
	// Claim request ID first; conflicts are compared after the unique row lock is acquired.
	if _, err = tx.ExecContext(r.Context(), `INSERT INTO gift_catalog_actions(request_id,operator_id,gift_id,command_json,reason) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE request_id=VALUES(request_id)`, c.RequestID, c.OperatorID, c.GiftID, string(raw), c.Reason); err != nil {
		financeReply(w, nil, err)
		return
	}
	var saved string
	var applied bool
	if err = tx.QueryRowContext(r.Context(), `SELECT command_json,applied FROM gift_catalog_actions WHERE request_id=? FOR UPDATE`, c.RequestID).Scan(&saved, &applied); err != nil {
		financeReply(w, nil, err)
		return
	}
	var prior catalogCommand
	if json.Unmarshal([]byte(saved), &prior) != nil || !reflect.DeepEqual(c, prior) {
		httpx.WriteError(w, 409, "OPERATION_CONFLICT", "单号已用于其他礼物修改")
		return
	}
	// An old retry returns its receipt without reapplying stale prices.
	if !applied {
		if _, err = tx.ExecContext(r.Context(), `INSERT INTO gift_catalog(gift_id,display_name,coin_price,match_points,diamond_reward,enabled) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),coin_price=VALUES(coin_price),match_points=VALUES(match_points),diamond_reward=VALUES(diamond_reward),enabled=VALUES(enabled)`, c.GiftID, c.Name, c.CoinPrice, c.MatchPoints, c.DiamondReward, c.Enabled); err != nil {
			financeReply(w, nil, err)
			return
		}
		if _, err = tx.ExecContext(r.Context(), `UPDATE gift_catalog_actions SET applied=TRUE WHERE request_id=?`, c.RequestID); err != nil {
			financeReply(w, nil, err)
			return
		}
	}
	if err = tx.Commit(); err != nil {
		financeReply(w, nil, err)
		return
	}
	financeReply(w, map[string]any{"request_id": c.RequestID, "gift_id": c.GiftID}, nil)
}
