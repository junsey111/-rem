const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports) { const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports}; }
test('free private video needs no creator approval; paid video requires server approval',async()=>{
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'live-dm-video-test-'));
 let approved=false,approvalReads=0;const videos=new Map();
 stub('@/config/secret',{OPENIM_DM_MEDIA_DIR:folder,REVENUE_API_URL:'http://fixture'});
 stub('@/config/mysql',{__esModule:true,default:{transaction:async run=>run({})}});
 stub('@/model/user.model',{__esModule:true,default:{findByPk:async id=>({id,status:1})}});
 stub('@/service/openim-session',{registerOpenImContact:async()=>{}});
 stub('@/utils/request',{__esModule:true,default:{get:async()=>{approvalReads++;return {reviews:approved?[{status:'APPROVED'}]:[]};}}});
 stub('@/model/openimDmVideo.model',{DmVideo:{
  findOne:async({where})=>[...videos.values()].find(row=>row.owner_user_id===where.owner_user_id&&row.request_id===where.request_id),
  findByPk:async id=>videos.get(id),create:async row=>videos.set(row.video_id,row),
 },DmVideoUnlock:{}});
 try {
  const {storeDmVideo}=require('../dist/service/openim-dm');
  const bytes=Buffer.concat([Buffer.from([0,0,0,24]),Buffer.from('ftypisom0000')]);
  const meta=(price)=>({request_id:crypto.randomUUID(),recipient_id:'2',title:'视频',price_coins:price,duration_sec:5});
  const free=await storeDmVideo(1,meta(0),bytes,'fixture-token');assert.equal(free.paywalled,false);assert.equal(approvalReads,0);
  await assert.rejects(storeDmVideo(1,meta(10),bytes,'fixture-token'),error=>error.statusCode===403);
  assert.equal(videos.size,1);
  approved=true;const paid=await storeDmVideo(1,meta(10),bytes,'fixture-token');assert.equal(paid.paywalled,true);assert.equal(paid.price_coins,10);
 } finally { assert.ok(path.resolve(folder).startsWith(path.resolve(os.tmpdir())+path.sep));fs.rmSync(folder,{recursive:true,force:true}); }
});
