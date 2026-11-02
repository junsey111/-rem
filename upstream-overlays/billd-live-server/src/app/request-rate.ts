// ponytail: per-process windows; use a shared atomic store before deploying multiple API replicas.
export class RequestRateLimiter {
  private windows = new Map<string, { count: number; resetAt: number }>();
  constructor(private maxKeys = 10000) {}

  consume(key: string, limit: number, now = Date.now()): number {
    let entry = this.windows.get(key);
    if (!entry || entry.resetAt <= now) {
      if (!entry && this.windows.size >= this.maxKeys) {
        for (const [oldKey, value] of this.windows) if (value.resetAt <= now) this.windows.delete(oldKey);
        if (this.windows.size >= this.maxKeys) return 60;
      }
      entry = { count: 0, resetAt: now + 60000 };
      this.windows.set(key, entry);
    }
    if (entry.count >= limit) return Math.max(1, Math.ceil((entry.resetAt - now) / 1000));
    entry.count++;
    return 0;
  }
}

export function peerAddress(ctx): string {
  const address = ctx.req.socket.remoteAddress || 'unknown';
  return address.startsWith('::ffff:') ? address.slice(7) : address;
}
