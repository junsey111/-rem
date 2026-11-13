package apiserver

import (
	"net/http/httptest"
	"strings"
	"testing"
)

func TestManualRechargePaymentRef(t *testing.T) {
	command := manualRechargeCommand{
		RequestID: "123e4567-e89b-42d3-a456-426614174000",
		UserID:    42, OperatorID: 7, Coins: 100, Reason: " 客服收款确认 ",
	}
	ref, ok := command.paymentRef()
	if !ok || ref != "manual:42:7:123e4567-e89b-42d3-a456-426614174000:客服收款确认" {
		t.Fatalf("unexpected reference %q, valid=%v", ref, ok)
	}
	if replay, valid := command.paymentRef(); !valid || replay != ref {
		t.Fatal("same command did not produce same idempotency reference")
	}
	for name, mutate := range map[string]func(*manualRechargeCommand){
		"bad ID":         func(c *manualRechargeCommand) { c.RequestID = "123" },
		"zero user":      func(c *manualRechargeCommand) { c.UserID = 0 },
		"zero operator":  func(c *manualRechargeCommand) { c.OperatorID = 0 },
		"zero coins":     func(c *manualRechargeCommand) { c.Coins = 0 },
		"too many coins": func(c *manualRechargeCommand) { c.Coins = 100001 },
		"blank reason":   func(c *manualRechargeCommand) { c.Reason = "   " },
		"long reason":    func(c *manualRechargeCommand) { c.Reason = strings.Repeat("字", 41) },
	} {
		t.Run(name, func(t *testing.T) {
			invalid := command
			mutate(&invalid)
			if _, valid := invalid.paymentRef(); valid {
				t.Fatal("invalid command accepted")
			}
		})
	}
}

func TestRechargeCursor(t *testing.T) {
	for _, test := range []struct {
		raw   string
		valid bool
	}{
		{"", true}, {"23", true}, {"0", false}, {"-1", false}, {"bad", false},
	} {
		request := httptest.NewRequest("GET", "/v1/users/me/recharges?before_id="+test.raw, nil)
		_, valid := rechargeCursor(request)
		if valid != test.valid {
			t.Errorf("cursor %q valid=%v", test.raw, valid)
		}
	}
}
