# 媒体访问控制

2026-11-03：SRS 普通直播和 LiveKit 直播连接接入服务端观看权限。真实支付、打款及实名开关仍关闭。

## 行为

- Android 开播先向 Billd `POST /srs/publish-token` 申请绑定账号会话和直播间的票据。SRS `on_publish` 回调核对票据、当前登录会话、主播归属与 Revenue 开播权限；连麦观众不能冒充主播推普通直播。
- SRS 不再对外提供原始 HLS、HTTP-FLV、WebRTC、SRT 播放入口。HLS 写入共享卷，由 Nginx 对播放列表和每个 `.ts` 分片做鉴权，客户端须携带当前 Billd Bearer。媒体响应禁止缓存。SRS 管理 API 仅绑定宿主机回环地址。
- 首次获取付费媒体权限时，数据库记录持续 60 秒的试看窗口。退出、重连、服务重启或不发送 App 心跳均不会重置窗口。客户端心跳仍用于 UI 进度，服务端以较大的已上报进度或实际经过时间判断到期。这是持续窗口，不是可暂停累计的 60 秒。
- LiveKit 直播令牌绑定原直播场次，试看令牌有效期不超过剩余试看时间；现有连接由每 5 秒一轮的核对任务移除。PK 观众须同时具备两个场次的观看权限，不能通过免费一侧绕过付费一侧。当前付费解锁入口仍按单场次提供，需分别进入对应房间解锁。
- SRS 已连接主播也会定期核对并移除失效会话、关闭场次或不再允许开播的连接。Billd 重启后不信任遗留推流连接，主播需重新申请票据并连接。
- 媒体回调和 Nginx 子请求使用独立 `MEDIA_AUTH_KEY`，不复用账务内部服务密钥。权限服务故障时拒绝新播放请求；核对任务的执行时间及网络故障可能延长既有 LiveKit 连接的移除时间。

## 配置与兼容

`backend-compose.yml` 已包含受保护的 SRS 和 `media-gateway`。设置至少 32 位、仅含字母数字下划线短横线的随机 `MEDIA_AUTH_KEY`；保存在部署环境文件，不提交仓库。应用覆盖层并先执行 Revenue 迁移 `00022_media_preview.sql`。Billd 需使用锁文件安装依赖，包括封面重编码所需的 `sharp`。

新版 Android 会在 HLS 播放列表及分片请求中带登录头，并使用独立推流票据。更新服务端后旧 APK 无法继续匿名推流/播放，必须同步安装新版。开发网络仍使用 HTTP/RTMP，正式环境需配置 HTTPS/RTMPS。

现有 Debian 部署使用保留原配置的叠加文件，启动命令为：

```sh
cd /opt/live-platform
docker compose -p live-platform -f backend-compose.yml -f deploy/media-auth.compose.yml up -d --no-build
```

端口沿用 Billd `4300`、HLS `18088`、LiveKit `17880`；SRS 管理 API 为虚拟机本机 `127.0.0.1:11985`。数据库与原镜像回退点保存在虚拟机 `.deploy/before-media-auth-20261103/` 及 `*-before-media-auth:local` 镜像标签中，备份不上传 Git。

## 验证范围

Revenue/MySQL 验证无心跳超时、重连不重置、解锁后放行、封禁/关闭拒绝以及免费 PK 一侧不能绕过另一侧。Billd 回归包括真实 MySQL 和独立 LiveKit 信令/控制连接，验证试看撤权移除已连接观众。

Debian 独立测试容器使用真实 SRS 6、FFmpeg RTMP 推流及 Nginx 验证无效票据拒绝、有效推流生成 HLS、播放列表/分片鉴权、禁止 RTMP 匿名播放及缓存限制。该配置测试的鉴权服务为替身，真实权限逻辑另由路由及数据库测试覆盖，不能等同于 Android 真机整链路验收。日志在本机被忽略的 `.tools/media-*.out`。
