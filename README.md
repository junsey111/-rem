# 直播间 Android

这是基于 GitHub 上游项目拼装的 Android Kotlin App，目标功能来自需求图片：直播间、主播/用户等级、礼物和金币、主播收益、日/月榜、双人 PK、语音/视频互动、私信和运营入口。业务后端优先采用 GitHub 的 Billd Live Server，而不是另起一套自写后端。

## 合法使用与责任边界

项目作者仅支持本项目用于合法的软件开发、测试和运营。使用者不得将本项目或其部署服务用于诈骗、盗窃、洗钱、侵犯隐私、未授权访问、网络攻击、传播违法内容、侵犯知识产权、规避监管或其他违反适用法律法规的行为。项目作者和贡献者不授权、鼓励、指示或参与任何违法使用，也不代表或控制使用者的行为。

使用者对自己的部署、修改、数据、用户内容、账号权限、支付流程、直播内容、隐私保护、内容审核和法律合规承担全部责任。使用者不得以本项目作者、贡献者或上游项目名义对外作出承诺，也不得把作者或贡献者列为其运营服务的责任主体。

本项目按 `LICENSE` 提供，不提供任何明示或默示担保。因使用、修改、部署本项目，或因第三方服务、用户行为、数据泄露、支付争议、内容侵权、违法活动或监管要求产生的损失和责任，在适用法律允许的最大范围内由使用者自行承担，项目作者和贡献者不承担责任。法律强制规定不得排除的责任除外。

本项目包含 `third_party` 下的上游代码和依赖，它们继续适用各自的许可证、版权声明和使用条款；根目录许可证不会替代或覆盖这些条款。使用前请逐项检查上游许可证、商标、支付服务、媒体服务和部署地区的法律要求。以上内容是项目使用政策，不构成法律意见。

本仓库自有代码和文档（不含 `third_party` 下的上游代码和依赖）采用 Apache License 2.0，详见 [`LICENSE`](LICENSE)。标准开源许可证不能替代使用者应遵守的法律，也不能保证第三方不会滥用软件；责任边界以许可证、项目使用政策和适用法律共同确定。

## 已拉取的上游代码

代码在 `third_party` 下，版本见 `UPSTREAM.lock`。

`third_party/rem` 已通过 SSH 拉取自 `https://github.com/junsey111/-rem.git`；该仓库当前为空（没有 commit），所以暂时没有可接入的源码。

- SRS：普通直播推流、WebRTC/HLS/RTMP 播放。
- LiveKit Android SDK + LiveKit Server：双人 PK、连麦和实时音视频。
- OpenIM SDK Core + OpenIM Server：私信和聊天。
- ZLMediaKit：SRS 的可选替代媒体底座。
- Billd Live Server：上游提供用户、直播房间、Socket.IO 信令和支付宝入口；本仓库覆盖层补充账号会话、社交动态与审核、通知、直播权限、运营 API，并通过 Live Revenue Integrity Lab 提供钱包、礼物、榜单和结算。支付、实名仍按后端环境开关控制，不能以接口存在推断真实渠道已启用。
- Billd Live Kotlin：已拉取到 `third_party/billd-live-kotlin` 供复用；当前是 Android Studio 模板级工程，首页是占位页，API 默认连作者线上服务，不能直接作为完整客户端。版本见 `UPSTREAM.lock`。
- Live Revenue Integrity Lab：已拉取到 `third_party/live-revenue-integrity-lab`，作为业务账务服务。它提供充值、礼物扣币、双重记账、PK 比分、贡献榜/活动榜和直播结算对账 API，版本见 `UPSTREAM.lock`。

Revenue 的受保护接口在验证 Billd JWT 后，还会用同一个 Bearer 实时请求 Billd `/user/me`；只有当前有效、未禁用的账号才能继续访问钱包、送礼、直播、PK、资料、等级、榜单和结算。退出、重新登录替换令牌、账号禁用或 Billd 权威服务不可用时，业务请求会拒绝，不会仅凭旧 JWT 继续扣款或改变状态。

## 构建 APK

```powershell
& .tools/gradle-8.10.2/bin/gradle.bat :app:assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

本地开发可运行 `node scripts/dev-auto-update.cjs`。它监视 Android 源码变化，自动递增版本号、构建 APK、在 `127.0.0.1:4310` 提供更新清单和安装包，并安装到已连接的 Android 模拟器。运行 `scripts/install-dev-auto-update.ps1` 可注册当前用户登录后自动启动。App 启动时及运行期间每小时检查更新；其他设备仍需在 Android 系统界面确认安装。对外发布需要将更新服务部署到设备可访问的地址，并使用稳定的正式签名构建 APK；本地调试包及 `10.0.2.2` 地址仅适用于这台电脑的模拟器。

## 启动媒体服务

SRS 官方开发启动方式：

```powershell
docker run --rm -it -p 1935:1935 -p 1985:1985 -p 8088:8080 `
  -p 8000:8000/udp -p 10080:10080/udp ossrs/srs:6
```

LiveKit 服务端现在由统一后端 Compose 启动；单独运行时也可以使用官方开发模式：

```powershell
docker run --rm -it -p 7880:7880 -p 7881:7881 -p 7882:7882/udp livekit/livekit-server --dev
```

OpenIM 服务本体已通过上游 Dockerfile 构建并加入 `third_party/openim-server/docker-compose.yml`；容器配置通过官方 `IMENV_*` 环境覆盖连接其 MongoDB、Redis、etcd、Kafka 和 MinIO 依赖。统一启动脚本会等待 OpenIM API `10002` 与消息 WebSocket `10001` 端口。

OpenIM 用户注册和 `/auth/get_user_token` 由 Billd 的 `POST /openim/session` 代理。客户端先用 Billd JWT 调用此接口，服务端按 Billd 用户 ID 注册/同步 OpenIM 用户并签发用户 token；`OPENIM_SECRET` 仅在服务端环境设置，不放入 Android 或浏览器。OpenIM 管理员 ID 默认 `imAdmin`，需与 `third_party/openim-server/config/share.yml` 中的管理员配置一致。

消息会话现在通过 `openim_session` 表绑定当前 App 登录会话的 SHA-256 摘要；同一会话重复请求会先核实并复用有效消息令牌。签发前先提交待处理记录，远程签发超时或本地提交失败后仍能撤销孤立令牌。后台每轮完成后等待 5 秒，按数据库账号状态、当前令牌及期限核对；失效、禁用、删除或被替换的会话调用 OpenIM 强制下线，失败保留记录重试。新迁移 `20261102_openim_sessions.sql` 必须先执行；既有未绑定的 Android 消息令牌部署时重置一次。仅覆盖配置的 Android platform ID 与本应用记录，其他独立客户端不在本轮范围。

私信里的礼物和视频是 OpenIM 自定义消息（`dm_gift`、`dm_video`），不是第二套钱包。Android 先调用 Billd，再把 Revenue 已确认的 `im_custom` 原样发给对方。Revenue 仓库已实现 `POST /v1/dm/gifts`、`GET /v1/dm/gifts/:request_id`、`POST /v1/dm/video-unlocks` 和 `GET /v1/dm/videos/:id/access`；私信结算表由 `00020_dm_settlement.sql` 创建。部署时必须升级 Revenue 并应用该迁移，旧版或缺少路由的服务会 fail closed，不会发送带扣款结果的私信消息，也不会在 Billd 记账。Billd 路径可通过 `REVENUE_DM_GIFTS_PATH`、`REVENUE_DM_VIDEO_UNLOCK_PATH`、`REVENUE_DM_VIDEO_ACCESS_PATH` 覆盖。免费视频不走结算。视频文件只存在 Billd 磁盘，消息体里没有播放地址。`GET /openim/support` 使用 `OPENIM_SUPPORT_USER_ID`；留空则 App 不显示客服入口，也不会放一套预设回复。各入口按钮调用 `openSupportConversation`，复用当前 OpenIM 登录。`OPENIM_SECRET` 仍然只在服务端。

`POST /user/logout` 只撤销请求对应的当前 App 会话，迟到请求不会清除新登录。Android 退出前持久化待同步记录，联网时调用真实退出接口；离线可先退出本机，App 运行时每 10 秒重试，下次启动继续，并在新登录前同步。App 完全关闭且离线时无法立即通知服务端，远程会话仍需等同步或原登录到期。消息撤销也受核对轮次、服务故障及排队影响，不是零延迟。设备离线退出、重连与 SDK 交互仍待实测。

OpenIM 上游 Compose 使用固定的 `mongo`、`redis` 等容器名，在其独立 Compose 网络运行，并额外加入 `live_backend` 供 Billd 通过服务名访问。使用统一脚本时网络会自动创建；单独启动 OpenIM 前先执行 `docker network create live_backend`（已存在时可忽略），再运行 `docker compose --env-file .env up --build -d`（在 `third_party/openim-server` 目录执行）。模拟器 MinIO 地址默认使用 `10.0.2.2`，实体设备部署需将 `OPENIM_EXTERNAL_HOST` 改为可达的服务主机地址。LiveKit 由统一脚本等待 7880 端口；开发密钥为 `devkey/secret`，生产环境必须替换。

## App 端配置

默认模拟器地址为 `10.0.2.2`，端口为 Billd `4300`、SRS API `1985`、SRS 播放 `8088`、LiveKit `7880`、OpenIM API `10002`、WebSocket `10001`。首页会调用已拉取的 Billd `/live/list`，显示实际在线 Socket 房间；没有启动后端时显示连接状态。生产环境由应用 API 下发短期 LiveKit/OpenIM token；APK 不应包含服务端密钥。

构建到 VMware Debian 后端时，用构建参数替换模拟器地址和端口。当前 VM 的 Billd、SRS、LiveKit 端口分别是 `4300`、`11985`、`18088`、`17880`：

```powershell
.tools\gradle-8.10.2\bin\gradle.bat :app:assembleDebug `
  -PbackendHost=192.168.254.132 `
  -PsrsApiPort=11985 `
  -PsrsPlaybackPort=18088 `
  -PlivekitPort=17880
```

生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`。实体手机必须能访问 VMware 的桥接/局域网地址；模拟器仍使用默认的 `10.0.2.2`。如果首页显示“暂时没有在线直播”，说明服务可达但当前没有主播通过 Socket.IO 开播，不代表前端页面缺失。

Android 推送为可选配置。构建时传入 `-PfirebaseApiKey=... -PfirebaseAppId=... -PfirebaseProjectId=... -PfirebaseSenderId=...` 后，登录成功会向 Billd 登记 FCM 令牌；Android 13 及以上还需用户允许通知。不传这些参数时不会初始化 Firebase。Billd 容器需要同时设置 `FCM_SERVICE_ACCOUNT_JSON`（可传服务账号 JSON 字符串或容器内文件路径），否则通知保存在站内通知中心但不会调用 FCM。服务账号和构建参数只放本地环境，不要提交到 Git。

`LiveKitRoomGateway` 已调用官方 SDK 的 `LiveKit.create()` 和 `Room.connect()`。礼物、充值、榜单、收益、审核和管理员操作必须接入业务 API，由服务端校验余额和权限，不能在客户端计算或保存真实账目。

全屏大礼物使用 [YYEVA-Android](https://github.com/yylive/YYEVA-Android) `1.1.73`（`EvaAnimViewV3`，Apache-2.0；协议说明见 [YYEVA](https://github.com/yylive/YYEVA)）。`GiftOutbox` 只保留未确认的支付意图，保存或失败时不播特效。目录字段与 `GET /revenue/gifts` 一致：`effect_type` 为小写 `none`、`yyeva`、`svga`、`lottie`，另有 `effect_url`、`effect_video_mode`、`effect_md5`、`icon_url`。只有 `yyeva` 且 `effect_url` 指向自有 AE 插件导出的混合 `.mp4` 才会进单路全屏队列。不要把上游 demo 素材当作生产资源。给一条已有礼物挂上测试文件示例：

```sql
UPDATE gift_catalog
SET effect_type = 'yyeva',
    effect_url = 'https://your-host/effects/castle.mp4',
    effect_video_mode = '2',
    effect_md5 = ''
WHERE gift_id = 'CASTLE';
```

`effect_video_mode` 留空时由 MP4 内嵌配置决定；`1` 到 `5` 交给播放器。小礼物 `lottie` / `svga` 本次不播放。

当前功能实现、实际运行证据及尚未验收的链路见 [后端验证记录](BACKEND_VALIDATION.md)。接口、容器或 APK 构建通过不能代替完整业务验收。

## 后端启动

账务服务默认只运行版本化的结构与礼物目录迁移，不导入 `00002_seed.sql` 的演示账号、余额、直播或比赛。迁移由 Goose 记录，重复启动跳过已完成版本。历史环境中已经存在的演示记录不会自动删除，以免误删真实业务数据；须先核对记录来源与账本，再处理。

数据库回归测试可使用独立 MySQL 实例：设置 `MYSQL_TEST_DSN` 后，在 `third_party/live-revenue-integrity-lab` 中运行 `go test -mod=vendor ./tests/integration -run 'TestProductionMigrations|TestRechargeAndIdempotency|TestGiftFlow|TestPKGifts|TestSettlement' -count=1`。测试会新建名称以 `live_test_` 开头的数据库，结束后删除该测试库；不要将此变量指向生产实例。未设置时，上游业务测试继续使用 Testcontainers。

直播房间与媒体服务：

```powershell
docker compose -p live-platform -f backend-compose.yml up --build
```

统一通过 `scripts/start-backends.ps1` 启动。它先创建 `live_backend` 网络并启动 Billd/SRS，再启动账务服务；账务 API 通过该私有网络与 Billd 通信。

不使用 Docker 时，可运行 `.\scripts\start-local-backends.ps1`。脚本会直接启动本机 Redis，并连接本机 MySQL 后启动 Billd；默认按当前机器的 MySQL `13306`、Redis `8888` 配置。若 MySQL 服务未启动，可加 `-StartMySqlAsAdmin`，系统会请求管理员权限。若本机数据库账号不是 `billd/billd`，请传入 `-MySqlUser` 和 `-MySqlPassword`。Revenue、OpenIM、SRS 和 LiveKit 仍需各自的本地进程或 Docker 服务。

启动完成后可运行 `.\scripts\smoke-backends.ps1` 做一次真实冒烟检查；它会创建临时用户并验证 Billd 注册、登录、礼物目录、钱包和 OpenIM 会话代理。若暂时没有 OpenIM，可加 `-SkipOpenIm`。

账务服务的 MySQL、Redis、Kafka 使用独立的 `live-revenue-private` 网络；只有账务 API 同时加入 `live_backend`，因此不会与 Billd 的同名依赖容器发生 DNS 冲突。Socket.IO 开播登记需要在握手中携带 Billd JWT，服务端会校验主播用户 ID。

业务 API 默认通过 `http://localhost:18080` 暴露（可用 `REVENUE_PORT` 覆盖），例如 `POST /v1/gifts`、`GET /v1/lives/{id}/contributors`、`GET /v1/creators/me/earnings`、`GET /v1/users/me/level`、`GET /v1/rankings/creators`、`GET /v1/rankings/contributors`、`GET /v1/matches/{id}/score` 和 `GET /v1/settlements/{id}`。Billd API 为 `http://localhost:4300`，SRS 为 `http://localhost:1985`。

PK 必须先邀请并由对方接受：`POST /revenue/match-invitations`、`POST /revenue/match-invitations/{id}/respond`（明确传入 `accept` 布尔值）。`GET /revenue/lives/{id}/match` 返回当前比赛 ID（无比赛为 0）；双方主播可调用 `POST /revenue/matches/{id}/close` 结束 PK。观众只获得媒体订阅权限；结束后保留最终比分，关播会同时结束关联比赛。旧的单方面 `/revenue/matches/start` 已返回 410，客户端应使用邀请流程。

PK 数据库验证：配置独立测试实例 `MYSQL_TEST_DSN` 后在 Revenue 目录运行 `go test -mod=vendor ./internal/app/api -run TestMatchLifecycleMySQL -count=1`；会创建并删除独立测试库。Billd 凭证权限验证：在其目录执行 `pnpm build` 后运行 `node --test tests/livekit-permissions.test.cjs`。这些检查不能代替双设备实际音视频验收。

Billd 客户端登录入口为 `POST /user/create`（JSON：`username`、`password`、可选 `avatar`/`desc`）和 `POST /user/login`（JSON：`id`、`password`）。登录响应中的 `data` 是 JWT，后续账务接口使用 `Authorization: Bearer <JWT>`。`GET /live/list` 返回当前通过 Billd Socket.IO 开播的房间。

LiveKit 房间 token 入口为 `POST /livekit/token`（JSON：`live_session_id`），需要 Billd JWT。服务端先查询账务服务中的直播会话，再签发 10 分钟 token；主播获得发布权限，观众只获得订阅权限。`LIVEKIT_API_KEY`、`LIVEKIT_API_SECRET` 只配置在服务端环境。

私信 1v1 通话使用同一套密钥，房间名是 `dm-` 加 32 位十六进制，不会被直播/PK 扫描清掉。`POST /livekit/calls`（JSON：`peer_id`、`mode` 为 `video` 或 `voice`）为双方签发可发布的 10 分钟 token；对方用 `POST /livekit/calls/join`（JSON：`call_id`）加入，任意一方 `POST /livekit/calls/end` 挂断。响应里的 `signal_text` 是现有 OpenIM 单聊文本，格式 `dm-call:v1 call_id=<id> mode=video|voice from=<主叫账号>`，只负责把通话号送到当前会话，媒体不走 OpenIM。Android 实际连接 `BuildConfig.LIVEKIT_URL`（模拟器 `ws://10.0.2.2:7880`，VMware 构建加 `-PlivekitPort=17880`）。在 Billd 目录执行 `pnpm build` 后运行 `node --test tests/dm-call.test.cjs tests/dm-call-router.test.cjs` 可核对令牌权限；双设备画面和声音仍要连到 VMware 的 LiveKit `17880`。消息页进入会话后可选「视频通话」或「语音通话」，挂断会断开 LiveKit。

业务接口经由 `http://localhost:4300/revenue/...` 使用 Billd JWT，例如 `GET /revenue/level` 会返回当前用户的消费等级和主播等级；充值只接受支付服务使用 `PAYMENT_WEBHOOK_SECRET` 生成的 `X-Payment-Signature`。账务数据库中的用户和直播会话由 Billd 服务通过私有网络及 `X-Internal-Key` 自动同步。

真实支付宝充值入口为 `GET /alipay/recharge?coins=100`，异步通知为 `POST /alipay/notify`；通知会先由支付宝 SDK 验签，再写入账务钱包。

充值会先持久化订单，再返回手机网站支付地址。有效档位为 100/500/1000 金币（实付人民币 1/5/10 元）。本人订单列表为 `GET /alipay/orders`，单笔为 `GET /alipay/orders/{requestId}`，均需 Billd JWT。只有签名有效且匹配本系统订单、商户 app_id、精确金额及流水的通知才可入账；浏览器跳回不能证明到账。

既有数据库部署前执行 Billd 的 `migrations/20261102_recharge_orders.sql`，并部署对应订单 model 与路由。初始化使用 DB_SYNC_MODE=3 时不会自动创建新表，必须执行该迁移。新模型已在 initDb 中显式注册，以便正常数据库同步模式创建。商户缺少配置时接口返回 503；需要支付宝可访问的 PUBLIC_BASE_URL 才能接收真实通知。当前 VM 未配置有效商户，尚不能完成真实支付。

关注关系、动态、评论、访客和用户屏蔽功能需要先在既有 Billd 数据库执行 `migrations/20261105_user_follow_relation.sql`、`migrations/20261105_user_moments.sql`、`migrations/20261105_profile_visits.sql` 和 `migrations/20261105_user_blocks.sql`；这些脚本可重复执行，不会清理已有数据。动态支持纯文字与图片发布、全部/关注/本人/指定用户主页列表、点赞、评论分页、评论发布/本人删除及本人动态删除；所有新动态统一进入运营审核队列，通过后才会展示给其他用户，尚无自动内容识别或单独的评论审核队列。待审核动态不能通过评论和点赞接口被其他用户访问。访客记录在查看其他用户主页时产生，每个访客只保留最近查看时间。屏蔽关系会双向隐藏搜索、推荐、动态和评论，并阻止 OpenIM 私信送礼/视频及一对一通话；正式公开运营前仍需配置评论审核流程。

充值路由回归：先在 Billd 目录执行 `pnpm build`，再运行 `node --test tests/alipay-orders.test.cjs`。测试用临时 RSA 密钥验证真实 SDK 验签，订单和账务边界隔离；不能代替真实付款验收。

漏通知补偿：本人查询订单时，后端会对待确认订单向支付宝主动查单并验签，成功后使用同一账务幂等键入账。部署已有订单表时还须执行一次 `migrations/20261102_recharge_reconciliation.sql`；持久化查单时间保证同一订单 30 秒内最多一条请求取得查单资格。补偿在用户查询时触发，完整 Billd 服务另有每 30 秒扫描待确认订单的后台补偿。网络故障或未支付仍显示待确认，不根据超时推断到账。设置仅用于测试的 `MYSQL_TEST_URL` 后，可运行 `node --test tests/recharge-order-mysql.test.cjs` 验证真实模型和并发领取；测试创建并删除独立数据库。

## 更新上游代码

### VMware OpenIM 部署补充

当前 Debian 使用根目录 `openim-local-compose.yml`，而非统一脚本中的上游整套 Compose。不要同时启动两套 OpenIM。先启动既有 Billd 与 Revenue，使 `live_backend` 和 `live-revenue-private` 网络及 Kafka 就绪，再执行：

```powershell
.\scripts\build-openim-server.ps1 -IncludeSmoke
docker compose -p live-openim -f openim-local-compose.yml up -d
```

上述构建命令在 Windows 生成 Linux AMD64 二进制。远程部署须将 `.tools/openim-linux`、`third_party/openim-server/config` 和 Compose 文件按原目录结构复制到 Debian 后运行 Compose。这是开发部署，依赖已有 Revenue Kafka；不可仅凭容器运行判断私信可用。

真实消息验证程序输出于 `.tools/openim-linux/platform-im-smoke`，通过服务端环境 `OPENIM_SECRET` 读取密钥；运行会在 OpenIM 创建明确标注的验收账号，不会创建 Billd 用户或钱包。当前 VM 已通过双向消息与重连历史验证，设备端验收仍未完成。

运行 `scripts/update-upstream.ps1`，再重新构建 APK。


### 用户资料真实性

新账号不会自动填写年龄和地区。「我的」中可保存本人资料，标明未经身份核验；旧系统默认资料按未知处理。开播及收礼的服务端条件不会把未知资料当作成年，也不会因查询钱包或保存资料而恢复受限权限。自填资料不能替代正式身份、年龄核验；完整验证边界见 [BACKEND_VALIDATION.md](BACKEND_VALIDATION.md)。


### 送礼超时与重试

新版 Android 会保留待确认送礼；使用“重试并核实原送礼”先查询同一单号的服务端结果，已确认的不再发送；没有记录时仍使用原请求幂等核实，不将查不到解释为未扣币。未确认前不能发送下一笔。旧版缺少单号的请求会被拒绝，请更新 APK。「我的 → 查看送礼记录」按页显示本人已提交结果，包括成功扣币及明确拒绝。清除 App 数据、卸载或换设备后，登录仍可读取已提交记录，但无法重建没有服务端记录的本地待确认意图；设备断网和进程恢复仍需真机验收。部署须执行 Revenue 迁移 `00008_gift_history_index.sql`。详细测试边界见 [BACKEND_VALIDATION.md](BACKEND_VALIDATION.md)。


### 运营入口与权限

运营概览和账号分页仅对数据库中拥有 `SUPER_ADMIN` 角色的有效账号开放；普通用户不显示运营导航，直接请求接口也会被拒绝。新注册账号不会自动获得管理员权限。现有真实统计、账号读取、账号禁用/恢复、主播与举报审核，以及「直播管理与处置记录」：选择尚未结束的真实直播场次，填写原因并确认后强制下播，同时结束相关 PK，媒体随后由扫描清理。操作与审计按各自服务端事务提交，同一单号重试不会再次执行；手机保存未确认操作，可重启后继续核实。部署先执行 Revenue 迁移 `00009_moderation_actions.sql`。正式环境管理员账号须明确指定，不能根据昵称或客户端参数推断。


### 临时限流与业务状态

正常轮询按有效账号计数，超额返回 429 与 Retry-After，不再按全天日志数量自动永久封禁。登录/注册另按实际连接地址限制。当前为单进程窗口，多副本部署前需要共享计数适配；不信任客户端自带转发头。业务接口的 400/409/503 等状态与结果字段会保留，新审计不再保存正文、查询值和认证凭据，详细验证见 BACKEND_VALIDATION.md。

### 关播与媒体连接清理

Billd 通过内部 `LIVEKIT_API_URL`（默认 `http://livekit:7880`）调用 LiveKit 控制接口，每轮扫描完成后等待 5 秒，再按 Revenue 中真实直播、PK 和开播资格核对应用房间。关闭的房间和已结束 PK 会清理；禁用、删除、过期或被替换会话的参与者会移除，过期房间目录会清理。媒体令牌绑定当前登录会话，并禁止客户端自行修改元数据。旧版缺少绑定信息的媒体连接会被移除，重新进入后获取新令牌。

这是持续核对，不是即时撤销旧令牌：旧令牌在有效期内可能再次建连，后续扫描仍会移除。依赖故障、房间数量会延长处理时间，正式环境双端音视频及高负载验收尚未完成。Android 会根据 SDK 事件显示连接中断和恢复状态；运营账号禁用/恢复和强制下播入口均已接入后台，具体权限与状态传播见 [ADMIN_BACKEND.md](ADMIN_BACKEND.md)。

### 登录会话与真实账号资料

Android 会先读取 `GET /user/auth-methods`；短信服务已配置时默认展示短信登录，否则默认使用账号 ID/密码登录，用户可随时切换。`POST /user/sms/send-code` 通过服务端 HTTPS 短信适配器发送验证码，验证码 5 分钟有效、手机号 60 秒冷却、手机号每天最多 10 次、单 IP 每天最多 50 次，错误 5 次后失效并且只能成功使用一次。首次短信登录会创建普通 `USER` 账号；手机号只以 `PHONE_HASH_SECRET` 生成的 HMAC 摘要保存在 `user_phone_identity`，数据库和日志不会保存明文手机号。`SMS_ENABLED` 默认 `false`；启用前设置 `SMS_PROVIDER_URL`、至少 32 字符的 `SMS_PROVIDER_SECRET`、`SMS_PROVIDER_TEMPLATE_ID` 和至少 32 字符的稳定 `PHONE_HASH_SECRET`。适配器接收 JSON `{request_id,to,template_id,parameters:{code,expires_minutes}}`，校验 `X-Request-Timestamp` 与 `X-Request-Signature: sha256=<hex>`，签名为 `HMAC-SHA256(secret, timestamp + "." + 原始请求体)`；成功接收返回任意 2xx。密钥只配置在服务器环境，不能放入 APK。既有数据库需应用 Billd 迁移 `20261113_user_phone_identity.sql`。

密码登录在用户行事务锁内检查凭据及禁用状态，再保存新会话。令牌有效期由服务端固定为 24 小时，不接受客户端延长；每次生成独立会话，后续登录使旧会话失效。账号核对只接受 HS256、带有效签发/到期时间和当前数据库会话的令牌，账号查询故障返回 503。`GET /user/me` 只返回真实账号 ID、昵称和状态，不返回密码或令牌。Android 登录后及会话存续时读取该接口，昵称不再用「用户 + ID」替代；明确失效返回登录页，临时故障保留会话。旧版超长或缺少期限的令牌需重新登录。新增和修改密码使用 Node 原生 scrypt 加盐散列；既有明文密码仅在成功登录或修改密码时事务升级，不批量改写。Android「我的 → 修改密码」核对原密码，成功后注销旧会话并重新登录。迁移先将密码列扩至 255；任何账号升级后，回退必须保留散列认证能力，不能直接恢复明文校验旧镜像或缩短列。完整生产安全与设备验收仍未完成。


### 本地 Web 运营后台（2026-11-02）

本地管理员已创建，Web 与真实 API 已启动；账号处置、资金规则、提现退款审核、礼物目录、主播申请、举报和公屏管理的最新实现、启动方法与尚未完成项见 [ADMIN_BACKEND.md](ADMIN_BACKEND.md)。旧验收段落描述其当时状态，以此新增记录为本轮进展依据。

当前新增的支付/实名接口与默认关闭策略见 [INTEGRATION_INTERFACES.md](INTEGRATION_INTERFACES.md)，本地后台操作和验收边界见 [ADMIN_BACKEND.md](ADMIN_BACKEND.md)。新增连麦、封面、主播准入和 Android 账户服务代码已保存在仓库覆盖层；虚拟机当前 SSH 超时，尚未部署本轮变更。


### 媒体鉴权更新（2026-11-03）

SRS 推流票据、HLS 播放列表/分片鉴权、数据库权威试看窗口及 LiveKit 观看权限已接入，配置、迁移、新旧 APK 兼容和测试范围见 [MEDIA_SECURITY.md](MEDIA_SECURITY.md)。启动前须设置独立随机 `MEDIA_AUTH_KEY`，Revenue 迁移到 00022。旧文档所述直接访问 SRS 播放端口不再适用于新版配置。

消息通知偏好需要在既有 Billd 数据库执行 `migrations/20261106_user_notification_preferences.sql`；Android 通知设置页的四类偏好会按账号保存。Android 可选接入 FCM，需配置客户端 Firebase 参数、服务端 `FCM_SERVICE_ACCOUNT_JSON` 并取得用户通知权限；未配置时消息仍保存在站内通知中心，不会伪装成已送达的系统推送。

发现页的性别、年龄、同城筛选依赖 Revenue 的 `00025_profile_gender.sql`，任务奖励使用 `00026_task_rewards.sql`，守护页索引使用 `00027_creator_supporter_index.sql`，商城购买账本使用 `00028_social_catalog_purchases.sql`。部署 Revenue 时需按顺序执行这些迁移。性别是用户自填资料；筛选只匹配本人填写的完整资料，不对未填写信息作推断。主播后台最近 30 场记录通过 Revenue `/v1/creators/me/sessions` 按当前登录主播查询。守护页展示当前主播近 7 日成功收礼用户及金额；该列表不授予守护身份或权益。

任务中心支持每日签到、关注用户、首次关注、填写城市、发布动态、点赞、评论、直播送礼和开播九类任务。`/moments/tasks/today` 返回真实记录计算的进度；首次关注和填写城市只能领取一次，其余按 UTC 日期领取。送礼与开播进度由 Revenue 查询已提交记录。奖励经金币账本入账，并发同单重试返回同一回执；同单修改参数会被拒绝。每日签到由 `/moments/tasks/checkin` 幂等记录。管理后台只允许配置已有计算器的任务，未支持的旧配置在用户端隐藏。部署前执行 Billd 对应增量 SQL，并将 Revenue 迁移到 `00032`。

### 当前验证状态（2026-11-13）

Android、Billd、Revenue 和 Web 管理后台本轮接入与回归范围见 [最新验收记录](BACKEND_VALIDATION.md#2026-11-13本地业务接口与管理后台回归)。本地后台当前运行在 http://127.0.0.1:8002/ ，API 为 24300、Revenue 为 24808。真实支付、短信供应商和实名仍未启用；完整媒体、离线推送和 Android 双设备流程尚未完成本轮验收。
