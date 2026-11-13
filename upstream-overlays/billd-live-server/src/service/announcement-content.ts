export const contentImagePattern = /^\/platform-content\/images\/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

// A typed document keeps scripts, arbitrary HTML and remote image URLs out of announcements.
export function validAnnouncement(item: any): boolean {
  const text = (value: any, max: number) => typeof value === 'string' && value.trim().length > 0 && Array.from(value).length <= max;
  if (!item || !text(item.title, 80) || typeof item.published !== 'boolean'
    || typeof item.published_at !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(item.published_at)) return false;
  if (item.cover_path && (typeof item.cover_path !== 'string' || !contentImagePattern.test(item.cover_path))) return false;
  if (item.blocks === undefined) return text(item.body, 10000);
  if (!Array.isArray(item.blocks) || !item.blocks.length || item.blocks.length > 120) return false;
  let length = 0;
  let hasContent = false;
  for (const block of item.blocks) {
    if (!block || !['paragraph', 'heading', 'bullet', 'image'].includes(block.type)) return false;
    if (block.type === 'image') {
      if (typeof block.url !== 'string' || !contentImagePattern.test(block.url)) return false;
      hasContent = true;
      continue;
    }
    if (block.align !== undefined && !['left', 'center', 'right'].includes(block.align)) return false;
    if (block.type === 'heading' && ![2, 3].includes(block.level)) return false;
    if (!Array.isArray(block.runs) || block.runs.length > 200) return false;
    for (const run of block.runs) {
      if (!run || typeof run.text !== 'string') return false;
      for (const style of ['bold', 'italic', 'underline']) if (run[style] !== undefined && typeof run[style] !== 'boolean') return false;
      length += Array.from(run.text).length;
      hasContent ||= run.text.trim().length > 0;
    }
  }
  return hasContent && length <= 10000 && typeof item.body === 'string' && Array.from(item.body).length <= 10000;
}
