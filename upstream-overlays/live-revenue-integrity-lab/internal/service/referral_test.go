package service

import "testing"

func TestLoadReferralPolicyDefaultsOff(t *testing.T) {
	t.Setenv("REFERRALS_ENABLED", "")
	t.Setenv("REFERRAL_INVITER_COINS", "")
	t.Setenv("REFERRAL_INVITEE_COINS", "")
	policy := LoadReferralPolicy()
	if policy.Enabled || policy.InviterCoins != 100 || policy.InviteeCoins != 50 {
		t.Fatalf("policy = %+v", policy)
	}
	t.Setenv("REFERRALS_ENABLED", "true")
	t.Setenv("REFERRAL_INVITER_COINS", "20")
	t.Setenv("REFERRAL_INVITEE_COINS", "0")
	policy = LoadReferralPolicy()
	if !policy.usable() || policy.InviterCoins != 20 || policy.InviteeCoins != 0 {
		t.Fatalf("enabled policy = %+v", policy)
	}
	t.Setenv("REFERRAL_INVITER_COINS", "100001")
	if LoadReferralPolicy().usable() {
		t.Fatal("oversized reward stayed enabled")
	}
}
