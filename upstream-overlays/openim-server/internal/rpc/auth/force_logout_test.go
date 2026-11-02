package auth

import (
	"context"
	"errors"
	"testing"

	"github.com/openimsdk/open-im-server/v3/pkg/common/storage/controller"
	"github.com/openimsdk/protocol/constant"
	"github.com/openimsdk/protocol/msggateway"
	"github.com/openimsdk/tools/discovery"
	"google.golang.org/grpc"
)

type logoutDiscovery struct {
	discovery.Conn
	connections []grpc.ClientConnInterface
}

func (d logoutDiscovery) GetConns(context.Context, string, ...grpc.DialOption) ([]grpc.ClientConnInterface, error) {
	return d.connections, nil
}

type logoutConnection struct {
	grpc.ClientConnInterface
	failure error
	calls   int
}

func (c *logoutConnection) Invoke(_ context.Context, _ string, args, _ any, _ ...grpc.CallOption) error {
	c.calls++
	request := args.(*msggateway.KickUserOfflineReq)
	if request.PlatformID != 1 || len(request.KickUserIDList) != 1 || request.KickUserIDList[0] != "42" {
		return errors.New("wrong logout target")
	}
	return c.failure
}

type logoutTokens struct {
	controller.AuthDatabase
	flags   map[string]int
	writes  int
	failure error
}

func (d *logoutTokens) GetTokensWithoutError(context.Context, string, int) (map[string]int, error) {
	return d.flags, nil
}
func (d *logoutTokens) SetTokenMapByUidPid(_ context.Context, _ string, _ int, flags map[string]int) error {
	d.flags = flags
	d.writes++
	return d.failure
}

func TestForceLogoutKeepsFailureVisibleAndInvalidatesTokens(t *testing.T) {
	gatewayFailure := errors.New("gateway unavailable")
	storeFailure := errors.New("token store unavailable")
	for _, scenario := range []string{"success", "gateway-failure", "store-failure", "no-gateway"} {
		t.Run(scenario, func(t *testing.T) {
			first, second := &logoutConnection{}, &logoutConnection{}
			tokens := &logoutTokens{flags: map[string]int{"one": constant.NormalToken, "two": constant.NormalToken}}
			conns := []grpc.ClientConnInterface{first, second}
			if scenario == "gateway-failure" {
				first.failure = gatewayFailure
			}
			if scenario == "store-failure" {
				tokens.failure = storeFailure
			}
			if scenario == "no-gateway" {
				conns = nil
			}
			server := &authServer{RegisterCenter: logoutDiscovery{connections: conns}, authDatabase: tokens, config: &Config{}}
			err := server.forceKickOff(context.Background(), "42", 1)
			if (err == nil) != (scenario == "success") {
				t.Fatalf("unexpected result: %v", err)
			}
			if scenario == "gateway-failure" && !errors.Is(err, gatewayFailure) {
				t.Fatal("gateway failure swallowed")
			}
			if scenario == "store-failure" && !errors.Is(err, storeFailure) {
				t.Fatal("token store failure swallowed")
			}
			if tokens.writes != 1 || tokens.flags["one"] != constant.KickedToken || tokens.flags["two"] != constant.KickedToken {
				t.Fatal("all tokens must be invalidated in one write, even on kick failure")
			}
			if scenario != "no-gateway" && (first.calls != 1 || second.calls != 1) {
				t.Fatal("failure must not skip remaining gateways")
			}
		})
	}
}
