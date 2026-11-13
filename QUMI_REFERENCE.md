# 趣蜜界面复刻记录

用户要求逐页复刻 MuMu 中趣蜜的可见前端，包括登录、导航、详情页、弹窗和交互状态。以实际截图为准，不以之前抖音/快手方向替代。

## 已采集

- 用户协议弹窗：紫色背景、白色圆角容器、协议链接、同意/不同意。协议是否接受由用户决定。
- 登录页：1080×1920，深紫模糊背景，左对齐图标、很高兴认识你 / Nice to meet you，半透明胶囊输入框，紫色登录按钮。原图保存在本地 `.tools/qumi-reference/login.png`。
- 登录后的资料完善页：`5秒填写` / `让大家更好的认识你`，要求选择性别并明确提示“性别选择后不可以修改哦”；男性/女性头像选项与底部“确定”按钮。截图 `.tools/qumi-reference/01-onboarding-gender.png`，控件树 `.tools/qumi-reference/01-onboarding-gender.xml`。采集停在此页，未代用户选择不可修改的个人资料。
- 用户选择男生后显示的完整资料表单：性别、必填生日、可随机更换的昵称、可选邀请码和底部确认。截图 `.tools/qumi-reference/02-onboarding-birthday-nickname.png`（顶部）及 `.tools/qumi-reference/02-onboarding-lower.png`（滚动后）；控件树 `.tools/qumi-reference/02-onboarding-birthday-nickname.xml`。
- 生日选择器：白色底部滚轮选择面板，年/月/日三列，底部取消、紫色确认按钮；仅打开观察，未选择或提交生日。截图 `.tools/qumi-reference/03-birthday-picker.png`，控件树 `.tools/qumi-reference/03-birthday-picker.xml`。
- 已按用户授权完成资料并进入首页：选男生；生日控件最终显示 `1988-01-01`；昵称曾用默认名称被占用，改用随机名称 `潇洒的浪荡子` 后成功。首次进入出现“青少年模式”提醒。截图 `.tools/qumi-reference/04-profile-submit-result.png`。所有资料均由用户授权后代为填写。
- 主页标签截图：发现/找蜜友 `.tools/qumi-reference/06-home.png`，动态 `.tools/qumi-reference/15-moments.png`，消息 `.tools/qumi-reference/14-messages.png`，我的 `.tools/qumi-reference/13-mine.png`。资料提交后曾有随机满屏个人主页载入遮罩，`11-overlay.png` 为加载态，不视为页面成品。
- 交友推荐列表与筛选入口：`.tools/qumi-reference/16-friends.png`、`.tools/qumi-reference/17-friends-filter.png`。
- 我的页面滚动下半部：装扮商城、认证中心、美颜效果、客服、设置 `.tools/qumi-reference/19-mine-lower.png`。
- 设置页与账户安全入口：`.tools/qumi-reference/20-settings.png`、`.tools/qumi-reference/21-account-security.png`；低部设置项 `.tools/qumi-reference/22-settings-lower.png`。
- 钱包页与账单明细：`.tools/qumi-reference/26-wallet-loaded.png`、`.tools/qumi-reference/27-wallet-bills.png`；`.tools/qumi-reference/25-wallet.png` 是加载中状态。
- 邀请好友页：`.tools/qumi-reference/41-identity-center.png`（文件名误标，画面实际是邀请好友，展示邀请码、邀请统计和原 App 的下级分成比例）。点击该入口后趣蜜进入 Uni 小程序容器并黑屏，状态见 `.tools/qumi-reference/42-invite-friends.png` 与 `42-invite-friends.xml`；没有执行分享或邀请操作。
- 青少年模式详情与密码页：`.tools/qumi-reference/47-youth-mode.png`、`.tools/qumi-reference/48-youth-password.png`。页面显示模式未开启，开启前需要设置四位独立密码；只打开密码页观察，未输入或保存密码。
- 装扮商城：`.tools/qumi-reference/50-costume-shop.png`；聊天气泡 `.tools/qumi-reference/51-shop-chat-bubbles.png`；我的装扮 `.tools/qumi-reference/52-my-outfits.png`；进场特效 `.tools/qumi-reference/54-entry-effects.png`；荣誉座驾 `.tools/qumi-reference/55-honor-cars.png`；向右滚动的分类 `.tools/qumi-reference/56-shop-more-categories.png`；勋章、开通广播、名片、头饰分别见 `57-shop-medals.png`、`58-shop-broadcast.png`、`59-shop-cards.png`、`60-shop-headwear.png`。仅浏览商品与库存，不曾充值或购买。
- 邀请好友页：`.tools/qumi-reference/41-identity-center.png`（该文件名误标；截图实际是邀请页，包含邀请码、邀请统计和原 App 展示的现金分成比例）。重新进入时趣蜜跳转到 Uni 小程序容器并停留在黑屏，当前状态保存在 `.tools/qumi-reference/42-invite-friends.png` 与 `.tools/qumi-reference/42-invite-friends.xml`；未触发分享或邀请操作。
- 账单的玫瑰、充值、提现分类分别保存为 `.tools/qumi-reference/28-rose-bills.png`、`29-recharge-records.png`、`30-withdraw-records.png`；均为空记录页。提现、兑换、充值操作未执行。
- 首页顶部“交蜜友”与“专属”分类：`.tools/qumi-reference/34-home-dating.png`、`35-home-exclusive.png`；交友列表“脱单”筛选 `.tools/qumi-reference/36-friends-single.png`。
- 设置页底部截图 `.tools/qumi-reference/22-settings-lower.png` 展示拉黑列表等入口。打开账户安全后触发了系统浏览器外链，未继续操作外链；已重返趣蜜。未确认返回后的截图 `.tools/qumi-reference/23-mine-return.png`、`31-current.png`、`32-current.png` 仅为中间转场/空态，不作为 UI 参考。重新启动后已回到趣蜜首页，当前为“找蜜友”分类。
- 个人主页顶部与首屏：`.tools/qumi-reference/37-personal-home.png`，下半页：`.tools/qumi-reference/38-personal-home-lower.png`。包含头像封面、等级、关于 TA/动态、认证信息、个人信息、标签、交友心声。
- 个人资料编辑页：`.tools/qumi-reference/39-personal-home-edit.png`；滚动后的字段与保存按钮：`.tools/qumi-reference/40-profile-edit-lower.png`。编辑页支持年龄、地区、生日、性别、身高、体重、工作、年收入、所在地、婚姻、标签和征友条件；服务端按生日计算年龄，性别首次保存后不可修改，生日不对外公开，扩展资料默认仅本人可见。
- 任务中心：`.tools/qumi-reference/67-task-center.png`，包含连续签到、积分、日常/新手/直播任务分类及任务奖励。账号显示 100 积分、0 天签到；没有点签到、领取或“去完成”。
- 我的等级：`.tools/qumi-reference/71-my-level.png`、`.tools/qumi-reference/72-my-level-lower.png`。展示等级、经验值、升级进度、经验来源和等级权益；采集账号为 LV.1、10 经验，数值仅作参考，不应硬编码进本平台。
- 我的守护及规则：`.tools/qumi-reference/74-my-guard.png`、`.tools/qumi-reference/75-my-guard-rules.png`。当前无排名；规则按最近 7 天礼物累计，门槛为 1000 玫瑰并依排名成为守护，榜单每日滚动更新。
- 认证中心：`.tools/qumi-reference/77-identity-center.png`。可见实名认证、微信认证、真人认证、语音签名、手机认证；测试账号仅手机认证显示已认证。未进入任何认证流程。
- 关于我们：`.tools/qumi-reference/79-about-us.png`，列出用户协议、隐私协议、平台行为规范、直播开通服务协议和直播管理规则。设置页见 `.tools/qumi-reference/78-settings.png`；点击“平台资质”两次都停留在设置页，没有出现详情。
- 主播后台：`.tools/qumi-reference/82-host-center.png`，展示主播资料、本月/累计收益及开播场次和分钟数；采集账号当前均为 0。直播分类页见 `.tools/qumi-reference/83-host-live-list.png`、`.tools/qumi-reference/84-host-match-room.png`、`.tools/qumi-reference/85-host-seven-singer.png`、`.tools/qumi-reference/86-host-nine-singer.png`、`.tools/qumi-reference/87-host-voice-room.png`，均为空列表；只浏览，未启动直播。
- 消息首页与公告：`.tools/qumi-reference/88-messages-home.png`；可见通知提示、系统公告、最近访客、趣蜜小秘书。系统公告列表与详情见 `.tools/qumi-reference/89-system-announcement.png`、`.tools/qumi-reference/90-announcement-detail.png`，含“绿色直播文明公约”及“严厉打击违规交易”。后续进入趣蜜小秘书会话成功，屏幕 `.tools/qumi-reference/101-secretary-chat.png`，控件树可读到整段反诈骗提醒；菜单设置页见 `.tools/qumi-reference/102-secretary-menu.png`，有主页、置顶聊天、清除聊天记录、举报、拉黑。消息里的关注、发送、礼物、语音/视频和菜单操作均未触发。会话向上滚动仍停留在消息底部（`.tools/qumi-reference/104-secretary-chat-history.png`），账号设置页头像没有有效加载，因此不推断未显示内容。
- 公告正文延迟加载后已采集完整滚动文本：`.tools/qumi-reference/111-announcement-detail-settled.png` 与控件树 `.tools/qumi-reference/111-announcement-detail-settled.xml` 保存《绿色直播文明公约》全篇；`.tools/qumi-reference/113-trading-detail-loaded.png` 与控件树 `.tools/qumi-reference/113-trading-detail-loaded.xml` 保存《严厉打击违规交易》全文；公告列表已确认其实际卡片结构与配图，见 `.tools/qumi-reference/112-announcement-list-loaded.png`。
- 关系列表：`.tools/qumi-reference/91-contacts-following.png`、`92-contacts-followers.png`、`93-contacts-friends.png`；关注/粉丝/好友三标签均为空，顶部搜索框提示按 ID 或昵称查找。最近访客页 `.tools/qumi-reference/94-recent-visitors.png` 为空，底部有贵族解锁入口；未触发付费。
- 直播发现空态与搜索：`.tools/qumi-reference/96-live-discovery-empty.png`、`97-search.png`。直播发现当前无房间；搜索页只有 ID/昵称输入和搜索按钮，未提交查询。`.tools/qumi-reference/95-current-screen.xml` 与 `97-search-current.xml` 是对应控件树。`97-original-search.png` 是一次误命名的发现页重复截图。
- 主页恢复采集时趣蜜曾发生崩溃，logcat 为 `NullPointerException: <get-zim>(...) must not be null`，位置在 `com.anxiang.message.ui.fragment.MessageChildFragment.initData`。冷启动后已恢复；`41-identity-center.png` 实际是邀请页而非认证中心，因此认证中心详情仍未采集。

## 实现状态

第一轮客户端复刻已开始：登录页按截图校准了品牌图标裁切、背景光区、胶囊字段、按钮与协议行；保留平台真实账号 ID / 密码登录和用户名注册，并增加协议勾选后才能提交。短信验证码、一键登录和趣蜜资料注册后端尚未实现，登录外观不是完整登录流程的等价物。协议弹窗只用本平台协议文案，没有复制趣蜜协议。

主壳层已改为趣蜜五个底部入口，默认进入“找蜜友”；首页顶部找蜜友/交蜜友/专属与热门/同城筛选可切换。“同城”调用 Revenue 用户发现服务的地区筛选，不使用本地假数据。公开用户搜索、推荐、关注和动态已连平台 API；服务端无结果或接口失败时显示空态/错误，不伪造用户和帖子。动态流支持全部/关注切换、刷新、游标分页重试、发布文字动态、点赞、评论和从作者行打开真实用户主页。

个人中心按截图拆分资料、钱包、账单、设置、邀请、商城、青少年模式、等级、任务、守护、主播后台、认证、联系人等子页。关注/粉丝/好友和公开用户搜索已接入分页接口；蜜友页使用双向同意流程，按 ID 邀请、接受/忽略或解除，不收费或发放礼物权益。认证中心读取 `/verification/me`；启用后须用户确认才调用 `/verification/start` 并打开服务端 HTTPS 服务商页面。微信、真人、语音和手机认证尚未接入。

等级页读取 `/revenue/level` 的观众消费积分、主播钻石积分和下一等级门槛，不再展示与计分服务无关的虚构任务奖励。任务中心支持每日签到、关注用户、发布/点赞/评论动态，以及按 Revenue 已接受礼物订单和已验证开播记录计算的直播任务；新手福利按真实关注关系和已保存城市字段判定。任务奖励由后台配置，未配置签到奖励时只记录签到，不发放虚构奖励。我的守护读取最近 7 日真实礼物支持列表，主播后台读取今日/月收益、开播场次和支持者服务。接口不可用时显示错误和重试。

消息页使用 OpenIM 私信；系统公告、访客和小秘书按已采集页面展示，小秘书仍是只读的反诈骗文案。平台活动和通知读取服务端内容；公告推送点开后会尝试直接进入对应详情。邀请页读取服务端邀请码、邀请人数与金币奖励，不展示接口未提供的现金分成。公会目录/申请/审核、贵族等级/购买、装扮目录/购买/装备均已接入服务端；付费操作由 Revenue 账本扣币，空目录表示后台尚未配置商品。青少年模式支持本机四位密码开关，密码仅保存哈希，状态不与服务端同步。钱包展示服务端余额和收益，充值调用已有支付宝订单接口，账单读取送礼、充值和提现记录。实名结果依赖服务商配置和回调联调；美颜只应用于普通 RTMP 推流，LiveKit 连麦/PK 暂不支持。设置偏好保存在本机，不声称同步到服务端。

消息页、小秘书会话及设置的最新改动已通过 `:app:assembleDebug` 并安装到 MuMu；公告详情的实际文本可在控件树完整读取。两条公告都已切换到复刻版验证全文、滚动和返回，没有新增崩溃。当前 APK 使用默认 Billd API 地址 `10.0.2.2:4300`，MuMu 请求余额和充值记录失败；Windows 本机 `127.0.0.1:4300` 未监听，用户此前提及的公网服务器端口可连但没有被选为当前客户端配置，因此资金数据仅显示服务不可用，未发起充值或提现。

## 待采集

- 已完成本轮补采：公会广场/公会榜/创建前实名提示（`131`–`133`）、贵族中心/等级/说明（`134`–`137`）、我的蜜友/规则（`139`–`141`）。对应实机截图保存在 `.tools/qumi-reference/`。未发起贵族购买、蜜友绑定或实名认证。
- 已实现发现、动态和用户搜索的独立页面与导航。发现和搜索展示服务端公开用户，动态页展示真实帖子并支持刷新、分页、点赞、评论及本人删除；新手/直播推荐等无服务数据的分类仍显示未接入，不生成假数据。
- 新增公会广场、公会榜、推荐公会空态，贵族等级浏览与购买，装扮目录与装备，蜜友双向关系，常见问题/客服，美颜、黑名单、平台资质页面。主入口和设置页入口已路由接入。公会/贵族/装扮展示服务端数据，无目录项时显示空态；蜜友仅显示平台真实关系，不显示趣蜜测试账号的等级、余额或关系。美颜设置按账号保存在本机，已应用于普通 SRS RTMP 推流，LiveKit 连麦/PK 暂不应用；实名仍依赖外部服务配置。
- MuMu 1080×1920 实测“我的”可打开公会、贵族、蜜友、美颜，设置页可打开拉黑列表；各页系统返回可用，蜜友规则弹窗可打开关闭。截图归档 `.tools/our-guild.png`、`.tools/our-noble-final.png`、`.tools/our-friends.png`、`.tools/our-beauty.png`、`.tools/our-blacklist.png`。构建和测试最终结果见 `design-qa.md`。

## 2026-11-05 补充验证

- 消息右上角进入关注/粉丝/好友列表，见 `117-friend-list-entry.png`；客户端已接到对应联系人页，断网也能打开。
- 小秘书设置的主页已采集：`118-secretary-home-profile.png`、`124-secretary-profile-lower.png`。新增对应可滚动主页与关于 TA/动态标签，返回路径为主页→设置→会话→消息首页；系统账号不沿用原 App 的在线状态、未读数或实时直播人数。
- 新主页对比归档：`our-secretary-profile-v1.png` 至 `our-secretary-profile-v3.png`；第二轮校准封面高度、标题、等级图标和底栏比例。尚不能称所有页面已逐像素一致。
- 修复客服、直播间“私信主播”仍跳到旧直播标签索引的问题，现在统一进入消息标签与真实 OpenIM 会话。
- 最新 `:app:assembleDebug` 通过，MuMu 安装验证联系人、会话、设置、主页及连续返回。真实聊天和资金服务仍受本地后端未启动限制。
- Windows 中文工作区下 Gradle 8.10.2 的测试 worker 无法读取原 classpath；使用 `gradle -I scripts/android-windows-tests.init.gradle :app:testDebugUnitTest :app:assembleDebug` 临时将非 ASCII classpath 复制至系统临时目录，45 项现有单元测试全部通过。该脚本仅改变测试运行路径，不修改 APK 的接口或业务逻辑。

已采集认证中心、关于我们、等级、主播后台、任务、守护、关系列表、最近访客、平台活动列表及完整详情、趣蜜小秘书会话和设置、搜索页。待继续覆盖可进入的观看直播间及安全可浏览的弹窗；当前测试账号没有可进入的直播房间，搜索也未提交查询。原趣蜜测试应用的平台资质入口无响应，详情不可见；本项目现已实现服务端资质读取、运营后台编辑发布和 Android 展示，未配置真实资料时保持未发布。离开趣蜜小程序页返回主界面会触发 `MessageChildFragment.initData` 空指针，需冷启动恢复。尚未观察到的页面不能推测为原版。

### 2026-11-13 平台资质与推送落地

- 平台资质现由 Billd 保存，运营后台仅限 `SUPER_ADMIN` 修改并记录请求单号、原因和审计；Android 设置页读取已发布资料。公开发布前需填真实主体名称与登记注册号，默认不发布。
- Android 收到公告 FCM 后，会按公告 ID 读取服务端公告并直接打开详情；公告已下架或读取失败时保留公告列表作为回退。FCM 客户端参数和服务端服务账号仍需部署方配置，未配置时只保留站内通知。

### 安卓主页细节迭代（第五轮）

小秘书主页新增固定标题及吸顶标签，校准资料区双列字号/行距、认证区、等级图标和固定底栏；补齐采集截图中可见的手机、关注、私信及打招呼装饰图。修复已登录浅色页面系统状态栏图标对比度，登录页保持深色系统栏。对照归档见 `compare-secretary-profile-v5.png`、`compare-secretary-lower-v5.png`，均在 `.tools/qumi-reference/`。源码继续复用原有 Compose 页面，没有新建项目或引入 UI 依赖。

第五轮 `:app:testDebugUnitTest :app:assembleDebug` 通过，45 项测试 0 失败，新 APK 已覆盖安装到 MuMu，未清空数据。关于 TA/动态切换、主页→设置→会话→消息首页返回链已实机检查。整体视觉验收仍未通过：封面裁切、首屏身份信息布局、未采集的更多菜单及其他页面仍需继续；后端未启动导致真实聊天和资金业务不可验收。

### 主页更多菜单与举报表单（2026-11-05）

已通过 ADB 采集原 App 主页右上角更多菜单（`128-profile-more.png/.xml`）和举报详情（`129-profile-report.png/.xml`），原 App 只浏览表单，未输入或提交举报。菜单只有举报/取消，表单有其他、传播谣言、盗用版权、政治暴恐、诈骗、恶意骚扰六项、说明输入与图片入口。

新增 `QumiReportScreen.kt` 复用表单：小秘书主页更多菜单及设置页举报入口均可进入；类型使用单选语义，内容可输入，图片入口明确提示上传暂未接入。小秘书不具备本平台用户 ID，提交显示不可举报系统展示账号，不向后端伪造用户。直播间针对真实主播的举报复用现有 `BusinessSheet`、`/revenue/reports` 和 `BusinessOutbox`：选择类型作为内容前缀，沿用保存原命令和原单号核实的流程；旧待处理申请保留原流程。真实服务端成功后才展示提交成功。

对照已保存 `compare-profile-report-v1.png`、`compare-profile-report-v2.png`、`compare-profile-report-final.png`；菜单最终证据 `our-profile-more-final.png`，取消行已完整显示。最终 APK 已安装，45 项现有测试全部通过。实机验证内容输入、系统账号提交提示、图片上传未接入提示、单选 checked 语义、系统返回和菜单取消；未调用真实举报接口，真实用户提交仍待后端联调。

### 设置、关于我们与协议入口（2026-11-05）

设置页按 `21-account-security.png`（文件名误标，画面实际为设置）重新核对，调整标题高度、卡片圆角、内外间距、26 sp 主行文本、真实版本与缓存值、小尺寸开关。开关保留 48 dp 触摸区及 Switch 无障碍语义，本机偏好保存规则不变。账户与安全仍进入本平台已有修改密码流程，未推断趣蜜外链账户安全详情。

新增 `QumiAboutScreen.kt` 和 ABOUT 子路由，按 `79-about-us.png` 还原图标、五项协议列表、行距和背景。五个入口均能进入独立标题的详情状态，系统返回回到列表，再回设置，再回我的。项目目前没有对应正式协议正文，详情明确显示“正式内容尚未发布”，没有把参考软件协议当成本平台文件，也不改变登录同意状态。

最新证据：`our-settings-final.png`、`our-about-final.png`，原尺寸左右对照 `compare-settings-final.png`、`compare-about-final.png`。五个协议入口均通过 ADB 逐项验证；原密码修改面板、青少年模式入口已检查仍可访问，没有修改密码或启用青少年模式。45 项既有单元测试通过，编译成功，最新 APK 已覆盖安装到 MuMu。

## 美颜设置接入推流（2026-11-05）

- 美颜页已从“暂未接入”说明改为自然磨皮开关、亮度/饱和度调节和自然预设。设置按平台账号 ID 保存在本机，不同步到服务端。
- 使用项目已有 RootEncoder 滤镜，在普通 SRS RTMP 主播推流时应用磨皮、亮度和饱和度；开播期间修改会更新当前推流滤镜。LiveKit 连麦/PK 画面仍不应用这些效果。
- `:app:testDebugUnitTest :app:assembleDebug` 通过，10 个测试类、45 项测试，0 失败；APK 已生成。当前没有连接 ADB 设备，未验证模拟器预览画面与滤镜的实际视觉效果，因此本页仍需真机/模拟器验收。

## 联系人关系分页（2026-11-05）

- 联系人关注、粉丝、好友以及发现页“专属”关注列表接入 `/user/relations` 的 `next_before_id`，按服务端每页 50 条追加；提供刷新、加载更多和分页失败重试。
- `:app:testDebugUnitTest :app:assembleDebug` 通过，45 项测试 0 失败。当前无 ADB 设备，尚未对长列表的实机滚动、刷新和连续分页进行验收。

## 用户公开主页（2026-11-05）

- 搜索、发现、联系人和最近访客中的用户行现在打开平台用户主页；私信按钮仍直接打开私聊。主页按 ID 读取真实账号信息、记录访问、关注/取消关注、进入私信、打开黑名单确认，并提供“关于 TA / 动态”切换。
- 动态读取新增 `/moments?scope=user&user_id=...`，复用服务端可见状态及双向拉黑过滤、动态游标分页、点赞和评论接口；没有使用趣蜜测试账号资料或本地假帖子。
- Billd `npm run typecheck`、`npm run build` 与 `node --test tests/moments-router.test.cjs` 通过。Android `:app:testDebugUnitTest :app:assembleDebug` 通过，45 项测试 0 失败。无 ADB 在线设备，尚未实机验收用户主页、真实关系变更与动态展示。

## 动态信息流与个人动态交互（2026-11-05）

- 动态列表增加手动刷新；切换“全部/关注”时清空旧列表并使用新范围请求。分页失败时保留已显示内容，在列表内用原游标重试，刷新/翻页期间显示进度。
- 本人主页动态接入现有点赞和评论服务，保留删除本人动态；评论计数在成功提交后更新。
- `:app:testDebugUnitTest :app:assembleDebug` 通过，45 项 Android 测试 0 失败。当前无 ADB 在线设备，未对刷新、分页错误恢复、点赞及评论做实机验收。

## 个人头像

- Android 资料编辑页支持从系统媒体选择器上传头像；服务端限制格式、体积和请求频率，统一裁切转码并持久保存。头像会显示在“我的”、本人资料页和公开用户主页。
- 头像读取需要有效登录，且尊重互相屏蔽关系；旧头像路径在替换后立即不可读取。Docker Compose 使用独立持久卷保存头像文件。
