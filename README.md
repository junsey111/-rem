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
- Billd Live Server：当前 checkout 提供用户、直播房间、Socket.IO 信令和支付宝入口；README 中提到但源码尚未提供的礼物、金币、收益、榜单、商品订单和 PK 业务需要后续补齐。
- Billd Live Kotlin：已拉取到 `third_party/billd-live-kotlin` 供复用；当前是 Android Studio 模板级工程，首页是占位页，API 默认连作者线上服务，不能直接作为完整客户端。版本见 `UPSTREAM.lock`。
- Live Revenue Integrity Lab：已拉取到 `third_party/live-revenue-integrity-lab`，作为业务账务服务。它提供充值、礼物扣币、双重记账、PK 比分、贡献榜/活动榜和直播结算对账 API，版本见 `UPSTREAM.lock`。

## 构建 APK

```powershell
& .tools/gradle-8.10.2/bin/gradle.bat :app:assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

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

`LiveKitRoomGateway` 已调用官方 SDK 的 `LiveKit.create()` 和 `Room.connect()`。礼物、充值、榜单、收益、审核和管理员操作必须接入业务 API，由服务端校验余额和权限，不能在客户端计算或保存真实账目。

## 后端启动

直播房间与媒体服务：

```powershell
docker compose -p live-platform -f backend-compose.yml up --build
```

统一通过 `scripts/start-backends.ps1` 启动。它先创建 `live_backend` 网络并启动 Billd/SRS，再启动账务服务；账务 API 通过该私有网络与 Billd 通信。

不使用 Docker 时，可运行 `.\scripts\start-local-backends.ps1`。脚本会直接启动本机 Redis，并连接本机 MySQL 后启动 Billd；默认按当前机器的 MySQL `13306`、Redis `8888` 配置。若 MySQL 服务未启动，可加 `-StartMySqlAsAdmin`，系统会请求管理员权限。若本机数据库账号不是 `billd/billd`，请传入 `-MySqlUser` 和 `-MySqlPassword`。Revenue、OpenIM、SRS 和 LiveKit 仍需各自的本地进程或 Docker 服务。

启动完成后可运行 `.\scripts\smoke-backends.ps1` 做一次真实冒烟检查；它会创建临时用户并验证 Billd 注册、登录、礼物目录、钱包和 OpenIM 会话代理。若暂时没有 OpenIM，可加 `-SkipOpenIm`。

账务服务的 MySQL、Redis、Kafka 使用独立的 `live-revenue-private` 网络；只有账务 API 同时加入 `live_backend`，因此不会与 Billd 的同名依赖容器发生 DNS 冲突。Socket.IO 开播登记需要在握手中携带 Billd JWT，服务端会校验主播用户 ID。

业务 API 默认通过 `http://localhost:18080` 暴露（可用 `REVENUE_PORT` 覆盖），例如 `POST /v1/gifts`、`GET /v1/lives/{id}/contributors`、`GET /v1/creators/me/earnings`、`GET /v1/users/me/level`、`GET /v1/rankings/creators`、`GET /v1/rankings/contributors`、`GET /v1/matches/{id}/score` 和 `GET /v1/settlements/{id}`。Billd API 为 `http://localhost:4300`，SRS 为 `http://localhost:1985`。

Billd 客户端登录入口为 `POST /user/create`（JSON：`username`、`password`、可选 `avatar`/`desc`）和 `POST /user/login`（JSON：`id`、`password`）。登录响应中的 `data` 是 JWT，后续账务接口使用 `Authorization: Bearer <JWT>`。`GET /live/list` 返回当前通过 Billd Socket.IO 开播的房间。

LiveKit 房间 token 入口为 `POST /livekit/token`（JSON：`live_session_id`），需要 Billd JWT。服务端先查询账务服务中的直播会话，再签发 10 分钟 token；主播获得发布权限，观众只获得订阅权限。`LIVEKIT_API_KEY`、`LIVEKIT_API_SECRET` 只配置在服务端环境。

业务接口经由 `http://localhost:4300/revenue/...` 使用 Billd JWT，例如 `GET /revenue/level` 会返回当前用户的消费等级和主播等级；充值只接受支付服务使用 `PAYMENT_WEBHOOK_SECRET` 生成的 `X-Payment-Signature`。账务数据库中的用户和直播会话由 Billd 服务通过私有网络及 `X-Internal-Key` 自动同步。

真实支付宝充值入口为 `GET /alipay/recharge?coins=100`，异步通知为 `POST /alipay/notify`；通知会先由支付宝 SDK 验签，再写入账务钱包。

## 更新上游代码

运行 `scripts/update-upstream.ps1`，再重新构建 APK。
