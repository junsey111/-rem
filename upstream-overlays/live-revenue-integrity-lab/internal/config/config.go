// Package config contains runtime configuration wiring.
package config

import (
	"os"
)

// Config contains runtime settings for all binaries.
type Config struct {
	HTTPAddr          string
	JWTSecret         string
	PaymentSecret     string
	InternalKey       string
	BilldAccountURL   string
	MySQLDSN          string
	KafkaBrokers      []string
	RedisAddr         string
	AllowedGiftRegion map[string]struct{}
}

// Load returns config from env with sane local defaults.
func Load() Config {
	cfg := Config{
		HTTPAddr:        getEnv("HTTP_ADDR", ":8080"),
		JWTSecret:       getEnv("JWT_SECRET", ""),
		PaymentSecret:   getEnv("PAYMENT_WEBHOOK_SECRET", ""),
		InternalKey:     getEnv("INTERNAL_SERVICE_KEY", ""),
		BilldAccountURL: getEnv("BILLD_ACCOUNT_URL", "http://billd-live-server:4300/user/me"),
		MySQLDSN:        getEnv("MYSQL_DSN", "root:root@tcp(localhost:3306)/live_revenue?parseTime=true&multiStatements=true"),
		RedisAddr:       getEnv("REDIS_ADDR", "localhost:6379"),
		KafkaBrokers:    []string{getEnv("KAFKA_BROKER", "localhost:9092")},
		AllowedGiftRegion: map[string]struct{}{
			"US": {},
			"KR": {},
			"JP": {},
			"GB": {},
			"DE": {},
			"FR": {},
		},
	}
	return cfg
}

func getEnv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
