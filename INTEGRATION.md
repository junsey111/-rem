# GitHub upstream integration

The Android app uses upstream open-source projects as its infrastructure:

```text
Android app
  ├─ LiveKit Android SDK -> self-hosted LiveKit SFU -> dual-host PK / voice / video
  ├─ SRS (or ZLMediaKit) -> RTMP/WebRTC/HLS live broadcast
  └─ OpenIM SDK Core      -> OpenIM Server -> private messages and chat
```

`app/src/main/java/cn/liveroom/app/LiveKitRoomGateway.kt` adapts the official LiveKit Android `Room` API. Room access uses short-lived credentials issued by the Billd application gateway.

## What remains server-side

The app must call an application API for login, room discovery, gift purchase, coin balance, host revenue, daily/monthly ranking, moderation, and LiveKit/OpenIM token issuance. Those operations must be authorized on the server. The Android client must never contain the LiveKit API secret, OpenIM administrator secret, or payment secret.

## Android integration status

The Android client uses the Billd application gateway for registration/login and JWT persistence, live-room discovery, Revenue gift catalog and gift submission, wallet balance, user/creator levels, daily/monthly creator and contributor rankings, creator earnings, Alipay recharge URL creation, live-session start/close, LiveKit token issuance, and Billd Socket.IO host-room registration. Gift and wallet changes are accepted only after the server response; the client does not maintain a local ledger. The OpenIM SDK adapter is present in the Android app and uses Billd-issued user credentials. Direct messages can also carry a server-built gift receipt or a free/paid video message. Billd forwards gift purchase and paid unlock to Revenue and returns HTTP 501 when those Revenue routes are not deployed yet. The Android client sends the custom message only after that hook returns a payload, and it does not apply a local balance change. `OPENIM_SUPPORT_USER_ID` opens a real support conversation; an empty value hides the entry.

The debug APK can be built for the VMware backend with:

```powershell
$argsList = @(':app:assembleDebug', '-PbackendHost=192.168.254.132', '-PsrsApiPort=11985', '-PsrsPlaybackPort=18088', '-PlivekitPort=17880', '-PopenImApiPort=10002', '-PopenImWsPort=10001')
& .tools/gradle-8.10.2/bin/gradle.bat @argsList
```

The VMware check on 2026-11-01 confirms Billd on `4300` and LiveKit on `17880` (HTTP 200). OpenIM is not running: its Compose image pull failed because the VM could not reach Docker Hub, so messaging is not yet available end to end. SRS is configured on `11985/18088`; broadcast ingest/playback has not been verified from an Android device. The operations tab currently shows online rooms only; this backend checkout does not expose moderation, bans, or aggregate operations endpoints. PK/co-host controls are not implemented. Alipay can only complete payment after production merchant credentials and callback configuration are supplied and a real sandbox/production payment is verified.

## Verification snapshot

- `:app:assembleDebug` succeeds with VMware endpoint properties, and the APK installs and launches on the connected Android emulator.
- Billd registration/login, wallet, levels, earnings, rankings, live start/close, and LiveKit credential issuance were previously exercised against the VMware backend with a temporary account.
- LiveKit's VMware HTTP endpoint responds successfully. A full camera/microphone publish and subscriber playback session has not been verified in this run.
- OpenIM, Alipay settlement, moderation, and PK/co-host remain unverified or unavailable as noted above. Ordinary host RTMP ingest is implemented and covered by unit tests for the URL and camera-ownership decision; a device session that publishes to SRS and plays the HLS URL was not run here. Do not treat these as production-ready.

## Ordinary host ingest

When the host opens or joins their own ordinary live, the app publishes the camera and microphone to SRS with RootEncoder (`RtmpCamera2`) over RTMP:

```text
rtmp://<host>:<srsRtmpPort>/live/{liveSessionId}
```

`<host>` is the same `backendHost` as the other service URLs. `srsRtmpPort` defaults to `1935` (override with `-PsrsRtmpPort=`). `BuildConfig.SRS_RTMP_URL` / `UpstreamServices.srsIngestUrl` is that origin only — no app path and no credentials. `{liveSessionId}` is the Revenue live session id (Billd room id). `ossrs/srs:6` with `conf/docker.conf` enables HLS from that RTMP app/stream, so viewers play `{SRS_PLAYBACK_URL}/live/{liveSessionId}.m3u8`.

The device camera is opened by only one publisher at a time:

- Ordinary live (no active PK, no accepted or muted co-host seat): SRS RTMP owns the camera and microphone. LiveKit still connects so a match or co-host can start, but it does not publish local camera or microphone.
- Active PK, or an accepted/muted co-host seat: RTMP ingest stops and releases the camera, then `LiveKitRoomGateway.publishCameraAndMicrophone()` runs as before. When that session ends, RTMP ingest resumes.

PK subscribers and accepted co-hosts stay on LiveKit. Ordinary viewers do not see a picture until this RTMP publish is active.

## Ordinary viewer playback

Non-interactive viewers play HLS with Media3 ExoPlayer from `BuildConfig.SRS_PLAYBACK_URL` (the HTTP server, container port 8080; default host port `8088` / VMware `18088`). The playlist is:

```text
{SRS_PLAYBACK_URL}/live/{liveSessionId}.m3u8
```

`liveSessionId` is the Billd `/live/list` `roomId`, which is the Revenue live session id. `ossrs/srs:6` `conf/docker.conf` enables HLS on the default vhost and serves `[app]/[stream].m3u8`. This client assumes app `live` and stream `{liveSessionId}`. `/live/list` does not return a play URL. A missing base URL or a non-http(s) origin does not invent a stream; playback errors stay on screen.

Ordinary viewers do not join LiveKit. The host camera for that same `live/{liveSessionId}` is the RTMP ingest above, not LiveKit. LiveKit publishes the host camera only after RTMP has stopped, for an active PK or an accepted/muted co-host seat. HTTP-FLV (`.flv` on the same app/stream) is what SRS http_remux serves; this player does not use it.

## Local service choices

- Start with **SRS** for ordinary broadcast ingest and playback.
- Use **LiveKit** for interactive dual-host PK and private-message 1v1 audio/video calls.
- Use **OpenIM Server** plus the AAR generated from `third_party/openim-sdk-core` for private messages. OpenIM carries the call invite only; it does not carry media.
- Choose **ZLMediaKit** instead of SRS when protocol conversion or GB28181/RTSP is required; running both for the same stream is unnecessary.

## Private 1v1 call

`POST /livekit/calls`, `POST /livekit/calls/join`, and `POST /livekit/calls/end` issue a 10-minute LiveKit token for two authenticated Billd users. Both participants can publish. `mode` is `video` (`microphone` + `camera`) or `voice` (`microphone` only). The room name is `dm-` plus 32 hex characters, so the live/PK reconciler does not delete it. `LIVEKIT_API_KEY` and `LIVEKIT_API_SECRET` stay on the server. The response `url` is `LIVEKIT_URL` (emulator `ws://10.0.2.2:7880`, VMware `ws://192.168.254.132:17880`). The Android call screen dials `BuildConfig.LIVEKIT_URL` from `-PbackendHost` and `-PlivekitPort` instead, because the token is not bound to a host.

OpenIM hook: `signal_transport` is `openim-c2c-text` and `signal_text` is `dm-call:v1 call_id=<32 hex> mode=video|voice from=<caller id>`. The app sends that string with the existing `OpenImGateway.sendText` on the current conversation. The callee can tap 接听 or paste the call id. Replace `sendText` with a custom OpenIM content type later; do not add a second chat service. Joining with `call_id` does not require the message to arrive.

Verify the grants without a device, from `upstream-overlays/billd-live-server` after `pnpm build`:

```bash
node --test tests/dm-call.test.cjs tests/dm-call-router.test.cjs tests/livekit-permissions.test.cjs
```

The first test signs real tokens and checks that both users can publish, voice cannot publish camera, a third user is rejected, and `dm-` rooms do not match the live/PK room pattern. Dual-device audio and video still need the VMware LiveKit on port `17880`. Build the APK with `-PbackendHost=192.168.254.132 -PlivekitPort=17880`, open 消息, and start 视频通话 or 语音通话. Hang up disconnects the room and calls `/livekit/calls/end`.
