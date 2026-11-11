<template>
  <section class="extended-admin">
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback" role="status">{{ notice }}</p>

    <template v-if="active === 'overview'">
      <div class="filters">
        <label>开始日期 <input v-model="dates.start" type="date" /></label>
        <label>结束日期 <input v-model="dates.end" type="date" /></label>
        <button :disabled="loading" @click="load">查询</button>
      </div>
      <div class="stat-grid" v-if="dashboard">
        <div class="stat" v-for="item in stats" :key="item.label">
          <span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.unit }}</small>
        </div>
      </div>
      <p class="hint">收益按已接受礼物的金币流水统计；人民币充值另计。下载数：{{ dashboard?.download_note || '尚未接入真实下载计数' }}。</p>
    </template>

    <template v-else-if="active === 'moments'">
      <div class="heading"><h2>动态与视频</h2><button :disabled="loading" @click="load">刷新</button></div>
      <div class="review-filters" role="group" aria-label="审核状态"><button v-for="filter in reviewFilters" :key="filter.value" :class="{ selected: momentReviewStatus === filter.value }" :disabled="loading" @click="momentReviewStatus = filter.value; load()">{{ filter.label }}</button></div>
      <p class="hint">新动态和视频审核通过后才对其他用户展示。点击内容可查看全文、图片、视频与审核记录。</p>
      <div class="table-wrap"><table><thead><tr><th>内容</th><th>发布者</th><th>互动</th><th>时间</th><th>状态 / 操作</th></tr></thead>
        <tbody><tr v-for="row in moments" :key="row.id">
          <td class="moment-cell"><b>{{ row.media_kind === 'video' ? '视频' : row.media_kind === 'image' ? '图片' : '文字' }}</b><p class="moment-text">{{ row.content || '无文字说明' }}</p><MomentMediaPreview v-if="row.media_kind" :moment-id="String(row.id)" :kind="row.media_kind" @open="momentDetail = row" /><button class="detail-link" @click="momentDetail = row">查看详情 / 审核</button></td>
          <td>{{ row.username }}<small>ID {{ row.user_id }}</small></td>
          <td>赞 {{ row.like_count }} · 评论 {{ row.comment_count }}</td><td>{{ formatDate(row.created_at) }}</td>
          <td><span :class="['review-status', (row.review_status || 'APPROVED').toLowerCase()]">{{ reviewLabel(row.review_status || 'APPROVED') }}</span><small>{{ row.visible ? row.review_status && row.review_status !== 'APPROVED' ? '未对外展示' : row.hidden ? '作者隐藏' : '展示中' : '已删除' }}</small><button v-if="row.visible" class="danger" :disabled="saving" @click="removeMoment(row)">删除</button></td>
        </tr><tr v-if="!moments.length"><td colspan="5" class="empty">{{ loading ? '正在加载' : '暂无动态' }}</td></tr></tbody>
      </table></div><button v-if="momentCursor" :disabled="loading" @click="loadMoreMoments">加载更多</button>
      <MomentReviewDialog v-if="momentDetail" :row="momentDetail" @close="momentDetail = null" @reviewed="updateMomentReview" />
    </template>

    <template v-else-if="active === 'creator-list'">
      <div class="filters"><label>开始日期 <input v-model="dates.start" type="date" /></label><label>结束日期 <input v-model="dates.end" type="date" /></label><button :disabled="loading" @click="load">查询</button></div>
      <p class="hint">展示已审核通过的主播；收益为选定日期内收到的礼物金币和钻石奖励。</p>
      <div class="table-wrap"><table><thead><tr><th>主播 ID</th><th>通过时间</th><th>礼物金币</th><th>奖励钻石</th></tr></thead>
        <tbody><tr v-for="row in creators" :key="row.user_id"><td>{{ row.user_id }}</td><td>{{ formatDate(row.approved_at) }}</td><td>{{ row.gift_coins }}</td><td>{{ row.reward_diamonds }}</td></tr>
          <tr v-if="!creators.length"><td colspan="4" class="empty">{{ loading ? '正在加载' : '暂无已认证主播' }}</td></tr></tbody>
      </table></div>
    </template>

    <template v-else-if="active === 'transactions'">
      <div class="filters"><label>用户 ID <input v-model.trim="transactionUser" inputmode="numeric" placeholder="留空查看全部" /></label><button :disabled="loading" @click="load">查询明细</button></div>
      <p class="hint">金币变动来自钱包流水。正数为入账，负数为平台消费；最多显示最近 100 条。</p>
      <div class="table-wrap"><table><thead><tr><th>流水号</th><th>用户</th><th>类型</th><th>金币变动</th><th>时间</th></tr></thead>
        <tbody><tr v-for="row in transactions" :key="row.tx_id"><td class="mono">{{ row.tx_id }}</td><td>{{ row.user_id }}</td><td>{{ transactionLabel(row.type) }}</td><td :class="row.coin_change < 0 ? 'negative' : 'positive'">{{ row.coin_change > 0 ? '+' : '' }}{{ row.coin_change }}</td><td>{{ formatDate(row.created_at) }}</td></tr>
          <tr v-if="!transactions.length"><td colspan="5" class="empty">{{ loading ? '正在加载' : '暂无流水' }}</td></tr></tbody>
      </table></div>
    </template>

    <template v-else-if="active === 'recharges'">
      <div class="filters"><label>用户 ID <input v-model.trim="rechargeUser" inputmode="numeric" placeholder="留空查看全部" /></label><button :disabled="loading" @click="load">查询充值</button></div>
      <p class="hint">显示已入账的充值记录。线下人工入账和历史在线充值均可查询；每页 25 条。</p>
      <div class="table-wrap"><table><thead><tr><th>流水号</th><th>用户</th><th>入账方式</th><th>金币</th><th>入账后余额</th><th>时间</th></tr></thead>
        <tbody><tr v-for="row in recharges" :key="row.tx_id"><td class="mono">{{ row.request_id }}</td><td>{{ row.user_id }}</td><td>{{ row.source === 'MANUAL' ? '人工入账' : '在线充值' }}</td><td class="positive">+{{ row.coins }}</td><td>{{ row.balance_after }}</td><td>{{ formatDate(row.created_at) }}</td></tr>
          <tr v-if="!recharges.length"><td colspan="6" class="empty">{{ loading ? '正在加载' : error ? '加载失败，请重试' : '暂无充值记录' }}</td></tr></tbody>
      </table></div><button v-if="rechargeCursor" :disabled="loading" @click="loadMoreRecharges">加载更多</button>
    </template>

    <template v-else-if="active === 'announcements'">
      <div class="heading"><div><h2>系统公告</h2><p>设置封面和图文正文，发布后在应用消息页展示。</p></div><button @click="announcements.push({ title: '', body: '', blocks: [], cover_path: '', published_at: today, published: false })">新增公告</button></div>
      <div v-for="(item, index) in announcements" :key="announcementKey(item)" class="editor">
        <div class="heading"><b>公告 {{ index + 1 }}</b><button class="danger" @click="announcements.splice(index, 1)">移除</button></div>
        <label>标题<input v-model="item.title" maxlength="80" /></label>
        <label>展示日期<input v-model="item.published_at" type="date" /></label>
        <div class="cover-setting"><span>公告封面</span><img v-if="item.cover_path" :src="'/backend' + item.cover_path" alt="公告封面预览" /><div class="cover-actions"><label class="upload-cover">{{ coverUploading ? '上传中…' : item.cover_path ? '更换封面' : '上传封面' }}<input type="file" accept="image/jpeg,image/png,image/webp" :disabled="coverUploading" @change="uploadAnnouncementCover($event, item)" /></label><button v-if="item.cover_path" @click="item.cover_path = ''">移除封面</button></div><small>建议使用横向图片，支持 JPG、PNG、WebP，最大 3MB。</small></div>
        <div><label>图文正文</label><AnnouncementEditor :blocks="item.blocks" :body="item.body" @change="Object.assign(item, $event)" @busy="editorUploading = $event" /></div>
        <label class="check"><input v-model="item.published" type="checkbox" /> 发布到应用</label>
      </div>
      <p v-if="!contentConfigured" class="hint">尚未保存后台公告，应用继续显示原有公告。首次保存后由这里的内容接管。</p>
      <div class="save-row"><label>修改原因<input v-model="reason" maxlength="200" /></label><button class="primary" :disabled="saving || loading || coverUploading || editorUploading" @click="saveContent">保存公告</button></div>
    </template>

    <template v-else-if="active === 'secretary'">
      <div class="editor"><h2>小秘书消息</h2><p>应用消息列表和小秘书会话将展示此内容。</p><label>消息内容<textarea v-model="secretaryMessage" rows="8" maxlength="3000"></textarea></label></div>
      <p v-if="!contentConfigured" class="hint">尚未保存后台内容，应用继续显示原有小秘书消息。</p>
      <div class="save-row"><label>修改原因<input v-model="reason" maxlength="200" /></label><button class="primary" :disabled="saving" @click="saveContent">保存内容</button></div>
    </template>

    <template v-else-if="active === 'noble'">
      <p class="hint">贵族由平台人工核实开通。选择每一级拥有的权益，应用将按勾选结果展示。</p>
      <div class="tier-grid"><div v-for="(tier, index) in noble.tiers" :key="index" class="editor">
        <h3>{{ tierNames[index] }} · 已选 {{ tier.privilege_ids.length }} 项</h3><label>达成条件<input v-model="tier.condition" maxlength="200" /></label>
        <label>说明<input v-model="tier.detail" maxlength="200" /></label>
        <fieldset class="perk-options"><legend>拥有的权益</legend>
          <label v-for="(perk, perkIndex) in noble.privileges" :key="perkIndex" class="check perk-option">
            <input v-model="tier.privilege_ids" type="checkbox" :value="perkIndex + 1" />{{ perk.title }}
          </label>
        </fieldset>
      </div></div>
      <h2>权益内容</h2><div class="perk-grid"><div v-for="(perk, index) in noble.privileges" :key="index" class="editor perk">
        <b>{{ index + 1 }}. {{ perk.title }}</b><label>名称<input v-model="perk.title" maxlength="40" /></label><label>说明<input v-model="perk.description" maxlength="120" /></label>
      </div></div>
      <div class="save-row"><label>修改原因<input v-model="reason" maxlength="200" /></label><button class="primary" :disabled="saving" @click="saveContent">保存贵族规则</button></div>
    </template>
  </section>
</template>

<script lang="ts" setup>
import { computed, reactive, ref, watch, onMounted } from 'vue';
import axios from 'axios';
import AnnouncementEditor from './AnnouncementEditor.vue';
import MomentMediaPreview from './MomentMediaPreview.vue';
import MomentReviewDialog from './MomentReviewDialog.vue';
const props = defineProps<{ active: string }>();
const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' });
const dates = reactive({ start: today, end: today });
const loading = ref(false), saving = ref(false), error = ref(''), notice = ref('');
const dashboard = ref<any>(null), moments = ref<any[]>([]), creators = ref<any[]>([]), transactions = ref<any[]>([]), recharges = ref<any[]>([]);
const momentCursor = ref(''), transactionUser = ref(''), rechargeUser = ref(''), rechargeCursor = ref('');
const momentReviewStatus = ref('ALL'), momentDetail = ref<any>(null);
const reviewFilters = [{ value: 'ALL', label: '全部' }, { value: 'PENDING', label: '待审核' }, { value: 'APPROVED', label: '已通过' }, { value: 'REJECTED', label: '已驳回' }];
const reviewLabel = (status: string) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status);
function updateMomentReview(result: any) { const row = moments.value.find(item => String(item.id) === String(result.id)); if (row) Object.assign(row, result); notice.value = result.review_status === 'APPROVED' ? '审核已通过，内容可在前台展示' : '内容已驳回，不会在前台展示'; }
const announcements = ref<any[]>([]), secretaryMessage = ref(''), contentVersion = ref(0), contentConfigured = ref(false), reason = ref('');
const coverUploading = ref(false), editorUploading = ref(false);
const announcementKeys = new WeakMap<object, string>();
function announcementKey(item: object) { if (!announcementKeys.has(item)) announcementKeys.set(item, crypto.randomUUID()); return announcementKeys.get(item); }
async function uploadAnnouncementCover(event: Event, item: any) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; if (!file) return;
  coverUploading.value = true; error.value = '';
  try {
    if (file.size > 3 * 1024 * 1024) throw new Error('图片不能超过 3MB');
    const response = await axios.post('/backend/operations/content-images', file, { timeout: 20000,
      headers: { Authorization: `Bearer ${sessionStorage.getItem('live_ops_token') || ''}`, 'Content-Type': 'application/octet-stream' } });
    if (response.data.code !== 200) throw new Error(response.data.message || '上传失败');
    item.cover_path = response.data.data.image_path;
  } catch (failure) { handleError(failure); }
  finally { coverUploading.value = false; input.value = ''; }
}
const tierNames = ['骑士', '子爵', '侯爵', '伯爵', '公爵', '国王'];
const perkNames = ['贵族勋章', '开通/续费广播', '贵族名片', '入场特效', '贵宾席', '专属头饰', '开通特效', '贵族礼物', '升级加速', '贵族弹幕', '发言气泡', '豪华座驾', '榜单隐身', '进场隐身', '防踢功能'];
const perkDescriptions = ['尊贵身份象征', '进房间通知', '专属贵族礼物', '直播间、聊天室特效', '优先展示', '彰显与众不同', '贵族等级特效', '可赠送贵族礼物', '送礼经验值+12%', '专属弹幕样式', '专属发言气泡', '专属豪华座驾', '可设置榜单隐身', '以神秘身份观看', '直播间防踢'];
const noble = reactive({ tiers: ['98元 / 有效期30天', '398元 / 有效期30天', '998元 / 有效期30天', '2888元 / 有效期30天', '充值2万元获得公爵30天', '充值5万元获得国王30天'].map((condition, index) => ({ condition, detail: `开通${tierNames[index]}贵族立享专属特权`, privilege_ids: Array.from({ length: [5, 7, 10, 13, 14, 15][index] }, (_, i) => i + 1) })), privileges: perkNames.map((title, index) => ({ title, description: perkDescriptions[index] })) });
const stats = computed(() => dashboard.value ? [
  { label: '新增用户 · 今日', value: dashboard.value.users.today, unit: '人' },
  { label: '新增用户 · 本周', value: dashboard.value.users.week, unit: '人' },
  { label: '注册用户 · 累计', value: dashboard.value.users.total, unit: '人' },
  { label: '新增用户 · 所选日期', value: dashboard.value.users.selected, unit: '人' },
  { label: '礼物流水 · 今日', value: dashboard.value.gift_coins.today, unit: '金币' },
  { label: '礼物流水 · 本周', value: dashboard.value.gift_coins.week, unit: '金币' },
  { label: '礼物流水 · 累计', value: dashboard.value.gift_coins.total, unit: '金币' },
  { label: '礼物流水 · 所选日期', value: dashboard.value.gift_coins.selected, unit: '金币' },
  { label: '在线充值 · 所选日期', value: (dashboard.value.selected_recharge_fen / 100).toFixed(2), unit: '元' },
  { label: '下载 · 今日', value: dashboard.value.downloads?.today ?? '—', unit: '次' },
  { label: '下载 · 本周', value: dashboard.value.downloads?.week ?? '—', unit: '次' },
  { label: '下载 · 累计', value: dashboard.value.downloads?.total ?? '—', unit: '次' },
  { label: '下载 · 所选日期', value: dashboard.value.downloads?.selected ?? '—', unit: '次' },
] : []);
const request = async (path: string, options: any = {}) => {
  const response = await axios({ url: '/backend' + path, timeout: 15000, ...options,
    headers: { Authorization: `Bearer ${sessionStorage.getItem('live_ops_token') || ''}` } });
  if (response.data.code !== 200) throw new Error(response.data.message || '请求失败');
  return response.data.data;
};
const formatDate = (date: string) => date ? new Date(date).toLocaleString('zh-CN') : '—';
const transactionLabel = (type: string) => ({ RECHARGE: '充值', GIFT_DEBIT: '直播打赏', REFERRAL_REWARD: '邀请奖励', ADMIN_ADJUST: '后台余额调整' }[type] || type);
function handleError(e: any) { error.value = e?.response?.data?.message || e?.message || '加载失败'; }
function queryDates() {
  if (!dates.start || !dates.end || dates.start > dates.end) throw new Error('请选择有效的日期范围');
  return { start: dates.start, end: dates.end };
}
async function load() {
  loading.value = true; error.value = ''; notice.value = '';
  try {
    if (props.active === 'overview') dashboard.value = await request('/operations/dashboard', { params: queryDates() });
    else if (props.active === 'moments') {
      const data = await request('/operations/moments', { params: { review_status: momentReviewStatus.value } }); moments.value = data.moments; momentCursor.value = data.next_before_id;
    } else if (props.active === 'creator-list') creators.value = (await request('/operations/insights/creators', { params: queryDates() })).creators;
    else if (props.active === 'transactions') {
      if (transactionUser.value && !/^[1-9][0-9]*$/.test(transactionUser.value)) throw new Error('请输入有效用户 ID');
      transactions.value = (await request('/operations/insights/transactions', { params: transactionUser.value ? { user_id: transactionUser.value } : {} })).transactions;
    } else if (props.active === 'recharges') {
      if (rechargeUser.value && !/^[1-9][0-9]*$/.test(rechargeUser.value)) throw new Error('请输入有效用户 ID');
      const data = await request('/operations/finance/recharges', { params: rechargeUser.value ? { user_id: rechargeUser.value } : {} });
      recharges.value = data.records || []; rechargeCursor.value = data.next_before_id || '';
    } else {
      const key = props.active === 'noble' ? 'noble' : props.active;
      const data = await request(`/operations/content/${key}`);
      contentVersion.value = data.version; contentConfigured.value = data.configured;
      if (data.value) {
        if (key === 'announcements') announcements.value = data.value.items;
        if (key === 'secretary') secretaryMessage.value = data.value.message;
        if (key === 'noble') {
          noble.tiers = data.value.tiers.map((tier: any) => ({
            condition: tier.condition, detail: tier.detail,
            privilege_ids: Array.isArray(tier.privilege_ids) ? tier.privilege_ids
              : Array.from({ length: tier.privilege_count || 0 }, (_, i) => i + 1),
          }));
          noble.privileges = data.value.privileges;
        }
      }
    }
  } catch (e) { handleError(e); } finally { loading.value = false; }
}
async function loadMoreMoments() {
  if (!momentCursor.value) return;
  loading.value = true; error.value = '';
  try { const data = await request('/operations/moments', { params: { before_id: momentCursor.value, review_status: momentReviewStatus.value } }); moments.value.push(...data.moments); momentCursor.value = data.next_before_id; }
  catch (e) { handleError(e); } finally { loading.value = false; }
}
async function loadMoreRecharges() {
  if (!rechargeCursor.value) return;
  loading.value = true; error.value = '';
  try {
    const data = await request('/operations/finance/recharges', { params: { ...(rechargeUser.value ? { user_id: rechargeUser.value } : {}), before_id: rechargeCursor.value } });
    recharges.value.push(...(data.records || [])); rechargeCursor.value = data.next_before_id || '';
  } catch (e) { handleError(e); } finally { loading.value = false; }
}
async function removeMoment(row: any) {
  const reason = window.prompt(`删除动态 ${row.id} 的原因`);
  if (!reason?.trim()) return;
  saving.value = true; error.value = '';
  try { await request(`/operations/moments/${row.id}/hide`, { method: 'POST', data: { reason: reason.trim(), request_id: crypto.randomUUID() } }); row.visible = false; notice.value = '动态已从应用中移除'; }
  catch (e) { handleError(e); } finally { saving.value = false; }
}
async function saveContent() {
  if (!reason.value.trim()) { error.value = '请填写修改原因'; return; }
  const key = props.active === 'noble' ? 'noble' : props.active;
  const value = key === 'announcements' ? { items: announcements.value } : key === 'secretary' ? { message: secretaryMessage.value }
    : { tiers: noble.tiers.map(tier => ({ ...tier, privilege_count: tier.privilege_ids.length })), privileges: noble.privileges };
  saving.value = true; error.value = ''; notice.value = '';
  try { const data = await request(`/operations/content/${key}`, { method: 'PUT', data: {
    request_id: crypto.randomUUID(), expected_version: contentVersion.value, reason: reason.value.trim(), value,
  } }); contentVersion.value = data.version; contentConfigured.value = true; reason.value = ''; notice.value = '已保存，应用重新打开页面后显示新内容'; }
  catch (e) { handleError(e); } finally { saving.value = false; }
}
watch(() => props.active, () => { momentDetail.value = null; load(); });
onMounted(load);
</script>

<style scoped>
.extended-admin { display: grid; gap: 20px; color: #282234; width: 100%; min-width: 0; }
.filters, .heading, .save-row { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; }
.heading { justify-content: space-between; align-items: center; }
.heading h2, h2 { font-size: 18px; margin: 0; }
.heading p, .hint, .editor p { margin: 5px 0 0; color: #706b7b; font-size: 13px; line-height: 1.5; }
label { display: grid; gap: 6px; min-width: 0; font-size: 13px; color: #605b6c; }
input, textarea { min-width: 0; max-width: 100%; border: 1px solid #d9d4e3; border-radius: 5px; padding: 9px 10px; font: inherit; background: #fff; color: #282234; }
textarea { width: 100%; resize: vertical; box-sizing: border-box; }
button { border: 1px solid #6c45c7; border-radius: 5px; color: #6c45c7; background: #fff; padding: 9px 14px; cursor: pointer; font: inherit; font-size: 13px; }
button.primary { background: #6c45c7; color: #fff; }
button.danger { border-color: transparent; color: #b83f55; }
button:disabled { opacity: .5; cursor: not-allowed; }
.stat-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.stat { padding: 16px; background: #fff; border: 1px solid #e7e3ed; border-radius: 6px; min-height: 104px; }
.stat span, .stat small { display: block; color: #706b7b; font-size: 12px; }
.stat strong { display: block; font-size: 26px; margin: 10px 0 2px; color: #322646; }
.table-wrap { overflow: auto; max-width: 100%; min-width: 0; background: #fff; border: 1px solid #e7e3ed; border-radius: 6px; }
table { border-collapse: collapse; width: 100%; text-align: left; font-size: 13px; }
th, td { padding: 12px; border-bottom: 1px solid #eeeaf3; }
th { background: #f8f6fb; color: #71677e; white-space: nowrap; }
td small { display: block; color: #797383; margin-top: 4px; }
.moment-cell { min-width: 250px; max-width: 420px; }
.moment-cell b { color: #6c45c7; margin-right: 8px; }
.moment-cell span { overflow-wrap: anywhere; }
.moment-text { white-space: pre-wrap; overflow-wrap: anywhere; margin: 8px 0; line-height: 1.7; }
.detail-link { padding: 7px 0; border: none; background: transparent; margin-top: 6px; }
.review-filters { display: flex; flex-wrap: wrap; gap: 8px; }.review-filters button { border-color: #e4dced; border-radius: 20px; padding: 7px 17px; color: #897996; }.review-filters .selected { background: #7044d8; color: white; border-color: #7044d8; }
.review-status { display: inline-block; white-space: nowrap; padding: 5px 9px; border-radius: 20px; font-size: 12px; }.review-status.pending { background: #fff5db; color: #9b6c10; }.review-status.approved { background: #eaf7f0; color: #347c5e; }.review-status.rejected { background: #fff0f3; color: #b34559; }
.empty { text-align: center; color: #817a8d; padding: 30px; }
.negative { color: #be4d57; }.positive { color: #2a7c58; }
.editor { display: grid; gap: 11px; min-width: 0; background: #fff; border: 1px solid #e7e3ed; border-radius: 6px; padding: 16px; }
.editor h3 { font-size: 15px; margin: 0; }
.cover-setting { display: grid; gap: 8px; color: #605b6c; font-size: 13px; }
.cover-setting img { width: 360px; max-width: 100%; aspect-ratio: 3.2; object-fit: cover; border-radius: 8px; border: 1px solid #e7e0ef; }
.cover-setting small { color: #93889f; }.cover-actions { display: flex; align-items: center; gap: 8px; }
.upload-cover { display: inline-flex; position: relative; cursor: pointer; border: 1px solid #7951ed; color: #7951ed; padding: 8px 12px; border-radius: 5px; overflow: hidden; }
.upload-cover input { position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; }
.tier-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.perk-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.perk { gap: 8px; }.perk b { font-size: 13px; }
.check { display: flex; align-items: center; }
.perk-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 10px; min-width: 0; max-width: 100%; margin: 0; padding: 10px; border: 1px solid #e7e3ed; border-radius: 5px; }
.perk-options legend { padding: 0 4px; color: #605b6c; font-size: 13px; }
.perk-option { gap: 6px; min-width: 0; cursor: pointer; line-height: 1.3; }
.perk-option input { width: 16px; height: 16px; flex: 0 0 16px; margin: 0; padding: 0; accent-color: #6c45c7; }
.save-row { position: sticky; bottom: 0; width: 100%; min-width: 0; padding: 12px; background: #f8f6fb; border-top: 1px solid #e7e3ed; }
.save-row label { flex: 1; min-width: 0; }
.save-row input { width: 100%; }
.feedback { padding: 10px 12px; background: #edf8f1; color: #296848; border-radius: 5px; margin: 0; }
.feedback.error { background: #fff0f1; color: #a33748; }
@media (max-width: 1100px) { .stat-grid { grid-template-columns: repeat(3, 1fr); } .perk-grid, .tier-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 650px) {
  .stat-grid, .perk-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .tier-grid { grid-template-columns: minmax(0, 1fr); }
  .filters label { flex: 1 1 140px; min-width: 0; }
  .table-wrap table { width: max-content; min-width: 100%; }
  .table-wrap th, .table-wrap td { white-space: nowrap; }
  .table-wrap .moment-cell { white-space: normal; }
  .save-row { align-items: stretch; flex-direction: column; }
  .save-row button { width: 100%; }
}
</style>
