package service

import (
	"context"
	"database/sql"
	"strings"
)

func profileAgeEligible(age sql.NullInt64, region sql.NullString, source string) bool {
	if source != "SELF_REPORTED" || !age.Valid || !region.Valid {
		return false
	}
	if strings.EqualFold(region.String, "KR") {
		return age.Int64 >= 19
	}
	return age.Int64 >= 18
}

// CanStartLive uses stored account permissions, never client-supplied flags.
func (s *Service) CanStartLive(ctx context.Context, user uint64) (bool, error) {
	var age sql.NullInt64
	var region sql.NullString
	var source, standing string
	var enabled bool
	err := s.db.QueryRowContext(ctx, `SELECT age_years,region_code,profile_source,account_standing,(can_go_live AND NOT EXISTS(SELECT 1 FROM platform_account_state p WHERE p.user_id=users.user_id AND p.disabled=TRUE) AND (NOT EXISTS(SELECT 1 FROM creator_admission_settings WHERE singleton=1 AND require_approval=TRUE) OR EXISTS(SELECT 1 FROM review_requests q WHERE q.user_id=users.user_id AND q.kind='CREATOR' AND q.status='APPROVED'))) FROM users WHERE user_id=?`, user).Scan(&age, &region, &source, &standing, &enabled)
	if err == sql.ErrNoRows {
		return false, nil
	}
	return err == nil && enabled && standing == "GOOD" && profileAgeEligible(age, region, source), err
}
