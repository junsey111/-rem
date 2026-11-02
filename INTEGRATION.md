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
- OpenIM, Alipay settlement, moderation, PK/co-host and Android SRS broadcast/playback remain unverified or unavailable as noted above. Do not treat these as production-ready.

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
