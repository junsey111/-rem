<template>
  <div ref="root" :class="['moment-media', { large }]">
    <p v-if="loading" class="media-hint">正在加载{{ kind === 'video' ? '视频' : '图片' }}…</p>
    <div v-else-if="error" class="media-hint"><span>{{ error }}</span><button type="button" @click="load">重新加载</button></div>
    <template v-else-if="url">
      <button v-if="kind === 'image'" type="button" class="image-button" :aria-label="large ? '原图预览' : '放大查看图片'" @click="$emit('open')">
        <img :src="url" alt="发布的图片" />
      </button>
      <video v-else :src="url" controls playsinline preload="metadata" aria-label="发布的视频" />
    </template>
    <p v-else class="media-hint">{{ kind === 'video' ? '视频' : '图片' }}预览</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import axios from 'axios';
const props = defineProps<{ momentId: string; kind: string; large?: boolean }>();
defineEmits<{ (e: 'open'): void }>();
const root = ref<HTMLElement>(), url = ref(''), error = ref(''), loading = ref(false);
let observer: IntersectionObserver | undefined, controller: AbortController | undefined, disposed = false;
async function load() {
  if (loading.value || disposed) return;
  loading.value = true; error.value = '';
  controller = new AbortController();
  try {
    if (!/^[1-9][0-9]{0,19}$/.test(props.momentId)) throw new Error('内容编号无效');
    const response = await axios.get(`/backend/operations/moments/${props.momentId}/media`, {
      responseType: 'blob', timeout: 30000, signal: controller.signal,
      headers: { Authorization: `Bearer ${sessionStorage.getItem('live_ops_token') || ''}` },
    });
    const blob: Blob = response.data;
    if (!(props.kind === 'image' ? /^image\/(jpeg|png|webp)$/ : /^video\/(mp4|webm)$/).test(blob.type)) throw new Error('媒体格式无法预览');
    if (!disposed) { if (url.value) URL.revokeObjectURL(url.value); url.value = URL.createObjectURL(blob); }
  } catch (failure: any) {
    if (!disposed && !axios.isCancel(failure)) error.value = failure.response?.status === 404 ? '原图片或视频已不存在' : failure.response?.status === 401 ? '登录已过期，请重新登录' : '媒体加载失败';
  } finally { loading.value = false; }
}
onMounted(() => {
  if (props.large || !('IntersectionObserver' in window)) { load(); return; }
  observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) { observer?.disconnect(); load(); } }, { rootMargin: '160px' });
  if (root.value) observer.observe(root.value);
});
onBeforeUnmount(() => { disposed = true; observer?.disconnect(); controller?.abort(); if (url.value) URL.revokeObjectURL(url.value); });
</script>

<style scoped>
.moment-media { width: 210px; max-width: 100%; min-height: 85px; margin-top: 10px; background: #f5f2fa; border: 1px solid #eee8f6; border-radius: 10px; overflow: hidden; }
.moment-media img, .moment-media video { display: block; width: 100%; height: 150px; object-fit: contain; background: #18151d; }
.image-button { display: block; width: 100%; border: 0; padding: 0; cursor: zoom-in; background: transparent; }
.media-hint { display: grid; gap: 8px; padding: 15px; font-size: 12px; color: #81758f; margin: 0; }
.media-hint button { width: fit-content; border: 1px solid #d9ccee; border-radius: 6px; padding: 6px 10px; background: #fff; color: #7044d8; cursor: pointer; }
.large { width: 100%; margin: 0; }
.large img, .large video { height: auto; max-height: 55vh; min-height: 160px; object-fit: contain; }
</style>
