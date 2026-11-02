# 本地运营后台

复用 GitHub `galaxy-s10/billd-live` 的 Vue 3 工程，后端沿用 Billd + Revenue。Web 源码改动保存在 `upstream-overlays/billd-live`；固定上游版本见 `UPSTREAM.lock`。不替换已确认的 Android 视觉方案。

## 当前入口

- Web：<http://localhost:8000/>
- 本地 Billd 管理 API：`127.0.0.1:24300`
- 本地 Revenue：`127.0.0.1:24808`
- 本地管理员 ID：`1`。密码和本地服务密钥保存在被 Git 忽略的 `.local/admin-local.json`，不要上传该文件。
- 专用 MySQL：`127.0.0.1:23306`。本地两个数据库为 `live_ops_local_billd`、`live_ops_local_revenue`。本次没有创建 VM 管理员、修改 VM 账号或部署新版本到 VM。

## 重启

需要已安装 MySQL 8、Node、pnpm、Go。专用 MySQL 的数据目录位于当前机器的 `%LOCALAPPDATA%/Temp/live-rem-mysql-integration/data`，不是系统其他 MySQL 的数据目录。当前已初始化，不要再次初始化或清空它。

先启动专用 MySQL（如果 23306 已在监听则跳过）：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe' --no-defaults "--datadir=$env:LOCALAPPDATA/Temp/live-rem-mysql-integration/data" --port=23306 --bind-address=127.0.0.1 --mysqlx=0 --console
```

在仓库根目录另开终端启动 API：

```powershell
Push-Location third_party/billd-live-server
pnpm build
Pop-Location
node scripts/start-admin-local.cjs
```

新机器先运行 `scripts/prepare-admin-web.ps1` 拉取固定 GitHub 版本并应用 Web 覆盖层，然后另开终端：

```powershell
cd third_party/billd-live
pnpm start
```

Web 开发服务器只绑定回环地址。API 脚本创建或复用专用本地管理员，使用限定两个数据库的账号运行服务，Revenue 执行正式迁移但跳过演示种子。该入口只启动管理所需 API，不代表本机完整音视频、OpenIM、Kafka 和结算 worker 均在运行。

## 已接入真实后端

| 页面 | 行为 |
|---|---|
| 运营总览、账号、直播 | 实际统计、分页账号、禁用/恢复并清除会话、强制下播、持久审计 |
| 充值订单 | 实际持久化订单；完整后端新增每 30 秒补查，管理子进程不启动商户查单 |
| 礼物目录 | 创建、改价、奖励设置、上架/下架，幂等和操作审计 |
| 主播审核、举报 | 用户提交、材料引用、审核/驳回、举报处理，按真实状态展示 |
| 提现、退款 | 申请、审核、驳回、人工回执登记；后台保存资金规则与版本审计 |
| 公屏管理 | 查询房间历史、分页、按分钟禁言/解禁、隐藏消息、处置记录 |

所有运营接口要求当前有效账号在数据库中拥有 `SUPER_ADMIN`，不接受客户端伪造的 operator_id。账号禁用与会话撤销、审计在同一事务提交，后续 worker 将禁用状态同步到 Revenue 并关闭会话。恢复账号不会复活旧 token。

## 资金规则和边界

首次启动规则未配置，不开放提现。可配置钻石单位、对应人民币分、平台分成基点、最低提现钻石、支付方式标识、已消费金币退款政策。金额使用整数计算并向下取整到分；规则快照随申请保留，变更只影响新申请。提现只使用已关闭且对账 PASS 的收益，待审核、已批准和已支付金额都计入预留。

退款目前是原充值整单申请。按原订单实际人民币金额登记，申请原子冻结金币，驳回释放一次。金币可互换，未消费判断依据当前可用余额，尚未按充值批次追踪消费；允许已消费退款时可能形成金币欠款，后续金币先弥补欠款，历史主播钻石不追缴。因此开启该政策前需明确平台承担历史赠礼收益的业务规则。被驳回的同一充值允许新申请，旧单与释放记录保留；同一充值最多一笔未驳回退款单。

“已支付”人工登记仅记录运营核实的外部回执。新增支付宝退款/转账执行接口，但按用户要求关闭真实调用；必须完成商户渠道验收才能启用。自动与人工付款共用唯一业务键，渠道结果不明时禁止另走人工重复付款；原单号和理由在数据库持久保存。

## 公屏接口

- 有效用户 `GET /rooms/:id/messages?before=<消息ID>`：最近 50 条，隐藏消息不返回。
- 运营 `GET /operations/rooms/:id/messages`、`GET /operations/rooms/:id/chat-actions`。
- 运营 `POST /operations/rooms/:id/chat`：`request_id`、`user_id`、`action`（MUTE/UNMUTE/HIDE）、`reason`；MUTE 带 `minutes`（1–10080），HIDE 带 `message_id`。
- Socket.IO `message` 保持 `data.text`/旧版 `data.msg`，可传 UUID v4 `data.request_id` 核实重试。落库后广播，发送者收到 `messageAccepted`。同一单号重试不重广播。Android 公屏通过 POST `/rooms/:id/messages` 发送并每 3 秒读取最近 50 条，已隐藏消息不再返回；HTTP 历史接口支持向前分页。
- 正式库先备份并应用本轮 Billd SQL 增量（账号处置、公屏、封面、provider_attempt、provider_attempt_reason、identity_verification）；`provider_attempt_reason` 为一次性 ALTER，必须在建 provider_attempt 表后执行一次。Revenue goose 迁移到 00017。不要通过 force/alter 重置正式数据库。

## 本轮新增

- 普通观众 AUDIO/VIDEO 连麦申请、主播接受/拒绝/暂停/恢复/移除、主动退出；每间房最多 3 位观众连麦，PK 期间不允许接入。数据库权威控制 LiveKit 麦克风/摄像头发布授权，后台定期核对并撤销权限。暂停连麦会同时停止声音和画面。主播可移除断线后遗留的席位，目前没有自动席位过期回收。
- 封面二进制上传：最多 3MB、静态 JPEG/PNG/WebP、最多 1600 万像素，服务端重编码 JPEG 并移除元数据，保存在 MySQL；列表只返回实际存在的封面。
- 主播准入开关：后台可要求材料审核通过才能开播，礼物和媒体权限同步遵循准入条件。默认不改既有部署策略；本地管理员库已开启。该审核不是第三方 KYC。
- Android 新增账户服务（提现、退款、主播申请、举报、分页记录）、封面、连麦、公屏与实名入口；业务申请先保存原请求单号，网络结果不明时用原单号核实。
- 支付宝查单后退款/转账接口、服务接入状态页；实名签名适配器接口。配置与签名格式见 [INTEGRATION_INTERFACES.md](INTEGRATION_INTERFACES.md)。充值、退款/打款、实名均默认禁用，没有真实付款或实名认证。

## 仍待环境验收与业务边界

Debian `192.168.254.132` 本轮 SSH 在 banner exchange 阶段超时，新增功能未部署到 VM，无法核查和修复其 Kafka/磁盘。本机只有管理/API 子集，未运行完整 LiveKit、OpenIM、Kafka 和结算链路。ADB 无设备，也没有可用 AVD，因此双设备音视频、Android 新界面操作和设备异常恢复未验收。独立 LiveKit 测试被明确跳过。

实名仅实现外部 HTTPS 签名适配器契约，没有选定阿里云/腾讯云的直接 SDK；认证结果单独保存，尚未作为 Revenue 年龄资料来源或强制 KYC 门槛。用户要求本阶段不启用真实实名。退款仍是整单及当前余额口径，被驳回退款可重新申请，数据库保证同一充值最多一笔未驳回单；已批准提现不支持在渠道结果不明时释放预留。以上边界不能描述成“全平台全部验收完成”。

## 验证

Revenue `MYSQL_TEST_DSN` 指向专用本地 MySQL 后 `go test ./...` 通过，包括真实数据库财务并发/幂等、迁移、目录审核和账号状态测试。Billd 新增账号处置、财务网关和真实 Socket.IO/MySQL 公屏测试已通过。Web typecheck 和生产构建通过；上游旧 Sass 与包体积提示保留。测试使用独立临时库，不向本地管理库插入虚假资金或直播记录。

本轮 Billd 完整顺序回归：17 项中 16 通过、0 失败、0 取消，1 项因独立 LiveKit 未启动跳过；随后充值关闭开关与支付适配器定向回归 2 项通过。Revenue 全套 go test 通过，新增准入版本/重试/实际开播权限测试通过。Web typecheck/build 通过；浏览器实际核对服务未启用，保存并刷新确认准入设置。Android assembleDebug/lintDebug 与 JVM 测试通过，无真机验收。
