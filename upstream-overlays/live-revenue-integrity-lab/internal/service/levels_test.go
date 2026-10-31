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
}
