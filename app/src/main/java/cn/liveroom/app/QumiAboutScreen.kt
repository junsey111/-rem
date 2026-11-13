package cn.liveroom.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class QumiDocumentSection(val heading: String, val body: String)

private val QumiDocuments = mapOf(
    "用户协议" to listOf(
        QumiDocumentSection("一、服务说明", "现场 Live 为用户提供直播观看、直播发布、即时消息、动态分享、虚拟道具和账户管理等服务。你完成注册、登录或使用服务，即表示已阅读并同意本协议。"),
        QumiDocumentSection("二、账户使用", "你应使用真实、合法、可控制的注册信息，并妥善保管登录凭据。账号仅限本人使用；发现未经授权的登录、冒用或安全问题时，应立即通过应用内客服联系我们。"),
        QumiDocumentSection("三、内容与互动", "你对自己发布的文字、图片、视频、语音和直播内容负责，并保证拥有相应权利。评论、私信、送礼、关注和其他互动应遵守法律法规及平台规则。"),
        QumiDocumentSection("四、虚拟服务", "金币、玫瑰、贵族、装扮和其他虚拟权益仅可在平台内按页面规则使用，不具有法定货币属性，不得私下买卖或转让。充值、消费、退款、提现和权益发放以服务端确认的订单及账务记录为准。"),
        QumiDocumentSection("五、禁止行为", "不得利用平台实施违法犯罪、诈骗、骚扰、传播淫秽色情或暴力内容、侵犯他人权利、操纵互动数据、绕过安全措施或干扰服务运行。"),
        QumiDocumentSection("六、处理措施", "平台可根据风险、举报、审核结果和法律要求，对内容采取限制展示、删除、禁言、限制功能、冻结或关闭账号等措施，并保留必要的审计记录。"),
        QumiDocumentSection("七、服务变更", "平台会根据运营、安全和法律要求调整功能。影响用户权益的规则变更会在应用内提示；已确认的订单和账务记录按照当时有效的规则处理。"),
        QumiDocumentSection("八、联系我们", "你可以通过应用内“常见问题/客服”提交咨询、投诉或申诉。平台会在核实必要信息后处理，不会要求你在公开渠道提供密码、支付密钥或验证码。"),
    ),
    "隐私协议" to listOf(
        QumiDocumentSection("一、我们处理的信息", "为提供账号、直播、消息、动态、支付和安全服务，平台可能处理账号标识、昵称、头像、资料信息、设备与网络日志、直播媒体、互动记录、订单和客服内容。"),
        QumiDocumentSection("二、使用目的", "信息用于登录鉴权、展示用户选择的资料、建立消息和直播连接、完成订单与账务核对、内容审核、风险控制、故障排查以及改进服务。个性化推荐可在设置中关闭。"),
        QumiDocumentSection("三、信息共享", "仅在提供服务所必需、获得你的授权、履行法律义务或保护用户和平台安全时，向受托服务商、支付或消息服务提供必要信息。平台不会出售个人信息。"),
        QumiDocumentSection("四、保存与安全", "平台按实现服务和履行义务所需的期限保存信息，并采取访问控制、传输保护、审计和最小化收集等措施。不同服务的记录可能由相应服务提供方按其规则保存。"),
        QumiDocumentSection("五、你的权利", "你可以在应用内查看或修改可编辑资料、管理隐私偏好、申请删除动态、管理屏蔽关系，并通过客服询问、更正或依法申请处理个人信息。部分记录因法律、账务或安全需要无法立即删除。"),
        QumiDocumentSection("六、未成年人", "未成年人应在监护人指导下使用服务。平台不鼓励未成年人进行直播、私下交易或不适合其年龄的互动；监护人发现相关问题可通过客服联系我们。"),
        QumiDocumentSection("七、协议更新", "隐私处理发生实质变化时，平台会在应用内更新本协议并说明变更内容。继续使用更新后的服务表示你已了解相关变化。"),
    ),
    "平台行为规范" to listOf(
        QumiDocumentSection("一、基本原则", "尊重他人、真实表达、理性互动。不得发布或传播违反法律法规、公共秩序、社会公德或平台规则的内容。"),
        QumiDocumentSection("二、内容红线", "禁止诈骗、赌博、洗钱、传销、毒品交易、暴恐、极端主义、淫秽色情、未成年人性剥削、侵犯隐私、恶意造谣、侵权盗用和诱导线下危险行为。"),
        QumiDocumentSection("三、互动秩序", "禁止辱骂、威胁、骚扰、跟踪、歧视、恶意刷屏、诱导打赏、冒充他人、发布虚假联系方式或以私信方式实施诈骗。"),
        QumiDocumentSection("四、账号与数据", "禁止买卖或出租账号、批量注册、外挂刷量、伪造互动、攻击接口、窃取凭据、传播恶意程序及绕过风控或审核。"),
        QumiDocumentSection("五、举报与申诉", "举报应基于真实情况并提供可核实信息，不得恶意举报。被处置用户可以通过客服提交事实、订单号或其他必要证据，平台会按流程复核。"),
    ),
    "直播开通服务协议" to listOf(
        QumiDocumentSection("一、开通条件", "申请直播前应完成平台要求的账号、年龄、身份和安全校验，并遵守当地法律法规。平台会根据账户状态、内容风险和运营规则决定是否开通。"),
        QumiDocumentSection("二、主播责任", "主播应对直播内容、连麦参与者、直播标题、封面和互动行为负责，不得诱导未成年人、不当索取财物、发布虚假承诺或将平台用于违法活动。"),
        QumiDocumentSection("三、直播管理", "平台可对直播进行自动和人工审核，接收举报并在必要时采取限流、禁言、断流、关闭场次或限制开播等措施。严重风险可先处置后通知。"),
        QumiDocumentSection("四、收益与结算", "礼物、付费观看及其他收益以平台和账务服务确认的记录为准。分成、兑换比例、最低提现额和支付方式由运营后台配置并在相关页面展示，未配置或未启用时不会发起真实打款。"),
        QumiDocumentSection("五、技术与安全", "主播应使用合法来源的设备、网络和素材，不得共享令牌、泄露后台密钥或尝试绕过媒体权限。发生异常时应停止相关操作并联系平台。"),
    ),
    "直播管理规则" to listOf(
        QumiDocumentSection("一、审核范围", "平台审核直播封面、标题、资料、实时画面、聊天、礼物和举报证据，重点识别违法违规、侵权、诈骗、未成年人风险和不当营销。"),
        QumiDocumentSection("二、处置等级", "根据风险程度，平台可采取提示整改、限制互动、删除内容、禁言、强制下播、暂停开播、冻结相关权益、封禁账号或依法移交有关部门等措施。"),
        QumiDocumentSection("三、订单与权益", "对疑似欺诈、异常交易或未完成核验的订单，平台可以暂缓权益发放或提现处理。已确认的账务处理由账务服务依照幂等订单和退款规则执行。"),
        QumiDocumentSection("四、证据与审计", "平台会记录必要的操作人、请求号、处置原因、订单号和时间等审计信息，用于复核、申诉和法律合规。用户提交的证据应真实、合法且与事项相关。"),
        QumiDocumentSection("五、复核与申诉", "对审核或账号处置有异议，可通过客服提交账号、场次、订单或处置编号及具体理由。平台会在核实身份和事实后反馈处理结果。"),
    ),
)

@Composable
internal fun QumiAboutScreen(onBack: () -> Unit) {
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(document != null) { document = null }
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F8F8))) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (document != null) document = null else onBack() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.Black)
            }
            Text(document ?: "关于我们", Modifier.weight(1f), fontSize = 28.sp, color = Color.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        if (document != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("版本 1.0 · 2026-11-13", color = Color(0xFF888888), fontSize = 14.sp)
                QumiDocuments[document!!].orEmpty().forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(section.heading, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(section.body, color = Color(0xFF333333), fontSize = 16.sp, lineHeight = 27.sp)
                    }
                }
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(61.dp))
            Image(painterResource(R.drawable.qumi_about_logo), "应用图标", Modifier.align(Alignment.CenterHorizontally).size(92.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(70.dp))
            Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                listOf("用户协议", "隐私协议", "平台行为规范", "直播开通服务协议", "直播管理规则").forEachIndexed { index, title ->
                    Row(Modifier.fillMaxWidth().height(if (index == 0) 76.dp else if (index == 4) 99.dp else 88.dp)
                        .clickable { document = title }.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), color = Color(0xFF333333), fontSize = 16.sp)
                        Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF969AA6), modifier = Modifier.size(20.dp))
                    }
                    if (index < 4) HorizontalDivider(thickness = .5.dp, color = Color(0xFFE7E7E7))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
