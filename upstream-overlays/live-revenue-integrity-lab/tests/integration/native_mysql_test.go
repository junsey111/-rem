package integration

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"github.com/go-sql-driver/mysql"
	"github.com/pressly/goose/v3"
)

// MYSQL_TEST_DSN points to a test server only. Each call creates and drops its own database.
func startIsolatedMySQL(ctx context.Context, t *testing.T, dsn string, seed bool) (*sql.DB, func(context.Context)) {
	t.Helper()
	config, err := mysql.ParseDSN(dsn)
	if err != nil {
		t.Fatal(err)
	}
	config.DBName = ""
	config.MultiStatements = true
	config.ParseTime = true
	admin, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		t.Fatal(err)
	}
	name := fmt.Sprintf("live_test_%d", time.Now().UnixNano())
	if _, err := admin.ExecContext(ctx, "CREATE DATABASE `"+name+"`"); err != nil {
		admin.Close()
		t.Fatal(err)
	}
	config.DBName = name
	db, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		t.Fatal(err)
	}
	cleanup := func(ctx context.Context) {
		db.Close()
		if _, err := admin.ExecContext(ctx, "DROP DATABASE `"+name+"`"); err != nil {
			t.Errorf("drop isolated test database: %v", err)
		}
		admin.Close()
	}
	t.Cleanup(func() { cleanup(context.Background()) })
	_, currentFile, _, _ := runtime.Caller(0)
	options := []goose.ProviderOption{}
	if !seed {
		options = append(options, goose.WithExcludeNames([]string{"00002_seed.sql"}))
	}
	provider, err := goose.NewProvider(goose.DialectMySQL, db, os.DirFS(filepath.Join(filepath.Dir(currentFile), "../../migrations")), options...)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := provider.Up(ctx); err != nil {
		t.Fatal(err)
	}
	if seed {
		if _, err := db.ExecContext(ctx, `UPDATE users SET profile_source='SELF_REPORTED'`); err != nil { t.Fatal(err) }
	}
	// Test cleanup owns the database; existing integration callers can keep their defer.
	return db, func(context.Context) {}
}

func TestProductionMigrationsExcludeDemoAndPreserveBalances(t *testing.T) {
	dsn := os.Getenv("MYSQL_TEST_DSN")
	if dsn == "" {
		t.Skip("MYSQL_TEST_DSN is required for isolated native MySQL migration verification")
	}
	ctx := context.Background()
	db, _ := startIsolatedMySQL(ctx, t, dsn, false)
	for _, table := range []string{"users", "wallet_accounts", "live_sessions", "live_matches", "gift_orders"} {
		var count int
		if err := db.QueryRowContext(ctx, "SELECT COUNT(*) FROM "+table).Scan(&count); err != nil {
			t.Fatal(err)
		}
		if count != 0 {
			t.Fatalf("production migration inserted demo activity into %s: %d", table, count)
		}
	}
	var catalogCount int
	if err := db.QueryRowContext(ctx, "SELECT COUNT(*) FROM gift_catalog WHERE enabled=TRUE").Scan(&catalogCount); err != nil {
		t.Fatal(err)
	}
	if catalogCount == 0 {
		t.Fatal("production has no gift catalog")
	}
	if _, err := db.ExecContext(ctx, `INSERT INTO users(user_id,age_years,region_code,account_standing,can_go_live,live_gifts_enabled,account_type) VALUES (42,25,'US','GOOD',TRUE,TRUE,'NORMAL'); INSERT INTO wallet_accounts(user_id,currency,available_balance) VALUES (42,'COIN',37)`); err != nil {
		t.Fatal(err)
	}
	_, currentFile, _, _ := runtime.Caller(0)
	provider, err := goose.NewProvider(goose.DialectMySQL, db, os.DirFS(filepath.Join(filepath.Dir(currentFile), "../../migrations")), goose.WithExcludeNames([]string{"00002_seed.sql"}))
	if err != nil {
		t.Fatal(err)
	}
	results, err := provider.Up(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 0 {
		t.Fatalf("restart reapplied %d migrations", len(results))
	}
	var balance int
	if err := db.QueryRowContext(ctx, "SELECT available_balance FROM wallet_accounts WHERE user_id=42 AND currency='COIN'").Scan(&balance); err != nil {
		t.Fatal(err)
	}
	if balance != 37 {
		t.Fatalf("restart changed balance: %d", balance)
	}
}
