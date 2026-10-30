# Upstream components

These repositories are cloned from GitHub and kept as the source bases for this app:

| Local path | Upstream | App responsibility |
| --- | --- | --- |
| `livekit-client-android` | https://github.com/livekit/client-sdk-android | Android WebRTC rooms, audio/video tracks, dual-host PK and calls |
| `livekit-server` | https://github.com/livekit/livekit | Self-hosted SFU for LiveKit rooms |
| `srs` | https://github.com/ossrs/srs | RTMP/WebRTC/HLS broadcast media server |
| `openim-sdk-core` | https://github.com/openimsdk/openim-sdk-core | OpenIM messaging core; upstream includes Android binding instructions |
| `openim-server` | https://github.com/openimsdk/open-im-server | OpenIM gateway, message sync and private-message service |
| `ZLMediaKit` | https://github.com/ZLMediaKit/ZLMediaKit | Optional SRS replacement for RTSP/GB28181 and protocol conversion |

ZLMediaKit is an optional SRS replacement when RTSP/GB28181/protocol conversion is needed:
https://github.com/ZLMediaKit/ZLMediaKit

## OpenIM Android AAR

The official OpenIM repository does not publish a separate Android SDK repository. Build the AAR from the checked out source as documented upstream:

```powershell
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
Set-Location third_party/openim-sdk-core
gomobile bind -androidapi 26 -v -trimpath -ldflags="-s -w" -o ./open_im_sdk.aar -target=android ./open_im_sdk/ ./open_im_sdk_callback/
```

Run `scripts/build-openim-aar.ps1`; it copies the generated `open_im_sdk.aar` to `app/libs/`, where Gradle loads it automatically. The app does not ship fake OpenIM credentials.

## Runtime wiring

- `SRS_URL` is the broadcaster RTMP/WebRTC ingest and viewer playback configuration.
- `LIVEKIT_URL` and a short-lived token are used for PK/voice/video rooms.
- `OPENIM_API_URL`, `OPENIM_WS_URL`, and a user token are used for private messages.
- Coins, gifts, ranks, revenue, and admin actions belong in the application API and must be authorized server-side.
