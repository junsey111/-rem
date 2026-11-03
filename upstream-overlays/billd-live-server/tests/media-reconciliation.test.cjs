const assert=require('node:assert/strict');
const {test}=require('node:test');
const http=require('node:http');
const path=require('node:path');
const {once}=require('node:events');
const jwt=require('jsonwebtoken');
const mysql=require('mysql2/promise');
const {Sequelize}=require('sequelize');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}

test('real LiveKit signalling/control and MySQL remove stale sessions, close rooms, restrict publishing and recover after failed authority',{
 skip:!process.env.MYSQL_TEST_URL||!process.env.LIVEKIT_TEST_URL,timeout:45000,
},async()=>{
 const base=new URL(process.env.MYSQL_TEST_URL);
 const admin=await mysql.createConnection({host:base.hostname,port:Number(base.port||3306),user:decodeURIComponent(base.username),password:decodeURIComponent(base.password)});
 const name='live_test_media_'+Date.now();
 const sockets=[];let db,authority,control,model;
 const oldUrl=process.env.LIVEKIT_API_URL;process.env.LIVEKIT_API_URL=process.env.LIVEKIT_TEST_URL;
 const managed=['11','22','pk-991'];const external='unmanaged-test-'+Date.now();
 try{
  await admin.query(`CREATE DATABASE \`${name}\``);await admin.query(`USE \`${name}\``);
  await admin.query('CREATE TABLE user(id BIGINT PRIMARY KEY,status INT,token TEXT,deleted_at DATETIME NULL)');
  const tokens=new Map();
  for(let id=1;id<=7;id++){
   const token=jwt.sign({userInfo:{id}},'isolated-account-secret',{expiresIn:id===4?-1:60});tokens.set(id,token);
   await admin.query('INSERT INTO user VALUES(?,?,?,?)',[id,id===2?2:1,id===3?'rotated':token,id===6?new Date():null]);
  }
  let unavailable=false;
  const states={'11':{room:'11',open:true,directory_open:true,publishers:['1']},'22':{room:'22',open:false,directory_open:false,publishers:[]},'pk-991':{room:'pk-991',open:false,directory_open:false,publishers:[]}};
  authority=http.createServer((req,res)=>{
   if(req.headers['x-internal-key']!=='isolated-authority-key'){res.writeHead(401);res.end('{}');return;}
   const room=decodeURIComponent(req.url.split('/').pop());
   res.writeHead(unavailable?503:200,{'Content-Type':'application/json'});res.end(JSON.stringify(unavailable?{code:'UNAVAILABLE'}:states[room]));
  });authority.listen(0,'127.0.0.1');await once(authority,'listening');
  db=new Sequelize(name,decodeURIComponent(base.username),decodeURIComponent(base.password),{host:base.hostname,port:Number(base.port||3306),dialect:'mysql',logging:false});
  stub('@/config/mysql',{__esModule:true,default:db});stub('@/utils',{initTable:()=>{}});
  stub('@/config/secret',{INTERNAL_SERVICE_KEY:'isolated-authority-key',REVENUE_API_URL:`http://127.0.0.1:${authority.address().port}`,LIVEKIT_API_KEY:'devkey',LIVEKIT_API_SECRET:'secret'});
  let mediaLocked=false;
  stub('@/service/media-access',{requireMediaAccess:async id=>{if(mediaLocked && id==='5')throw Error('preview expired');return 600;}});
  const media=require('../dist/service/media-authority');control=media.liveKitControl;
  model=require('../dist/model/live.model').default;await model.sync();
  for(const room of [...managed,external])await control('CreateRoom',room,{name:room});
  const join=async(room,id,proof=true)=>{
   const token=jwt.sign({metadata:proof?JSON.stringify({session_hash:media.mediaSessionHash(tokens.get(id)||'unknown'),live_id:room}):'',video:{room,roomJoin:true,canPublish:true,canSubscribe:true,canUpdateOwnMetadata:false}},'secret',{issuer:'devkey',subject:String(id),expiresIn:60});
   const url=new URL(process.env.LIVEKIT_TEST_URL.replace(/^http/,'ws')+'/rtc');
   url.search=new URLSearchParams({access_token:token,protocol:'15',sdk:'js',version:'2.0.0',auto_subscribe:'0'}).toString();
   const ws=new WebSocket(url);sockets.push(ws);await once(ws,'open');return ws;
  };
  for(const id of [1,2,3,4,5,6,7,9])await join('11',id,id!==7);
  await join('22',1);
  let participants=[];
  for(let i=0;i<50;i++){participants=(await control('ListParticipants','11')).participants;if(participants.length===8)break;await new Promise(r=>setTimeout(r,30));}
  assert.equal(participants.length,8,'Real signal participants must be present before reconciliation');
  const disconnected=[];
  const socket={connected:true,data:{mediaSessionHash:media.mediaSessionHash(tokens.get(1))},emit:()=>{},disconnect:()=>{socket.connected=false;disconnected.push('host');}};
  const io={sockets:{sockets:new Map([['connected-host',socket]])}};
  await model.create({roomId:'11',socketId:'connected-host',data:JSON.stringify({data:{userId:1}})});
  await model.create({roomId:'22',socketId:'dead-host',data:JSON.stringify({data:{userId:1}})});
  await model.create({roomId:'11',socketId:'crashed-process',data:JSON.stringify({data:{userId:1}})});
  unavailable=true;await assert.rejects(media.reconcileMedia(io));
  assert.equal((await control('ListParticipants','11')).participants.length,8);
  unavailable=false;await media.reconcileMedia(io);
  const rooms=(await control('ListRooms')).rooms.map(r=>r.name);
  assert.ok(!rooms.includes('22'));assert.ok(!rooms.includes('pk-991'));assert.ok(rooms.includes(external));
  participants=(await control('ListParticipants','11')).participants;
  assert.deepEqual(participants.map(p=>p.identity).sort(),['1','5']);
  assert.equal(participants.find(p=>p.identity==='1').permission.can_publish,true);
  assert.equal(participants.find(p=>p.identity==='5').permission.can_publish,false);
  assert.equal(participants.find(p=>p.identity==='5').permission.can_publish_data,false);
  assert.equal(await model.count(),1);assert.equal(disconnected.length,0);
  // Reusing an unexpired but rotated/revoked token can reconnect; another pass must evict again.
  await join('11',3);await media.reconcileMedia(io);
  assert.ok(!(await control('ListParticipants','11')).participants.some(p=>p.identity==='3'));
  mediaLocked=true;await media.reconcileMedia(io);
  assert.ok(!(await control('ListParticipants','11')).participants.some(p=>p.identity==='5'),'expired preview must disconnect an existing LiveKit participant');
  await admin.query('UPDATE user SET status=2 WHERE id=1');await media.reconcileMedia(io);
  assert.ok(!(await control('ListParticipants','11')).participants.some(p=>p.identity==='1'));
  assert.equal(await model.count(),0);assert.deepEqual(disconnected,['host']);
 }finally{
  for(const ws of sockets)ws.close();
  if(control)for(const room of [...managed,external])await control('DeleteRoom',room).catch(()=>{});
  if(authority?.listening)await new Promise(resolve=>authority.close(resolve));
  if(db)await db.close();await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);await admin.end();
  if(oldUrl===undefined)delete process.env.LIVEKIT_API_URL;else process.env.LIVEKIT_API_URL=oldUrl;
 }
});
