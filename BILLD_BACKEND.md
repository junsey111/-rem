# Backend selected from GitHub

The business backend is [`galaxy-s10/billd-live-server`](https://github.com/galaxy-s10/billd-live-server), checked out under `third_party/billd-live-server`.

Its upstream README lists SRS WebRTC/RTMP/HLS support, one-to-one/one-to-many/many-to-many PK, authentication, payment and orders, products, gifts, live-room management, private deployment, MySQL, Redis and Socket.IO. The repository is MIT licensed.

The upstream source remains intact. The integration adds `src/config/secret.ts`, which reads deployment secrets from environment variables, and two small TypeScript compatibility fixes: missing Bilibili environment exports and validation for the GitHub repository query. `pnpm build` passes.

Start it when Docker Desktop is running:

    docker compose -f backend-compose.yml up --build

The backend listens on `http://localhost:4300`. It initializes its MySQL models before serving API requests. Configure real OAuth, Alipay and storage values in a private environment file before using those flows; example values are only for local development.

The Android app's room API base URL should point to this Billd backend. Monetary business operations are provided by the separately orchestrated GitHub service `third_party/live-revenue-integrity-lab` at port 8080. LiveKit/OpenIM remain separate infrastructure for RTC and private messaging.

Billd exposes a `/revenue` API gateway. It validates active Billd JWTs, syncs the authenticated user and live sessions over a private Docker network, and forwards gifts/ranking/wallet reads plus `GET /revenue/level`. The level gateway forwards to `GET /v1/users/me/level`; both levels are calculated server-side from accepted gift ledger entries. The revenue API also accepts HS256 Billd tokens and derives the gift sender from the token. Recharge is a payment callback and requires `X-Payment-Signature`, a base64url HMAC-SHA256 of `request_id:viewer_id:coins:payment_ref` using `PAYMENT_WEBHOOK_SECRET`.

For private messaging, call `POST /openim/session` with the Billd `Authorization: Bearer <jwt>` header. The server registers or synchronizes the current user with the upstream OpenIM Server and returns a short-lived OpenIM user token plus the public API/WebSocket endpoints. `OPENIM_SECRET` and `OPENIM_ADMIN_USER_ID` remain server-only settings.

The payment flow is `GET /alipay/recharge?coins=100` for an authenticated user, followed by Alipay's `POST /alipay/notify`. The notification is verified with the Alipay SDK before the signed ledger recharge request is sent.

Verified in this checkout:

- HTTP `GET /live/list` for active rooms, plus the same route under `/admin`.
- Socket.IO room join/leave, live status, online-user presence, chat messages and WebRTC offer/answer/candidate signaling.
- `/alipay` is present as a payment integration entry point.

Billd itself does not contain the gift and wallet domain. Those functions are now supplied by `live-revenue-integrity-lab`, which has MySQL migrations, transactional wallet/gift services, Kafka outbox, Redis leaderboard projectors, and settlement reconciliation. Product/order catalog and payment-provider integration remain separate from the coin ledger; Billd's Alipay route is retained as the payment entry point and should call the signed recharge callback after a verified payment notification.
