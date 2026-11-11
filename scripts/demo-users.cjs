// Local walkthrough identities use normal profiles and follow relations, never real earnings.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const profiles = [
  [651287, '小橙', '晚间聊天', '51287'], [648036, '阿宁', '心动点歌', '48036'],
  [647971, '小夏', '一起听歌', '47971'], [641181, '小伊', '舞台时刻', '41181'],
];
async function seedDemoUsers(User, models, Review) {
  for (const [publicId, name, title, asset] of profiles) {
    let user = await User.findOne({ where: { public_id: publicId } });
    if (user && !String(user.desc || '').startsWith('【演示账号】')) throw new Error(`Demo public ID ${publicId} is already in use`);
    if (!user) user = await User.create({ public_id: publicId, username: name,
      password: crypto.randomBytes(32).toString('hex'), status: 1,
      avatar: `/demo-media/${asset}.jpg`, desc: `【演示账号】${title}，欢迎来听歌、聊天～` });
    if (models && Review) for (const kind of ['image', 'video']) {
      const content = kind === 'image' ? `【演示动态】我是${name}，欢迎来到我的主页，一起聊聊今天的心情吧 💜`
        : `【演示视频】${title}，分享一段直播片段～`;
      await User.sequelize.transaction(async transaction => {
        const [post, created] = await models.Moment.findOrCreate({ where:{ user_id:user.id, content }, defaults:{ visible:true, hidden:false }, transaction });
        if (!created) return;
        const file = kind === 'image' ? path.resolve(__dirname, `../app/src/main/res/drawable-nodpi/qumi_demo_live_${asset}.jpg`)
          : path.resolve(__dirname, `../app/src/main/res/raw/qumi_demo_live_${asset}.mp4`);
        await models.MomentMedia.create({ moment_id:post.id, media_kind:kind,
          content_type:kind==='image'?'image/jpeg':'video/mp4', bytes:fs.readFileSync(file) }, { transaction });
        await Review.create({ moment_id:post.id, status:'APPROVED', reason:'本地演示内容', reviewed_at:new Date() }, { transaction });
      });
    }
  }
}
function demoMedia(root) {
  const allowed = new Map(profiles.map(([, , , asset]) => [`/demo-media/${asset}.jpg`,
    path.join(root, 'app/src/main/res/drawable-nodpi', `qumi_demo_live_${asset}.jpg`)]));
  return async (ctx, next) => {
    const file = allowed.get(ctx.path);
    if (!file || ctx.method !== 'GET') return next();
    ctx.type = 'image/jpeg'; ctx.set('Cache-Control', 'public, max-age=86400');
    ctx.body = fs.createReadStream(file);
  };
}
module.exports = { seedDemoUsers, demoMedia };
