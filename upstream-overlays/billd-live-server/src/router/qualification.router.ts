import Router from 'koa-router';

import { requireOperations } from '@/app/auth/operations';
import successHandler from '@/app/handler/success-handle';
import { CustomError } from '@/model/customError.model';
import PlatformQualification from '@/model/platformQualification.model';
import PlatformQualificationAudit from '@/model/platformQualificationAudit.model';
import sequelize from '@/config/mysql';

const router = new Router();
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const fields = [
  'platform_name', 'subject_name', 'registration_number', 'office_address',
  'contact_email', 'icp_record', 'network_culture_license', 'live_service_license',
] as const;
const fieldLimits: Record<typeof fields[number], number> = {
  platform_name: 120,
  subject_name: 160,
  registration_number: 64,
  office_address: 240,
  contact_email: 160,
  icp_record: 120,
  network_culture_license: 120,
  live_service_license: 120,
};

function publicQualification(row: any) {
  if (!row || Number(row.published) !== 1) return { configured: false, qualification: null };
  return {
    configured: true,
    qualification: Object.fromEntries(fields.map(field => [field, row[field] || null])),
  };
}

function readPayload(body: any) {
  const payload: Record<string, string | boolean> = {};
  for (const field of fields) {
    if (typeof body?.[field] !== 'string' || Array.from(body[field].trim()).length > fieldLimits[field])
      throw new CustomError('资质字段格式无效', 400, 400);
    payload[field] = body[field].trim();
  }
  if (typeof body?.published !== 'boolean') throw new CustomError('发布状态无效', 400, 400);
  payload.published = body.published;
  if (payload.contact_email && !/^.{1,120}@[^@\s]{1,120}$/.test(String(payload.contact_email)))
    throw new CustomError('联系邮箱格式无效', 400, 400);
  if (payload.published && (!payload.platform_name || !payload.subject_name || !payload.registration_number))
    throw new CustomError('发布前必须填写平台名称、主体名称和登记注册号', 400, 400);
  return payload;
}

router.get('/qualification', async ctx => {
  const row: any = await PlatformQualification.findByPk(1, { raw: true });
  successHandler({ ctx, data: publicQualification(row) });
});

router.get('/operations/qualification', async ctx => {
  await requireOperations(ctx);
  const row: any = await PlatformQualification.findByPk(1, { raw: true });
  successHandler({ ctx, data: { configured: Boolean(row && row.platform_name && row.subject_name), qualification: row || null } });
});

router.put('/operations/qualification', async ctx => {
  const operator = await requireOperations(ctx);
  const body = ctx.request.body || {};
  const requestId = body.request_id;
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';
  if (typeof requestId !== 'string' || !UUID_V4.test(requestId) || !reason || Array.from(reason).length > 200)
    throw new CustomError('操作单号或原因无效', 400, 400);
  const payload = readPayload(body);
  const encoded = JSON.stringify(payload);
  const result = await sequelize.transaction(async transaction => {
    const [row] = await PlatformQualification.findOrCreate({
      where: { id: 1 },
      defaults: { id: 1, ...payload, updated_by: Number(operator.id) },
      transaction,
    });
    await row.reload({ transaction, lock: transaction.LOCK.UPDATE });
    const existing: any = await PlatformQualificationAudit.findOne({
      where: { request_id: requestId }, transaction, lock: transaction.LOCK.UPDATE,
    });
    if (existing) {
      if (existing.payload !== encoded || existing.reason !== reason || Number(existing.operator_id) !== Number(operator.id))
        throw new CustomError('该操作单号已用于其他资质变更', 409, 409);
      return { qualification: JSON.parse(existing.payload), replayed: true };
    }
    await row.update({ ...payload, updated_by: Number(operator.id) }, { transaction });
    await PlatformQualificationAudit.create({
      request_id: requestId, operator_id: Number(operator.id), reason, payload: encoded,
    }, { transaction });
    return { qualification: row, replayed: false };
  });
  successHandler({ ctx, data: result });
});

export default router;
