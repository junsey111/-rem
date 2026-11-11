import Router from 'koa-router';
import { requireOperations } from '@/app/auth/operations';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import { Moment, MomentMedia } from '@/model/moment.model';
import MomentReview, { MomentReviewAction } from '@/model/momentReview.model';
import sequelize from '@/config/mysql';
import { momentReviewFields } from '@/service/moment-review';

const router = new Router({ prefix: '/operations/moments' });
const validId = (id: string) => /^[1-9][0-9]{0,19}$/.test(id);

router.get('/:id/media', async ctx => {
  await requireOperations(ctx);
  if (!validId(ctx.params.id) || !await Moment.findByPk(ctx.params.id)) throw new CustomError('动态不存在', 404, 404);
  const media: any = await MomentMedia.findByPk(ctx.params.id);
  if (!media) throw new CustomError('原图片或视频已不存在', 404, 404);
  ctx.type = media.content_type;
  ctx.set('Cache-Control', 'private, no-store');
  ctx.set('X-Content-Type-Options', 'nosniff');
  ctx.body = media.bytes;
});

router.get('/:id/reviews', async ctx => {
  await requireOperations(ctx);
  if (!validId(ctx.params.id)) throw new CustomError('动态编号无效', 400, 400);
  const actions = await MomentReviewAction.findAll({ where: { moment_id: ctx.params.id }, order: [['id', 'DESC']], limit: 50, raw: true });
  successHandler({ ctx, data: { actions } });
});

router.post('/:id/review', async ctx => {
  const operator = await requireOperations(ctx);
  const body = ctx.request.body || {};
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';
  if (!validId(ctx.params.id) || !['APPROVED', 'REJECTED'].includes(body.decision)
    || !Number.isSafeInteger(body.expected_version) || body.expected_version < 0
    || typeof body.request_id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(body.request_id)
    || Array.from(reason).length > 200 || (body.decision === 'REJECTED' && !reason))
    throw new CustomError('请填写有效审核结果，驳回必须填写原因', 400, 400);
  const finalReason = reason || '审核通过';
  const result = await sequelize.transaction(async transaction => {
    const moment: any = await Moment.findByPk(ctx.params.id, { transaction, lock: transaction.LOCK.UPDATE });
    if (!moment) throw new CustomError('动态不存在', 404, 404);
    const previous: any = await MomentReviewAction.findOne({ where: { request_id: body.request_id }, transaction });
    if (previous) {
      if (String(previous.moment_id) !== ctx.params.id || Number(previous.operator_id) !== Number(operator.id)
        || previous.decision !== body.decision || previous.reason !== finalReason || previous.from_version !== body.expected_version)
        throw new CustomError('该操作单号已用于其他审核', 409, 409);
      return momentReviewFields(await MomentReview.findByPk(ctx.params.id, { transaction }));
    }
    if (!moment.visible) throw new CustomError('内容已删除，不能再审核', 409, 409);
    const review: any = await MomentReview.findByPk(ctx.params.id, { transaction, lock: transaction.LOCK.UPDATE });
    if (Number(review?.version || 0) !== body.expected_version) throw new CustomError('审核状态已更新，请刷新后再处理', 409, 409);
    const values = { status: body.decision, version: body.expected_version + 1, reason: finalReason,
      operator_id: Number(operator.id), reviewed_at: new Date() };
    if (review) await review.update(values, { transaction });
    else await MomentReview.create({ moment_id: ctx.params.id, ...values }, { transaction });
    await MomentReviewAction.create({ request_id: body.request_id, moment_id: ctx.params.id, operator_id: Number(operator.id),
      decision: body.decision, from_version: body.expected_version, reason: finalReason }, { transaction });
    return momentReviewFields(values);
  });
  successHandler({ ctx, data: { id: ctx.params.id, ...result } });
});

export default router;
