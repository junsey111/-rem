import crypto from 'crypto';
const prefix = '$scrypt$v1$32768$8$3$';
const pattern = /^\$scrypt\$v1\$32768\$8\$3\$([0-9a-f]{32})\$([0-9a-f]{64})$/;
const options = { N: 32768, r: 8, p: 3, maxmem: 64 * 1024 * 1024 };
const derive = (password: string, salt: string): Promise<Buffer> => new Promise((resolve, reject) => {
  crypto.scrypt(password, salt, 32, options, (error, key) => error ? reject(error) : resolve(key));
});

export function validPassword(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= 1024;
}

export const isPasswordHash = (value: string) => pattern.test(value);

export async function hashPassword(password: string): Promise<string> {
  if (!validPassword(password)) throw Error('Invalid password');
  const salt = crypto.randomBytes(16).toString('hex');
  const key = await derive(password, salt);
  return prefix + salt + '$' + key.toString('hex');
}

export async function verifyPassword(password: unknown, stored: unknown): Promise<boolean> {
  if (!validPassword(password) || typeof stored !== 'string') return false;
  const match = pattern.exec(stored);
  if (match) {
    const key = await derive(password, match[1]);
    return crypto.timingSafeEqual(key, Buffer.from(match[2], 'hex'));
  }
  if (stored.startsWith('$scrypt$')) return false;
  // Legacy plaintext is accepted only until a successful transactional login upgrades it.
  const digest = (value: string) => crypto.createHash('sha256').update(value).digest();
  return crypto.timingSafeEqual(digest(password), digest(stored));
}
