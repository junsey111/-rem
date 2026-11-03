const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const jwt = require('jsonwebtoken');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));
function stub(file, value) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename: id, loaded: true, exports: value };
}
const token = jwt.sign({ userInfo: { id: 2 } }, 'account-secret', {
  expiresIn: 60,
});
let reports = [],
  listFailure = false,
  participantFailure = false,
  sessionValid = true,
  queried = [];
stub('@/config/secret', {
  INTERNAL_SERVICE_KEY: 'worker-key',
  REVENUE_API_URL: 'http://revenue',
  LIVEKIT_API_KEY: 'devkey',
  LIVEKIT_API_SECRET: 'secret',
});
stub('@/model/user.model', {
  __esModule: true,
  default: {
    findByPk: async () => ({
      status: 1,
      token: sessionValid ? token : 'rotated',
    }),
  },
});
stub('@/model/live.model', { __esModule: true, default: {} });
stub('@/service/media-access', { requireMediaAccess: async () => 60 });
const axiosClient = require('axios');
axiosClient.post = async (url) => {
  if (url.endsWith('ListRooms')) {
    if (listFailure) throw Error('unavailable');
    return { data: { rooms: [{ name: '11' }] } };
  }
  if (url.endsWith('ListParticipants')) {
    if (participantFailure) throw Error('probe unavailable');
    return {
      data: {
        participants: [
          {
            identity: '2',
            metadata: JSON.stringify({
              session_hash:
                require('../dist/service/media-authority').mediaSessionHash(
                  token
                ),
              live_id: '11',
            }),
          },
        ],
      },
    };
  }
  throw Error('unexpected control request');
};
stub('@/utils/request', {
  __esModule: true,
  default: {
    get: async (url, options) => {
      assert.equal(options.headers['X-Internal-Key'], 'worker-key');
      queried.push(options.params.after_id || '');
      return options.params.after_id
        ? {
            seats: [{ cohost_id: '3', live_session_id: '22', user_id: '4' }],
            next_after_id: '',
          }
        : {
            seats: [
              { cohost_id: '1', live_session_id: '11', user_id: '2' },
              { cohost_id: '2', live_session_id: '11', user_id: '3' },
            ],
            next_after_id: '2',
          };
    },
    post: async (url, body, options) => {
      assert.equal(options.headers['X-Internal-Key'], 'worker-key');
      reports.push(body);
      return {};
    },
  },
});
test('seat worker uses actual media/session state, enumerates vanished rooms, and skips failed probes', async () => {
  const { reconcileCohostSeats } = require('../dist/service/media-authority');
  await reconcileCohostSeats();
  assert.deepEqual(queried, ['', '2']);
  assert.deepEqual(reports, [
    { cohost_id: '1', online: true },
    { cohost_id: '2', online: false },
    { cohost_id: '3', online: false },
  ]);
  reports = [];
  sessionValid = false;
  await reconcileCohostSeats();
  assert.equal(reports[0].online, false);
  reports = [];
  participantFailure = true;
  await assert.rejects(reconcileCohostSeats());
  assert.deepEqual(reports, [{ cohost_id: '3', online: false }]);
  reports = [];
  listFailure = true;
  await assert.rejects(reconcileCohostSeats());
  assert.deepEqual(reports, []);
});
