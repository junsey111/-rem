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
