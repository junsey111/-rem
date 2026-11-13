# Backend selected from GitHub

The business backend is [`galaxy-s10/billd-live-server`](https://github.com/galaxy-s10/billd-live-server), checked out under `third_party/billd-live-server`.

Its upstream README lists SRS WebRTC/RTMP/HLS support, one-to-one/one-to-many/many-to-many PK, authentication, payment and orders, products, gifts, live-room management, private deployment, MySQL, Redis and Socket.IO. The repository is MIT licensed.

The upstream repository remains the base; product changes live in `upstream-overlays/billd-live-server` and are copied into the build checkout. The overlay adds the product APIs and environment based configuration while keeping deployment credentials out of source control. The Billd TypeScript project is built with `pnpm build`.

Start it when Docker Desktop is running:

    docker compose -f backend-compose.yml up --build

The backend listens on `http://localhost:4300`. It initializes its MySQL models before serving API requests. Configure real OAuth, Alipay and storage values in a private environment file before using those flows; example values are only for local development.

The Android app's room API base URL should point to this Billd backend. Monetary business operations are provided by the separately orchestrated GitHub service `third_party/live-revenue-integrity-lab` at port 8080. LiveKit/OpenIM remain separate infrastructure for RTC and private messaging.

The social-profile API stores optional profile details in `user_social_profile`. Normal Billd startup with `DB_SYNC_MODE=2` creates or updates the model; deployments using `DB_SYNC_MODE=3` must apply `upstream-overlays/billd-live-server/migrations/20261113_user_social_profile.sql` before serving these routes. Extended details are private by default and public reads return them only after the owner enables profile visibility.

Billd exposes a `/revenue` API gateway. It validates active Billd JWTs, syncs the authenticated user and live sessions over a private Docker network, and forwards gifts/ranking/wallet reads plus `GET /revenue/level`. The level gateway forwards to `GET /v1/users/me/level`; both levels are calculated server-side from accepted gift ledger entries. The revenue API also accepts HS256 Billd tokens and derives the gift sender from the token. Recharge is a payment callback and requires `X-Payment-Signature`, a base64url HMAC-SHA256 of `request_id:viewer_id:coins:payment_ref` using `PAYMENT_WEBHOOK_SECRET`.

The task center supports daily check-in, following users, moment interactions, one-time `first_follow` and `set_city` tasks, and live tasks for accepted gift quantity and verified session starts. Billd calculates social and profile progress from its own records; for `send_gift` and `start_live`, it calls the Revenue internal-key-protected `POST /v1/internal/task-progress`. One-time tasks use a reserved claim date so the existing claim ledger enforces lifetime rewards without a second wallet path. Apply Revenue migrations `00031_task_gift_progress_index.sql` and `00032_task_live_progress_index.sql` before enabling those Revenue-backed tasks on an existing database.

For private messaging, call `POST /openim/session` with the Billd `Authorization: Bearer <jwt>` header. The server registers or synchronizes the current user with the upstream OpenIM Server and returns a short-lived OpenIM user token plus the public API/WebSocket endpoints. `OPENIM_SECRET` and `OPENIM_ADMIN_USER_ID` remain server-only settings. Gift receipts and paid video unlocks are proxied to the implemented Revenue routes (`POST /v1/dm/gifts`, `GET /v1/dm/gifts/:request_id`, `POST /v1/dm/video-unlocks`, `GET /v1/dm/videos/:id/access`). Deploy Revenue with migration `00020_dm_settlement.sql` before enabling these flows; older deployments without the routes fail closed and Billd will not send a charged receipt or move a balance.

The payment flow is `GET /alipay/recharge?coins=100` for an authenticated user, followed by Alipay's `POST /alipay/notify`. The notification is verified with the Alipay SDK before the signed ledger recharge request is sent.

Verified in this checkout:

- HTTP `GET /live/list` for active rooms, plus the same route under `/admin`.
- Socket.IO room join/leave, live status, online-user presence, chat messages and WebRTC offer/answer/candidate signaling.
- `/alipay` is present as a payment integration entry point.

Billd itself does not contain the gift and wallet domain. Those functions are now supplied by `live-revenue-integrity-lab`, which has MySQL migrations, transactional wallet/gift services, Kafka outbox, Redis leaderboard projectors, and settlement reconciliation. Product/order catalog and payment-provider integration remain separate from the coin ledger; Billd's Alipay route is retained as the payment entry point and should call the signed recharge callback after a verified payment notification.

## SMS login

`POST /user/sms/send-code` and `POST /user/sms/login` implement SMS login and first-time account creation. SMS delivery stays disabled unless `SMS_ENABLED=true` and the configured HTTPS adapter, `SMS_PROVIDER_SECRET`, `SMS_PROVIDER_TEMPLATE_ID`, and stable `PHONE_HASH_SECRET` are present. The outbound adapter contract and rate limits are documented in `README.md`; SMS credentials belong only in the server's private environment. Apply `upstream-overlays/billd-live-server/migrations/20261113_user_phone_identity.sql` on existing databases. Normal startup with `DB_SYNC_MODE=2` creates the model automatically.
