import Router from 'koa-router';
import sharp from 'sharp';
import { fn, literal, Op, col } from 'sequelize';

import { authJwt } from '@/app/auth/authJwt';
import { RequestRateLimiter } from '@/app/request-rate';
import successHandler from '@/app/handler/success-handle';
import { REVENUE_API_URL } from '@/config/secret';
import { CustomError } from '@/model/customError.model';
import User from '@/model/user.model';
import FollowRelation from '@/model/followRelation.model';
import { Moment, MomentComment, MomentFavorite, MomentLike, MomentMedia } from '@/model/moment.model';
import DailyCheckin from '@/model/dailyCheckin.model';
import axios from '@/utils/request';
import MomentReview from '@/model/momentReview.model';
import { approvedMomentSql, momentReviewFields } from '@/service/moment-review';

const router = new Router({ prefix: '/moments' });
const rate = new RequestRateLimiter();
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MAX_VIDEO_BYTES = 20 * 1024 * 1024;

function mediaMeta(ctx) {
  const header = ctx.get('x-moment-meta');
  if (!header || header.length > 2048 || !/^[A-Za-z0-9+/]+={0,2}$/.test(header))
    throw new CustomError('动态媒体信息无效', 400, 400);
  let meta: any;
  try { meta = JSON.parse(Buffer.from(header, 'base64').toString('utf8')); }
  catch { throw new CustomError('动态媒体信息无效', 400, 400); }
  if (!['image', 'video'].includes(meta?.kind) || typeof meta?.content !== 'string')
    throw new CustomError('动态媒体信息无效', 400, 400);
  const content = meta.content.trim();
  if (Array.from(content).length > 500) throw new CustomError('动态内容不能超过 500 个字符', 400, 400);
  return { kind: meta.kind as 'image' | 'video', content };
}

async function readMediaBytes(ctx, limit: number): Promise<Buffer> {
  const length = Number(ctx.get('content-length'));
  if (Number.isFinite(length) && length > limit) throw new CustomError('媒体文件太大', 413, 413);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of ctx.req) {
    const bytes = Buffer.from(chunk);
    size += bytes.length;
    if (size > limit) throw new CustomError('媒体文件太大', 413, 413);
    chunks.push(bytes);
  }
  if (!size) throw new CustomError('请选择图片或视频', 400, 400);
  return Buffer.concat(chunks);
}

async function normalizedMedia(kind: 'image' | 'video', input: Buffer) {
  if (kind === 'image') {
    try {
      const metadata = await sharp(input, { limitInputPixels: 20000000, failOn: 'warning' }).metadata();
      if (!['jpeg', 'png', 'webp'].includes(metadata.format || '') || (metadata.pages || 1) > 1) throw Error('format');
      const bytes = await sharp(input, { limitInputPixels: 20000000, failOn: 'warning' })
        .rotate().resize(1440, 1440, { fit: 'inside', withoutEnlargement: true }).jpeg({ quality: 82 }).toBuffer();
      return { kind, contentType: 'image/jpeg', bytes };
    } catch { throw new CustomError('图片仅支持静态 JPEG、PNG、WebP', 400, 400); }
  }
  const isMp4 = input.length >= 12 && input.toString('ascii', 4, 8) === 'ftyp';
  const isWebm = input.length >= 4 && input.subarray(0, 4).equals(Buffer.from([0x1a, 0x45, 0xdf, 0xa3]));
  if (!isMp4 && !isWebm) throw new CustomError('视频仅支持 MP4 或 WebM', 400, 400);
  return { kind, contentType: isMp4 ? 'video/mp4' : 'video/webm', bytes: input };
}

async function currentUser(ctx) {
  const auth = await authJwt(ctx);
  if (auth.code !== 200 || !auth.userInfo?.id) throw new CustomError(auth.message || '请重新登录', auth.code || 401, auth.code || 401);
  return auth.userInfo;
}

function blockedUserIds(userId: number) {
  return literal(`(SELECT blocked_id FROM user_block_relation WHERE blocker_id = ${userId} UNION SELECT blocker_id FROM user_block_relation WHERE blocked_id = ${userId})`);
}

async function visibleMoment(id: string, userId: number) {
  return Moment.findOne({
    attributes: ['id', 'user_id'],
    where: { id, visible: true, user_id: { [Op.notIn]: blockedUserIds(Number(userId)) },
      [Op.and]: [literal(`(${approvedMomentSql} OR user_moment.user_id = ${Number(userId)})`)],
      [Op.or]: [{ hidden: false }, { user_id: Number(userId) }] },
  });
}

async function checkinStatus(userId: number) {
  const today = new Date().toISOString().slice(0, 10);
  const rows: any[] = await DailyCheckin.findAll({
    attributes: ['checkin_date'], where: { user_id: userId, checkin_date: { [Op.lte]: today } },
    order: [['checkin_date', 'DESC']], limit: 370, raw: true,
  });
  const offsets = new Set(rows.map(row => {
    const value = row.checkin_date instanceof Date ? row.checkin_date.toISOString().slice(0, 10) : String(row.checkin_date).slice(0, 10);
    return Math.round((Date.parse(`${today}T00:00:00Z`) - Date.parse(`${value}T00:00:00Z`)) / 86400000);
  }).filter(days => days >= 0));
  const checkedIn = offsets.has(0);
  let expected = checkedIn ? 0 : 1;
  let streak = 0;
  while (offsets.has(expected)) { streak++; expected++; }
  return { today, checked_in: checkedIn, streak_days: streak, reward_configured: false };
}

router.get('/tasks/checkin', async ctx => {
  const user = await currentUser(ctx);
  successHandler({ ctx, data: await checkinStatus(Number(user.id)) });
});

router.post('/tasks/checkin', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`daily-checkin:${user.id}`, 10)) throw new CustomError('签到操作过于频繁，请稍后重试', 429, 429);
  const today = new Date().toISOString().slice(0, 10);
  await DailyCheckin.findOrCreate({ where: { user_id: user.id, checkin_date: today }, defaults: { user_id: user.id, checkin_date: today } });
  successHandler({ ctx, data: await checkinStatus(Number(user.id)) });
});

router.get('/tasks/today', async ctx => {
  const user = await currentUser(ctx);
  const today = { [Op.gte]: literal('UTC_DATE()') };
  const [published, liked, commented] = await Promise.all([
    Moment.count({ where: { user_id: user.id, visible: true, createdAt: today } }),
    MomentLike.count({ where: { user_id: user.id, createdAt: today } }),
    MomentComment.count({ where: { user_id: user.id, createdAt: today, visible: true } }),
  ]);
  successHandler({ ctx, data: { published, liked, commented } });
});

router.get('/interactions', async ctx => {
  const user = await currentUser(ctx);
  const userId = Number(user.id);
  const limit = String(ctx.query.limit || '30') === '100' ? 100 : 30;
  if (ctx.query.limit && !['30', '100'].includes(String(ctx.query.limit)))
    throw new CustomError('分页数量无效', 400, 400);
  const rawBefore = String(ctx.query.before || '');
  const match = rawBefore ? /^(\d{13}):(l|c):([1-9]\d{0,19})(?::([1-9]\d{0,9}))?$/.exec(rawBefore) : null;
  if (rawBefore && (!match || (match[2] === 'l') !== Boolean(match[4])))
    throw new CustomError('分页游标无效', 400, 400);
  const cursor = match ? { at: new Date(Number(match[1])), source: match[2], id: match[3], actorId: Number(match[4] || 0) } : null;
  if (cursor && (!Number.isFinite(cursor.at.getTime()) || cursor.at.getTime() > Date.now() + 60_000))
    throw new CustomError('分页游标无效', 400, 400);

  const accessibleMoments = literal(`(SELECT id FROM user_moment WHERE visible = 1 AND (hidden = 0 OR user_id = ${userId}) AND (${approvedMomentSql} OR user_id = ${userId}) AND user_id NOT IN ${blockedUserIds(userId).val})`);
  const ownedMoments = literal(`(SELECT id FROM user_moment WHERE visible = 1 AND user_id = ${userId})`);
  const activeActors = literal('(SELECT id FROM user WHERE status = 1)');
  const actorFilter = { [Op.ne]: userId, [Op.in]: activeActors, [Op.notIn]: blockedUserIds(userId) };
  const beforeFor = (source: 'l' | 'c'): any => {
    if (!cursor) return {};
    if (source !== cursor.source) return { created_at: { [source === 'l' ? Op.lte : Op.lt]: cursor.at } };
    if (source === 'c') return { [Op.or]: [
      { created_at: { [Op.lt]: cursor.at } },
      { created_at: cursor.at, id: { [Op.lt]: cursor.id } },
    ] };
    return { [Op.or]: [
      { created_at: { [Op.lt]: cursor.at } },
      { created_at: cursor.at, moment_id: { [Op.lt]: cursor.id } },
      { created_at: cursor.at, moment_id: cursor.id, user_id: { [Op.lt]: cursor.actorId } },
    ] };
  };
  const [likes, comments]: any[][] = await Promise.all([
    MomentLike.findAll({ attributes: ['moment_id', 'user_id', 'created_at'],
      where: { [Op.and]: [{ moment_id: { [Op.in]: ownedMoments }, user_id: actorFilter }, beforeFor('l')] },
      order: [['created_at', 'DESC'], ['moment_id', 'DESC'], ['user_id', 'DESC']], limit: limit + 1, raw: true }),
    MomentComment.findAll({ attributes: ['id', 'moment_id', 'user_id', 'parent_id', 'reply_to_user_id', 'content', 'created_at'],
      where: { [Op.and]: [{ visible: true, moment_id: { [Op.in]: accessibleMoments }, user_id: actorFilter,
        [Op.or]: [{ parent_id: null, moment_id: { [Op.in]: ownedMoments } }, { reply_to_user_id: userId }] }, beforeFor('c')] },
      order: [['created_at', 'DESC'], ['id', 'DESC']], limit: limit + 1, raw: true }),
  ]);
  const events = [
    ...likes.map(row => ({ source: 'l' as const, row })),
    ...comments.map(row => ({ source: 'c' as const, row })),
  ];
  const compareIds = (left: string, right: string) => left.length === right.length ? left.localeCompare(right) : left.length - right.length;
  events.sort((left, right) => {
    const time = new Date(right.row.created_at).getTime() - new Date(left.row.created_at).getTime();
    if (time) return time;
    if (left.source !== right.source) return left.source === 'c' ? -1 : 1;
    const id = compareIds(String(right.source === 'c' ? right.row.id : right.row.moment_id),
      String(left.source === 'c' ? left.row.id : left.row.moment_id));
    return id || (left.source === 'l' ? Number(right.row.user_id) - Number(left.row.user_id) : 0);
  });
  const page = events.slice(0, limit);
  const momentIds = [...new Set(page.map(item => String(item.row.moment_id)))];
  const moments: any[] = momentIds.length ? await Moment.findAll({ attributes: ['id', 'user_id', 'content', 'created_at'],
    where: { id: { [Op.in]: momentIds }, visible: true }, raw: true }) : [];
  const momentById = new Map(moments.map(row => [String(row.id), row]));
  const userIds = [...new Set([...page.map(item => Number(item.row.user_id)), ...moments.map(row => Number(row.user_id))])];
  const [users, media, commentCounts]: any[][] = await Promise.all([
    userIds.length ? User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: userIds }, status: 1 }, raw: true }) : [],
    momentIds.length ? MomentMedia.findAll({ attributes: ['moment_id', 'media_kind'], where: { moment_id: { [Op.in]: momentIds } }, raw: true }) : [],
    momentIds.length ? MomentComment.findAll({ attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']],
      where: { moment_id: { [Op.in]: momentIds }, visible: true }, group: ['moment_id'], raw: true }) : [],
  ]);
  const userById = new Map(users.map(row => [Number(row.id), row]));
  const mediaById = new Map(media.map(row => [String(row.moment_id), row.media_kind]));
  const countById = new Map(commentCounts.map(row => [String(row.moment_id), Number(row.count)]));
  const interactions = page.map(({ source, row }) => {
    const moment = momentById.get(String(row.moment_id));
    const actor = userById.get(Number(row.user_id));
    const author = moment && userById.get(Number(moment.user_id));
    if (!moment || !actor || !author) return null;
    const mediaKind = mediaById.get(String(moment.id)) || null;
    return { id: source === 'l' ? `like:${row.moment_id}:${row.user_id}:${new Date(row.created_at).getTime()}` : `comment:${row.id}`,
      type: source === 'l' ? 'like' : row.parent_id ? 'reply' : 'comment',
      actor: { id: Number(actor.id), username: actor.username, avatar: actor.avatar || null },
      content: source === 'c' ? row.content : null, created_at: row.created_at,
      moment: { id: String(moment.id), user_id: Number(moment.user_id), content: moment.content,
        created_at: moment.created_at, author: { id: Number(author.id), username: author.username, avatar: author.avatar || null },
        comment_count: countById.get(String(moment.id)) || 0, media_kind: mediaKind,
        media_path: mediaKind ? `/moments/${moment.id}/media` : null } };
  }).filter(Boolean);
  const last = page[page.length - 1];
  const nextBefore = events.length > limit && last
    ? `${new Date(last.row.created_at).getTime()}:${last.source}:${last.source === 'c' ? last.row.id : last.row.moment_id}${last.source === 'l' ? `:${last.row.user_id}` : ''}` : '';
  successHandler({ ctx, data: { interactions, next_before: nextBefore } });
});

router.get('/', async ctx => {
  const user = await currentUser(ctx);
  const scope = String(ctx.query.scope || 'all');
  if (!['all', 'following', 'mine', 'mine_post', 'mine_video', 'liked_post', 'liked_video', 'favorite_video', 'user', 'video'].includes(scope)) throw new CustomError('动态范围无效', 400, 400);
  const targetUserId = String(ctx.query.user_id || '');
  if (scope === 'user' && (!/^[1-9][0-9]{0,9}$/.test(targetUserId) || !Number.isSafeInteger(Number(targetUserId))))
    throw new CustomError('用户 ID 无效', 400, 400);
  if (scope !== 'user' && targetUserId) throw new CustomError('动态范围无效', 400, 400);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页游标无效', 400, 400);
  const conditions: any[] = [{ visible: true }];
  if (scope === 'mine' || scope === 'mine_post' || scope === 'mine_video') {
    conditions.push({ user_id: user.id });
    if (scope === 'mine_video') conditions.push({ id: { [Op.in]: literal("(SELECT moment_id FROM user_moment_media WHERE media_kind = 'video')") } });
    if (scope === 'mine_post') conditions.push({ id: { [Op.notIn]: literal("(SELECT moment_id FROM user_moment_media WHERE media_kind = 'video')") } });
  }
  else {
    conditions.push(literal(approvedMomentSql));
    conditions[0].hidden = false;
    conditions.push({ user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } });
    if (scope === 'following') conditions.push({ user_id: { [Op.in]: literal(`(SELECT followed_id FROM user_follow_relation WHERE follower_id = ${Number(user.id)})`) } });
    if (scope === 'user') conditions.push({ user_id: Number(targetUserId) });
    if (scope === 'video') conditions.push({ id: { [Op.in]: literal("(SELECT moment_id FROM user_moment_media WHERE media_kind = 'video')") } });
    if (scope === 'liked_post' || scope === 'liked_video')
      conditions.push({ id: { [Op.in]: literal(`(SELECT moment_id FROM user_moment_like WHERE user_id = ${Number(user.id)})`) } });
    if (scope === 'favorite_video')
      conditions.push({ id: { [Op.in]: literal(`(SELECT moment_id FROM user_moment_favorite WHERE user_id = ${Number(user.id)})`) } });
    if (scope === 'liked_video' || scope === 'favorite_video')
      conditions.push({ id: { [Op.in]: literal("(SELECT moment_id FROM user_moment_media WHERE media_kind = 'video')") } });
    if (scope === 'liked_post')
      conditions.push({ id: { [Op.notIn]: literal("(SELECT moment_id FROM user_moment_media WHERE media_kind = 'video')") } });
  }
  const where: any = { [Op.and]: conditions };
  if (before) where.id = { [Op.lt]: before };
  const rows: any[] = await Moment.findAll({ where, order: [['id', 'DESC']], limit: 31, raw: true });
  const hasMore = rows.length > 30;
  const page = rows.slice(0, 30);
  const ids = page.map(row => row.id);
  const userIds = [...new Set(page.map(row => Number(row.user_id)))];
  const [authors, follows, counts, commentCounts, liked, favorites, favoriteCounts, media, reviews] = await Promise.all([
    userIds.length ? User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: userIds }, status: 1 }, raw: true }) : [],
    userIds.length ? FollowRelation.findAll({ attributes: ['followed_id'], where: { follower_id: user.id, followed_id: { [Op.in]: userIds } }, raw: true }) : [],
    ids.length ? MomentLike.findAll({ attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], where: { moment_id: { [Op.in]: ids } }, group: ['moment_id'], raw: true }) : [],
    ids.length ? MomentComment.findAll({ attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], where: { visible: true, moment_id: { [Op.in]: ids }, user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } }, group: ['moment_id'], raw: true }) : [],
    ids.length ? MomentLike.findAll({ attributes: ['moment_id'], where: { moment_id: { [Op.in]: ids }, user_id: user.id }, raw: true }) : [],
    ids.length ? MomentFavorite.findAll({ attributes: ['moment_id'], where: { moment_id: { [Op.in]: ids }, user_id: user.id }, raw: true }) : [],
    ids.length ? MomentFavorite.findAll({ attributes: ['moment_id', [fn('COUNT', col('moment_id')), 'count']], where: { moment_id: { [Op.in]: ids } }, group: ['moment_id'], raw: true }) : [],
    ids.length ? MomentMedia.findAll({ attributes: ['moment_id', 'media_kind'], where: { moment_id: { [Op.in]: ids } }, raw: true }) : [],
    ids.length ? MomentReview.findAll({ where: { moment_id: { [Op.in]: ids } }, raw: true }) : [],
  ]);
  const authorById = new Map(authors.map((author: any): [number, any] => [Number(author.id), author]));
  const followedIds = new Set(follows.map((item: any) => Number(item.followed_id)));
  const countById = new Map(counts.map((item: any): [string, number] => [String(item.moment_id), Number(item.count)]));
  const commentCountById = new Map(commentCounts.map((item: any): [string, number] => [String(item.moment_id), Number(item.count)]));
  const likedIds = new Set(liked.map((item: any) => String(item.moment_id)));
  const favoriteIds = new Set(favorites.map((item: any) => String(item.moment_id)));
  const favoriteCountById = new Map(favoriteCounts.map((item: any): [string, number] => [String(item.moment_id), Number(item.count)]));
  const mediaById = new Map(media.map((item: any): [string, string] => [String(item.moment_id), item.media_kind]));
  const reviewById = new Map(reviews.map((item: any): [string, any] => [String(item.moment_id), item]));
  const moments = page.map(row => ({ id: String(row.id), user_id: Number(row.user_id), content: row.content, created_at: row.created_at, hidden: Boolean(row.hidden),
    author: authorById.get(Number(row.user_id)) || null, like_count: countById.get(String(row.id)) || 0,
    comment_count: commentCountById.get(String(row.id)) || 0, is_liked: likedIds.has(String(row.id)),
    favorite_count: favoriteCountById.get(String(row.id)) || 0, is_favorited: favoriteIds.has(String(row.id)),
    is_following: followedIds.has(Number(row.user_id)),
    media_kind: mediaById.get(String(row.id)) || null, ...momentReviewFields(reviewById.get(String(row.id))),
    media_path: mediaById.has(String(row.id)) ? `/moments/${row.id}/media` : null }))
    .filter(row => row.author);
  successHandler({ ctx, data: { moments, next_before_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

router.get('/:id/media', async ctx => {
  const user = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id) || !await visibleMoment(ctx.params.id, Number(user.id)))
    throw new CustomError('媒体不存在或不可查看', 404, 404);
  const media: any = await MomentMedia.findByPk(ctx.params.id);
  if (!media) throw new CustomError('媒体不存在', 404, 404);
  const bytes: Buffer = media.bytes;
  const size = bytes.length;
  ctx.type = media.content_type;
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.set('Cache-Control', 'private, max-age=60');
  ctx.set('Accept-Ranges', 'bytes');
  const range = ctx.get('range');
  if (!range) { ctx.body = bytes; return; }
  const match = /^bytes=(\d*)-(\d*)$/.exec(range);
  const suffix = match && !match[1] ? Number(match[2]) : 0;
  const start = match && match[1] ? Number(match[1]) : Math.max(0, size - suffix);
  const end = match && match[2] && match[1] ? Number(match[2]) : size - 1;
  if (!match || !Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || end < start || start >= size || suffix < 0) {
    ctx.status = 416;
    ctx.set('Content-Range', `bytes */${size}`);
    return;
  }
  const last = Math.min(end, size - 1);
  ctx.status = 206;
  ctx.set('Content-Range', `bytes ${start}-${last}/${size}`);
  ctx.set('Content-Length', String(last - start + 1));
  ctx.body = bytes.subarray(start, last + 1);
});

router.get('/:id/comments', async ctx => {
  const user = await currentUser(ctx);
  if (!user.id) throw new CustomError('请重新登录', 401, 401);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const before = String(ctx.query.before_id || '');
  if (before && !/^[1-9][0-9]{0,19}$/.test(before)) throw new CustomError('分页游标无效', 400, 400);
  const moment: any = await visibleMoment(ctx.params.id, user.id);
  if (!moment) throw new CustomError('动态不存在或不可查看', 404, 404);
  const where: any = { moment_id: ctx.params.id, parent_id: null, visible: true,
    user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } };
  if (before) where.id = { [Op.lt]: before };
  const rows: any[] = await MomentComment.findAll({ where, order: [['id', 'DESC']], limit: 31, raw: true });
  const hasMore = rows.length > 30;
  const page = rows.slice(0, 30);
  const authorIds = [...new Set(page.map(row => Number(row.user_id)))];
  const [authors, replyCounts]: any[] = await Promise.all([
    authorIds.length ? User.findAll({ attributes: ['id', 'username', 'avatar'], where: { id: { [Op.in]: authorIds }, status: 1 }, raw: true }) : [],
    page.length ? MomentComment.findAll({ attributes: ['parent_id', [fn('COUNT', col('parent_id')), 'count']],
      where: { parent_id: { [Op.in]: page.map(row => row.id) }, visible: true,
        user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } }, group: ['parent_id'], raw: true }) : [],
  ]);
  const authorById = new Map(authors.map((author: any): [number, any] => [Number(author.id), author]));
  const replyCountById = new Map(replyCounts.map((item: any): [string, number] => [String(item.parent_id), Number(item.count)]));
  const comments = page.map(row => ({ id: String(row.id), user_id: Number(row.user_id), content: row.content,
    created_at: row.created_at, author: authorById.get(Number(row.user_id)) || null,
    reply_count: replyCountById.get(String(row.id)) || 0, is_mine: Number(row.user_id) === Number(user.id) }))
    .filter(row => row.author);
  successHandler({ ctx, data: { comments, next_before_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

router.get('/:id/comments/:commentId/replies', async ctx => {
  const user = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id) || !/^[1-9][0-9]{0,19}$/.test(ctx.params.commentId))
    throw new CustomError('评论不存在', 404, 404);
  const after = String(ctx.query.after_id || '');
  if (after && !/^[1-9][0-9]{0,19}$/.test(after)) throw new CustomError('分页游标无效', 400, 400);
  if (!await visibleMoment(ctx.params.id, Number(user.id))) throw new CustomError('动态不存在或不可查看', 404, 404);
  const root: any = await MomentComment.findOne({ attributes: ['id'],
    where: { id: ctx.params.commentId, moment_id: ctx.params.id, parent_id: null, visible: true } });
  if (!root) throw new CustomError('评论不存在', 404, 404);
  const where: any = { moment_id: ctx.params.id, parent_id: root.id, visible: true,
    user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } };
  if (after) where.id = { [Op.gt]: after };
  const rows: any[] = await MomentComment.findAll({ where, order: [['id', 'ASC']], limit: 31, raw: true });
  const hasMore = rows.length > 30;
  const page = rows.slice(0, 30);
  const authorIds = [...new Set(page.flatMap(row => [Number(row.user_id), Number(row.reply_to_user_id)].filter(Boolean)))];
  const authors: any[] = authorIds.length ? await User.findAll({ attributes: ['id', 'username', 'avatar'],
    where: { id: { [Op.in]: authorIds }, status: 1 }, raw: true }) : [];
  const authorById = new Map(authors.map((author: any): [number, any] => [Number(author.id), author]));
  const replies = page.map(row => ({ id: String(row.id), parent_id: String(root.id), user_id: Number(row.user_id),
    content: row.content, created_at: row.created_at, author: authorById.get(Number(row.user_id)) || null,
    reply_to_username: authorById.get(Number(row.reply_to_user_id))?.username || null,
    is_mine: Number(row.user_id) === Number(user.id) })).filter(row => row.author);
  successHandler({ ctx, data: { replies, next_after_id: hasMore ? String(page[page.length - 1].id) : '' } });
});

router.post('/:id/comments', async ctx => {
  const user = await currentUser(ctx);
  if (!user.id) throw new CustomError('请重新登录', 401, 401);
  if (rate.consume(`moment-comment:${user.id}`, 30)) throw new CustomError('评论过于频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const content = typeof ctx.request.body?.content === 'string' ? ctx.request.body.content.trim() : '';
  if (!content || Array.from(content).length > 500) throw new CustomError('评论需为 1 到 500 个字符', 400, 400);
  const moment: any = await visibleMoment(ctx.params.id, user.id);
  if (!moment) throw new CustomError('动态不存在或不可查看', 404, 404);
  const replyToId = String(ctx.request.body?.reply_to_comment_id || '');
  if (replyToId && !/^[1-9][0-9]{0,19}$/.test(replyToId)) throw new CustomError('回复目标无效', 400, 400);
  const target: any = replyToId ? await MomentComment.findOne({ attributes: ['id', 'user_id', 'parent_id'],
    where: { id: replyToId, moment_id: moment.id, visible: true,
      user_id: { [Op.notIn]: blockedUserIds(Number(user.id)) } } }) : null;
  if (replyToId && !target) throw new CustomError('回复目标不存在', 404, 404);
  const parentId = target ? target.parent_id || target.id : null;
  const row: any = await MomentComment.create({ moment_id: moment.id, user_id: user.id,
    parent_id: parentId, reply_to_user_id: target?.user_id || null, content });
  const replyTo: any = target ? await User.findOne({ attributes: ['username'], where: { id: target.user_id } }) : null;
  successHandler({ ctx, data: { comment: { id: String(row.id), user_id: Number(user.id), content: row.content,
    created_at: row.created_at, parent_id: parentId ? String(parentId) : null,
    reply_to_username: replyTo?.username || null,
    author: { id: Number(user.id), username: user.username, avatar: user.avatar || null }, is_mine: true } } });
});

router.delete('/:id/comments/:commentId', async ctx => {
  const user = await currentUser(ctx);
  if (!user.id) throw new CustomError('请重新登录', 401, 401);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id) || !/^[1-9][0-9]{0,19}$/.test(ctx.params.commentId))
    throw new CustomError('评论不存在', 404, 404);
  const moment: any = await visibleMoment(ctx.params.id, user.id);
  if (!moment) throw new CustomError('动态不存在或不可查看', 404, 404);
  const target: any = await MomentComment.findOne({ attributes: ['id', 'parent_id'],
    where: { id: ctx.params.commentId, moment_id: ctx.params.id, user_id: user.id, visible: true } });
  if (!target) throw new CustomError('评论不存在或已删除', 404, 404);
  const [updated] = await MomentComment.update({ visible: false }, {
    where: { id: ctx.params.commentId, moment_id: ctx.params.id, user_id: user.id, visible: true },
  });
  if (!updated) throw new CustomError('评论不存在或已删除', 404, 404);
  const [deletedReplies] = target.parent_id ? [0] : await MomentComment.update({ visible: false }, {
    where: { moment_id: ctx.params.id, parent_id: target.id, visible: true },
  });
  successHandler({ ctx, data: { id: ctx.params.commentId, deleted: true, deleted_count: updated + deletedReplies } });
});

router.post('/', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`moment-create:${user.id}`, 10)) throw new CustomError('发布过于频繁，请稍后重试', 429, 429);
  if (ctx.is('application/octet-stream')) {
    const meta = mediaMeta(ctx);
    if (meta.kind === 'video') {
      const applications: any = await axios.get(`${REVENUE_API_URL}/v1/users/me/creator-applications`, {
        headers: { Authorization: ctx.headers.authorization || '' }, timeout: 10000,
      });
      if (!applications?.reviews?.some((application) => application.status === 'APPROVED'))
        throw new CustomError('请先完成主播认证', 403, 403);
    }
    const input = await readMediaBytes(ctx, meta.kind === 'image' ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES);
    const media = await normalizedMedia(meta.kind, input);
    const database = Moment.sequelize;
    if (!database) throw Error('Moment database is unavailable');
    const row: any = await database.transaction(async transaction => {
      const moment: any = await Moment.create({ user_id: user.id, content: meta.content }, { transaction });
      await MomentReview.create({ moment_id: moment.id, status: 'PENDING' }, { transaction });
      await MomentMedia.create({ moment_id: moment.id, media_kind: media.kind, content_type: media.contentType, bytes: media.bytes }, { transaction });
      return moment;
    });
    successHandler({ ctx, data: { id: String(row.id), user_id: Number(user.id), content: row.content,
      created_at: row.created_at, media_kind: media.kind, media_path: `/moments/${row.id}/media`, review_status: 'PENDING' } });
    return;
  }
  const content = typeof ctx.request.body?.content === 'string' ? ctx.request.body.content.trim() : '';
  if (!content || Array.from(content).length > 500) throw new CustomError('动态内容需为 1 到 500 个字符', 400, 400);
  const database = Moment.sequelize;
  if (!database) throw Error('Moment database is unavailable');
  const row: any = await database.transaction(async transaction => {
    const moment: any = await Moment.create({ user_id: user.id, content }, { transaction });
    await MomentReview.create({ moment_id: moment.id, status: 'PENDING' }, { transaction });
    return moment;
  });
  successHandler({ ctx, data: { id: String(row.id), user_id: Number(user.id), content: row.content, created_at: row.created_at, review_status: 'PENDING' } });
});

router.put('/:id/hidden', async ctx => {
  const user = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('发布内容不存在', 404, 404);
  const hidden = ctx.request.body?.hidden;
  if (typeof hidden !== 'boolean') throw new CustomError('请选择显示或隐藏', 400, 400);
  const [updated] = await Moment.update({ hidden }, { where: { id: ctx.params.id, user_id: user.id, visible: true } });
  if (!updated) throw new CustomError('发布内容不存在或已删除', 404, 404);
  successHandler({ ctx, data: { id: ctx.params.id, hidden } });
});

router.delete('/:id', async ctx => {
  const user = await currentUser(ctx);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const [updated] = await Moment.update({ visible: false }, { where: { id: ctx.params.id, user_id: user.id, visible: true } });
  if (!updated) throw new CustomError('动态不存在或已删除', 404, 404);
  await MomentMedia.destroy({ where: { moment_id: ctx.params.id } });
  successHandler({ ctx, data: { id: ctx.params.id, deleted: true } });
});

router.put('/:id/like', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`moment-like:${user.id}`, 120)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const moment: any = await visibleMoment(ctx.params.id, Number(user.id));
  if (!moment) throw new CustomError('动态不存在', 404, 404);
  await MomentLike.findOrCreate({ where: { moment_id: moment.id, user_id: user.id } });
  successHandler({ ctx, data: { moment_id: String(moment.id), is_liked: true } });
});

router.delete('/:id/like', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`moment-like:${user.id}`, 120)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  await MomentLike.destroy({ where: { moment_id: ctx.params.id, user_id: user.id } });
  successHandler({ ctx, data: { moment_id: ctx.params.id, is_liked: false } });
});

router.put('/:id/favorite', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`moment-favorite:${user.id}`, 120)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const moment: any = await visibleMoment(ctx.params.id, Number(user.id));
  if (!moment) throw new CustomError('动态不存在或不可查看', 404, 404);
  await MomentFavorite.findOrCreate({ where: { moment_id: moment.id, user_id: user.id } });
  successHandler({ ctx, data: { moment_id: String(moment.id), is_favorited: true } });
});

router.delete('/:id/favorite', async ctx => {
  const user = await currentUser(ctx);
  if (rate.consume(`moment-favorite:${user.id}`, 120)) throw new CustomError('操作频繁，请稍后重试', 429, 429);
  if (!/^[1-9][0-9]{0,19}$/.test(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  await MomentFavorite.destroy({ where: { moment_id: ctx.params.id, user_id: user.id } });
  successHandler({ ctx, data: { moment_id: ctx.params.id, is_favorited: false } });
});

export default router;
