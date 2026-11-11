<template>
  <div class="announcement-editor">
    <div class="toolbar" role="toolbar" aria-label="公告排版">
      <button type="button" @mousedown.prevent="command('bold')"><b>加粗</b></button>
      <button type="button" @mousedown.prevent="command('italic')"><i>斜体</i></button>
      <button type="button" @mousedown.prevent="command('underline')"><u>下划线</u></button>
      <button type="button" @mousedown.prevent="command('formatBlock', 'h2')">标题</button>
      <button type="button" @mousedown.prevent="command('formatBlock', 'p')">正文</button>
      <button type="button" @mousedown.prevent="command('insertUnorderedList')">列表</button>
      <button type="button" @mousedown.prevent="command('justifyLeft')">居左</button>
      <button type="button" @mousedown.prevent="command('justifyCenter')">居中</button>
      <button type="button" :disabled="uploading" @mousedown.prevent="rememberSelection" @click="picker?.click()">{{ uploading ? '上传中…' : '插入图片' }}</button>
      <button type="button" @mousedown.prevent="command('undo')">撤销</button>
      <input ref="picker" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="insertImage" />
    </div>
    <div ref="editor" class="document" contenteditable="true" role="textbox" aria-label="公告正文" aria-multiline="true"
      data-placeholder="在这里输入公告，支持文字排版和插入图片…" @input="emitDocument" @paste="pasteText" @drop.prevent />
    <div class="editor-footer"><span>{{ count }} / 10000 字</span><span>支持 JPG、PNG、WebP，每张不超过 3MB</span></div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import axios from 'axios';
const props = defineProps<{ blocks?: any[]; body?: string }>();
const emit = defineEmits<{ (e: 'change', value: { blocks: any[]; body: string }): void; (e: 'busy', value: boolean): void }>();
const editor = ref<HTMLDivElement>(), picker = ref<HTMLInputElement>();
const count = ref(0), uploading = ref(false), error = ref('');
let selection: Range | undefined;
const imagePattern = /^\/platform-content\/images\/[0-9a-f-]{36}$/;
function rememberSelection() {
  const current = window.getSelection();
  if (current?.rangeCount && editor.value?.contains(current.anchorNode)) selection = current.getRangeAt(0).cloneRange();
}
function restoreSelection() {
  editor.value?.focus();
  if (selection && editor.value?.contains(selection.startContainer)) {
    const current = window.getSelection(); current?.removeAllRanges(); current?.addRange(selection);
  }
}
function command(name: string, value?: string) {
  editor.value?.focus(); document.execCommand(name, false, value); emitDocument();
}
function emitDocument() {
  if (!editor.value) return;
  const blocks: any[] = [];
  let runs: any[] = [];
  function flush(type = 'paragraph', level?: number, align = 'left') {
    if (runs.length) blocks.push({ type, ...(level ? { level } : {}), align, runs });
    runs = [];
  }
  function walk(node: Node, style: any = {}) {
    if (node.nodeType === Node.TEXT_NODE) {
      if (node.textContent) runs.push({ text: node.textContent, ...style });
      return;
    }
    if (!(node instanceof HTMLElement)) return;
    const tag = node.tagName;
    if (tag === 'IMG') {
      flush(); const url = node.getAttribute('data-image-path');
      if (url && imagePattern.test(url)) blocks.push({ type: 'image', url });
      return;
    }
    if (tag === 'BR') { runs.push({ text: '\n', ...style }); return; }
    const block = ['P', 'DIV', 'H2', 'H3', 'LI'].includes(tag);
    if (block) flush();
    const next = { ...style };
    if (['B', 'STRONG'].includes(tag) || node.style.fontWeight === 'bold') next.bold = true;
    if (['I', 'EM'].includes(tag) || node.style.fontStyle === 'italic') next.italic = true;
    if (tag === 'U' || node.style.textDecoration.includes('underline')) next.underline = true;
    Array.from(node.childNodes).forEach(child => walk(child, next));
    if (block) flush(tag === 'LI' ? 'bullet' : tag.startsWith('H') ? 'heading' : 'paragraph',
      tag === 'H2' ? 2 : tag === 'H3' ? 3 : undefined, ['center', 'right'].includes(node.style.textAlign) ? node.style.textAlign : 'left');
  }
  Array.from(editor.value.childNodes).forEach(node => walk(node)); flush();
  const body = blocks.filter(block => block.runs).map(block => block.runs.map(run => run.text).join('')).join('\n');
  count.value = Array.from(body).length;
  error.value = count.value > 10000 ? '正文不能超过 10000 字，请精简后保存' : '';
  emit('change', { blocks, body });
}
function addImage(url: string) {
  const image = document.createElement('img'); image.src = '/backend' + url; image.setAttribute('data-image-path', url); image.alt = '公告图片';
  return image;
}
function loadDocument() {
  if (!editor.value) return;
  editor.value.replaceChildren();
  const blocks = props.blocks || (props.body || '').split('\n').map(text => ({ type: 'paragraph', runs: [{ text }] }));
  for (const block of blocks) {
    if (block.type === 'image') { if (imagePattern.test(block.url)) editor.value.append(addImage(block.url)); continue; }
    const element = document.createElement(block.type === 'heading' ? (block.level === 3 ? 'h3' : 'h2') : block.type === 'bullet' ? 'li' : 'p');
    if (['center', 'right'].includes(block.align)) element.style.textAlign = block.align;
    for (const run of block.runs || []) {
      const span = document.createElement('span'); span.textContent = run.text;
      if (run.bold) span.style.fontWeight = 'bold'; if (run.italic) span.style.fontStyle = 'italic'; if (run.underline) span.style.textDecoration = 'underline';
      element.append(span);
    }
    editor.value.append(element);
  }
  count.value = Array.from(props.body || '').length;
}
function pasteText(event: ClipboardEvent) {
  event.preventDefault(); document.execCommand('insertText', false, event.clipboardData?.getData('text/plain') || ''); emitDocument();
}
async function insertImage(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file) return;
  uploading.value = true; emit('busy', true); error.value = '';
  try {
    if (file.size > 3 * 1024 * 1024) throw new Error('图片不能超过 3MB');
    const response = await axios.post('/backend/operations/content-images', file, { timeout: 20000,
      headers: { Authorization: `Bearer ${sessionStorage.getItem('live_ops_token') || ''}`, 'Content-Type': 'application/octet-stream' } });
    if (response.data.code !== 200) throw new Error(response.data.message || '上传失败');
    const url = response.data.data.image_path; if (!imagePattern.test(url)) throw new Error('图片地址无效');
    restoreSelection();
    const image = addImage(url), current = window.getSelection();
    if (current?.rangeCount && editor.value?.contains(current.anchorNode)) {
      const range = current.getRangeAt(0); range.deleteContents(); range.insertNode(image); range.setStartAfter(image); range.collapse(true);
      current.removeAllRanges(); current.addRange(range);
    } else editor.value?.append(image);
    emitDocument();
  } catch (failure: any) { error.value = failure?.response?.data?.message || failure.message || '图片上传失败'; }
  finally { uploading.value = false; emit('busy', false); input.value = ''; }
}
onMounted(loadDocument);
</script>

<style scoped>
.announcement-editor { border: 1px solid #ddd5ed; border-radius: 10px; overflow: hidden; background: white; min-width: 0; }
.toolbar { display: flex; flex-wrap: wrap; gap: 5px; padding: 9px; border-bottom: 1px solid #e9e3f2; background: #f7f3fe; }
.toolbar button { padding: 6px 9px; border-radius: 5px; border: 1px solid transparent; background: transparent; color: #6943b0; font-size: 13px; cursor: pointer; }
.toolbar button:hover { background: #eadeff; }.toolbar button:disabled { opacity: .5; }
.document { padding: 18px; min-height: 240px; max-height: 600px; overflow-y: auto; outline: none; line-height: 1.8; font-size: 15px; word-break: break-word; color: #302a3d; }
.document:empty::before { content: attr(data-placeholder); color: #a49bad; pointer-events: none; }
.document :deep(img) { display: block; max-width: 100%; height: auto; border-radius: 6px; margin: 12px auto; }
.document :deep(p) { margin: 0 0 8px; }.document :deep(h2) { font-size: 22px; margin: 12px 0; }
.editor-footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; border-top: 1px solid #eee8f5; padding: 8px 12px; color: #93889f; font-size: 12px; }
.error { padding: 8px 12px; color: #ba3e5d; margin: 0; }
</style>
