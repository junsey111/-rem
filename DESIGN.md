# 现场 Live visual system

## Direction

The Android client uses a night-stage system for viewers, streamers, and operators. The shell stays native Material 3: bottom navigation on compact screens, standard chips and sheets, and safe-area aware content.

The visual world contributes four things:

- **Palette:** ink black surfaces for a low-light viewing scene, coral for primary actions and failures, mint for live/healthy state, and amber for moderation or ranking emphasis.
- **Type:** Material 3's Roboto scale with tight titles and calm body copy for repeated scanning.
- **Density:** compact rows and short status lines so room state, host, category, and viewer count can be compared quickly.
- **Signature move:** every live state uses the same small pulse dot and status line, from the top bar through room cards and the operations list.

## Surface map

- Live: service health, room count, category filters, live room list, empty/error state, and interaction tools.
- Room sheet: HLS player, host status, wallet balance, gifts, PK/voice/video actions.
- Rankings: daily/monthly creator ranking.
- Messages: OpenIM conversation list.
- Profile: user/creator levels, wallet, earnings, and shortcuts.
- Operations: live count, user count, gift volume, moderation queue, and room management.

## Content truth

Room discovery is read from Billd `/live/list`. Wallet, gifts, earnings, rankings, moderation, OpenIM, and LiveKit credentials remain server-owned. The UI labels local values as demo feedback where the corresponding business endpoint is not yet wired.
