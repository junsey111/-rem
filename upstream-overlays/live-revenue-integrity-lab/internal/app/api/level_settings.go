package apiserver

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"unicode/utf8"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
	"github.com/mini_station/live-revenue-integrity-lab/internal/service"
)

type levelSettings struct {
	Version           uint64  `json:"version"`
	Configured        bool    `json:"configured"`
	ViewerThresholds  []int64 `json:"viewer_thresholds"`
	CreatorThresholds []int64 `json:"creator_thresholds"`
}

func validLevelThresholds(values []int64) bool {
	if len(values) != 5 {
		return false
	}
	previous := int64(0)
	for _, value := range values {
		if value <= previous || value > 1_000_000_000_000 {
			return false
		}
		previous = value
	}
	return true
}

func (s *Server) readLevelSettings(ctx context.Context) (levelSettings, error) {
	var settings levelSettings
	var viewer, creator []byte
	err := s.db.QueryRowContext(ctx, `SELECT version,configured,viewer_thresholds,creator_thresholds FROM level_settings WHERE singleton=1`).
		Scan(&settings.Version, &settings.Configured, &viewer, &creator)
	if err != nil {
		return settings, err
	}
	if json.Unmarshal(viewer, &settings.ViewerThresholds) != nil ||
		json.Unmarshal(creator, &settings.CreatorThresholds) != nil ||
		!validLevelThresholds(settings.ViewerThresholds) || !validLevelThresholds(settings.CreatorThresholds) {
		return settings, fmt.Errorf("invalid level settings in database")
	}
	return settings, nil
}

func (s *Server) handleLevelSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		settings, err := s.readLevelSettings(r.Context())
		financeReply(w, settings, err)
		return
	}
	if r.Method != http.MethodPut {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET/PUT required")
		return
	}
	var command struct {
		RequestID         string  `json:"request_id"`
		OperatorID        uint64  `json:"operator_id"`
		ExpectedVersion   *uint64 `json:"expected_version"`
		Reason            string  `json:"reason"`
		ViewerThresholds  []int64 `json:"viewer_thresholds"`
		CreatorThresholds []int64 `json:"creator_thresholds"`
	}
	if !financeBody(w, r, &command) {
		return
	}
	if !operationRequestPattern.MatchString(command.RequestID) || command.OperatorID == 0 ||
		command.ExpectedVersion == nil || strings.TrimSpace(command.Reason) == "" ||
		utf8.RuneCountInString(command.Reason) > 200 ||
		!validLevelThresholds(command.ViewerThresholds) || !validLevelThresholds(command.CreatorThresholds) {
		httpx.WriteError(w, http.StatusBadRequest, "INVALID_LEVEL_SETTINGS", "请填写递增的 2–6 级门槛和修改原因")
		return
	}
	_, hash, err := service.BodyHashHex(struct {
		Kind    string
		Command any
	}{"LEVELS", command})
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	tx, err := s.db.BeginTx(r.Context(), &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	defer tx.Rollback()
	var version uint64
	err = tx.QueryRowContext(r.Context(), `SELECT version FROM level_settings WHERE singleton=1 FOR UPDATE`).Scan(&version)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	var priorHash, snapshot []byte
	err = tx.QueryRowContext(r.Context(), `SELECT body_hash,settings_json FROM business_setting_actions WHERE request_id=?`, command.RequestID).Scan(&priorHash, &snapshot)
	if err == nil {
		if !bytes.Equal(priorHash, hash) {
			httpx.WriteError(w, http.StatusConflict, "OPERATION_CONFLICT", "该单号已用于其他设置变更")
			return
		}
		var prior levelSettings
		if json.Unmarshal(snapshot, &prior) != nil {
			financeReply(w, nil, fmt.Errorf("invalid saved level settings"))
			return
		}
		httpx.WriteJSON(w, http.StatusOK, prior)
		return
	}
	if err != sql.ErrNoRows {
		financeReply(w, nil, err)
		return
	}
	if version != *command.ExpectedVersion {
		httpx.WriteError(w, http.StatusConflict, "VERSION_CONFLICT", "设置已被修改，请刷新后重新确认")
		return
	}
	viewer, _ := json.Marshal(command.ViewerThresholds)
	creator, _ := json.Marshal(command.CreatorThresholds)
	settings := levelSettings{Version: version + 1, Configured: true,
		ViewerThresholds: command.ViewerThresholds, CreatorThresholds: command.CreatorThresholds}
	_, err = tx.ExecContext(r.Context(), `UPDATE level_settings SET version=?,configured=TRUE,viewer_thresholds=?,creator_thresholds=? WHERE singleton=1`,
		settings.Version, viewer, creator)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	snapshot, err = json.Marshal(settings)
	if err != nil {
		financeReply(w, nil, err)
		return
	}
	_, err = tx.ExecContext(r.Context(), `INSERT INTO business_setting_actions(request_id,operator_id,kind,target_id,body_hash,settings_json,reason) VALUES(?,?,'LEVELS',0,?,?,?)`,
		command.RequestID, command.OperatorID, hash, snapshot, command.Reason)
	if err == nil {
		err = tx.Commit()
	}
	financeReply(w, settings, err)
}
