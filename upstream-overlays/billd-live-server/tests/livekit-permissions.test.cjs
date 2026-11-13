// Exercise the real router and signed grants; replace only service boundaries.
const assert = require('node:assert/strict');
const { test } = require('node:test');
const path = require('node:path');
const jwt = require('jsonwebtoken');
const Koa = require('koa');
require('module-alias').addAlias('@', path.resolve(__dirname, '../dist'));

function stub(file, exports) {
  const id = require.resolve(file);
  require.cache[id] = { id, filename:id, loaded:true, exports };
}

let matchStatus = 'OPEN';
let liveStatus = 'OPEN';
let activeMatch = 991;
let cohostMode=null;
let paidAllowed=true;
stub('@/service/media-access',{requireMediaAccess:async()=>{if(!paidAllowed)throw Object.assign(Error('locked'),{statusCode:402});return 37;}});
stub('@/service/media-authority', { mediaAuthority:async room=>({room,open:liveStatus==='OPEN',publishers:room.startsWith('pk-')?['1','2']:(cohostMode?['1','9']:['1']),...(cohostMode?{publish_sources:{'9':cohostMode==='AUDIO'?['microphone']:['microphone','camera']}}:{})}), mediaSessionHash:token=>'hash-'+token });
stub('@/service/interaction-policy', { assertPeerAccess: async () => {} });
stub('@/config/secret', { LIVEKIT_API_KEY:'fixture-key', LIVEKIT_API_SECRET:'fixture-secret', REVENUE_API_URL:'http://fixture' });
stub('@/app/auth/authJwt', { authJwt:async ctx => ({ code:200, userInfo:{ id:Number(ctx.headers.authorization.slice(7)) } }) });
stub('@/app/handler/success-handle', { __esModule:true, default:({ctx,data}) => { ctx.body=data; } });
stub('@/utils/request', { __esModule:true, default:{ get:async url => {
  if (url.endsWith('/match')) return { match_id:activeMatch };
  if (url.includes('/v1/matches/')) return { match_id:991, live_session_id:11, opponent_live_session_id:22, creator_id:1, opponent_creator_id:2, status:matchStatus };
  const id=Number(url.split('/').pop());
  return { live_session_id:id, creator_id:id/11, status:liveStatus };
} } });

test('PK spectators subscribe; only participants publish; closed/unrelated matches refuse grants', async () => {
  const router = require('../dist/router/livekit.router').default;
  const app = new Koa();
  app.use(async (ctx,next) => { try { await next(); } catch(error) { ctx.status=error.statusCode||500; ctx.body={error:error.message}; } });
  app.use(require('koa-bodyparser')());
  app.use(router.routes());
  const server = app.listen(0,'127.0.0.1');
  await new Promise(resolve => server.once('listening',resolve));
  try {
    const call = async (user, live, match, expected=200) => {
      const response = await fetch(`http://127.0.0.1:${server.address().port}/livekit/token`, {method:'POST',headers:{authorization:`Bearer ${user}`,'Content-Type':'application/json'},body:JSON.stringify({live_session_id:live,...(match?{match_id:match}:{})})});
      const data=await response.json();
      assert.equal(response.status,expected,JSON.stringify(data));
      if (expected!==200) { assert.equal(data.token,undefined); return; }
      const claims=jwt.verify(data.token,'fixture-secret',{issuer:'fixture-key',algorithms:['HS256']});
      assert.equal(claims.sub,String(user));
      assert.equal(claims.exp-claims.iat,37);
      assert.equal(claims.video.canSubscribe,true);
      assert.equal(JSON.parse(claims.metadata).session_hash,'hash-'+user);
      return claims.video;
    };
    assert.deepEqual(await call(1,11), {room:'pk-991',roomJoin:true,canPublish:true,canSubscribe:true,canPublishData:false,canUpdateOwnMetadata:false});
    assert.equal((await call(2,22,991)).canPublish,true);
    assert.equal((await call(9,11)).canPublish,false);
    await call(3,33,991,403);
    matchStatus='CLOSED';
    await call(1,11,991,403);
    liveStatus='CLOSED';
    await call(1,11,991,400);
    liveStatus='OPEN'; activeMatch=0;
    assert.equal((await call(1,11)).room,'11');
    assert.equal((await call(9,11)).canPublish,false);
    cohostMode='AUDIO';const audio=await call(9,11);assert.equal(audio.canPublish,true);assert.deepEqual(audio.canPublishSources,['microphone']);
    cohostMode='VIDEO';assert.deepEqual((await call(9,11)).canPublishSources,['microphone','camera']);
    cohostMode=null;assert.equal((await call(9,11)).canPublish,false);
    paidAllowed=false;await call(9,11,undefined,402);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});
