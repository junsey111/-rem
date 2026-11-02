# 支付与实名接口（默认关闭）

本阶段按用户要求只完成接口，不启用真实资金和实名调用。Compose 与本地启动器强制关闭对应开关；不能仅凭前端状态认定真实渠道已验收。

## 开关

`ALIPAY_RECHARGE_ENABLED=false`、`ALIPAY_DISBURSEMENTS_ENABLED=false`、`IDENTITY_ENABLED=false`。即使填写了商户密钥，未明确启用开关也不会发起渠道请求。不要把密钥提交 Git 或写进 Android 包。

## 资金

- GET `/operations/integrations`：当前管理员查询接入状态，不返回密钥。
- POST `/operations/finance/withdrawals/:id/execute` 或 `/operations/finance/refunds/:id/execute`：当前管理员，JSON `{request_id: UUIDv4, reason: string}`。
- 仅处理 Revenue 中 APPROVED 的持久化申请；客户端不能指定金额或收款账号。已 PAID 且对应本接口的同一回执会返回原结果。
- 唯一渠道业务号 `withdrawals-<id>` / `refunds-<id>`；先签名查单，再执行转账/退款，金额从整数分转换为两位小数字符串。查单不明或处理中按未确认返回，禁止换单号重复付款。
- `provider_attempt` 保存原申请摘要、操作人、请求 UUID、理由、渠道回执与状态。渠道成功但记账超时，重试只继续原回执记账。人工 PAID 登记与自动执行互斥占用同一业务号。
- 退款使用原充值支付回执和整单金额；自动提现仅支持 `alipay`，收款账号和姓名来自原批准申请。
- 人工登记失败后也必须保留原 UUID、理由、回执和操作人核实；不能通过改回执新建付款流程。当前未实现渠道明确失败后的自动撤销/释放，需要人工核查。
- 启用前仍需商户资质、真实沙箱/渠道契约验收、回调域名/签名配置与资金对账；当前测试使用隔离数据库与服务替身，不发生真实付款。

## 实名适配器

认证资料在服务商托管页面收集。平台不保存身份证号、证件照片或姓名，只保存申请 UUID、用户、状态、年龄、地区、服务商证明引用。

- GET `/verification/me`：当前用户查询最新状态及 enabled。
- POST `/verification/start`：当前用户，JSON `{request_id: UUIDv4}`。关闭时返回 503，不创建申请或调用服务商。
- `IDENTITY_PROVIDER_URL` 必须是配置的 HTTPS 适配器地址；请求体 `{request_id,user_id,timestamp,signature}`。signature 为共享 `IDENTITY_PROVIDER_KEY` 的 HMAC-SHA256 小写十六进制，原文 `request_id:user_id:timestamp`，timestamp 为 Unix 秒。适配器应校验时效并按 request_id 幂等。
- 适配器返回 `{request_id,verification_url}`；只接受 HTTPS 且 origin 与 `IDENTITY_BROWSER_ORIGIN`（缺省为适配器 origin）一致。请求禁止重定向。
- POST `/verification/callback`：JSON `{request_id,user_id,status,age_years,region_code,provider_reference}`。status 为 VERIFIED/REJECTED，地区为两位大写代码，证明引用限字母数字下划线短横线。请求头 `X-Identity-Signature` 为相同密钥 HMAC-SHA256，原文 `request_id:user_id:status:age_years:region_code:provider_reference`。
- 回调锁定原申请、核对用户，最终结果不可变，相同结果重复回调幂等。启动认证不等于认证通过。
- 当前是外部适配器契约，不是某一家实名厂商的直接实现。认证结果尚不自动覆盖 Revenue 自报年龄资料，也未打开强制 KYC；实际厂商与启用策略确定后再联调。

## 本地与部署

本地后台：http://localhost:8000/ 。本地凭据位于 Git 忽略的 `.local/admin-local.json`。本地 API 为 24300，Revenue 为 24808，仅监听回环地址。

新增 Billd 表对应 `migrations/20261102_live_cover.sql`、`20261102_provider_attempt.sql`、`20261102_provider_attempt_reason.sql`、`20261102_identity_verification.sql`；reason 迁移为一次性 ALTER。Revenue 用 goose 升级到 00017。先备份，不使用 force/alter 重建既有业务库。
