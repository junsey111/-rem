const assert=require('node:assert/strict');
const {test}=require('node:test');
const path=require('node:path');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
const requestId=require.resolve('@/utils/request');
let response={allowed:true,access:{status:'PREVIEW',seconds_remaining:12}};
require.cache[requestId]={id:requestId,filename:requestId,loaded:true,exports:{__esModule:true,default:{post:async()=>response}}};
const configId=require.resolve('@/config/secret');
require.cache[configId]={id:configId,filename:configId,loaded:true,exports:{INTERNAL_SERVICE_KEY:'isolated',REVENUE_API_URL:'http://ledger'}};
const {requireMediaAccess}=require('../dist/service/media-access');
test('media grants use authoritative remaining seconds and reject malformed or denied authority',async()=>{
 assert.equal(await requireMediaAccess('1','501'),12);
 response={allowed:true,access:{status:'UNLOCKED'}};assert.equal(await requireMediaAccess('1','501'),600);
 for(const bad of [{allowed:false},{allowed:'true'},{allowed:true},{allowed:true,access:{status:'PREVIEW',seconds_remaining:0}},{allowed:true,access:{status:'PREVIEW',seconds_remaining:61}}]){
  response=bad;await assert.rejects(requireMediaAccess('1','501'));
 }
});
