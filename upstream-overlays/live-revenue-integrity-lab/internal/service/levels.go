package service

// LevelForPoints maps accumulated spend/earn points to a stable platform level.
// Thresholds stay server-side so clients cannot choose a level.
func LevelForPoints(points int64) int {
	if points < 100 {
		return 1
	}
	if points < 500 {
		return 2
	}
	if points < 2000 {
		return 3
	}
	if points < 10000 {
		return 4
	}
	if points < 50000 {
		return 5
	}
	return 6
}
