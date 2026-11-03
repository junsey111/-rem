# 业务设置与管理后台

后台通过现有平台账号登录，需运营权限。邀请奖励、直播收费均经 Billd 鉴权后访问内部 Go 接口，客户端不能指定操作人。

## 使用

- 邀请奖励：设置开关、邀请人和受邀人的金币数（0–100000），填写原因后保存。保存立即影响新兑换，不发放金币。已到账奖励及原单号回执保留。
- 直播管理 → 收费设置：从开放直播读取价格及版本。0 表示免费，最高 1000000 金币；已购买权益保留，关闭直播拒绝改价。
- 业务设置记录：查询操作人、原因、保存快照和时间，支持游标分页。
- 保存携带版本及 UUID。发生版本冲突请刷新并重新确认；网络结果不明时用原单号核实，避免重复操作。
- 第一次保存邀请规则之前兼容已有环境变量；之后数据库设置优先，不需重启。

## 启动

先应用 upstream overlays，执行 Go 数据库迁移（包括 `00023_business_settings.sql`），重新构建后端及 Web：

```powershell
./scripts/apply-upstream-overlays.ps1
pnpm --dir third_party/billd-live-server build
pnpm --dir third_party/billd-live build
```

在部署目录运行：

```sh
docker compose -p live-platform -f backend-compose.yml -f deploy/media-auth.compose.yml -f deploy/admin.compose.yml up -d --no-build
```

管理后台默认端口 `18090`（`ADMIN_WEB_PORT` 可覆盖），入口 `/admin` 或 `/console`。Nginx 的 `/backend/` 代理转发到现有 Billd 服务。现有后台权限验证保持生效，静态页面不会创建管理员。

本地联调仍可使用 `node scripts/start-admin-local.cjs` 和 Web `pnpm start`，入口 http://localhost:8000/console 。凭据保存在忽略提交的 `.local/admin-local.json`。

真实充值、提现、实名认证继续关闭；这些业务设置不启用外部服务商。

## 验证

- 带独立 MySQL 的 Go 全量测试覆盖版本冲突、并发写入、幂等回执、审计、价格变化及真实奖励账本。
- Billd gateway 测试覆盖无权限拒绝、伪造操作人拒绝、服务端身份注入、409 透传及网络异常。
- Web 类型检查、生产构建及本地浏览器保存与审计检查；390px 布局检查。
- 本轮未进行 Android 真机端到端验证。
