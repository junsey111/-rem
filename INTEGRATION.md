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

## Local service choices

- Start with **SRS** for ordinary broadcast ingest and playback.
- Use **LiveKit** for interactive dual-host PK and audio/video calls.
- Use **OpenIM Server** plus the AAR generated from `third_party/openim-sdk-core` for private messages.
- Choose **ZLMediaKit** instead of SRS when protocol conversion or GB28181/RTSP is required; running both for the same stream is unnecessary.
