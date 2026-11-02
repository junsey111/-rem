// Command platform-im-smoke verifies two actual OpenIM WebSocket peers and persisted replay.
// It creates clearly named verification users; it never contacts an existing user's account.
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"time"

	"github.com/gorilla/websocket"
	"github.com/openimsdk/protocol/sdkws"
	"google.golang.org/protobuf/proto"
)

var client = &http.Client{Timeout: 15 * time.Second}
var apiURL = "http://127.0.0.1:10002"

func call(path, token string, payload any) (json.RawMessage, error) {
	body, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequest(http.MethodPost, apiURL+path, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("operationID", fmt.Sprintf("platform-verification-%d", time.Now().UnixNano()))
	if token != "" {
		req.Header.Set("token", token)
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("%s: request failed", path)
	}
	defer resp.Body.Close()
	var envelope struct {
		ErrCode int             `json:"errCode"`
		ErrMsg  string          `json:"errMsg"`
		ErrDlt  string          `json:"errDlt"`
		Data    json.RawMessage `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&envelope); err != nil {
		return nil, fmt.Errorf("%s: invalid response", path)
	}
	if resp.StatusCode != http.StatusOK || envelope.ErrCode != 0 {
		return nil, fmt.Errorf("%s: HTTP %d, code %d: %s", path, resp.StatusCode, envelope.ErrCode, envelope.ErrMsg+" "+envelope.ErrDlt)
	}
	return envelope.Data, nil
}

func token(path, admin string, payload any) (string, error) {
	raw, err := call(path, admin, payload)
	if err != nil {
		return "", err
	}
	var value struct {
		Token string `json:"token"`
	}
	if err := json.Unmarshal(raw, &value); err != nil {
		return "", err
	}
	if value.Token == "" {
		return "", fmt.Errorf("%s returned no token", path)
	}
	return value.Token, nil
}

func connect(user, token string) (*websocket.Conn, error) {
	base := os.Getenv("OPENIM_SMOKE_WS")
	if base == "" {
		base = "ws://127.0.0.1:10001/"
	}
	u, err := url.Parse(base)
	if err != nil {
		return nil, err
	}
	q := url.Values{"sendID": {user}, "token": {token}, "platformID": {"1"}, "operationID": {"platform-verification"}, "sdkType": {"js"}, "isBackground": {"false"}, "isMsgResp": {"true"}}
	u.RawQuery = q.Encode()
	dialer := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	ws, _, err := dialer.Dial(u.String(), nil)
	if err != nil {
		return nil, fmt.Errorf("WebSocket handshake failed for verification peer")
	}
	ws.SetReadDeadline(time.Now().Add(15 * time.Second))
	_, raw, err := ws.ReadMessage()
	if err != nil {
		ws.Close()
		return nil, err
	}
	var ack struct {
		ErrCode int `json:"errCode"`
	}
	if json.Unmarshal(raw, &ack) != nil || ack.ErrCode != 0 {
		ws.Close()
		return nil, fmt.Errorf("WebSocket login rejected")
	}
	return ws, nil
}

type response struct {
	ReqIdentifier int32  `json:"reqIdentifier"`
	ErrCode       int    `json:"errCode"`
	ErrMsg        string `json:"errMsg"`
	Data          []byte `json:"data"`
}

func send(ws *websocket.Conn, sender, receiver, id string) error {
	body, _ := json.Marshal(map[string]string{"content": id})
	message := &sdkws.MsgData{SendID: sender, RecvID: receiver, ClientMsgID: id, SenderPlatformID: 1, MsgFrom: 100, ContentType: 101, SessionType: 1, Content: body, CreateTime: time.Now().UnixMilli(), Options: map[string]bool{"history": true, "persistent": true, "senderSync": true, "conversationUpdate": true}}
	data, err := proto.Marshal(message)
	if err != nil {
		return err
	}
	frame, err := json.Marshal(map[string]any{"reqIdentifier": 1003, "sendID": sender, "operationID": id, "msgIncr": id, "data": data})
	if err != nil {
		return err
	}
	return ws.WriteMessage(websocket.BinaryMessage, frame)
}

func received(ws *websocket.Conn, expected string) (string, int64, error) {
	ws.SetReadDeadline(time.Now().Add(30 * time.Second))
	for {
		_, raw, err := ws.ReadMessage()
		if err != nil {
			return "", 0, err
		}
		var envelope response
		if err := json.Unmarshal(raw, &envelope); err != nil {
			return "", 0, err
		}
		if envelope.ErrCode != 0 {
			return "", 0, fmt.Errorf("message rejected: %d %s", envelope.ErrCode, envelope.ErrMsg)
		}
		if envelope.ReqIdentifier != 2001 {
			continue
		}
		var pushed sdkws.PushMessages
		if err := proto.Unmarshal(envelope.Data, &pushed); err != nil {
			return "", 0, err
		}
		for conversation, batch := range pushed.Msgs {
			for _, message := range batch.Msgs {
				if message.ClientMsgID == expected {
					return conversation, message.Seq, nil
				}
			}
		}
	}
}

func run() error {
	secret := os.Getenv("OPENIM_SECRET")
	if secret == "" {
		return fmt.Errorf("OPENIM_SECRET is required")
	}
	if base := os.Getenv("OPENIM_SMOKE_API"); base != "" {
		apiURL = base
	}
	admin, err := token("/auth/get_admin_token", "", map[string]any{"secret": secret, "userID": "imAdmin"})
	if err != nil {
		return err
	}
	prefix := fmt.Sprintf("verification%d", time.Now().UnixMilli())
	a, b := prefix+"a", prefix+"b"
	_, err = call("/user/user_register", admin, map[string]any{"users": []map[string]string{{"userID": a, "nickname": "验收账号 A"}, {"userID": b, "nickname": "验收账号 B"}}})
	if err != nil {
		return err
	}
	ta, err := token("/auth/get_user_token", admin, map[string]any{"userID": a, "platformID": 1})
	if err != nil {
		return err
	}
	tb, err := token("/auth/get_user_token", admin, map[string]any{"userID": b, "platformID": 1})
	if err != nil {
		return err
	}
	wa, err := connect(a, ta)
	if err != nil {
		return err
	}
	defer wa.Close()
	wb, err := connect(b, tb)
	if err != nil {
		return err
	}
	defer wb.Close()
	// The gateway publishes presence asynchronously after the handshake.
	deadline := time.Now().Add(10 * time.Second)
	for {
		raw, err := call("/user/get_users_online_status", admin, map[string]any{"userIDs": []string{a, b}})
		if err != nil {
			return err
		}
		var status []struct {
			Status int `json:"status"`
		}
		if err := json.Unmarshal(raw, &status); err != nil {
			return err
		}
		if len(status) == 2 && status[0].Status == 1 && status[1].Status == 1 {
			break
		}
		if time.Now().After(deadline) {
			return fmt.Errorf("verification peers did not become online: %s", raw)
		}
		time.Sleep(100 * time.Millisecond)
	}
	first := prefix + "-a-to-b"
	if err := send(wa, a, b, first); err != nil {
		return err
	}
	conversation, seq, err := received(wb, first)
	if err != nil {
		return fmt.Errorf("A to B delivery: %w", err)
	}
	if seq <= 0 {
		return fmt.Errorf("message delivered without persistent sequence")
	}
	second := prefix + "-b-to-a"
	if err := send(wb, b, a, second); err != nil {
		return err
	}
	if _, _, err := received(wa, second); err != nil {
		return fmt.Errorf("B to A delivery: %w", err)
	}
	wb.Close()
	wb, err = connect(b, tb)
	if err != nil {
		return fmt.Errorf("reconnect: %w", err)
	}
	defer wb.Close()
	raw, err := call("/msg/pull_msg_by_seq", tb, map[string]any{"userID": b, "seqRanges": []map[string]any{{"conversationID": conversation, "begin": seq, "end": seq, "num": 1}}})
	if err != nil {
		return err
	}
	if !bytes.Contains(raw, []byte(first)) {
		return fmt.Errorf("persisted message missing after reconnect")
	}
	fmt.Printf("PASS: authenticated A→B and B→A WebSocket delivery; persisted history after reconnect. Verification users: %s, %s\n", a, b)
	return nil
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
