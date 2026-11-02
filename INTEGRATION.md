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

The Android client uses the Billd application gateway for registration/login and JWT persistence, live-room discovery, Revenue gift catalog and gift submission, wallet balance, user/creator levels, daily/monthly creator and contributor rankings, creator earnings, Alipay recharge URL creation, live-session start/close, LiveKit token issuance, and Billd Socket.IO host-room registration. Gift and wallet changes are accepted only after the server response; the client does not maintain a local ledger. The OpenIM SDK adapter is present in the Android app and uses Billd-issued user credentials.

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
- Use **LiveKit** for interactive dual-host PK and audio/video calls.
- Use **OpenIM Server** plus the AAR generated from `third_party/openim-sdk-core` for private messages.
- Choose **ZLMediaKit** instead of SRS when protocol conversion or GB28181/RTSP is required; running both for the same stream is unnecessary.
