import { createClient } from 'redis';

import { REDIS_CONFIG } from '@/config/secret';
import { chalkERROR, chalkINFO, chalkSUCCESS } from '@/utils/chalkTip';

export const redisClient = createClient({
  database: REDIS_CONFIG.database,
  disableOfflineQueue: true,
  socket: {
    port: REDIS_CONFIG.socket.port,
    host: REDIS_CONFIG.socket.host,
    connectTimeout: 5000,
    reconnectStrategy: retries => retries >= 3
      ? new Error('Redis connection retry limit reached')
      : Math.min(250 * (retries + 1), 1000),
  },
  password: REDIS_CONFIG.password,
});

redisClient.on('error', () => {
  console.log(chalkERROR('Redis 连接不可用'));
});

export const connectRedis = async () => {
  console.log(chalkINFO('开始连接 Redis'));
  await redisClient.connect();
  console.log(chalkSUCCESS('Redis 连接成功'));
  return redisClient;
};
