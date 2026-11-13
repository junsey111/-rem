package service

import (
	"context"
	"database/sql"
)

type earningsReader interface {
	QueryRowContext(context.Context, string, ...any) *sql.Row
}

// Live gifts require a reconciled, closed stream. The other sources are charged
// atomically and become earnings immediately; no live settlement contains them.
func earnedDiamonds(ctx context.Context, db earningsReader, user uint64) (int64, error) {
	var earned int64
	err := db.QueryRowContext(ctx, `SELECT
	 COALESCE((SELECT SUM(ss.diamond_reward_total) FROM stream_settlements ss JOIN live_sessions ls USING(live_session_id) JOIN reconciliation_results rr USING(live_session_id) WHERE ls.creator_id=? AND ls.status='CLOSED' AND rr.status='PASS'),0)
	 + COALESCE((SELECT SUM(diamond_reward) FROM dm_gift_orders WHERE recipient_id=? AND status='ACCEPTED'),0)
	 + COALESCE((SELECT SUM(diamond_reward) FROM dm_video_unlocks WHERE owner_id=? AND charged_coins>0),0)
	 + COALESCE((SELECT SUM(price_coins) FROM dm_call_charges WHERE owner_id=? AND price_coins>0),0)
	 + COALESCE((SELECT SUM(diamond_reward) FROM live_watch_unlocks WHERE creator_id=?),0)`,
		user, user, user, user, user).Scan(&earned)
	return earned, err
}

type Withdrawable struct {
	Earned    int64 `json:"earned_diamonds"`
	Reserved  int64 `json:"reserved_diamonds"`
	Available int64 `json:"available_diamonds"`
}

func (s *Service) Withdrawable(ctx context.Context, user uint64) (Withdrawable, error) {
	// One repeatable-read snapshot prevents a concurrent reservation from being
	// combined with a different earnings snapshot in the displayed balance.
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelRepeatableRead, ReadOnly: true})
	if err != nil {
		return Withdrawable{}, err
	}
	defer tx.Rollback()
	earned, err := earnedDiamonds(ctx, tx, user)
	if err != nil {
		return Withdrawable{}, err
	}
	var reserved int64
	if err = tx.QueryRowContext(ctx, `SELECT COALESCE(SUM(diamonds),0) FROM withdrawals WHERE user_id=? AND status!='REJECTED'`, user).Scan(&reserved); err != nil {
		return Withdrawable{}, err
	}
	if err = tx.Commit(); err != nil {
		return Withdrawable{}, err
	}
	return Withdrawable{Earned: earned, Reserved: reserved, Available: earned - reserved}, nil
}
