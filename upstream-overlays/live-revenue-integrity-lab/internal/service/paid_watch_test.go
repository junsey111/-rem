package service

import "testing"

func TestDeriveWatchAccess(t *testing.T) {
	free := DeriveWatchAccess("9", 0, 0, false)
	if free.Status != "FREE" || free.FreePreviewSeconds != 60 || free.UnlockPriceCoins != 0 || free.SecondsRemaining != 60 {
		t.Fatalf("free room: %+v", free)
	}
	preview := DeriveWatchAccess("9", 12, 15, false)
	if preview.Status != "PREVIEW" || preview.SecondsRemaining != 45 || preview.WatchedUnpaidSeconds != 15 {
		t.Fatalf("preview: %+v", preview)
	}
	locked := DeriveWatchAccess("9", 12, 60, false)
	if locked.Status != "LOCKED" || locked.SecondsRemaining != 0 || locked.Unlocked {
		t.Fatalf("locked: %+v", locked)
	}
	over := DeriveWatchAccess("9", 12, 90, false)
	if over.Status != "LOCKED" || over.SecondsRemaining != 0 {
		t.Fatalf("over preview: %+v", over)
	}
	open := DeriveWatchAccess("9", 12, 10, true)
	if open.Status != "UNLOCKED" || !open.Unlocked || open.SecondsRemaining != 50 {
		t.Fatalf("unlocked: %+v", open)
	}
}

func TestClampWatchDelta(t *testing.T) {
	if ClampWatchDelta(0) != 1 || ClampWatchDelta(-4) != 1 || ClampWatchDelta(1) != 1 || ClampWatchDelta(30) != 30 || ClampWatchDelta(31) != 30 {
		t.Fatal("delta clamp mismatch")
	}
}
