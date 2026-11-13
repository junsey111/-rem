export const TASK_TYPES = [
  { key: 'daily_checkin', label: '每日签到', category: 'daily' },
  { key: 'follow_user', label: '关注用户', category: 'social' },
  { key: 'first_follow', label: '首次关注用户', category: 'newcomer' },
  { key: 'set_city', label: '填写所在城市', category: 'newcomer' },
  { key: 'publish_moment', label: '发布动态', category: 'social' },
  { key: 'like_moment', label: '点赞动态', category: 'social' },
  { key: 'comment_moment', label: '评论动态', category: 'social' },
  { key: 'send_gift', label: '直播送礼', category: 'live' },
  { key: 'start_live', label: '开播一次', category: 'live' },
] as const;

const taskTypeKeys = new Set<string>(TASK_TYPES.map(task => task.key));
const oneTimeTaskKeys = new Set<string>(['first_follow', 'set_city']);
// Reserved in the existing DATE ledger for claims that can be collected once.
const oneTimeTaskDate = '1970-01-01';

export function isSupportedTaskType(key: string): boolean {
  return taskTypeKeys.has(key);
}

export function isOneTimeTask(key: string): boolean {
  return oneTimeTaskKeys.has(key);
}

export function taskClaimDate(key: string, today: string): string {
  return isOneTimeTask(key) ? oneTimeTaskDate : today;
}
