package apiserver

import (
    "encoding/json"
    "net/http"
    "strconv"
    "strings"

    "github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

func (s *Server) handleDmCallPrice(w http.ResponseWriter, r *http.Request) {
    owner := authenticatedUserID(r)
    switch r.Method {
    case http.MethodGet:
        price, err := s.svc.DmCallPrice(r.Context(), owner)
        if err != nil { httpx.WriteError(w, 503, "PRICE_UNAVAILABLE", "视频通话价格暂时无法读取"); return }
        httpx.WriteJSON(w, 200, map[string]any{"price_coins": price})
    case http.MethodPut:
        var body struct { Price *int `json:"price_coins"` }
        if json.NewDecoder(r.Body).Decode(&body) != nil || body.Price == nil { httpx.WriteError(w, 400, "INVALID_PRICE", "请填写视频通话价格"); return }
        if err := s.ensureUser(r.Context(), owner); err != nil { httpx.WriteError(w, 503, "PRICE_UNAVAILABLE", "账号暂时无法同步"); return }
        if appErr := s.svc.SetDmCallPrice(r.Context(), owner, *body.Price); appErr != nil {
            httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message); return
        }
        httpx.WriteJSON(w, 200, map[string]any{"price_coins": *body.Price})
    default:
        httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "only GET or PUT is allowed")
    }
}

func (s *Server) handleDmCallPriceByUser(w http.ResponseWriter, r *http.Request) {
    if r.Method != http.MethodGet { httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "only GET is allowed"); return }
    id := strings.TrimPrefix(r.URL.Path, "/v1/dm/call-prices/")
    owner, err := strconv.ParseUint(id, 10, 64)
    if err != nil || owner == 0 || len(id) > 10 { httpx.WriteError(w, 400, "INVALID_USER", "用户 ID 无效"); return }
    price, err := s.svc.DmCallPrice(r.Context(), owner)
    if err != nil { httpx.WriteError(w, 503, "PRICE_UNAVAILABLE", "视频通话价格暂时无法读取"); return }
    httpx.WriteJSON(w, 200, map[string]any{"price_coins": price})
}

func (s *Server) handleInternalDmCallSettle(w http.ResponseWriter, r *http.Request) {
    if r.Method != http.MethodPost { httpx.WriteError(w, 405, "METHOD_NOT_ALLOWED", "only POST is allowed"); return }
    var body struct {
        CallID string `json:"call_id"`
        PayerID uint64 `json:"payer_id"`
        OwnerID uint64 `json:"owner_id"`
        PriceCoins int `json:"price_coins"`
    }
    if json.NewDecoder(r.Body).Decode(&body) != nil { httpx.WriteError(w, 400, "BAD_JSON", "请求内容无效"); return }
    price, appErr, err := s.svc.SettleDmCall(r.Context(), body.CallID, body.PayerID, body.OwnerID, body.PriceCoins)
    if appErr != nil { httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message); return }
    if err != nil { httpx.WriteError(w, 503, "SETTLEMENT_UNAVAILABLE", "通话结算暂时不可用"); return }
    httpx.WriteJSON(w, 200, map[string]any{"call_id": body.CallID, "charged_coins": price})
}
