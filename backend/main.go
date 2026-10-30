package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/livekit/protocol/auth"
)

type User struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Role  string `json:"role"`
	Coins int64  `json:"coins"`
	Level int    `json:"level"`
}
type Room struct {
	ID       string `json:"id"`
	HostID   string `json:"hostId"`
	Title    string `json:"title"`
	Category string `json:"category"`
	Status   string `json:"status"`
	Viewers  int    `json:"viewers"`
}
type Gift struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Coins int64  `json:"coins"`
}
type PKInvite struct {
	ID        string    `json:"id"`
	RoomID    string    `json:"roomId"`
	From      string    `json:"from"`
	To        string    `json:"to"`
	Status    string    `json:"status"`
	CreatedAt time.Time `json:"createdAt"`
}
type State struct {
	Users      map[string]*User `json:"users"`
	Rooms      map[string]*Room `json:"rooms"`
	Gifts      []Gift           `json:"gifts"`
	GiftTotals map[string]int64 `json:"giftTotals"`
	Earnings   map[string]int64 `json:"earnings"`
	PK         []PKInvite       `json:"pk"`
}
type App struct {
	mu                                    sync.Mutex
	state                                 State
	file                                  string
	livekitURL, livekitKey, livekitSecret string
}

func main() {
	app := newApp(os.Getenv("DATA_FILE"), os.Getenv("LIVEKIT_API_KEY"), os.Getenv("LIVEKIT_API_SECRET"), os.Getenv("LIVEKIT_URL"))
	mux := http.NewServeMux()
	mux.HandleFunc("/health", app.health)
	mux.HandleFunc("/api/rooms", app.rooms)
	mux.HandleFunc("/api/users/", app.user)
	mux.HandleFunc("/api/gifts", app.gifts)
	mux.HandleFunc("/api/recharge", app.recharge)
	mux.HandleFunc("/api/rankings", app.rankings)
	mux.HandleFunc("/api/earnings/", app.earnings)
	mux.HandleFunc("/api/pk/invite", app.pkInvite)
	mux.HandleFunc("/api/livekit/token", app.livekitToken)
	addr := getenv("BIND_ADDR", ":8088")
	log.Printf("liveroom backend listening on %s", addr)
	log.Fatal(http.ListenAndServe(addr, withCORS(mux)))
}

func newApp(file, key, secret, url string) *App {
	if file == "" {
		file = filepath.Join("data", "state.json")
	}
	a := &App{file: file, livekitKey: key, livekitSecret: secret, livekitURL: url}
	a.state = State{Users: map[string]*User{}, Rooms: map[string]*Room{}, GiftTotals: map[string]int64{}, Earnings: map[string]int64{}}
	a.state.Gifts = []Gift{{"flower", "鲜花", 10}, {"star", "星星", 50}, {"firework", "烟花", 100}}
	_ = a.load()
	if len(a.state.Users) == 0 {
		a.state.Users["demo-viewer"] = &User{"demo-viewer", "演示观众", "viewer", 1280, 12}
		a.state.Users["demo-host"] = &User{"demo-host", "小满", "host", 0, 18}
		a.state.Rooms["demo-room"] = &Room{"demo-room", "demo-host", "今晚一起听歌", "才艺", "live", 0}
		_ = a.save()
	}
	return a
}

func (a *App) load() error {
	b, err := os.ReadFile(a.file)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, &a.state)
}
func (a *App) save() error {
	b, err := json.MarshalIndent(a.state, "", "  ")
	if err != nil {
		return err
	}
	if err = os.MkdirAll(filepath.Dir(a.file), 0755); err != nil {
		return err
	}
	return os.WriteFile(a.file, b, 0644)
}
func getenv(k, fallback string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return fallback
}
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func decode(r *http.Request, v any) error {
	if r.Method != http.MethodPost {
		return errors.New("POST required")
	}
	return json.NewDecoder(r.Body).Decode(v)
}
func (a *App) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, 200, map[string]any{"ok": true, "livekit": a.livekitURL != "", "time": time.Now()})
}

func (a *App) rooms(w http.ResponseWriter, r *http.Request) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if r.Method == http.MethodPost {
		var in Room
		if err := decode(r, &in); err != nil {
			writeJSON(w, 400, map[string]string{"error": err.Error()})
			return
		}
		if in.ID == "" {
			in.ID = id()
		}
		if in.Status == "" {
			in.Status = "live"
		}
		a.state.Rooms[in.ID] = &in
		_ = a.save()
		writeJSON(w, 201, in)
		return
	}
	list := make([]*Room, 0, len(a.state.Rooms))
	for _, room := range a.state.Rooms {
		list = append(list, room)
	}
	writeJSON(w, 200, list)
}
func (a *App) user(w http.ResponseWriter, r *http.Request) {
	id := strings.TrimPrefix(r.URL.Path, "/api/users/")
	a.mu.Lock()
	defer a.mu.Unlock()
	u, ok := a.state.Users[id]
	if !ok {
		writeJSON(w, 404, map[string]string{"error": "user not found"})
		return
	}
	writeJSON(w, 200, u)
}

func (a *App) gifts(w http.ResponseWriter, r *http.Request) {
	var in struct{ UserID, RoomID, GiftID string }
	if err := decode(r, &in); err != nil {
		writeJSON(w, 400, map[string]string{"error": err.Error()})
		return
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	user, ok := a.state.Users[in.UserID]
	if !ok {
		writeJSON(w, 404, map[string]string{"error": "user not found"})
		return
	}
	room, ok := a.state.Rooms[in.RoomID]
	if !ok {
		writeJSON(w, 404, map[string]string{"error": "room not found"})
		return
	}
	var gift Gift
	for _, g := range a.state.Gifts {
		if g.ID == in.GiftID {
			gift = g
			break
		}
	}
	if gift.ID == "" {
		writeJSON(w, 404, map[string]string{"error": "gift not found"})
		return
	}
	if user.Coins < gift.Coins {
		writeJSON(w, 409, map[string]string{"error": "insufficient coins"})
		return
	}
	user.Coins -= gift.Coins
	a.state.GiftTotals[room.HostID] += gift.Coins
	a.state.Earnings[room.HostID] += gift.Coins
	_ = a.save()
	writeJSON(w, 200, map[string]any{"gift": gift, "coins": user.Coins, "hostId": room.HostID})
}
func (a *App) recharge(w http.ResponseWriter, r *http.Request) {
	var in struct {
		UserID string `json:"userId"`
		Coins  int64  `json:"coins"`
	}
	if err := decode(r, &in); err != nil || in.Coins <= 0 {
		writeJSON(w, 400, map[string]string{"error": "userId and positive coins required"})
		return
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	u, ok := a.state.Users[in.UserID]
	if !ok {
		writeJSON(w, 404, map[string]string{"error": "user not found"})
		return
	}
	u.Coins += in.Coins
	_ = a.save()
	writeJSON(w, 200, u)
}
func (a *App) rankings(w http.ResponseWriter, _ *http.Request) {
	a.mu.Lock()
	defer a.mu.Unlock()
	type row struct {
		HostID string `json:"hostId"`
		Coins  int64  `json:"coins"`
		User   *User  `json:"user"`
	}
	rows := []row{}
	for id, coins := range a.state.GiftTotals {
		rows = append(rows, row{id, coins, a.state.Users[id]})
	}
	writeJSON(w, 200, rows)
}
func (a *App) earnings(w http.ResponseWriter, r *http.Request) {
	host := strings.TrimPrefix(r.URL.Path, "/api/earnings/")
	a.mu.Lock()
	defer a.mu.Unlock()
	writeJSON(w, 200, map[string]any{"hostId": host, "giftCoins": a.state.GiftTotals[host], "earnings": a.state.Earnings[host]})
}
func (a *App) pkInvite(w http.ResponseWriter, r *http.Request) {
	var in PKInvite
	if err := decode(r, &in); err != nil {
		writeJSON(w, 400, map[string]string{"error": err.Error()})
		return
	}
	in.ID = id()
	in.Status = "pending"
	in.CreatedAt = time.Now()
	a.mu.Lock()
	a.state.PK = append(a.state.PK, in)
	_ = a.save()
	a.mu.Unlock()
	writeJSON(w, 201, in)
}
func (a *App) livekitToken(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Room       string `json:"room"`
		Identity   string `json:"identity"`
		CanPublish bool   `json:"canPublish"`
	}
	if err := decode(r, &in); err != nil || in.Room == "" || in.Identity == "" {
		writeJSON(w, 400, map[string]string{"error": "room and identity required"})
		return
	}
	if a.livekitKey == "" || a.livekitSecret == "" {
		writeJSON(w, 503, map[string]string{"error": "LIVEKIT_API_KEY and LIVEKIT_API_SECRET are required"})
		return
	}
	grant := &auth.VideoGrant{RoomJoin: true, Room: in.Room, CanPublish: &in.CanPublish, CanSubscribe: func() *bool { v := true; return &v }()}
	token := auth.NewAccessToken(a.livekitKey, a.livekitSecret).SetIdentity(in.Identity).SetVideoGrant(grant)
	jwt, err := token.ToJWT()
	if err != nil {
		writeJSON(w, 500, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, 200, map[string]string{"url": a.livekitURL, "token": jwt})
}
func id() string { b := make([]byte, 8); _, _ = rand.Read(b); return hex.EncodeToString(b) }
func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if r.Method == http.MethodOptions {
			w.WriteHeader(204)
			return
		}
		next.ServeHTTP(w, r)
	})
}
