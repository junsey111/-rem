import { randomInt } from 'node:crypto';
import { Op, UniqueConstraintError } from 'sequelize';

import User from '@/model/user.model';

// Legacy accounts receive a public number on their next authenticated request.
export async function ensurePublicId(user: any): Promise<number> {
  const existing = Number(user.public_id);
  if (existing >= 100000 && existing <= 999999) return existing;
  for (let attempt = 0; attempt < 32; attempt += 1) {
    const candidate = randomInt(100000, 1000000);
    try {
      const [updated] = await User.update({ public_id: candidate } as any, {
        where: { id: user.id, public_id: { [Op.is]: null } },
      });
      if (updated) return candidate;
      const current: any = await User.findByPk(user.id, { attributes: ['public_id'] });
      const assigned = Number(current?.public_id);
      if (assigned >= 100000 && assigned <= 999999) return assigned;
    } catch (error) {
      if (!(error instanceof UniqueConstraintError)) throw error;
    }
  }
  throw new Error('暂时无法分配六位账号 ID，请重试');
}
