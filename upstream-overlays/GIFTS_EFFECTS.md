# 礼物特效字段（Android / YYEVA）

`GET /revenue/gifts` 转发 Revenue `GET /v1/gifts/catalog`。礼物在 `data.gifts[]`。`coin_price` 与既有 `gift_id` 不变。未配置特效的旧礼物默认静态。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `effect_type` | string | 小写：`none` \| `yyeva` \| `svga` \| `lottie`。默认 `none`。全屏大礼物用 `yyeva` |
| `effect_url` | string | 素材地址。`yyeva` 必须是 AE 官方 YYEVA 插件导出的混合 MP4（路径以 `.mp4` 结尾）。`none` 时为空字符串 |
| `effect_video_mode` | string | 可选，原样交给 YYEVA 播放器。没有则为 `""` |
| `effect_md5` | string | 可选，完整性校验。没有则为 `""` |
| `icon_url` | string | 可选图标。没有则为 `""` |

不要使用大写 `YYEVA` 作为接口值。

送礼扣币成功后，Billd 向该直播间的 Socket.IO 房间发送 `giftAccepted`。事件里只有房间、单号、礼物 id、观众 id、数量和 `sender_name`，没有特效地址，也不会进入账本。客户端用目录里的 `effect_url` 决定怎么播：

- `yyeva` 且地址是 http(s) 的 `.mp4`：全屏只播一路，后面的大礼物排队。同一个单号在这台设备上只播一次。单路超过 20 秒仍未结束就结束这一条并播放下一条。
- 其他 `effect_type`：不进全屏队列。每笔已确认礼物都进一条横幅（送礼人、礼物、`×数量`），下一条等当前这条结束，避免后一笔把前一笔盖掉。数量只显示在横幅上，不把同一笔拆成多次全屏播放。
- 价格不低于 500 且没有可播放的 YYEVA 文件时，横幅期间用应用内的典藏动画。`svga` 和 `lottie` 仍不另起播放器。
- 未确认、保存到本地待重试、或服务端拒绝的送礼不播放。
- 刚加入房间的连接会收到该进程最近 8 秒、最多 3 笔已确认礼物，让晚一点进来的观众、主播和送礼人看到同一段效果。这是内存重放，不是账务记录；多进程部署不会跨进程补发。

给一条已有礼物挂上自己的 YYEVA 测试文件：

```sql
UPDATE gift_catalog
SET effect_type = 'yyeva',
    effect_url = 'https://your-host/effects/castle.mp4',
    effect_video_mode = '2',
    effect_md5 = ''
WHERE gift_id = 'CASTLE';
```

`effect_video_mode` 留空时播放器使用 MP4 内嵌布局；`1` 到 `5` 是左右或上下的透明通道排列。仓库不附带商业 YYEVA 成片。没有这个地址时，城堡、狮子这类高价礼物仍显示应用内典藏动画和横幅。
