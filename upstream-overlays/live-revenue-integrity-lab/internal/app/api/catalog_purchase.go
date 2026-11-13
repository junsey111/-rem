package apiserver

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

func catalogReply(w http.ResponseWriter, status int, result service.CatalogPurchaseResponse, appErr *service.AppError, err error) {
	if err != nil { httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "catalog purchase unavailable"); return }
	if appErr != nil { httpx.WriteError(w, appErr.HTTPStatus, appErr.Code, appErr.Message); return }
	httpx.WriteJSON(w, status, result)
}

func (s *Server) handleInternalCatalogPurchase(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost { httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed"); return }
	defer r.Body.Close()
	var req service.CatalogPurchaseRequest
	if json.NewDecoder(http.MaxBytesReader(w, r.Body, 16*1024)).Decode(&req) != nil { httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid catalog purchase payload"); return }
	if err := s.ensureUser(r.Context(), req.UserID); err != nil { httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "account unavailable"); return }
	result, appErr, err := s.svc.PurchaseCatalog(r.Context(), req)
	status := http.StatusCreated
	if result.Idempotency == "replayed" { status = http.StatusOK }
	catalogReply(w, status, result, appErr, err)
}

func (s *Server) handleInternalCatalogReverse(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost { httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "only POST is allowed"); return }
	defer r.Body.Close()
	requestID := strings.TrimPrefix(r.URL.Path, "/v1/internal/catalog/purchases/")
	var body struct { UserID uint64 `json:"user_id"`; Reason string `json:"reason"` }
	if json.NewDecoder(http.MaxBytesReader(w, r.Body, 8*1024)).Decode(&body) != nil { httpx.WriteError(w, http.StatusBadRequest, "BAD_JSON", "invalid catalog reversal payload"); return }
	result, appErr, err := s.svc.ReverseCatalogPurchase(r.Context(), service.CatalogPurchaseReverseRequest{RequestID: requestID, UserID: body.UserID, Reason: body.Reason})
	status := http.StatusOK
	if result.Idempotency == "replayed" { status = http.StatusOK }
	catalogReply(w, status, result, appErr, err)
}
