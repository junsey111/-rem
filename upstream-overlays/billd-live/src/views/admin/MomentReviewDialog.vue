<template>
  <Teleport to="body">
    <dialog ref="dialog" class="moment-review-dialog" aria-labelledby="moment-review-title" @cancel.prevent="close" @click="backdrop">
      <header><div><small>内容审核 · #{{ row.id }}</small><h2 id="moment-review-title">动态详情</h2></div><button type="button" class="close" aria-label="关闭详情" :disabled="saving" @click="close">×</button></header>
      <div class="detail-body">
        <div class="author"><strong>{{ row.username }}</strong><span>ID {{ row.user_id }} · {{ formatDate(row.created_at) }}</span><b :class="(row.review_status || 'APPROVED').toLowerCase()">{{ statusLabel(row.review_status || 'APPROVED') }}</b></div>
        <p class="full-content">{{ row.content || '这条内容没有文字说明' }}</p>
        <MomentMediaPreview v-if="row.media_kind" :key="row.id" :moment-id="String(row.id)" :kind="row.media_kind" large />
        <p v-if="row.review_reason" class="review-note">审核意见：{{ row.review_reason }}</p>
        <p v-if="!row.visible" class="review-note">该内容已删除。</p>
        <section v-if="row.visible" class="decision-form">
          <label for="review-reason">审核意见 <span>驳回时必填</span></label>
          <textarea id="review-reason" v-model="reason" maxlength="200" rows="3" :disabled="saving || !!pending" placeholder="请写明驳回原因，方便发布者了解问题" />
          <p v-if="error" class="error" role="alert">{{ error }}</p>
          <div class="actions">
            <template v-if="pending"><button :disabled="saving" @click="emit('close')">稍后核实</button><button :disabled="saving" class="approve" @click="submit(pending.decision)">{{ saving ? '正在核实…' : '重试并核实原审核结果' }}</button></template>
            <template v-else><button v-if="row.review_status !== 'REJECTED'" :disabled="saving || !reason.trim()" class="reject" @click="submit('REJECTED')">驳回</button><button v-if="row.review_status !== 'APPROVED'" :disabled="saving" class="approve" @click="submit('APPROVED')">{{ saving ? '提交中…' : '通过审核' }}</button></template>
          </div>
        </section>
        <section class="history"><h3>审核记录</h3><p v-if="historyError">{{ historyError }} <button @click="loadHistory">重试</button></p><p v-else-if="!history.length">{{ historyLoading ? '正在加载…' : '暂无审核记录' }}</p><div v-for="item in history" :key="item.id" class="history-item"><b>{{ statusLabel(item.decision) }}</b><span>管理员 {{ item.operator_id }} · {{ formatDate(item.created_at) }}</span><p>{{ item.reason }}</p></div></section>
      </div>
    </dialog>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import axios from 'axios';
import MomentMediaPreview from './MomentMediaPreview.vue';
const props = defineProps<{ row: any }>();
const emit = defineEmits<{ (e: 'close'): void; (e: 'reviewed', value: any): void }>();
const dialog = ref<HTMLDialogElement>(), reason = ref(''), saving = ref(false), error = ref('');
const history = ref<any[]>([]), historyLoading = ref(false), historyError = ref('');
const pending = ref<any>(null);
const statusLabel = (status: string) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status);
const formatDate = (date: string) => date ? new Date(date).toLocaleString('zh-CN') : '';
const headers = () => ({ Authorization: `Bearer ${sessionStorage.getItem('live_ops_token') || ''}` });
function close() { if (!saving.value && !pending.value) emit('close'); }
function backdrop(event: MouseEvent) { if (event.target === dialog.value) { const bounds = dialog.value!.getBoundingClientRect(); if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) close(); } }
async function loadHistory() {
  historyLoading.value = true; historyError.value = '';
  try { const response = await axios.get(`/backend/operations/moments/${props.row.id}/reviews`, { headers: headers(), timeout: 15000 }); if (response.data.code !== 200) throw new Error(response.data.message); history.value = response.data.data.actions; }
  catch { historyError.value = '审核记录加载失败'; } finally { historyLoading.value = false; }
}
async function submit(decision: string) {
  if (saving.value) return;
  if (!pending.value && decision === 'REJECTED' && !reason.value.trim()) { error.value = '请填写驳回原因'; return; }
  pending.value ||= { request_id: crypto.randomUUID(), expected_version: props.row.review_version || 0, decision, reason: reason.value.trim() };
  saving.value = true; error.value = '';
  try {
    const response = await axios.post(`/backend/operations/moments/${props.row.id}/review`, pending.value, { headers: headers(), timeout: 15000 });
    if (response.data.code !== 200) { pending.value = null; throw new Error(response.data.message || '审核失败'); }
    emit('reviewed', response.data.data); pending.value = null; reason.value = ''; await loadHistory();
  } catch (failure: any) {
    error.value = failure.response?.data?.message || failure.message || '结果未确认，请重试核实';
    if (failure.response?.status >= 400 && failure.response?.status < 500) pending.value = null;
  } finally { saving.value = false; }
}
onMounted(() => { dialog.value?.showModal(); loadHistory(); });
onBeforeUnmount(() => dialog.value?.close());
</script>

<style scoped>
.moment-review-dialog { box-sizing: border-box; width: 640px; max-width: calc(100vw - 24px); max-height: calc(100dvh - 36px); padding: 0; border: 1px solid #e7def5; border-radius: 20px; color: #2d263a; background: #fff; box-shadow: 0 24px 90px #25133840; }
.moment-review-dialog::backdrop { background: #21152c80; backdrop-filter: blur(3px); }
header { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; background: linear-gradient(110deg, #eee5ff, #eaf1ff); } header small { color: #8066a0; } h2 { margin: 5px 0 0; font-size: 21px; }.close { border: 0; background: #ffffffa8; border-radius: 50%; color: #7653a8; font-size: 25px; width: 34px; height: 34px; cursor: pointer; }
.detail-body { padding: 22px 24px; }.author { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }.author span { font-size: 12px; color: #857b90; }.author b { margin-left: auto; font-size: 12px; border-radius: 20px; padding: 5px 10px; }.pending { color: #9b6c10; background: #fff5db; }.approved { color: #347c5e; background: #eaf7f0; }.rejected { color: #b34559; background: #fff0f3; }
.full-content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; font-size: 15px; margin: 20px 0; }.review-note { padding: 12px; background: #f6f2fb; border-radius: 9px; color: #7a658f; font-size: 13px; }
.decision-form { border-top: 1px solid #eee8f5; margin-top: 22px; padding-top: 18px; }label { display: block; font-size: 14px; font-weight: 600; }label span { font-weight: 400; font-size: 12px; color: #a294ae; margin-left: 8px; }textarea { box-sizing: border-box; width: 100%; margin-top: 10px; border: 1px solid #e2d9ed; border-radius: 10px; padding: 12px; resize: vertical; font: inherit; font-size: 13px; }textarea:focus { outline: 2px solid #ddcdfb; border-color: #8a61d8; }.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }button { cursor: pointer; }button:disabled { opacity: .5; cursor: not-allowed; }.actions button { padding: 10px 20px; border-radius: 9px; font: inherit; font-size: 13px; }.approve { background: #7044d8; color: white; border: 1px solid #7044d8; }.reject { background: #fff; color: #bd5062; border: 1px solid #edd5dc; }.error { color: #b84959; font-size: 13px; }
.history { padding-top: 20px; color: #82768e; font-size: 12px; }.history h3 { font-size: 14px; color: #52445e; }.history-item { padding: 12px 0; border-top: 1px solid #f0ebf6; }.history-item span { margin-left: 10px; }.history-item p { margin-bottom: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 600px) { header, .detail-body { padding: 18px 16px; }.author b { margin-left: 0; } }
</style>
