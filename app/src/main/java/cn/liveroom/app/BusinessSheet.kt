package cn.liveroom.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** Persist the exact command before submitting; an uncertain result always reuses its key. */
class BusinessOutbox(context: Context, userId: Long) {
    private val prefs=context.getSharedPreferences("business_outbox",Context.MODE_PRIVATE)
    private val key="${BuildConfig.BILLD_API_URL}:$userId"
    fun load(): JSONObject? = prefs.getString(key,null)?.let(::JSONObject)
    fun save(path:String,body:JSONObject) { check(load()==null){"请先核实上一笔业务申请"};check(prefs.edit().putString(key,JSONObject().put("path",path).put("body",body).toString()).commit()){"无法保存操作记录"} }
    fun clear(){check(prefs.edit().remove(key).commit()){"结果已确认，请再次核实本地记录"}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessSheet(session:UserSession,api:LivePlatformApi,initialTab:String="withdrawals",reportTarget:Long?=null,onDismiss:()->Unit){
    val context=LocalContext.current
    val outbox=remember(session.id){BusinessOutbox(context,session.id)}
    val scope=rememberCoroutineScope()
    var tab by remember { mutableStateOf(initialTab) }
    var pending by remember { mutableStateOf(runCatching{outbox.load()}) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(emptyList<JSONObject>()) }
    var nextCursor by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf<JSONObject?>(null) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }
    var order by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(reportTarget?.toString().orEmpty()) }
    var available by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val labels=mapOf("withdrawals" to "提现","refunds" to "退款","creator-applications" to "主播申请","reports" to "举报")
    LaunchedEffect(tab,refresh){
        busy=true;error=null;rows=emptyList()
        try{
            val result=api.businessRequest(session.token,"/revenue/$tab")
            val array=result.optJSONArray(if(tab=="withdrawals"||tab=="refunds")tab else "reviews")
            rows=if(array==null)emptyList()else (0 until array.length()).map{array.getJSONObject(it)}
            nextCursor=result.optString("next_before_id").takeUnless{it=="null"}.orEmpty()
            if(tab=="withdrawals"){
                rules=api.businessRequest(session.token,"/revenue/finance-rules").optJSONObject("rules")
                available=api.businessRequest(session.token,"/revenue/withdrawable").opt("available_diamonds")?.toString()
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"读取失败，请重试"}finally{busy=false}
    }
    fun send(){scope.launch{
        busy=true;error=null
        try{val command=pending.getOrThrow()?:return@launch;api.businessRequest(session.token,command.getString("path"),"POST",command.getJSONObject("body"));outbox.clear();pending=Result.success(null);refresh++}
        catch(e:CancellationException){throw e}catch(e:PlatformHttpException){error=e.message;if(e.statusCode in 400..499 && e.statusCode!=429){outbox.clear();pending=Result.success(null)}}
        catch(e:Exception){error="结果尚未确认，请用原单号核实。${e.message.orEmpty()}"}finally{busy=false}
    }}
    fun loadMore(){scope.launch{
        busy=true;error=null
        try{val result=api.businessRequest(session.token,"/revenue/$tab?before_id=$nextCursor");val array=result.getJSONArray(if(tab=="withdrawals"||tab=="refunds")tab else "reviews");rows=rows+(0 until array.length()).map{array.getJSONObject(it)};nextCursor=result.optString("next_before_id").takeUnless{it=="null"}.orEmpty()}
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"读取失败，请重试"}finally{busy=false}
    }}
    fun submit(){try{
        val body=JSONObject().put("request_id",UUID.randomUUID().toString())
        when(tab){
            "withdrawals"->{require(amount.toLongOrNull()?.let{it>0}==true){"请输入有效钻石数"};require(method.isNotBlank()&&account.isNotBlank()&&name.isNotBlank()){"请填写收款方式、账号和姓名"};body.put("diamonds",amount.toLong()).put("payout_method",method.trim()).put("payout_account",account.trim()).put("payout_name",name.trim())}
            "refunds"->{require(order.isNotBlank()&&content.isNotBlank()){"请填写充值单号和原因"};body.put("recharge_request_id",order.trim()).put("reason",content.trim())}
            "creator-applications"->{require(content.isNotBlank()&&evidence.isNotBlank()){"请填写简介和材料引用"};body.put("content",content.trim()).put("evidence_reference",evidence.trim())}
            "reports"->{require(target.toLongOrNull()?.let{it>0}==true&&content.isNotBlank()){"请填写被举报用户 ID 和问题描述"};body.put("target_type","USER").put("target_id",target.toLong()).put("content",content.trim()).put("evidence_reference",evidence.trim())}
        }
        outbox.save("/revenue/$tab",body);pending=Result.success(outbox.load());send()
    }catch(e:Exception){error=e.message}}
    ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()}){
        LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text("账户服务",style=MaterialTheme.typography.titleLarge);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){labels.forEach{(key,label)->TextButton(enabled=!busy,onClick={tab=key}){Text(label)}}}}
            item{if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(pending.isFailure)Text("本地申请记录无法读取，暂不能提交新申请",color=MaterialTheme.colorScheme.error)}
            if(pending.getOrNull()!=null)item{Text("有一笔申请待核实");Text(pending.getOrNull()!!.getJSONObject("body").getString("request_id"));Button(enabled=!busy,onClick={send()}){Text("用原单号核实")}}
            item{when(tab){
                "withdrawals"->{Text("可提现钻石 ${available?:"—"}");val policy=rules;if(policy==null)Text("平台尚未配置提现规则")else{Text("${policy.optLong("diamond_units")} 钻石 = ${policy.optLong("fiat_fen")} 分；分成 ${policy.optLong("platform_fee_bps")}/10000；最低 ${policy.optLong("minimum_withdraw_diamonds")} 钻石");Text("支持方式：${policy.optJSONArray("payout_methods")}")};OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("提现钻石")},modifier=Modifier.fillMaxWidth());OutlinedTextField(method,{method=it},label={Text("收款方式标识")},modifier=Modifier.fillMaxWidth());OutlinedTextField(account,{account=it},label={Text("收款账号")},modifier=Modifier.fillMaxWidth());OutlinedTextField(name,{name=it},label={Text("收款人姓名")},modifier=Modifier.fillMaxWidth())}
                "refunds"->{Text("输入本人已到账的充值订单号，按原订单整单申请。");OutlinedTextField(order,{order=it},label={Text("充值订单号")},modifier=Modifier.fillMaxWidth());OutlinedTextField(content,{content=it},label={Text("退款原因")},modifier=Modifier.fillMaxWidth())}
                "creator-applications"->{Text("运营审核材料后开通主播权限，材料引用不等于实名认证。");OutlinedTextField(content,{content=it},label={Text("主播简介")},modifier=Modifier.fillMaxWidth());OutlinedTextField(evidence,{evidence=it},label={Text("材料编号或引用")},modifier=Modifier.fillMaxWidth())}
                "reports"->{OutlinedTextField(target,{target=it.filter(Char::isDigit)},label={Text("被举报用户 ID")},modifier=Modifier.fillMaxWidth());OutlinedTextField(content,{content=it},label={Text("问题描述")},modifier=Modifier.fillMaxWidth());OutlinedTextField(evidence,{evidence=it},label={Text("证据引用（选填）")},modifier=Modifier.fillMaxWidth())}
            };Button(enabled=!busy&&pending.isSuccess&&pending.getOrNull()==null&&(tab!="withdrawals"||rules?.optBoolean("withdrawals_enabled")==true),onClick={submit()},modifier=Modifier.fillMaxWidth()){Text("提交${labels[tab]}")}}
            item{Text("申请记录",style=MaterialTheme.typography.titleMedium);TextButton(enabled=!busy,onClick={refresh++}){Text("刷新")};if(rows.isEmpty()&&!busy)Text("暂无记录")}
            rows.forEach{row->item{HorizontalDivider();Text("${row.optString("withdrawal_id",row.optString("refund_id",row.optString("review_id")))} · ${businessStatus(row.optString("status"))}");Text(row.optString("created_at"));if(row.has("payout_fen"))Text("预计到账 ${row.optLong("payout_fen")} 分");if(row.optString("decision_reason").isNotBlank())Text(row.optString("decision_reason"))}}
            if(nextCursor.matches(Regex("[1-9][0-9]*")))item{TextButton(enabled=!busy,onClick={loadMore()}){Text("加载更多记录")}}
        }
    }
}
fun businessStatus(status:String)=mapOf("PENDING" to "待审核","APPROVED" to "已通过","REJECTED" to "已驳回","PAID" to "已登记支付","RESOLVED" to "已处理","DISMISSED" to "不予处理","ACCEPTED" to "连麦中","MUTED" to "已禁言","REMOVED" to "已移除","LEFT" to "已退出")[status]?:status
