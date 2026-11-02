package service

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"math/big"
	"regexp"
	"slices"
	"strings"
	"unicode/utf8"
)

var financeRequestID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)
var payoutMethodID = regexp.MustCompile(`^[a-z][a-z0-9_]{0,31}$`)

// FinanceRules uses integer fen and basis points only. No policy is seeded.
type FinanceRules struct {
	WithdrawalsEnabled         bool     `json:"withdrawals_enabled"`
	DiamondUnits               int64    `json:"diamond_units"`
	FiatFen                    int64    `json:"fiat_fen"`
	PlatformFeeBPS             int64    `json:"platform_fee_bps"`
	MinimumDiamonds            int64    `json:"minimum_withdraw_diamonds"`
	PayoutMethods              []string `json:"payout_methods"`
	RefundConsumedCoinsAllowed bool     `json:"refund_consumed_coins_allowed"`
}
type FinanceSettings struct {
	Configured bool          `json:"configured"`
	Version    uint64        `json:"version"`
	Rules      *FinanceRules `json:"rules"`
}
type SettingsCommand struct {
	RequestID       string       `json:"request_id"`
	OperatorID      uint64       `json:"operator_id"`
	ExpectedVersion uint64       `json:"expected_version"`
	Reason          string       `json:"reason"`
	Rules           FinanceRules `json:"rules"`
}

func financeError(status int, code, message string) error {
	return &AppError{HTTPStatus: status, Code: code, Message: message}
}
func validFinanceText(s string, max int) bool {
	return strings.TrimSpace(s) != "" && utf8.RuneCountInString(s) <= max
}
func (r FinanceRules) Validate() error {
	if r.DiamondUnits < 1 || r.DiamondUnits > 1000000000 || r.FiatFen < 1 || r.FiatFen > 1000000000 || r.PlatformFeeBPS < 0 || r.PlatformFeeBPS > 10000 || r.MinimumDiamonds < 1 || r.MinimumDiamonds > 1000000000000 || len(r.PayoutMethods) < 1 || len(r.PayoutMethods) > 10 {
		return financeError(400, "INVALID_FINANCE_RULES", "资金规则数值或打款方式无效")
	}
	seen := map[string]bool{}
	for _, m := range r.PayoutMethods {
		if !payoutMethodID.MatchString(m) || seen[m] {
			return financeError(400, "INVALID_FINANCE_RULES", "打款方式需为不重复的英文标识")
		}
		seen[m] = true
	}
	return nil
}

// Quote rounds gross and fee down to whole fen, with overflow checked before conversion.
func (r FinanceRules) Quote(diamonds int64) (gross, fee, net int64, err error) {
	if err = r.Validate(); err != nil {
		return
	}
	if diamonds < r.MinimumDiamonds || diamonds > 1000000000000 {
		err = financeError(400, "INVALID_WITHDRAWAL_AMOUNT", "未达到最低提现钻石或金额超限")
		return
	}
	g := new(big.Int).Mul(big.NewInt(diamonds), big.NewInt(r.FiatFen))
	g.Div(g, big.NewInt(r.DiamondUnits))
	if !g.IsInt64() {
		err = financeError(400, "INVALID_WITHDRAWAL_AMOUNT", "金额超限")
		return
	}
	gross = g.Int64()
	f := new(big.Int).Mul(g, big.NewInt(r.PlatformFeeBPS))
	f.Div(f, big.NewInt(10000))
	fee = f.Int64()
	net = gross - fee
	if net < 1 {
		err = financeError(400, "INVALID_WITHDRAWAL_AMOUNT", "实际打款金额必须至少为一分")
	}
	return
}
func scanFinanceSettings(row interface{ Scan(...any) error }) (FinanceSettings, error) {
	var result FinanceSettings
	var raw sql.NullString
	if err := row.Scan(&result.Version, &raw); err != nil {
		return result, err
	}
	if raw.Valid {
		result.Rules = &FinanceRules{}
		if err := json.Unmarshal([]byte(raw.String), result.Rules); err != nil {
			return result, err
		}
		result.Configured = true
	}
	return result, nil
}
func (s *Service) GetFinanceSettings(ctx context.Context) (FinanceSettings, error) {
	return scanFinanceSettings(s.db.QueryRowContext(ctx, `SELECT version,rules_json FROM finance_settings WHERE singleton=1`))
}
func (s *Service) UpdateFinanceSettings(ctx context.Context, c SettingsCommand) (FinanceSettings, error) {
	if !financeRequestID.MatchString(c.RequestID) || c.OperatorID == 0 || !validFinanceText(c.Reason, 200) {
		return FinanceSettings{}, financeError(400, "INVALID_OPERATION", "操作单号、管理员和原因无效")
	}
	if err := c.Rules.Validate(); err != nil {
		return FinanceSettings{}, err
	}
	raw, _ := json.Marshal(c.Rules)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return FinanceSettings{}, err
	}
	defer tx.Rollback()
	current, err := scanFinanceSettings(tx.QueryRowContext(ctx, `SELECT version,rules_json FROM finance_settings WHERE singleton=1 FOR UPDATE`))
	if err != nil {
		return current, err
	}
	var version, operator uint64
	var stored, reason string
	err = tx.QueryRowContext(ctx, `SELECT version,operator_id,rules_json,reason FROM finance_settings_history WHERE request_id=?`, c.RequestID).Scan(&version, &operator, &stored, &reason)
	if err == nil {
		var rules FinanceRules
		if json.Unmarshal([]byte(stored), &rules) != nil {
			return current, errors.New("invalid saved finance rules")
		}
		b, _ := json.Marshal(rules)
		if string(b) != string(raw) || operator != c.OperatorID || reason != c.Reason || version != c.ExpectedVersion+1 {
			return current, financeError(409, "OPERATION_CONFLICT", "单号已用于其他规则修改")
		}
		return FinanceSettings{Configured: true, Version: version, Rules: &rules}, nil
	}
	if err != sql.ErrNoRows {
		return current, err
	}
	if current.Version != c.ExpectedVersion {
		return current, financeError(409, "SETTINGS_VERSION_CONFLICT", "规则已被修改，请刷新后重试")
	}
	version = current.Version + 1
	if _, err = tx.ExecContext(ctx, `INSERT INTO finance_settings_history(version,request_id,operator_id,rules_json,reason) VALUES(?,?,?,?,?)`, version, c.RequestID, c.OperatorID, string(raw), c.Reason); err != nil {
		return current, err
	}
	if _, err = tx.ExecContext(ctx, `UPDATE finance_settings SET version=?,rules_json=?,updated_at=UTC_TIMESTAMP(6) WHERE singleton=1`, version, string(raw)); err != nil {
		return current, err
	}
	if err = tx.Commit(); err != nil {
		return current, err
	}
	return FinanceSettings{Configured: true, Version: version, Rules: &c.Rules}, nil
}

type WithdrawalRequest struct {
	RequestID     string `json:"request_id"`
	Diamonds      int64  `json:"diamonds"`
	PayoutMethod  string `json:"payout_method"`
	PayoutAccount string `json:"payout_account"`
	PayoutName    string `json:"payout_name"`
}
type Withdrawal struct {
	ID     uint64 `json:"withdrawal_id,string"`
	UserID uint64 `json:"user_id,string"`
	WithdrawalRequest
	GrossFen        int64        `json:"gross_fen"`
	FeeFen          int64        `json:"fee_fen"`
	PayoutFen       int64        `json:"payout_fen"`
	SettingsVersion uint64       `json:"settings_version"`
	Rules           FinanceRules `json:"rules"`
	Status          string       `json:"status"`
	PaymentRef      string       `json:"payment_ref"`
	CreatedAt       string       `json:"created_at"`
}

const withdrawalSelect = `SELECT withdrawal_id,user_id,request_id,diamonds,payout_method,payout_account,payout_name,gross_fen,fee_fen,payout_fen,settings_version,rules_json,status,COALESCE(payment_ref,''),CAST(created_at AS CHAR) FROM withdrawals `

func (s *Service) GetWithdrawal(ctx context.Context, id uint64) (Withdrawal, error) {
	return scanWithdrawal(s.db.QueryRowContext(ctx, withdrawalSelect+`WHERE withdrawal_id=?`, id))
}

func scanWithdrawal(row interface{ Scan(...any) error }) (Withdrawal, error) {
	var w Withdrawal
	var raw string
	err := row.Scan(&w.ID, &w.UserID, &w.RequestID, &w.Diamonds, &w.PayoutMethod, &w.PayoutAccount, &w.PayoutName, &w.GrossFen, &w.FeeFen, &w.PayoutFen, &w.SettingsVersion, &raw, &w.Status, &w.PaymentRef, &w.CreatedAt)
	if err == nil {
		err = json.Unmarshal([]byte(raw), &w.Rules)
	}
	return w, err
}
func (s *Service) CreateWithdrawal(ctx context.Context, user uint64, c WithdrawalRequest) (Withdrawal, error) {
	if user == 0 || !financeRequestID.MatchString(c.RequestID) || !validFinanceText(c.PayoutAccount, 200) || !validFinanceText(c.PayoutName, 80) {
		return Withdrawal{}, financeError(400, "INVALID_WITHDRAWAL", "提现单号或收款信息无效")
	}
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return Withdrawal{}, err
	}
	defer tx.Rollback()
	var standing string
	if err = tx.QueryRowContext(ctx, `SELECT account_standing FROM users WHERE user_id=? FOR UPDATE`, user).Scan(&standing); err != nil {
		return Withdrawal{}, err
	}
	old, err := scanWithdrawal(tx.QueryRowContext(ctx, withdrawalSelect+`WHERE user_id=? AND request_id=?`, user, c.RequestID))
	if err == nil {
		if old.WithdrawalRequest != c {
			return old, financeError(409, "OPERATION_CONFLICT", "该单号已用于其他提现")
		}
		return old, nil
	}
	if err != sql.ErrNoRows {
		return old, err
	}
	if standing != "GOOD" {
		return old, financeError(403, "ACCOUNT_RESTRICTED", "当前账号不能提现")
	}
	settings, err := scanFinanceSettings(tx.QueryRowContext(ctx, `SELECT version,rules_json FROM finance_settings WHERE singleton=1 FOR SHARE`))
	if err != nil {
		return old, err
	}
	if !settings.Configured || !settings.Rules.WithdrawalsEnabled {
		return old, financeError(409, "WITHDRAWALS_DISABLED", "平台尚未开放提现")
	}
	if !slices.Contains(settings.Rules.PayoutMethods, c.PayoutMethod) {
		return old, financeError(400, "INVALID_PAYOUT_METHOD", "请选择平台支持的打款方式")
	}
	gross, fee, net, err := settings.Rules.Quote(c.Diamonds)
	if err != nil {
		return old, err
	}
	var earned, reserved int64
	// Only reconciled, closed streams are available; pending/approved/paid orders all reserve earnings.
	if err = tx.QueryRowContext(ctx, `SELECT COALESCE(SUM(ss.diamond_reward_total),0) FROM stream_settlements ss JOIN live_sessions ls USING(live_session_id) JOIN reconciliation_results rr USING(live_session_id) WHERE ls.creator_id=? AND ls.status='CLOSED' AND rr.status='PASS'`, user).Scan(&earned); err != nil {
		return old, err
	}
	if err = tx.QueryRowContext(ctx, `SELECT COALESCE(SUM(diamonds),0) FROM withdrawals WHERE user_id=? AND status!='REJECTED'`, user).Scan(&reserved); err != nil {
		return old, err
	}
	if c.Diamonds > earned-reserved {
		return old, financeError(409, "INSUFFICIENT_WITHDRAWABLE", "可提现钻石不足，请等待直播结算")
	}
	raw, _ := json.Marshal(settings.Rules)
	result, err := tx.ExecContext(ctx, `INSERT INTO withdrawals(user_id,request_id,diamonds,gross_fen,fee_fen,payout_fen,settings_version,rules_json,payout_method,payout_account,payout_name) VALUES(?,?,?,?,?,?,?,?,?,?,?)`, user, c.RequestID, c.Diamonds, gross, fee, net, settings.Version, string(raw), c.PayoutMethod, c.PayoutAccount, c.PayoutName)
	if err != nil {
		return old, err
	}
	id, _ := result.LastInsertId()
	w, err := scanWithdrawal(tx.QueryRowContext(ctx, withdrawalSelect+`WHERE withdrawal_id=?`, id))
	if err != nil {
		return w, err
	}
	return w, tx.Commit()
}
func (s *Service) ListWithdrawals(ctx context.Context, user, before uint64) ([]Withdrawal, error) {
	query := withdrawalSelect + `WHERE 1=1`
	args := []any{}
	if user > 0 {
		query += ` AND user_id=?`
		args = append(args, user)
	}
	if before > 0 {
		query += ` AND withdrawal_id<?`
		args = append(args, before)
	}
	rows, err := s.db.QueryContext(ctx, query+` ORDER BY withdrawal_id DESC LIMIT 26`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []Withdrawal{}
	for rows.Next() {
		w, err := scanWithdrawal(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, w)
	}
	return items, rows.Err()
}

type WithdrawalAction struct {
	RequestID  string `json:"request_id"`
	OperatorID uint64 `json:"operator_id"`
	Action     string `json:"action"`
	Reason     string `json:"reason"`
	PaymentRef string `json:"payment_ref"`
}

func (s *Service) ReviewWithdrawal(ctx context.Context, id uint64, c WithdrawalAction) (Withdrawal, error) {
	if id == 0 || !financeRequestID.MatchString(c.RequestID) || c.OperatorID == 0 || !validFinanceText(c.Reason, 200) || !slices.Contains([]string{"APPROVE", "REJECT", "PAID"}, c.Action) || len(c.PaymentRef) > 128 || (c.Action == "PAID" && !validFinanceText(c.PaymentRef, 128)) || (c.Action != "PAID" && c.PaymentRef != "") {
		return Withdrawal{}, financeError(400, "INVALID_OPERATION", "请填写有效的审核操作、原因和打款回执")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return Withdrawal{}, err
	}
	defer tx.Rollback()
	w, err := scanWithdrawal(tx.QueryRowContext(ctx, withdrawalSelect+`WHERE withdrawal_id=? FOR UPDATE`, id))
	if err == sql.ErrNoRows {
		return w, financeError(404, "WITHDRAWAL_NOT_FOUND", "提现单不存在")
	}
	if err != nil {
		return w, err
	}
	var prior WithdrawalAction
	var priorID uint64
	err = tx.QueryRowContext(ctx, `SELECT withdrawal_id,request_id,operator_id,action,reason,payment_ref FROM withdrawal_actions WHERE request_id=?`, c.RequestID).Scan(&priorID, &prior.RequestID, &prior.OperatorID, &prior.Action, &prior.Reason, &prior.PaymentRef)
	if err == nil {
		if priorID != id || prior != c {
			return w, financeError(409, "OPERATION_CONFLICT", "操作单号已用于其他操作")
		}
		return w, nil
	}
	if err != sql.ErrNoRows {
		return w, err
	}
	next := ""
	switch c.Action {
	case "APPROVE":
		if w.Status == "PENDING" {
			next = "APPROVED"
		}
	case "REJECT":
		if w.Status == "PENDING" {
			next = "REJECTED"
		}
	case "PAID":
		if w.Status == "APPROVED" {
			next = "PAID"
		}
	}
	if next == "" {
		return w, financeError(409, "WITHDRAWAL_STATE_CONFLICT", "提现状态已改变，请刷新核实")
	}
	if _, err = tx.ExecContext(ctx, `INSERT INTO withdrawal_actions(request_id,withdrawal_id,operator_id,action,reason,payment_ref) VALUES(?,?,?,?,?,?)`, c.RequestID, id, c.OperatorID, c.Action, c.Reason, c.PaymentRef); err != nil {
		return w, err
	}
	var ref any
	if c.PaymentRef != "" {
		ref = c.PaymentRef
	}
	if _, err = tx.ExecContext(ctx, `UPDATE withdrawals SET status=?,payment_ref=? WHERE withdrawal_id=?`, next, ref, id); err != nil {
		return w, err
	}
	w.Status = next
	w.PaymentRef = c.PaymentRef
	return w, tx.Commit()
}
