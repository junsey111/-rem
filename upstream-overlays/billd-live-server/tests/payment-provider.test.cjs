const {test}=require('node:test');
const assert=require('node:assert/strict');
const path=require('node:path');
require('module-alias').addAlias('@',path.resolve(__dirname,'../dist'));
function stub(file,exports){const id=require.resolve(file);require.cache[id]={id,filename:id,loaded:true,exports};}
let replies=[],calls=[];
class SDK { async exec(method,body,options){calls.push({method,body,options});return replies.shift();} }
stub('alipay-sdk',{__esModule:true,default:SDK});
stub('@/config/secret',{ALIPAY_CONFIG:{appId:'test',privateKey:'test',alipayPublicKey:'test'}});
const {executeAlipay}=require('../dist/service/payment-provider');
test('disabled payment makes no calls; enabled transfer/refund query first and retain exact business key and fen',async()=>{
 const subject={user_id:42,payout_method:'alipay',payout_fen:101,payout_account:'account',payout_name:'name',amount_fen:599};
 process.env.ALIPAY_DISBURSEMENTS_ENABLED='false';
 await assert.rejects(executeAlipay('withdrawals','withdrawals-1',subject));assert.equal(calls.length,0);
 process.env.ALIPAY_DISBURSEMENTS_ENABLED='true';
 replies=[{code:'40004',sub_code:'ORDER_NOT_EXIST'},{code:'10000',status:'SUCCESS',out_biz_no:'withdrawals-1',order_id:'receipt'}];
 assert.equal(await executeAlipay('withdrawals','withdrawals-1',subject),'receipt');
 assert.equal(calls[0].method,'alipay.fund.trans.common.query');assert.equal(calls[1].body.bizContent.trans_amount,'1.01');assert.equal(calls[1].body.bizContent.out_biz_no,'withdrawals-1');assert.equal(calls[1].options.validateSign,true);
 calls=[];replies=[{code:'10000',status:'DEALING',out_biz_no:'withdrawals-1'}];
 await assert.rejects(executeAlipay('withdrawals','withdrawals-1',subject));assert.equal(calls.length,1);
 calls=[];const order={user_id:42,amount_fen:599,payment_ref:'trade',request_id:'recharge'};
 replies=[{code:'40004',sub_code:'ACQ.REFUND_NOT_EXIST'},{code:'10000',trade_no:'trade',refund_fee:'5.99'}];
 assert.equal(await executeAlipay('refunds','refunds-1',subject,order),'trade:refunds-1');assert.equal(calls[1].body.bizContent.refund_amount,'5.99');assert.equal(calls[1].body.bizContent.out_request_no,'refunds-1');
 calls=[];replies=[{code:'10000',refund_status:'REFUND_SUCCESS',out_request_no:'refunds-1',trade_no:'trade',refund_amount:'5.99'}];
 assert.equal(await executeAlipay('refunds','refunds-1',subject,order),'trade:refunds-1');assert.equal(calls.length,1);
 process.env.ALIPAY_DISBURSEMENTS_ENABLED='false';
});
