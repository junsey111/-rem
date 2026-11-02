package apiserver

import (
	"encoding/json"
	"net/http"
	"net/url"
	"reflect"
	"regexp"
	"strings"
	"unicode/utf8"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

type catalogCommand struct {
	RequestID       string `json:"request_id"`
	OperatorID      uint64 `json:"operator_id"`
	GiftID          string `json:"gift_id"`
	Name            string `json:"display_name"`
	CoinPrice       uint32 `json:"coin_price"`
	MatchPoints     uint32 `json:"match_points"`
	DiamondReward   uint32 `json:"diamond_reward"`
	Enabled         bool   `json:"enabled"`
	EffectType      string `json:"effect_type,omitempty"`
	EffectURL       string `json:"effect_url,omitempty"`
	EffectVideoMode string `json:"effect_video_mode,omitempty"`
	EffectMD5       string `json:"effect_md5,omitempty"`
	IconURL         string `json:"icon_url,omitempty"`
	Reason          string `json:"reason"`
}

func validCatalogEffect(c *catalogCommand) bool {
	if len(c.EffectURL) > 512 || len(c.IconURL) > 512 || len(c.EffectVideoMode) > 64 || len(c.EffectMD5) > 64 {
		return false
	}
	switch c.EffectType {
	case "":
		return c.EffectURL == "" && c.EffectVideoMode == "" && c.EffectMD5 == "" && c.IconURL == ""
	case "none":
		c.EffectURL = ""
		c.EffectVideoMode = ""
		c.EffectMD5 = ""
		return true
	case "yyeva":
		return yyevaMP4(c.EffectURL)
	case "svga", "lottie":
		return strings.TrimSpace(c.EffectURL) != ""
	default:
		return false
	}
}

func yyevaMP4(raw string) bool {
	raw = strings.TrimSpace(raw)
	if raw == "" || len(raw) > 512 {
		return false
	}
	path := raw
	if parsed, err := url.Parse(raw); err == nil && parsed.Path != "" {
		path = parsed.Path
	}
	if i := strings.IndexAny(path, "?#"); i >= 0 {
		path = path[:i]
	}
	return strings.HasSuffix(strings.ToLower(path), ".mp4")
}

func (s *Server) handleAdminCatalog(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		rows, err := s.db.QueryContext(r.Context(), `SELECT gift_id,display_name,coin_price,match_points,diamond_reward,enabled,effect_type,effect_url,effect_video_mode,effect_md5,icon_url FROM gift_catalog ORDER BY coin_price,gift_id`)
		if err != nil {
			financeReply(w, nil, err)
			return
		}
		defer rows.Close()
		items := []map[string]any{}
		for rows.Next() {
			var c catalogCommand
			if err = rows.Scan(&c.GiftID, &c.Name, &c.CoinPrice, &c.MatchPoints, &c.DiamondReward, &c.Enabled, &c.EffectType, &c.EffectURL, &c.EffectVideoMode, &c.EffectMD5, &c.IconURL); err != nil {
				financeReply(w, nil, err)
				return
			}
			items = append(items, catalogGiftJSON(c))
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
	if !operationRequestPattern.MatchString(c.RequestID) || c.OperatorID == 0 || !regexp.MustCompile(`^[a-zA-Z0-9_-]{1,32}$`).MatchString(c.GiftID) || strings.TrimSpace(c.Name) == "" || utf8.RuneCountInString(c.Name) > 64 || c.CoinPrice < 1 || c.CoinPrice > 1000000 || c.MatchPoints > 1000000 || c.DiamondReward > 1000000 || strings.TrimSpace(c.Reason) == "" || utf8.RuneCountInString(c.Reason) > 200 || !validCatalogEffect(&c) {
		httpx.WriteError(w, 400, "INVALID_GIFT", "请填写有效的礼物标识、价格和特效。全屏大礼物 effect_type 使用 yyeva，effect_url 须为 AE YYEVA 插件导出的 mp4")
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
		if c.EffectType == "" {
			_, err = tx.ExecContext(r.Context(), `INSERT INTO gift_catalog(gift_id,display_name,coin_price,match_points,diamond_reward,enabled) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),coin_price=VALUES(coin_price),match_points=VALUES(match_points),diamond_reward=VALUES(diamond_reward),enabled=VALUES(enabled)`, c.GiftID, c.Name, c.CoinPrice, c.MatchPoints, c.DiamondReward, c.Enabled)
		} else {
			_, err = tx.ExecContext(r.Context(), `INSERT INTO gift_catalog(gift_id,display_name,coin_price,match_points,diamond_reward,enabled,effect_type,effect_url,effect_video_mode,effect_md5,icon_url) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),coin_price=VALUES(coin_price),match_points=VALUES(match_points),diamond_reward=VALUES(diamond_reward),enabled=VALUES(enabled),effect_type=VALUES(effect_type),effect_url=VALUES(effect_url),effect_video_mode=VALUES(effect_video_mode),effect_md5=VALUES(effect_md5),icon_url=VALUES(icon_url)`, c.GiftID, c.Name, c.CoinPrice, c.MatchPoints, c.DiamondReward, c.Enabled, c.EffectType, c.EffectURL, c.EffectVideoMode, c.EffectMD5, c.IconURL)
		}
		if err != nil {
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

func catalogGiftJSON(c catalogCommand) map[string]any {
	if c.EffectType == "" {
		c.EffectType = "none"
	}
	return map[string]any{
		"gift_id":           c.GiftID,
		"display_name":      c.Name,
		"coin_price":        c.CoinPrice,
		"match_points":      c.MatchPoints,
		"diamond_reward":    c.DiamondReward,
		"enabled":           c.Enabled,
		"effect_type":       c.EffectType,
		"effect_url":        c.EffectURL,
		"effect_video_mode": c.EffectVideoMode,
		"effect_md5":        c.EffectMD5,
		"icon_url":          c.IconURL,
	}
}
