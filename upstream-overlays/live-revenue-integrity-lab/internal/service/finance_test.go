package service

import "testing"

func TestFinanceQuote(t *testing.T) {
	r := FinanceRules{DiamondUnits: 100, FiatFen: 100, PlatformFeeBPS: 2000, MinimumDiamonds: 100, PayoutMethods: []string{"bank"}}
	gross, fee, net, err := r.Quote(199)
	if err != nil || gross != 199 || fee != 39 || net != 160 {
		t.Fatalf("quote: %d %d %d %v", gross, fee, net, err)
	}
	if _, _, _, err = r.Quote(99); err == nil {
		t.Fatal("minimum must be enforced")
	}
	r.PlatformFeeBPS = 10001
	if r.Validate() == nil {
		t.Fatal("fee above 100 percent accepted")
	}
	r.PlatformFeeBPS = 10000
	if _, _, _, err = r.Quote(100); err == nil {
		t.Fatal("zero net accepted")
	}
	r.PlatformFeeBPS = 0
	r.FiatFen = 1000000000
	r.DiamondUnits = 1
	if _, _, _, err = r.Quote(1000000000000); err == nil {
		t.Fatal("overflow accepted")
	}
	r.PayoutMethods = []string{"bank", "bank"}
	if r.Validate() == nil {
		t.Fatal("duplicate payout methods accepted")
	}
}
