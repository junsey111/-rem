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
- Use **LiveKit** for interactive dual-host PK and audio/video calls.
- Use **OpenIM Server** plus the AAR generated from `third_party/openim-sdk-core` for private messages.
- Choose **ZLMediaKit** instead of SRS when protocol conversion or GB28181/RTSP is required; running both for the same stream is unnecessary.
