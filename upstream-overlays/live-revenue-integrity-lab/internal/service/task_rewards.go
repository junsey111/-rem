package service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"
)

const txTypeTaskReward = "TASK_REWARD"

var taskRewardRequestID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)
var taskKeyPattern = regexp.MustCompile(`^[a-z][a-z0-9_.-]{0,63}$`)

type TaskRewardRequest struct {
	RequestID   string `json:"request_id"`
	UserID      uint64 `json:"user_id"`
	TaskKey     string `json:"task_key"`
	TaskDate    string `json:"task_date"`
	RewardCoins uint32 `json:"reward_coins"`
}

type TaskRewardResponse struct {
	ClaimID     uint64 `json:"claim_id,string"`
	TxID        uint64 `json:"tx_id,string"`
	UserID      uint64 `json:"user_id,string"`
	TaskKey     string `json:"task_key"`
	TaskDate    string `json:"task_date"`
	RewardCoins uint32 `json:"reward_coins"`
	NewBalance  int64  `json:"new_balance"`
	Idempotency string `json:"idempotency"`
}

func (s *Service) GrantTaskReward(ctx context.Context, req TaskRewardRequest) (TaskRewardResponse, *AppError, error) {
	req.TaskKey = strings.TrimSpace(req.TaskKey)
	req.TaskDate = strings.TrimSpace(req.TaskDate)
	if !taskRewardRequestID.MatchString(strings.ToLower(req.RequestID)) || req.UserID == 0 || !taskKeyPattern.MatchString(req.TaskKey) || req.RewardCoins == 0 || req.RewardCoins > 1000000 {
		return TaskRewardResponse{}, &AppError{HTTPStatus: 400, Code: "INVALID_TASK_REWARD", Message: "task reward request is invalid"}, nil
	}
	if _, err := time.Parse("2006-01-02", req.TaskDate); err != nil {
		return TaskRewardResponse{}, &AppError{HTTPStatus: 400, Code: "INVALID_TASK_DATE", Message: "task date is invalid"}, nil
	}
	req.RequestID = strings.ToLower(req.RequestID)
	_, bodyHash, err := BodyHashHex(req)
	if err != nil {
		return TaskRewardResponse{}, nil, err
	}

	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return TaskRewardResponse{}, nil, err
	}
	defer tx.Rollback()
	// Serialize same-user claims before checking idempotency so concurrent retries replay the committed claim.
	balance, err := lockCoinBalance(ctx, tx, req.UserID)
	if err != nil {
		return TaskRewardResponse{}, nil, err
	}

	var existing TaskRewardResponse
	var existingHash []byte
	err = tx.QueryRowContext(ctx, `
		SELECT claim_id,tx_id,user_id,task_key,CAST(task_date AS CHAR),reward_coins,new_balance,body_hash
		FROM task_reward_claims WHERE user_id=? AND ((task_key=? AND task_date=?) OR request_id=?) FOR UPDATE`,
		req.UserID, req.TaskKey, req.TaskDate, req.RequestID).Scan(&existing.ClaimID, &existing.TxID, &existing.UserID, &existing.TaskKey, &existing.TaskDate, &existing.RewardCoins, &existing.NewBalance, &existingHash)
	if err == nil {
		if !bytesEqual(existingHash, bodyHash) {
			return TaskRewardResponse{}, &AppError{HTTPStatus: 409, Code: "IDEMPOTENCY_PAYLOAD_MISMATCH", Message: "task reward was requested with different data"}, nil
		}
		if err := tx.Commit(); err != nil {
			return TaskRewardResponse{}, nil, err
		}
		existing.Idempotency = "replayed"
		return existing, nil, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return TaskRewardResponse{}, nil, err
	}

	newBalance := balance + int64(req.RewardCoins)
	if newBalance < balance {
		return TaskRewardResponse{}, &AppError{HTTPStatus: 400, Code: "INVALID_TASK_REWARD", Message: "task reward exceeds balance limit"}, nil
	}
	txID, err := postLedger(ctx, tx, txTypeTaskReward, req.UserID, req.RequestID, bodyHash, newBalance, []LedgerEntry{
		{AccountCode: fmt.Sprintf("USER_COINS:%d", req.UserID), Amount: int64(req.RewardCoins)},
		{AccountCode: "PLATFORM_TASK_REWARDS", Amount: -int64(req.RewardCoins)},
	})
	if err != nil {
		return TaskRewardResponse{}, nil, err
	}
	if err = setCoinBalance(ctx, tx, req.UserID, newBalance); err != nil {
		return TaskRewardResponse{}, nil, err
	}
	result, err := tx.ExecContext(ctx, `
		INSERT INTO task_reward_claims(user_id,task_key,task_date,request_id,reward_coins,body_hash,tx_id,new_balance,created_at)
		VALUES(?,?,?,?,?,?,?,?,?)`, req.UserID, req.TaskKey, req.TaskDate, req.RequestID, req.RewardCoins, bodyHash, txID, newBalance, time.Now().UTC())
	if err != nil {
		if strings.Contains(strings.ToLower(err.Error()), "duplicate") {
			return TaskRewardResponse{}, &AppError{HTTPStatus: 409, Code: "TASK_ALREADY_CLAIMED", Message: "task reward has already been claimed"}, nil
		}
		return TaskRewardResponse{}, nil, err
	}
	claimID64, err := result.LastInsertId()
	if err != nil || claimID64 < 1 {
		return TaskRewardResponse{}, nil, fmt.Errorf("invalid task claim id")
	}
	if err := tx.Commit(); err != nil {
		return TaskRewardResponse{}, nil, err
	}
	return TaskRewardResponse{ClaimID: uint64(claimID64), TxID: txID, UserID: req.UserID, TaskKey: req.TaskKey, TaskDate: req.TaskDate,
		RewardCoins: req.RewardCoins, NewBalance: newBalance, Idempotency: "created"}, nil, nil
}
