package service

import "testing"

func TestLevelForPoints(t *testing.T) {
	tests := []struct {
		points int64
		level  int
	}{
		{0, 1}, {99, 1}, {100, 2}, {499, 2}, {500, 3},
		{2000, 4}, {10000, 5}, {50000, 6},
	}
	for _, test := range tests {
		if got := LevelForPoints(test.points); got != test.level {
			t.Fatalf("LevelForPoints(%d) = %d, want %d", test.points, got, test.level)
		}
	}
	thresholds := []struct {
		points int64
		next   int64
	}{{0, 100}, {99, 100}, {100, 500}, {499, 500}, {500, 2000}, {2000, 10000}, {10000, 50000}, {50000, 0}}
	for _, test := range thresholds {
		if got := NextLevelPoints(test.points); got != test.next {
			t.Fatalf("NextLevelPoints(%d) = %d, want %d", test.points, got, test.next)
		}
	}
}
