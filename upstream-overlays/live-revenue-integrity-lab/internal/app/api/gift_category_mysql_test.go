package apiserver

import (
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestGiftCategoryPersistsAndOldRetriesCannotOverwriteIt(t *testing.T) {
	db := newOperationsTestDB(t)
	s := &Server{db: db}
	body := func(id int, category string) string {
		suffix := ""
		if category != "" {
			suffix = fmt.Sprintf(`,"category":%q`, category)
		}
		return fmt.Sprintf(`{"request_id":"31000000-0000-4000-8000-%012d","operator_id":9,"gift_id":"CATEGORY_TEST","display_name":"分类测试","coin_price":10,"match_points":2,"diamond_reward":1,"enabled":true,"effect_type":"none","reason":"test"%s}`, id, suffix)
	}
	call := func(command string, want int) {
		t.Helper()
		w := httptest.NewRecorder()
		s.handleAdminCatalog(w, httptest.NewRequest("PUT", "/v1/internal/catalog", strings.NewReader(command)))
		if w.Code != want {
			t.Fatalf("catalog: %d %s", w.Code, w.Body.String())
		}
	}
	call(body(1, "heart"), 200)
	call(body(2, "premium"), 200)
	call(body(1, "heart"), 200)
	call(body(1, "party"), 409)
	call(body(3, "unknown"), 400)
	call(body(4, ""), 200) // Older clients omit category and must preserve the operator's choice.
	var category string
	if err := db.QueryRow(`SELECT category FROM gift_catalog WHERE gift_id='CATEGORY_TEST'`).Scan(&category); err != nil || category != "premium" {
		t.Fatalf("category=%q err=%v", category, err)
	}
	w := httptest.NewRecorder()
	s.handleGiftCatalog(w, httptest.NewRequest("GET", "/v1/gifts/catalog", nil))
	if w.Code != 200 {
		t.Fatalf("public: %d %s", w.Code, w.Body.String())
	}
	var response struct {
		Gifts []struct {
			ID       string `json:"gift_id"`
			Category string `json:"category"`
		} `json:"gifts"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	for _, gift := range response.Gifts {
		if gift.ID == "CATEGORY_TEST" {
			if gift.Category != "premium" {
				t.Fatal(gift)
			}
			return
		}
	}
	t.Fatal("category missing from public gift catalog")
}
