# 付费观看 60 秒试看（Android 联调）

Billd 前缀 `http://<billd>/revenue`。三个轮询接口都要 `Authorization: Bearer <Billd JWT>`。成功时业务字段在 `data` 里，不要改字段名。

```json
{
  "code": 200,
  "data": {
    "live_id": "501",
    "free_preview_seconds": 60,
    "watched_unpaid_seconds": 0,
    "seconds_remaining": 60,
    "unlocked": false,
    "unlock_price_coins": 10,
    "status": "PREVIEW"
  }
}
```

`liveId` 是账务直播场次 id（十进制字符串），与 `POST /revenue/lives/start` 返回的 `live_session_id` 相同。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/revenue/lives/:liveId/watch-access` | 当前试看状态 |
| POST | `/revenue/lives/:liveId/watch-heartbeat` | body `{ "request_id": "<uuid-v4>", "delta_seconds": 1..30 }`。超出 1..30 由服务端夹紧。同一 `request_id` 只计一次。仅 `PREVIEW` 增加 `watched_unpaid_seconds`，且不超过 60 |
| POST | `/revenue/lives/:liveId/watch-unlock` | body `{ "request_id": "<uuid-v4>" }`。成功后 `unlocked=true`，`status=UNLOCKED`。余额不足 HTTP **402**，`message` 为「余额不足，无法解锁付费观看，本次未扣币」，不扣币；充值后可沿用同一 `request_id` 再试 |

`status`：`PREVIEW` | `LOCKED` | `UNLOCKED` | `FREE`。

- `unlock_price_coins == 0`：`FREE`（免费房）。心跳不累加未付费时长。
- 未解锁且价格大于 0，已看不足 60 秒：`PREVIEW`。`seconds_remaining = max(0, 60 - watched_unpaid_seconds)`。
- 未解锁且已看满 60 秒：`LOCKED`，`seconds_remaining = 0`。
- 已解锁：`UNLOCKED`。主播查看自己的付费房也是 `UNLOCKED`，不会扣自己的币。

主播设置价格（不是观众轮询接口）：`PUT /revenue/lives/:liveId/watch-price`，body `{ "unlock_price_coins": 10 }`。`0` 表示免费。未设置时按免费房。

Revenue 对应路径：`/v1/lives/:id/watch-access|watch-heartbeat|watch-unlock|watch-price`。
