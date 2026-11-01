# GitHub upstream integration

The Android app uses upstream open-source projects as its infrastructure:

```text
Android app
  ├─ LiveKit Android SDK -> self-hosted LiveKit SFU -> dual-host PK / voice / video
  ├─ SRS (or ZLMediaKit) -> RTMP/WebRTC/HLS live broadcast
  └─ OpenIM SDK Core      -> OpenIM Server -> private messages and chat
```

`app/src/main/java/cn/liveroom/app/LiveKitRoomGateway.kt` is the app-side adapter around the official LiveKit Android `Room` API. The current UI deliberately keeps the room screen usable without credentials; the gateway is ready for the application API to provide a short-lived token.

## What remains server-side

The app must call an application API for login, room discovery, gift purchase, coin balance, host revenue, daily/monthly ranking, moderation, and LiveKit/OpenIM token issuance. Those operations must be authorized on the server. The Android client must never contain the LiveKit API secret, OpenIM administrator secret, or payment secret.

## Android integration status

The Android client now uses the Billd application gateway for real registration/login and JWT persistence, live-room discovery, Revenue gift catalog and gift submission, wallet balance, user/creator levels, daily/monthly creator and contributor rankings, creator earnings, Alipay recharge URL creation, live-session start/close, LiveKit token issuance, and Billd Socket.IO host-room registration. Gift and wallet changes are accepted only after the server response; the client does not maintain a local ledger.

The debug APK can be built for the VMware backend with:

```powershell
$argsList = @(':app:assembleDebug', '-PbackendHost=192.168.254.132', '-PsrsApiPort=11985', '-PsrsPlaybackPort=18088', '-PlivekitPort=17880', '-PopenImApiPort=10002', '-PopenImWsPort=10001')
& .tools/gradle-8.10.2/bin/gradle.bat @argsList
```

The current VM check confirms Billd on `4300` and SRS on `11985/18088`; the VM currently does not expose LiveKit on `17880` or OpenIM on `10002/10001`. Until those services are reachable, LiveKit camera/microphone rooms and OpenIM messages cannot pass end to end. The Android UI reports those failures instead of displaying demo success. The operations tab also reports that this backend checkout does not expose moderation, bans, or aggregate operations endpoints.

## Local service choices

- Start with **SRS** for ordinary broadcast ingest and playback.
- Use **LiveKit** for interactive dual-host PK and audio/video calls.
- Use **OpenIM Server** plus the AAR generated from `third_party/openim-sdk-core` for private messages.
- Choose **ZLMediaKit** instead of SRS when protocol conversion or GB28181/RTSP is required; running both for the same stream is unnecessary.
