package service

var DefaultLevelThresholds = []int64{100, 500, 2000, 10000, 50000}

func LevelForPoints(points int64) int {
	return LevelForPointsWithThresholds(points, DefaultLevelThresholds)
}

func NextLevelPoints(points int64) int64 {
	return NextLevelPointsWithThresholds(points, DefaultLevelThresholds)
}

func LevelForPointsWithThresholds(points int64, thresholds []int64) int {
	for index, threshold := range thresholds {
		if points < threshold {
			return index + 1
		}
	}
	return len(thresholds) + 1
}

func NextLevelPointsWithThresholds(points int64, thresholds []int64) int64 {
	level := LevelForPointsWithThresholds(points, thresholds)
	if level > len(thresholds) {
		return 0
	}
	return thresholds[level-1]
}
