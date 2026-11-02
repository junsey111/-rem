# 邀请送币 / Invite coin rewards

Billd 在 `Authorization: Bearer <Billd JWT>` 下把请求转到 Revenue。成功时 HTTP 体包在 Billd 的 `data` 里，字段与 Revenue 一致。失败是 HTTP 4xx，Revenue 体为 `{ "code", "message" }`，并且没有钱包分录。Billd 兑换失败时原样转出这个 `code` 和 `message`。

活动榜 `GET /revenue/campaigns/{id}/leaderboard` 不是邀请码，不能当作拉新。

| 方法 | Billd | Revenue | 作用 |
| --- | --- | --- | --- |
| GET | `/revenue/referrals/me` | `/v1/referrals/me` | 是否开通，以及本人邀请码、邀请人数、作为邀请人获得的金币 |
| POST | `/revenue/referrals/redeem` | `/v1/referrals/redeem` | 用邀请码兑换。正文只要 `request_id` 和 `code` |

`GET` 在关闭时只返回 `{ "enabled": false }`，不编造邀请码。开通后返回 `enabled`、`code`、`invited_count`、`rewarded_coins`。`rewarded_coins` 只累计本人作为邀请人拿到的金币，被邀请人自己拿到的金币只进钱包余额。

`POST` 的 `request_id` 是 UUID v4。幂等键是 `(invitee, request_id)`。同一正文再次请求返回同一张回执，`idempotency` 为 `replayed`，余额不变。同一单号换成另一个邀请码返回 409 `IDEMPOTENCY_PAYLOAD_MISMATCH`。邀请码不区分大小写。

规则：

- 不能兑换自己的码：409 `SELF_REFERRAL`。
- 码必须已存在：404 `REFERRAL_CODE_NOT_FOUND`。
- 每个被邀请人只能成功兑换一次。再次用新单号兑换返回 409 `REFERRAL_ALREADY_REDEEMED`。
- 功能关闭时兑换返回 403 `REFERRALS_DISABLED`。已经成功的同一单号在关闭后重放，仍返回原回执。
- 上述拒绝都不写钱包分录，也不占住 `request_id`。修正邀请码或重新开通后，可以用原单号再试。
- 邀请人奖励和被邀请人奖励来自 `REFERRAL_INVITER_COINS`、`REFERRAL_INVITEE_COINS`，范围 0 到 100000。开通后的默认值是邀请人 100、被邀请人 50。某一侧为 0 时那一侧不写分录。非法配置会关掉功能。
- 默认 `REFERRALS_ENABLED` 未设置，接口保持关闭。设置为 `true` / `1` / `yes` / `on` 才开通。

金币只走现有 `COIN` 钱包。`USER_COINS:{user}` 增加，`SYSTEM_REFERRAL_SOURCE` 等额减少，与充值的 `SYSTEM_TOPUP_SOURCE` 同一套复式记账，不另开钱包。两边的入账写在同一张 `REFERRAL_REWARD` 流水和同一个数据库事务里；任一步失败则整笔回滚，不留部分分录。这笔流水不进入直播礼物单、付费观看或私信结算。
