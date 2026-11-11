const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const Koa = require('koa');
const { Op } = require('sequelize');

require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}

class CustomError extends Error {
  constructor(message, statusCode) { super(message); this.statusCode = statusCode; }
}

const rows = [{ id: 5, user_id: 2, content: '真实动态', visible: true, hidden: false,
  created_at: new Date('2026-11-05T10:00:00Z') }];
const mediaRows = new Map();
const reviewRows = new Map();
stub('@/model/momentReview.model', { __esModule: true, default: {
  findAll: async () => [...reviewRows.values()],
  create: async value => { reviewRows.set(String(value.moment_id), value); return value; },
} });
let nextMomentId = 6;
const comments = [{ id: 50, moment_id: 5, user_id: 2, content: '真实评论', created_at: new Date('2026-11-05T10:01:00Z') }];
let nextCommentId = 51;
const likes = new Set();
const favorites = new Set();
const checkinDates = [];
let feedWhere;
const taskWhere = [];
stub('@/app/auth/authJwt', { authJwt: async ctx => ({ code: 200, userInfo: { id: Number(ctx.get('authorization').slice(7)) } }) });
stub('@/model/customError.model', { CustomError });
stub('@/app/handler/success-handle', { __esModule: true, default: ({ ctx, data }) => { ctx.status = 200; ctx.body = { data }; } });
stub('@/config/secret', { REVENUE_API_URL: 'http://revenue.test' });
stub('@/utils/request', { __esModule: true, default: { get: async (_url, options) => ({
  reviews: options.headers.Authorization === 'Bearer 1' ? [{ status: 'APPROVED' }] : [],
}) } });
stub('@/model/user.model', { __esModule: true, default: {
  findAll: async () => [{ id: 1, username: '我', avatar: null }, { id: 2, username: '作者', avatar: null }],
  findOne: async ({ where }) => ({ id: where.id, username: where.id === 2 ? '作者' : '我' }),
} });
stub('@/model/followRelation.model', { __esModule: true, default: { findAll: async () => [] } });
stub('@/model/dailyCheckin.model', { __esModule: true, default: {
  findAll: async () => [...checkinDates].sort().reverse().map(checkin_date => ({ checkin_date })),
  findOrCreate: async ({ where }) => {
    const created = !checkinDates.includes(where.checkin_date);
    if (created) checkinDates.push(where.checkin_date);
    return [{ ...where }, created];
  },
} });
stub('@/model/moment.model', { Moment: {
  sequelize: { transaction: async callback => callback({}) },
  findAll: async ({ where }) => {
    feedWhere = where;
    return rows.filter(row => (where[Op.and] || []).every(condition => {
      if (condition.visible !== undefined && row.visible !== condition.visible) return false;
      if (condition.hidden !== undefined && row.hidden !== condition.hidden) return false;
      if (condition.val?.includes('moment_review') && reviewRows.get(String(row.id))?.status !== undefined && reviewRows.get(String(row.id)).status !== 'APPROVED') return false;
      if (typeof condition.user_id === 'number' && row.user_id !== condition.user_id) return false;
      const inQuery = condition.id?.[Op.in]?.val || '';
      if (inQuery.includes('user_moment_like') && (!inQuery.includes('user_id = 1') || !likes.has(String(row.id)))) return false;
      if (inQuery.includes('user_moment_favorite') && (!inQuery.includes('user_id = 1') || !favorites.has(String(row.id)))) return false;
      if (inQuery.includes("media_kind = 'video'") && mediaRows.get(String(row.id))?.media_kind !== 'video') return false;
      if (condition.id?.[Op.notIn] && mediaRows.get(String(row.id))?.media_kind === 'video') return false;
      return true;
    }));
  },
  count: async ({ where }) => { taskWhere.push(where); return 2; },
  create: async value => {
    const row = { id: nextMomentId++, user_id: value.user_id, content: value.content,
      visible: true, hidden: false, created_at: new Date() };
    rows.push(row);
    return row;
  },
  update: async (values, { where }) => {
    const row = rows.find(item => String(item.id) === String(where.id) &&
      item.user_id === where.user_id && item.visible === where.visible);
    if (!row) return [0];
    Object.assign(row, values);
    return [1];
  },
  findOne: async ({ where }) => {
    const row = rows.find(item => String(item.id) === String(where.id) && item.visible === where.visible);
    if (!row) return null;
    const permitted = (where[Op.or] || []).some(condition =>
      (condition.hidden === false && !row.hidden) || condition.user_id === row.user_id);
    return permitted ? { id: row.id, user_id: row.user_id } : null;
  },
}, MomentMedia: {
  findAll: async () => [...mediaRows.values()].map(({ moment_id, media_kind }) => ({ moment_id, media_kind })),
  findByPk: async id => mediaRows.get(String(id)) || null,
  create: async value => { mediaRows.set(String(value.moment_id), value); return value; },
  destroy: async ({ where }) => mediaRows.delete(String(where.moment_id)),
}, MomentComment: {
  count: async ({ where }) => { taskWhere.push(where); return 4; },
  findAll: async options => {
    if (options.group) {
      if (options.group[0] === 'parent_id') return comments.filter(row => row.parent_id && row.visible !== false)
        .map(row => ({ parent_id: row.parent_id, count: '1' }));
      return [{ moment_id: 5, count: String(comments.filter(row => row.visible !== false).length) }];
    }
    return comments.filter(row => row.visible !== false &&
      (options.where.parent_id === null ? !row.parent_id : options.where.parent_id ? row.parent_id === Number(options.where.parent_id) : true));
  },
  findOne: async ({ where }) => comments.find(row => row.id === Number(where.id) && row.visible !== false &&
    (where.parent_id !== null || !row.parent_id)) || null,
  create: async value => {
    const row = { id: nextCommentId++, ...value, created_at: new Date() };
    comments.push(row);
    return row;
  },
  update: async (_values, { where }) => {
    const found = comments.filter(row => row.visible !== false && (where.id
      ? row.id === Number(where.id) && row.user_id === where.user_id
      : row.parent_id === Number(where.parent_id)));
    found.forEach(row => { row.visible = false; });
    return [found.length];
  },
}, MomentLike: {
  count: async ({ where }) => { taskWhere.push(where); return 3; },
  findAll: async options => options.group ? [{ moment_id: 5, count: '2' }] : [...likes].map(moment_id => ({ moment_id })),
  findOrCreate: async ({ where }) => { likes.add(String(where.moment_id)); return [null, true]; },
  destroy: async ({ where }) => likes.delete(String(where.moment_id)),
}, MomentFavorite: {
  findAll: async options => options.group
    ? [...favorites].map(moment_id => ({ moment_id, count: '1' }))
    : [...favorites].map(moment_id => ({ moment_id })),
  findOrCreate: async ({ where }) => { favorites.add(String(where.moment_id)); return [null, true]; },
  destroy: async ({ where }) => favorites.delete(String(where.moment_id)),
} });

test('authenticated moments support feed, text publishing, comments, likes, and owned deletion', async () => {
  const router = require('../dist/router/moments.router').default;
  const app = new Koa();
  app.use(async (ctx, next) => {
    try { await next(); } catch (error) { ctx.status = error.statusCode || 500; ctx.body = { error: error.message }; }
  });
  app.use(require('koa-bodyparser')());
  app.use(router.routes());
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  const call = async (url, method = 'GET', body, userId = 1) => {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${url}`, {
      method, headers: { authorization: `Bearer ${userId}`, ...(body ? { 'content-type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    return { status: response.status, json: await response.json() };
  };
  try {
    const feed = await call('/moments?scope=all');
    assert.equal(feed.status, 200);
    assert.equal(feed.json.data.moments[0].author.username, '作者');
    assert.equal(feed.json.data.moments[0].like_count, 2);
    assert.equal(feed.json.data.moments[0].comment_count, 1);
    assert.equal(feed.json.data.moments[0].favorite_count, 0);
    assert.equal(feed.json.data.moments[0].is_favorited, false);
    const videos = await call('/moments?scope=video');
    assert.equal(videos.status, 200);
    const [videoAndKey] = Object.getOwnPropertySymbols(feedWhere);
    const videoCondition = feedWhere[videoAndKey].find(condition => condition.id)?.id;
    assert.match(videoCondition[Object.getOwnPropertySymbols(videoCondition)[0]].val, /media_kind = 'video'/);
    const following = await call('/moments?scope=following');
    assert.equal(following.status, 200);
    const userFeed = await call('/moments?scope=user&user_id=2');
    assert.equal(userFeed.status, 200);
    assert.equal(userFeed.json.data.moments[0].user_id, 2);
    const [userFeedAndKey] = Object.getOwnPropertySymbols(feedWhere);
    assert.ok(feedWhere[userFeedAndKey].some(condition => condition.user_id === 2));
    const blockedCondition = feedWhere[userFeedAndKey].find(condition => condition.user_id?.[Op.notIn])?.user_id;
    const [notInKey] = Object.getOwnPropertySymbols(blockedCondition);
    assert.match(blockedCondition[notInKey].val, /user_block_relation/);
    assert.equal((await call('/moments?scope=user')).status, 400);
    assert.equal((await call('/moments?scope=user&user_id=0')).status, 400);
    assert.equal((await call('/moments?scope=all&user_id=2')).status, 400);
    await call('/moments?scope=following');
    const [andKey] = Object.getOwnPropertySymbols(feedWhere);
    assert.equal(feedWhere[andKey].length, 4);
    const blockedUsers = feedWhere[andKey].find(condition => condition.user_id?.[Op.notIn]).user_id[Op.notIn];
    const followedUsers = feedWhere[andKey].find(condition => condition.user_id?.[Op.in]).user_id[Op.in];
    assert.match(blockedUsers.val, /user_block_relation/);
    assert.match(followedUsers.val, /user_follow_relation/);
    const dailyProgress = await call('/moments/tasks/today');
    assert.equal(dailyProgress.status, 200);
    assert.deepEqual(dailyProgress.json.data, { published: 2, liked: 3, commented: 4 });
    assert.equal(taskWhere.length, 3);
    assert.ok(taskWhere.every(where => where.user_id === 1 && where.createdAt));
    assert.equal(taskWhere[0].visible, true);
    const beforeCheckin = await call('/moments/tasks/checkin');
    assert.equal(beforeCheckin.json.data.checked_in, false);
    assert.equal(beforeCheckin.json.data.streak_days, 0);
    const checkin = await call('/moments/tasks/checkin', 'POST');
    assert.equal(checkin.json.data.checked_in, true);
    assert.equal(checkin.json.data.streak_days, 1);
    assert.equal(checkin.json.data.reward_configured, false);
    await call('/moments/tasks/checkin', 'POST');
    assert.equal(checkinDates.length, 1);
    const listedComments = await call('/moments/5/comments');
    assert.equal(listedComments.status, 200);
    assert.equal(listedComments.json.data.comments[0].content, '真实评论');
    assert.equal(listedComments.json.data.comments[0].is_mine, false);
    assert.equal((await call('/moments/5/comments?before_id=bad')).status, 400);
    const createdComment = await call('/moments/5/comments', 'POST', { content: '  我的评论  ' });
    assert.equal(createdComment.status, 200);
    assert.equal(createdComment.json.data.comment.content, '我的评论');
    assert.equal((await call('/moments/5/comments', 'POST', { content: ' ' })).status, 400);
    const reply = await call('/moments/5/comments', 'POST', { content: '回复作者', reply_to_comment_id: '50' });
    assert.equal(reply.status, 200);
    assert.equal(reply.json.data.comment.parent_id, '50');
    const roots = await call('/moments/5/comments');
    assert.equal(roots.json.data.comments.find(comment => comment.id === '50').reply_count, 1);
    assert.equal(roots.json.data.comments.some(comment => comment.id === reply.json.data.comment.id), false);
    const replies = await call('/moments/5/comments/50/replies');
    assert.equal(replies.json.data.replies[0].content, '回复作者');
    assert.equal(replies.json.data.replies[0].reply_to_username, '作者');
    assert.equal((await call('/moments/5/comments/50/replies?after_id=bad')).status, 400);
    assert.equal((await call('/moments/5/comments/50', 'DELETE')).status, 404);
    assert.equal((await call(`/moments/5/comments/${reply.json.data.comment.id}`, 'DELETE')).json.data.deleted_count, 1);
    assert.equal((await call('/moments/5/comments/51', 'DELETE')).status, 200);
    assert.equal((await call('/moments', 'POST', { content: '  新动态  ' })).json.data.content, '新动态');
    assert.equal((await call('/moments', 'POST', { content: ' ' })).status, 400);
    const png = await require('sharp')({ create: { width: 2, height: 2, channels: 3, background: '#8844cc' } }).png().toBuffer();
    const upload = async (kind, bytes, content = '', userId = 1) => fetch(`http://127.0.0.1:${server.address().port}/moments`, {
      method: 'POST',
      headers: { authorization: `Bearer ${userId}`, 'content-type': 'application/octet-stream',
        'x-moment-meta': Buffer.from(JSON.stringify({ kind, content })).toString('base64') },
      body: bytes,
    });
    const imagePostResponse = await upload('image', png, '带图片的动态');
    assert.equal(imagePostResponse.status, 200);
    const imagePost = (await imagePostResponse.json()).data;
    assert.equal(imagePost.media_kind, 'image');
    const mp4 = Buffer.concat([Buffer.from([0, 0, 0, 16]), Buffer.from('ftypisom'), Buffer.alloc(8)]);
    assert.equal((await upload('video', mp4, '未认证视频', 2)).status, 403);
    const videoPost = (await (await upload('video', mp4, '测试视频')).json()).data;
    const myVideos = await call('/moments?scope=mine_video');
    assert.equal(myVideos.json.data.moments.some(post => post.id === videoPost.id), true);
    const myPosts = await call('/moments?scope=mine_post');
    assert.equal(myPosts.json.data.moments.some(post => post.id === videoPost.id), false);
    assert.equal(myPosts.json.data.moments.some(post => post.id === imagePost.id), true);
    const image = await fetch(`http://127.0.0.1:${server.address().port}${imagePost.media_path}`, { headers: { authorization: 'Bearer 1' } });
    assert.equal(image.status, 200);
    assert.match(image.headers.get('content-type'), /image\/jpeg/);
    const imageBytes = Buffer.from(await image.arrayBuffer());
    assert.ok(imageBytes.length > 100);
    const range = await fetch(`http://127.0.0.1:${server.address().port}${imagePost.media_path}`, {
      headers: { authorization: 'Bearer 1', range: 'bytes=0-9' },
    });
    assert.equal(range.status, 206);
    assert.deepEqual(Buffer.from(await range.arrayBuffer()), imageBytes.subarray(0, 10));
    assert.equal((await upload('video', png)).status, 400);
    assert.equal((await call('/moments/5/hidden', 'PUT', { hidden: true })).status, 404);
    assert.equal((await call('/moments/5/hidden', 'PUT', { hidden: true }, 2)).status, 200);
    assert.equal((await call('/moments?scope=all')).json.data.moments.some(post => post.id === '5'), false);
    const ownerPosts = await call('/moments?scope=mine_post', 'GET', null, 2);
    assert.equal(ownerPosts.json.data.moments.find(post => post.id === '5')?.hidden, true);
    assert.equal((await call('/moments/5/like', 'PUT')).status, 404);
    assert.equal((await call(`/moments/${imagePost.id}/hidden`, 'PUT', { hidden: true }, 2)).status, 404);
    assert.equal((await call(`/moments/${imagePost.id}/hidden`, 'PUT', { hidden: true })).status, 200);
    const hiddenMedia = await fetch(`http://127.0.0.1:${server.address().port}${imagePost.media_path}`,
      { headers: { authorization: 'Bearer 2' } });
    assert.equal(hiddenMedia.status, 404);
    assert.equal((await fetch(`http://127.0.0.1:${server.address().port}${imagePost.media_path}`,
      { headers: { authorization: 'Bearer 1' } })).status, 200);
    assert.equal((await call(`/moments/${imagePost.id}/hidden`, 'PUT', { hidden: false })).status, 200);
    assert.equal((await call('/moments/5/hidden', 'PUT', { hidden: false }, 2)).status, 200);
    const feedWithMedia = await call('/moments?scope=all');
    assert.equal(feedWithMedia.json.data.moments.some(post => post.id === imagePost.id), false);
    assert.equal((await call('/moments?scope=mine_post')).json.data.moments.find(post => post.id === imagePost.id)?.media_kind, 'image');
    assert.equal((await call('/moments/5/like', 'PUT')).status, 200);
    assert.equal(likes.has('5'), true);
    assert.equal((await call('/moments/5/like', 'DELETE')).status, 200);
    assert.equal(likes.has('5'), false);
    assert.equal((await call('/moments/5/favorite', 'PUT')).status, 200);
    assert.equal(favorites.has('5'), true);
    const favoredFeed = await call('/moments?scope=all');
    assert.equal(favoredFeed.json.data.moments.find(post => post.id === '5')?.is_favorited, true);
    assert.equal(favoredFeed.json.data.moments.find(post => post.id === '5')?.favorite_count, 1);
    assert.equal((await call('/moments/5/favorite', 'DELETE')).status, 200);
    assert.equal(favorites.has('5'), false);
    reviewRows.get(videoPost.id).status = 'APPROVED';
    await call('/moments/5/like', 'PUT');
    await call(`/moments/${videoPost.id}/like`, 'PUT');
    await call(`/moments/${videoPost.id}/favorite`, 'PUT');
    const likedPosts = await call('/moments?scope=liked_post');
    assert.equal(likedPosts.status, 200);
    assert.equal(likedPosts.json.data.moments.some(post => post.id === '5'), true);
    assert.equal(likedPosts.json.data.moments.some(post => post.id === videoPost.id), false);
    const likedVideos = await call('/moments?scope=liked_video');
    assert.equal(likedVideos.json.data.moments.some(post => post.id === videoPost.id), true);
    assert.equal(likedVideos.json.data.moments.some(post => post.id === '5'), false);
    const favoriteVideos = await call('/moments?scope=favorite_video');
    assert.equal(favoriteVideos.json.data.moments.some(post => post.id === videoPost.id), true);
    assert.equal(favoriteVideos.json.data.moments.some(post => post.id === '5'), false);
    assert.equal((await call('/moments?scope=liked_video', 'GET', null, 2)).json.data.moments.length, 0);
    assert.equal((await call('/moments?scope=favorite_video', 'GET', null, 2)).json.data.moments.length, 0);
    await call('/moments/5/like', 'DELETE');
    await call(`/moments/${videoPost.id}/like`, 'DELETE');
    await call(`/moments/${videoPost.id}/favorite`, 'DELETE');
    assert.equal((await call('/moments/6', 'DELETE')).status, 200);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});
