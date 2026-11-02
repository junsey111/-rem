// Command migrate applies versioned schema and catalog migrations without demo data.
package main

import (
	"context"
	"log"
	"os"
	"time"

	"github.com/mini_station/live-revenue-integrity-lab/internal/config"
	"github.com/mini_station/live-revenue-integrity-lab/internal/db"
	"github.com/pressly/goose/v3"
)

func main() {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	sqlDB, err := db.OpenMySQL(ctx, config.Load().MySQLDSN)
	if err != nil {
		log.Fatal(err)
	}
	defer sqlDB.Close()
	migrationsDir := os.Getenv("MIGRATIONS_DIR")
	if migrationsDir == "" {
		migrationsDir = "/app/migrations"
	}
	provider, err := goose.NewProvider(goose.DialectMySQL, sqlDB, os.DirFS(migrationsDir),
		goose.WithExcludeNames([]string{"00002_seed.sql"}))
	if err != nil {
		log.Fatal(err)
	}
	results, err := provider.Up(ctx)
	if err != nil {
		log.Fatal(err)
	}
	log.Printf("applied %d migrations; demo seed excluded", len(results))
}
