# 私信礼物与付费视频结算

Billd 在 `Authorization: Bearer <Billd JWT>` 下把请求原样转到 Revenue。成功时 HTTP 体就是回执本身，不要再包一层 `data`。失败是 HTTP 4xx，体为 `{ "code", "message" }`，并且没有钱包分录。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/v1/dm/gifts` | 扣观众金币，给收件人记钻石 |
| GET | `/v1/dm/gifts/:requestId` | 只返回该登录观众已接受的同一张回执；没有则 404 |
| POST | `/v1/dm/video-unlocks` | 按请求里的 `price_coins` 结算解锁。Revenue 不保存视频文件 |
| GET | `/v1/dm/videos/:id/access` | `{ "unlocked": boolean, "video_id" }`。未解锁也是 200 |

送礼幂等键是 `(viewer_id, request_id)`。同一正文返回同一张回执，`idempotency` 为 `replayed`。正文冲突返回 409 `IDEMPOTENCY_PAYLOAD_MISMATCH`。`gift_order_id` 是非空字符串。`gift_name` 最长 40 个字符。

余额不足不落拒绝单，所以同一 `request_id` 在充值后可以再付。其余送礼拒绝（`SELF_GIFT`、`GIFT_NOT_FOUND`、`RECIPIENT_NOT_ELIGIBLE`）会记下，重放仍是原错误且不扣币。收件人沿用直播送礼的主播资格（账号状态、年龄、地区、可收礼）。

视频以 `video_id` 为不透明字符串。第一次成功结算记下 owner 和价格；之后价格不一致返回 `PRICE_MISMATCH`，主人不一致返回 `VIDEO_NOT_FOUND`，都不扣币。`price_coins == 0` 只记解锁、不写账。同一观众对同一视频只扣一次；再次请求 `charged_coins` 为 0 且 `idempotency` 为 `replayed`。视频主人读取 access 时 `unlocked` 为 true。余额不足同样不占住 `request_id`。

金币只走现有 `COIN` 钱包：观众 `USER_COINS` 减少，`PLATFORM_RESERVED_COINS` 等额增加，与直播礼物相同。收件人钻石记在 `dm_gift_orders.diamond_reward`（目录钻石 × 数量）和付费解锁的 `dm_video_unlocks.diamond_reward`（等于实扣金币），并计入 `GET /v1/creators/me/earnings` 和用户等级。不另开钻石钱包。可提现仍只来自已对账的直播场次结算。私信流水不进入直播礼物单、PK 或场次对账。
