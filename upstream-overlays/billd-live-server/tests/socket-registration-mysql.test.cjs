const assert=require('node:assert/strict');
const {test}=require('node:test');
const http=require('node:http');
const path=require('node:path');
const {EventEmitter,once}=require('node:events');
const jwt=require('jsonwebtoken');
const mysql=require('mysql2/promise');
const {Sequelize}=require('sequelize');
const {randomUUID}=require('node:crypto');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
async function listen(server){server.listen(0,'127.0.0.1');await once(server,'listening');return server.address().port;}
class Client extends EventEmitter {
 constructor(url,token){super();this.ws=new WebSocket(url);this.events=[];this.ws.addEventListener('message',({data})=>{
  const frame=String(data);if(frame.startsWith('0'))this.ws.send('40'+JSON.stringify({token}));
  else if(frame==='2')this.ws.send('3');
  else if(frame.startsWith('40'))this.emit('ready');
  else if(frame.startsWith('42')){const [event,payload]=JSON.parse(frame.slice(2));this.events.push(event);this.emit(event,payload);}
 });}
 wait(event){return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{this.off(event,done);reject(Error('Timed out: '+event));},5000);const done=value=>{clearTimeout(timer);resolve(value);};this.once(event,done);});}
 send(event,data){this.ws.send('42'+JSON.stringify([event,data]));}
 async join(data,event='joined'){const result=this.wait(event);this.send('join',data);return result;}
 close(){this.ws.close();}
}
test('real Socket.IO and MySQL enforce account token, live ownership, committed registration and cleanup',{skip:!process.env.MYSQL_TEST_URL,timeout:30000},async()=>{
 const base=new URL(process.env.MYSQL_TEST_URL);
 const admin=await mysql.createConnection({host:base.hostname,port:Number(base.port||3306),user:decodeURIComponent(base.username),password:decodeURIComponent(base.password),multipleStatements:true});
 const name=`live_test_socket_${Date.now()}`,secret='isolated-socket-jwt';
 let db,io,server,revenue;const clients=[];
 try{
  await admin.query(`CREATE DATABASE \`${name}\``);await admin.query(`USE \`${name}\``);
  await admin.query('CREATE TABLE user(id BIGINT PRIMARY KEY,username VARCHAR(100),token TEXT,status INT,deleted_at DATETIME NULL)');
  await admin.query('CREATE TABLE role(id BIGINT PRIMARY KEY,role_value VARCHAR(30),deleted_at DATETIME NULL); CREATE TABLE user_role(user_id BIGINT,role_id BIGINT,deleted_at DATETIME NULL); INSERT INTO role VALUES(1,"SUPER_ADMIN",NULL); INSERT INTO user_role VALUES(1,1,NULL)');
  const token=id=>jwt.sign({userInfo:{id}},secret,{expiresIn:60});
  const ownerToken=token(1),otherToken=token(2);
  await admin.query("INSERT INTO user VALUES(1,'Actual host',?,1,NULL),(2,'Other host',?,1,NULL)",[ownerToken,otherToken]);
  db=new Sequelize(name,decodeURIComponent(base.username),decodeURIComponent(base.password),{host:base.hostname,port:Number(base.port||3306),dialect:'mysql',logging:false});
  const lives={11:{live_session_id:11,creator_id:1,status:'OPEN'},22:{live_session_id:22,creator_id:2,status:'OPEN'},33:{live_session_id:33,creator_id:1,status:'CLOSED'}};
  let releaseLookup,lookupStarted;
  revenue=http.createServer(async(req,res)=>{try{
   jwt.verify(req.headers.authorization.slice(7),secret);
   const id=req.url.split('/').pop();
   if(id==='44'){lookupStarted?.();await new Promise(resolve=>{releaseLookup=resolve;});}
   const live=lives[id]||(id==='44'?{live_session_id:44,creator_id:1,status:'OPEN'}:null);
   res.writeHead(live?200:404,{'Content-Type':'application/json'});res.end(JSON.stringify(live||{code:'NOT_FOUND'}));
  }catch{res.writeHead(401);res.end('{}');}});
  const revenuePort=await listen(revenue);
  stub('@/config/secret',{JWT_SECRET:secret,REVENUE_API_URL:`http://127.0.0.1:${revenuePort}`});
  stub('@/config/mysql',{__esModule:true,default:db});
  stub('@/utils',{initTable:()=>{}});
  stub('@/service/user.service',{__esModule:true,default:{findAndToken:async id=>{const [rows]=await admin.query('SELECT * FROM user WHERE id=? AND deleted_at IS NULL',[id]);return rows[0];}}});
  const model=require('../dist/model/live.model').default;await model.sync();
  for(const model of Object.values(require('../dist/model/roomChat.model')))await model.sync();
  server=http.createServer();io=require('../dist/config/websocket').connectWebSocket(server);
  const port=await listen(server);
  const connect=async token=>{const c=new Client(`ws://127.0.0.1:${port}/socket.io/?EIO=4&transport=websocket`,token);clients.push(c);await c.wait('ready');return c;};
  const input=(roomId='11',userId=1)=>({roomId,roomName:'Real room',isAdmin:true,data:{userId,username:'Forged name',token:'must not persist'}});
  const owner=await connect(ownerToken);
  const joined=await owner.join(input());assert.equal(joined.data.username,'Actual host');assert.equal(joined.data.token,undefined);
  assert.equal(await model.count(),1); // joined must follow the committed write
  const directory=await model.findAndCountAll();
  assert.equal(directory.count,1);
  assert.equal(directory.rows[0].roomId,'11');
  assert.deepEqual(JSON.parse(directory.rows[0].data),{roomId:'11',roomName:'Real room',roomType:'SINGLE',welcomeText:'',backgroundId:0,isAdmin:true,data:{userId:1,username:'Actual host'}});
  const {roomPresence}=require('../dist/service/room-presence');
  assert.equal(roomPresence.activity('11').viewer_count,0);
  await owner.join(input());assert.equal(await model.count(),1);
  const clone=await connect(ownerToken);await clone.join(input(),'joinRejected');assert.equal(await model.count(),1);
  const visitor=await connect();await visitor.join(input(),'joinRejected');assert.equal(visitor.events.includes('joined'),false);
  const other=await connect(otherToken);await other.join(input('11',2),'joinRejected');await other.join(input('22',1),'joinRejected');
  await owner.join(input('33'),'joinRejected');await owner.join(input('999'),'joinRejected');
  await owner.join(input('../11'),'joinRejected');
  await admin.query('UPDATE user SET token=? WHERE id=1',['rotated']);await owner.join(input(),'joinRejected');
  await admin.query('UPDATE user SET token=?,status=2 WHERE id=1',[ownerToken]);await owner.join(input(),'joinRejected');
  await admin.query('UPDATE user SET status=1 WHERE id=1');
  await visitor.join({roomId:'11'});const before=owner.events.filter(e=>e==='roomNoLive').length;
  // Real relays authenticate every event, require membership and use server-owned identity.
  const blocked=async(client,event,data)=>{
   const count=owner.events.filter(e=>e===event).length;
   const rejected=client.wait('eventRejected');client.send(event,data);assert.equal((await rejected).event,event);
   await new Promise(resolve=>setTimeout(resolve,30));assert.equal(owner.events.filter(e=>e===event).length,count);
  };
  for(const event of ['message','offer','answer','candidate','roomLiveing','roomNoLive']) {
   await blocked(visitor,event,{roomId:'11',data:{text:'anonymous'}});
   await blocked(other,event,{roomId:'11',data:{text:'outside'}});
  }
  await blocked(visitor,'sendBlob',{data:{timestamp:'../../probe',blob:'must-not-write'}});
  await other.join({roomId:'11'});
  assert.equal(roomPresence.activity('11').viewer_count,1);
  await blocked(other,'roomNoLive',{roomId:'11'});
  const liveNotice=other.wait('roomLiveing');owner.send('roomLiveing',{roomId:'11'});assert.deepEqual(await liveNotice,{roomId:'11'});
  await admin.query('UPDATE user SET token=? WHERE id=1',['revoked-notice']);
  const noticeDenied=owner.wait('eventRejected');owner.send('roomNoLive',{roomId:'11'});assert.equal((await noticeDenied).event,'roomNoLive');
  await admin.query('UPDATE user SET token=? WHERE id=1',[ownerToken]);
  for(const event of ['message','offer','answer','candidate']) {
   const received=owner.wait(event);
   other.send(event,{roomId:'11',socketId:'forged',userId:1,username:'forged',data:{userId:1,username:'forged',text:'real payload'}});
   const payload=await received;assert.equal(payload.userId,2);assert.equal(payload.username,'Other host');assert.notEqual(payload.socketId,'forged');
   assert.equal(payload.data.userId,2);assert.equal(payload.data.username,'Other host');assert.equal(payload.data.text,'real payload');
  }
  await blocked(other,'message',{roomId:'11',data:{text:'x'.repeat(32769)}});
  const {ChatMessage,ChatMute,ChatAction}=require('../dist/model/roomChat.model');
  const {moderateChat}=require('../dist/service/room-chat');
  assert.equal(await ChatMessage.count(),1);
  const requestId=randomUUID(),payload={roomId:'11',data:{text:'persisted once',request_id:requestId}};
  let accepted=other.wait('messageAccepted');other.send('message',payload);const receipt=await accepted;
  accepted=other.wait('messageAccepted');other.send('message',payload);assert.equal((await accepted).messageId,receipt.messageId);
  assert.equal(await ChatMessage.count(),2);
  await blocked(other,'message',{roomId:'11',data:{text:'different',request_id:requestId}});
  const mute={request_id:randomUUID(),action:'MUTE',minutes:10,reason:'integration moderation'};
  await assert.rejects(()=>moderateChat(2,otherToken,'11',2,mute),e=>e.statusCode===403);
  await moderateChat(1,ownerToken,'11',2,mute);
  const until=(await ChatMute.findOne()).get('muted_until');
  await moderateChat(1,ownerToken,'11',2,mute);assert.equal(new Date((await ChatMute.findOne()).get('muted_until')).getTime(),new Date(until).getTime());
  assert.equal(await ChatAction.count(),1);
  await blocked(other,'message',{roomId:'11',data:{text:'muted'}});assert.equal(await ChatMessage.count(),2);
  await moderateChat(1,ownerToken,'11',2,{request_id:randomUUID(),action:'UNMUTE',reason:'appeal accepted'});
  accepted=other.wait('messageAccepted');other.send('message',{roomId:'11',data:{msg:'legacy message'}});await accepted;
  assert.equal(await ChatMessage.count(),3);
  await moderateChat(1,ownerToken,'11',2,{request_id:randomUUID(),action:'HIDE',message_id:receipt.messageId,reason:'hide test'});
  assert.equal((await ChatMessage.findByPk(receipt.messageId)).get('hidden'),true);
  assert.equal(await ChatMessage.count({where:{hidden:false}}),2);
  await admin.query('UPDATE user SET status=2 WHERE id=2');
  await blocked(other,'message',{roomId:'11',data:{text:'disabled'}});
  await admin.query('UPDATE user SET status=1,token=? WHERE id=2',['revoked']);
  await blocked(other,'candidate',{roomId:'11',data:{candidate:'revoked'}});
  await admin.query('UPDATE user SET token=? WHERE id=2',[otherToken]);
  visitor.send('roomNoLive',{roomId:'11'});await new Promise(resolve=>setTimeout(resolve,100));assert.equal(owner.events.filter(e=>e==='roomNoLive').length,before);
  const left=owner.wait('leaved');owner.send('leave',{});await left;assert.equal(await model.count(),0);
  const noRoom=other.wait('eventRejected');other.send('message',{roomId:'11',data:{text:'closed'}});await noRoom;
  await owner.join(input());assert.equal(await model.count(),1);
  owner.close();
  for(let i=0;i<50 && await model.count();i++)await new Promise(resolve=>setTimeout(resolve,20));assert.equal(await model.count(),0);
  // Disconnect during upstream authorization must never leave a phantom directory record.
  const pending=await connect(ownerToken);
  const lookup=new Promise(resolve=>{lookupStarted=resolve;});pending.send('join',input('44'));await lookup;
  pending.close();releaseLookup();await new Promise(resolve=>setTimeout(resolve,150));assert.equal(await model.count(),0);
  const revokedPending=await connect(ownerToken);
  const lookup2=new Promise(resolve=>{lookupStarted=resolve;});revokedPending.send('join',input('44'));await lookup2;
  await admin.query('UPDATE user SET token=? WHERE id=1',['revoked-during-lookup']);
  const refused=revokedPending.wait('joinRejected');releaseLookup();await refused;assert.equal(await model.count(),0);
  await admin.query('UPDATE user SET token=? WHERE id=1',[ownerToken]);
  // Failed persistence must not acknowledge a successful host registration.
  await admin.query("CREATE TRIGGER reject_live BEFORE INSERT ON live FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated write failure'");
  await clone.join(input(),'joinRejected');assert.equal(clone.events.includes('joined'),false);assert.equal(await model.count(),0);
  await admin.query('DROP TRIGGER reject_live');
  const duplicate=await connect(ownerToken);
  const outcomes=await Promise.all([clone,duplicate].map(c=>{
   const result=new Promise(resolve=>{c.once('joined',()=>resolve('joined'));c.once('joinRejected',()=>resolve('rejected'));});
   c.send('join',input());return result;
  }));
  assert.deepEqual(outcomes.sort(),['joined','rejected']);assert.equal(await model.count(),1);
  await admin.query('UPDATE user SET deleted_at=UTC_TIMESTAMP() WHERE id=2');await other.join(input('22',2),'joinRejected');
 }finally{
  for(const c of clients)c.close();if(io)await new Promise(resolve=>io.close(resolve));
  if(revenue?.listening)await new Promise(resolve=>revenue.close(resolve));
  if(db)await db.close();await admin.query(`DROP DATABASE IF EXISTS \`${name}\``);await admin.end();
 }
});
