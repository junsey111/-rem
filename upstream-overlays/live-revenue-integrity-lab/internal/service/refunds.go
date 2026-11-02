package service

import (
	"context"
	"database/sql"
	"fmt"
	"slices"
)

type RefundRequest struct {
	RequestID         string `json:"request_id"`
	UserID            uint64 `json:"user_id"`
	RechargeRequestID string `json:"recharge_request_id"`
	AmountFen         int64  `json:"amount_fen"`
	Reason            string `json:"reason"`
}
type Refund struct {
	ID uint64 `json:"refund_id,string"`
	RefundRequest
	Coins           int64  `json:"coins"`
	SettingsVersion uint64 `json:"settings_version"`
	ConsumedAllowed bool   `json:"consumed_allowed"`
	Status          string `json:"status"`
	PaymentRef      string `json:"payment_ref"`
	CreatedAt       string `json:"created_at"`
}

const refundSelect = `SELECT refund_id,request_id,user_id,recharge_request_id,amount_fen,reason,coins,settings_version,consumed_allowed,status,COALESCE(payment_ref,''),CAST(created_at AS CHAR) FROM refunds `

func (s *Service) GetRefund(ctx context.Context, id uint64) (Refund, error) {
	return scanRefund(s.db.QueryRowContext(ctx, refundSelect+`WHERE refund_id=?`, id))
}

func scanRefund(row interface{ Scan(...any) error }) (Refund, error) {
	var r Refund
	err := row.Scan(&r.ID, &r.RequestID, &r.UserID, &r.RechargeRequestID, &r.AmountFen, &r.Reason, &r.Coins, &r.SettingsVersion, &r.ConsumedAllowed, &r.Status, &r.PaymentRef, &r.CreatedAt)
	return r, err
}

// refundPosting changes the same wallet row and balanced ledger as gift/recharge transactions.
func refundPosting(ctx context.Context, tx *sql.Tx, user uint64, request, kind string, delta int64) error {
	result, err := tx.ExecContext(ctx, `INSERT INTO wallet_transactions(tx_type,actor_user_id,request_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))`, kind, user, request)
	if err != nil {
		return err
	}
	id, err := result.LastInsertId()
	if err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, `INSERT INTO wallet_entries(tx_id,account_code,amount) VALUES(?,?,?),(?,?,?)`, id, fmt.Sprintf("USER_COINS:%d", user), delta, id, "SYSTEM_REFUND_CLEARING", -delta); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `UPDATE wallet_accounts SET available_balance=available_balance+? WHERE user_id=? AND currency='COIN'`, delta, user)
	return err
}

// CreateRefund accepts fiat amount only from the authenticated payment gateway's original order.
// A consumed-coin refund can create debt; negative coin balance prevents further gifts until repaid.
func (s *Service) CreateRefund(ctx context.Context, c RefundRequest) (Refund, error) {
	if !financeRequestID.MatchString(c.RequestID) || c.UserID == 0 || !validFinanceText(c.RechargeRequestID, 64) || c.AmountFen < 1 || c.AmountFen > 1000000000 || !validFinanceText(c.Reason, 200) {
		return Refund{}, financeError(400, "INVALID_REFUND", "退款单号、充值订单或原因无效")
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return Refund{}, err
	}
	defer tx.Rollback()
	var coins int64
	if err = tx.QueryRowContext(ctx, `SELECT coins FROM recharge_requests WHERE viewer_id=? AND request_id=? FOR UPDATE`, c.UserID, c.RechargeRequestID).Scan(&coins); err == sql.ErrNoRows {
		return Refund{}, financeError(404, "RECHARGE_NOT_FOUND", "尚未确认该充值到账")
	}
	if err != nil {
		return Refund{}, err
	}
	old, err := scanRefund(tx.QueryRowContext(ctx, refundSelect+`WHERE user_id=? AND request_id=?`, c.UserID, c.RequestID))
	if err == nil {
		if old.RefundRequest != c {
			return old, financeError(409, "OPERATION_CONFLICT", "单号已用于其他退款")
		}
		return old, nil
	}
	if err != sql.ErrNoRows {
		return old, err
	}
	var exists int
	if err = tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM refunds WHERE recharge_request_id=? AND status<>'REJECTED'`, c.RechargeRequestID).Scan(&exists); err != nil {
		return old, err
	}
	if exists != 0 {
		return old, financeError(409, "REFUND_ALREADY_REQUESTED", "该充值已申请退款，请查看原退款单")
	}
	settings, err := scanFinanceSettings(tx.QueryRowContext(ctx, `SELECT version,rules_json FROM finance_settings WHERE singleton=1 FOR SHARE`))
	if err != nil {
		return old, err
	}
	if !settings.Configured {
		return old, financeError(409, "FINANCE_NOT_CONFIGURED", "平台尚未配置退款规则")
	}
	var balance int64
	if err = tx.QueryRowContext(ctx, `SELECT available_balance FROM wallet_accounts WHERE user_id=? AND currency='COIN' FOR UPDATE`, c.UserID).Scan(&balance); err != nil {
		return old, err
	}
	if balance < coins && !settings.Rules.RefundConsumedCoinsAllowed {
		return old, financeError(409, "CONSUMED_COINS_NOT_REFUNDABLE", "金币已消费，当前规则不允许退款")
	}
	result, err := tx.ExecContext(ctx, `INSERT INTO refunds(request_id,user_id,recharge_request_id,coins,amount_fen,reason,settings_version,consumed_allowed) VALUES(?,?,?,?,?,?,?,?)`, c.RequestID, c.UserID, c.RechargeRequestID, coins, c.AmountFen, c.Reason, settings.Version, settings.Rules.RefundConsumedCoinsAllowed)
	if err != nil {
		return old, err
	}
	id, _ := result.LastInsertId()
	if err = refundPosting(ctx, tx, c.UserID, c.RequestID, "REFUND_HOLD", -coins); err != nil {
		return old, err
	}
	record, err := scanRefund(tx.QueryRowContext(ctx, refundSelect+`WHERE refund_id=?`, id))
	if err != nil {
		return record, err
	}
	return record, tx.Commit()
}
func (s *Service) ListRefunds(ctx context.Context, user, before uint64) ([]Refund, error) {
	query := refundSelect + `WHERE 1=1`
	args := []any{}
	if user > 0 {
		query += ` AND user_id=?`
		args = append(args, user)
	}
	if before > 0 {
		query += ` AND refund_id<?`
		args = append(args, before)
	}
	rows, err := s.db.QueryContext(ctx, query+` ORDER BY refund_id DESC LIMIT 26`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []Refund{}
	for rows.Next() {
		r, err := scanRefund(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, r)
	}
	return items, rows.Err()
}
func (s *Service) ReviewRefund(ctx context.Context, id uint64, c WithdrawalAction) (Refund, error) {
	if id == 0 || !financeRequestID.MatchString(c.RequestID) || c.OperatorID == 0 || !validFinanceText(c.Reason, 200) || !slices.Contains([]string{"APPROVE", "REJECT", "PAID"}, c.Action) || len(c.PaymentRef) > 128 || (c.Action == "PAID" && !validFinanceText(c.PaymentRef, 128)) || (c.Action != "PAID" && c.PaymentRef != "") {
		return Refund{}, financeError(400, "INVALID_OPERATION", "请填写有效的退款审核、原因和回执")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return Refund{}, err
	}
	defer tx.Rollback()
	record, err := scanRefund(tx.QueryRowContext(ctx, refundSelect+`WHERE refund_id=? FOR UPDATE`, id))
	if err == sql.ErrNoRows {
		return record, financeError(404, "REFUND_NOT_FOUND", "退款单不存在")
	}
	if err != nil {
		return record, err
	}
	var prior WithdrawalAction
	var priorID uint64
	err = tx.QueryRowContext(ctx, `SELECT refund_id,request_id,operator_id,action,reason,payment_ref FROM refund_actions WHERE request_id=?`, c.RequestID).Scan(&priorID, &prior.RequestID, &prior.OperatorID, &prior.Action, &prior.Reason, &prior.PaymentRef)
	if err == nil {
		if priorID != id || prior != c {
			return record, financeError(409, "OPERATION_CONFLICT", "单号已用于其他操作")
		}
		return record, nil
	}
	if err != sql.ErrNoRows {
		return record, err
	}
	next := ""
	switch c.Action {
	case "APPROVE":
		if record.Status == "PENDING" {
			next = "APPROVED"
		}
	case "REJECT":
		if record.Status == "PENDING" {
			next = "REJECTED"
		}
	case "PAID":
		if record.Status == "APPROVED" {
			next = "PAID"
		}
	}
	if next == "" {
		return record, financeError(409, "REFUND_STATE_CONFLICT", "退款状态已改变，请刷新核实")
	}
	if next == "REJECTED" {
		var balance int64
		if err = tx.QueryRowContext(ctx, `SELECT available_balance FROM wallet_accounts WHERE user_id=? AND currency='COIN' FOR UPDATE`, record.UserID).Scan(&balance); err != nil {
			return record, err
		}
		if err = refundPosting(ctx, tx, record.UserID, record.RequestID, "REFUND_RELEASE", record.Coins); err != nil {
			return record, err
		}
	}
	if _, err = tx.ExecContext(ctx, `INSERT INTO refund_actions(request_id,refund_id,operator_id,action,reason,payment_ref) VALUES(?,?,?,?,?,?)`, c.RequestID, id, c.OperatorID, c.Action, c.Reason, c.PaymentRef); err != nil {
		return record, err
	}
	var ref any
	if c.PaymentRef != "" {
		ref = c.PaymentRef
	}
	if _, err = tx.ExecContext(ctx, `UPDATE refunds SET status=?,payment_ref=? WHERE refund_id=?`, next, ref, id); err != nil {
		return record, err
	}
	record.Status = next
	record.PaymentRef = c.PaymentRef
	return record, tx.Commit()
}
