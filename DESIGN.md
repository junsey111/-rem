# 现场 Live visual system

## Direction

The Android client uses a commerce-grade, content-first system for viewers, streamers, and operators. It follows the patterns that make large consumer apps easy to use: show the valuable content first, keep one primary action per surface, reveal advanced controls only in context, and make every monetary or live-state change explicit. The shell stays native Material 3: bottom navigation on compact screens, standard chips and sheets, and safe-area aware content.

The visual world contributes four things:

- **Palette:** an editorial off-white canvas and near-black content surfaces for strong hierarchy, coral for the single primary action and failures, mint for live/healthy state, and amber for ranking or moderation emphasis.
- **Type:** Material 3's Roboto scale with tight titles and calm body copy for repeated scanning.
- **Density:** generous first-glance spacing and compact comparison rows; room title and host lead, technical IDs stay out of the consumer path.
- **Signature move:** every surface has one clear next action, while live state uses the same small pulse dot and short status line.

## Surface map

- Live: content-first discovery, live room list, honest empty/error state, and a single prominent start-live action.
- Live room: full-window black video canvas with safe-area-aware overlays; gifts and real-host PK selection in contextual sheets. Host exit requires confirmation.
- Rankings: daily/monthly creator ranking.
- Messages: automatic connection, conversation-first list, contextual message composer, explicit connection retry.
- Profile: user/creator levels, wallet, earnings, and shortcuts.
- Operations: server-authorized statistics and account pages, plus live-session management and audited forced closure. Select a real open session, enter a reason, confirm the irreversible action, and retain the request identifier until the server confirms its outcome. Pending operations survive app restarts; empty/error states never substitute fake records. Ban/unban and review workflows remain unavailable.

## Content truth

Room discovery is read from Billd `/live/list`. Wallet, gifts, earnings, rankings, moderation, OpenIM, and LiveKit credentials remain server-owned. Unavailable data stays unavailable. No demo balances, fake hosts, successful payment claims, or healthy-state indicators are substituted.
