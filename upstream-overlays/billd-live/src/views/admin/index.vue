<template>
  <AdminLogin
    v-if="!authorized"
    v-model:username="credentials.username"
    v-model:password="credentials.password"
    :saving="saving"
    :error="error"
    @submit="login"
  />
  <div
    v-else
    class="admin-shell"
  >
    <button v-if="menuOpen" class="sidebar-backdrop" aria-label="关闭菜单" @click="menuOpen = false"></button>
    <aside :class="['sidebar', { 'mobile-open': menuOpen }]">
      <div class="brand">
        <span class="brand-mark">现</span><span>现场运营后台</span>
      </div>
      <p class="section-label">运营中心</p>
      <button
        v-for="item in nav"
        :key="item.key"
        :class="['nav-item', { active: active === item.key }]"
        @click="active = item.key; menuOpen = false"
      >
        {{ item.label }}
      </button>
      <div class="sidebar-foot">
        {{ loading ? '正在加载' : error ? '需要处理' : '已连接后台' }}
        <button
          class="nav-item"
          @click="logout"
        >
          退出登录
        </button>
      </div>
    </aside>
    <main class="content">
      <header class="topbar">
        <button class="menu-toggle" type="button" title="打开菜单" aria-label="打开菜单" @click="menuOpen = true">☰</button>
        <div>
          <h1>{{ current.label }}</h1>
        </div>
        <button
          class="ghost"
          :disabled="loading"
          @click="refresh"
        >
          刷新数据
        </button>
      </header>
      <p
        v-if="error"
        role="alert"
        class="error-banner"
      >
        {{ error }}
      </p>
      <p
        v-if="notice"
        role="status"
        class="save-notice"
      >
        {{ notice }}
      </p>
      <p
        v-if="loading"
        role="status"
      >
        正在加载数据…
      </p>
      <section
        v-if="pendingCommand"
        class="command-panel"
      >
        <h2>有一笔操作尚未确认</h2>
        <p>操作单号：{{ pendingCommand.data.request_id }}</p>
        <p>请先用原单号重试确认。重复提交相同单号不会重复扣款或审核。</p>
        <button
          class="primary"
          :disabled="saving"
          @click="sendCommand"
        >
          用原单号核实
        </button>
      </section>
      <section v-if="active === 'support-qr' || active === 'recharge-qr'" class="settings">
        <h2>{{ active === 'support-qr' ? '客服二维码' : '充值二维码' }}</h2>
        <img
          v-if="qrConfigured"
          :src="`${apiBase}/qr/${active === 'support-qr' ? 'support' : 'recharge'}?v=${qrVersion}`"
          alt="当前二维码"
          class="support-qr-preview"
        />
        <p v-else>尚未设置二维码</p>
        <label>
          上传二维码图片
          <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="saving" @change="uploadQr" />
        </label>
        <p class="form-note">支持 PNG、JPEG、WebP，文件不超过 3MB。上传后用户再次打开弹窗即可看到新图片。</p>
      </section>
      <section
        v-if="review"
        class="command-panel"
      >
        <h2>{{ review.title }}</h2>
        <form @submit.prevent="submitReview">
          <label v-if="review.command?.action === 'MUTE'"
            >禁言分钟数<input
              v-model.number="review.command.minutes"
              type="number"
              min="1"
              max="10080"
              required
          /></label>
          <label
            >操作原因<input
              v-model="review.reason"
              required
              maxlength="200"
          /></label>
          <label v-if="review.action === 'PAID'"
            >银行或支付渠道回执号<input
              v-model="review.payment_ref"
              required
              maxlength="128"
          /></label>
          <p v-if="review.action === 'PAID'">
            请核实实际付款成功后登记。此操作会确认已支付，不能代替真实打款。
          </p>
          <button
            class="primary"
            :disabled="saving || !!pendingCommand"
          >
            确认提交
          </button>
          <button
            type="button"
            class="ghost"
            @click="review = null"
          >
            取消
          </button>
        </form>
      </section>
      <section
        v-if="active === 'overview'"
        class="page"
      >
        <ExtendedAdmin active="overview" />
        <div class="section-heading">
          <div>
            <h2>实时直播</h2>
            <p>仅展示后端返回的开放直播间</p>
          </div>
          <button
            class="outline"
            @click="active = 'lives'"
          >
            查看全部
          </button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>直播 ID</th>
                <th>主播</th>
                <th>开始时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="live in lives"
                :key="live.live_session_id"
              >
                <td class="mono">{{ live.live_session_id }}</td>
                <td>{{ live.creator_id }}</td>
                <td>{{ formatDate(live.started_at) }}</td>
                <td>
                  <button
                    class="danger-link"
                    @click="closeLive(live.live_session_id)"
                  >
                    强制下播
                  </button>
                </td>
              </tr>
              <tr v-if="!lives.length">
                <td
                  colspan="4"
                  class="empty"
                >
                  暂无开放直播
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <ExtendedAdmin v-else-if="extendedViews.includes(active)" :active="active" />
      <section
        v-else-if="active === 'referrals'"
        class="page"
      >
        <form
          class="settings"
          @submit.prevent="saveReferrals"
        >
          <h2>邀请奖励规则</h2>
          <p>
            当前保存状态：{{
              referral.configured ? '已配置' : '使用服务默认规则'
            }}
            · 版本 {{ referral.version }}
          </p>
          <label class="toggle"
            ><input
              v-model="referral.enabled"
              type="checkbox"
            />启用邀请奖励</label
          >
          <div class="form-grid">
            <label
              >下级充值奖励（%）<input
                v-model.number="rechargePercent"
                type="number"
                min="0"
                max="100"
                step="0.01"
                required
            /></label>
            <label
              >下级提现奖励（%）<input
                v-model.number="withdrawalPercent"
                type="number"
                min="0"
                max="100"
                step="0.01"
                required
            /></label>
          </div>
          <p>
            奖励在下级充值到账或提现完成时计入邀请人的平台消费余额，不可提现。修改只影响之后完成的订单。
          </p>
          <label
            >修改原因<input
              v-model="referral.reason"
              required
              maxlength="200"
          /></label>
          <button
            class="primary"
            :disabled="saving || loading || !referralLoaded || !!pendingCommand"
          >
            保存邀请规则
          </button>
        </form>
      </section>
      <section
        v-else-if="active === 'levels'"
        class="page"
      >
        <form class="settings" @submit.prevent="saveLevels">
          <h2>等级达成条件</h2>
          <p>用户按累计消费金币升级，主播按累计获得钻石升级。1 级无需门槛，最高为 6 级。版本 {{ levels.version }}</p>
          <div v-for="(_, index) in levels.viewer_thresholds" :key="index" class="form-grid">
            <label>用户 Lv.{{ index + 2 }}（金币）<input
              v-model.number="levels.viewer_thresholds[index]"
              type="number" min="1" max="1000000000000" step="1" required
            /></label>
            <label>主播 Lv.{{ index + 2 }}（钻石）<input
              v-model.number="levels.creator_thresholds[index]"
              type="number" min="1" max="1000000000000" step="1" required
            /></label>
          </div>
          <label>修改原因<input v-model="levels.reason" required maxlength="200" /></label>
          <button class="primary" :disabled="saving || loading || !levelsLoaded || !!pendingCommand">保存等级条件</button>
        </form>
      </section>
      <section
        v-else-if="active === 'business-actions'"
        class="page"
      >
        <p>
          记录邀请奖励、等级条件与直播收费的设置变更，保留操作人、原因和保存时的规则。
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>操作时间 / 单号</th>
                <th>设置对象</th>
                <th>保存结果</th>
                <th>操作人 / 原因</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in rows"
                :key="row.action_id"
              >
                <td>
                  {{ formatDate(row.created_at)
                  }}<small>{{ row.request_id }}</small>
                </td>
                <td>
                  {{
                    row.kind === 'REFERRALS'
                      ? '邀请奖励'
                      : row.kind === 'LEVELS' ? '等级条件' : '直播收费 · ' + row.target_id
                  }}
                </td>
                <td>
                  <template v-if="row.kind === 'REFERRALS'"
                    >{{ row.settings.enabled ? '已启用' : '已关闭'
                    }}<small
                      >充值 {{ (row.settings.recharge_bps || 0) / 100 }}% /
                      提现 {{ (row.settings.withdrawal_bps || 0) / 100 }}%</small
                    ></template
                  ><template v-else-if="row.kind === 'LEVELS'"
                    >用户与主播 Lv.2–6 门槛</template
                  ><template v-else
                    >{{ row.settings.unlock_price_coins }} 金币</template
                  ><small>版本 {{ row.settings.version }}</small>
                </td>
                <td>
                  {{ row.operator_id }}<small>{{ row.reason }}</small>
                </td>
              </tr>
              <tr v-if="!rows.length">
                <td
                  colspan="4"
                  class="empty"
                >
                  {{
                    error
                      ? '加载失败，请刷新'
                      : loading
                      ? '正在加载…'
                      : '暂无设置变更'
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          v-if="nextCursor"
          class="outline"
          :disabled="loading"
          @click="loadMore"
        >
          加载更多
        </button>
      </section>
      <section
        v-else-if="active === 'creators' || active === 'reports'"
        class="page"
      >
        <p>
          {{
            active === 'creators'
              ? '核实材料后审核入驻；审核通过不等同于第三方实名认证。'
              : '核查举报并记录结论；需要禁用账号或下播时，请到对应管理页处理。'
          }}
        </p>
        <form
          v-if="active === 'creators'"
          class="settings"
          @submit.prevent="saveAdmission"
        >
          <label class="toggle"
            ><input
              v-model="admission.require_approval"
              type="checkbox"
            />要求主播审核通过后才能开播</label
          >
          <p>
            开启后也会撤销未审核主播的现有媒体权限；年龄和账号状态限制继续独立生效。
          </p>
          <label
            >修改原因<input
              v-model="admission.reason"
              required
              maxlength="200"
          /></label>
          <button
            class="primary"
            :disabled="saving || loading || !!pendingCommand"
          >
            保存准入规则
          </button>
        </form>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>申请 / 用户</th>
                <th>{{ active === 'creators' ? '姓名' : '对象 / 内容' }}</th>
                <th>{{ active === 'creators' ? '联系电话' : '材料引用' }}</th>
                <th>审核结果</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in rows"
                :key="row.review_id"
              >
                <td>
                  {{ row.review_id }} / {{ row.user_id
                  }}<small>{{ formatDate(row.created_at) }}</small>
                </td>
                <td v-if="active === 'creators'">{{ row.content }}</td>
                <td v-else>
                  {{ row.target_type }} · {{ row.target_id }}<small>{{ row.content }}</small>
                </td>
                <td>{{ row.evidence_reference }}</td>
                <td>
                  {{ statusLabel(row.status)
                  }}<small>{{ row.decision_reason }}</small>
                </td>
                <td>
                  <template v-if="row.status === 'PENDING'"
                    ><button
                      class="outline"
                      :disabled="!!pendingCommand"
                      @click="
                        reviewRequest(
                          row,
                          active === 'creators' ? 'APPROVED' : 'RESOLVED'
                        )
                      "
                    >
                      {{ active === 'creators' ? '通过' : '已处理' }}</button
                    ><button
                      class="danger-link"
                      :disabled="!!pendingCommand"
                      @click="
                        reviewRequest(
                          row,
                          active === 'creators' ? 'REJECTED' : 'DISMISSED'
                        )
                      "
                    >
                      {{ active === 'creators' ? '驳回' : '不予处理' }}
                    </button></template
                  >
                </td>
              </tr>
              <tr v-if="!rows.length">
                <td
                  colspan="5"
                  class="empty"
                >
                  {{
                    error
                      ? '加载失败，请刷新'
                      : loading
                      ? '正在加载…'
                      : '暂无申请记录'
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          v-if="nextCursor"
          class="outline"
          :disabled="loading"
          @click="loadMore"
        >
          加载更多
        </button>
      </section>
      <section
        v-else-if="active === 'gifts'"
        class="page"
      >
        <div class="section-heading">
          <p>价格与钻石奖励只影响之后的送礼订单，既有账本保留原值。</p>
          <button
            class="primary"
            :disabled="!!pendingCommand"
            @click="editGift()"
          >
            新增礼物
          </button>
        </div>
        <form
          v-if="giftEditor"
          class="settings"
          @submit.prevent="saveGift"
        >
          <h2>{{ giftEditor.existing ? '编辑礼物' : '新增礼物' }}</h2>
          <label
            >礼物标识<input
              v-model="giftEditor.gift_id"
              :disabled="giftEditor.existing || !!giftEditor.icon_url"
              pattern="[a-zA-Z0-9_-]{1,32}"
              required
              maxlength="32"
          /></label>
          <label>礼物图片
            <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="iconUploading || saving || !!pendingCommand" @change="uploadGiftIcon" />
          </label>
          <div v-if="giftEditor.icon_url || giftSampleIcon(giftEditor.gift_id)" class="gift-image-preview">
            <img :src="giftEditor.icon_url ? giftIconSrc(giftEditor.icon_url) : giftSampleIcon(giftEditor.gift_id)" alt="礼物图片预览" />
            <span>{{ giftEditor.icon_url ? '已上传图片，保存礼物后在应用中显示' : '示例图片，可上传替换' }}</span>
          </div>
          <label
            >礼物名称<input
              v-model="giftEditor.display_name"
              required
              maxlength="64"
          /></label>
          <div class="form-grid">
            <label>前台分类
              <select v-model="giftEditor.category" required>
                <option value="recommend">推荐</option>
                <option value="heart">心意</option>
                <option value="party">派对</option>
                <option value="premium">典藏</option>
              </select>
            </label>
            <label
              >金币价格<input
                v-model.number="giftEditor.coin_price"
                type="number"
                min="1"
                max="1000000"
                required /></label
            ><label
              >钻石奖励<input
                v-model.number="giftEditor.diamond_reward"
                type="number"
                min="0"
                max="1000000"
                required /></label
            ><label
              >PK 积分<input
                v-model.number="giftEditor.match_points"
                type="number"
                min="0"
                max="1000000"
                required
            /></label>
          </div>
          <label class="toggle"
            ><input
              v-model="giftEditor.enabled"
              type="checkbox"
            />上架礼物</label
          >
          <label
            >修改原因<input
              v-model="giftEditor.reason"
              required
              maxlength="200"
          /></label>
          <button
            class="primary"
            :disabled="saving || iconUploading || !!pendingCommand"
          >
            {{ iconUploading ? '上传中…' : '保存礼物' }}</button
          ><button
            class="ghost"
            type="button"
            @click="giftEditor = null"
          >
            取消
          </button>
        </form>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>标识 / 名称</th>
                <th>金币价格</th>
                <th>前台分类</th>
                <th>钻石 / PK 积分</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="gift in gifts"
                :key="gift.gift_id"
              >
                <td>
                  <div class="gift-table-name">
                    <img :src="gift.icon_url ? giftIconSrc(gift.icon_url) : giftSampleIcon(gift.gift_id)" alt="" />
                    <span>{{ gift.gift_id }}<small>{{ gift.display_name }}</small></span>
                  </div>
                </td>
                <td>{{ gift.coin_price }}</td>
                <td>{{ giftCategoryLabel(gift.category) }}</td>
                <td>{{ gift.diamond_reward }} / {{ gift.match_points }}</td>
                <td>{{ gift.enabled ? '已上架' : '已下架' }}</td>
                <td>
                  <button
                    class="outline"
                    :disabled="!!pendingCommand"
                    @click="editGift(gift)"
                  >
                    编辑
                  </button>
                </td>
              </tr>
              <tr v-if="!gifts.length">
                <td
                  class="empty"
                  colspan="6"
                >
                  {{ error ? '加载失败，请刷新' : '暂无礼物' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section
        v-else-if="active === 'users'"
        class="page"
      >
        <div class="section-heading">
          <div>
            <h2>账号管理</h2>
            <p>账号状态来自 Billd 用户服务</p>
          </div>
        </div>
        <form class="user-search" @submit.prevent="userPage = 1; refresh()">
          <input v-model.trim="userSearch" placeholder="搜索用户 ID 或用户名" maxlength="40" />
          <button class="primary">搜索</button>
        </form>
        <p v-if="walletInfo" class="wallet-insight">用户 {{ walletInfo.user_id }} 当前余额：<strong>{{ walletInfo.balance_coins }} 金币</strong></p>
        <div v-if="walletInfo" class="balance-tools">
          <button class="outline" :disabled="!!balanceEditor.user_id" @click="startBalanceEdit">设置余额</button>
          <span>余额调整须填写原因；调整会保留操作流水。</span>
        </div>
        <form v-if="balanceEditor.user_id" class="settings balance-editor" @submit.prevent="submitBalanceAdjustment">
          <div class="section-heading"><div><h2>设置用户 {{ balanceEditor.user_id }} 余额</h2><p>操作前余额 {{ balanceEditor.expected_balance }} 金币</p></div><button v-if="!balanceEditor.request_id" type="button" class="outline" @click="clearBalanceEditor">取消</button></div>
          <label>目标余额<input v-model.number="balanceEditor.target_balance" type="number" min="0" max="1000000000" required :disabled="!!balanceEditor.request_id" /></label>
          <label>调整原因<input v-model.trim="balanceEditor.reason" maxlength="200" required :disabled="!!balanceEditor.request_id" /></label>
          <p v-if="balanceEditor.request_id">操作单号：{{ balanceEditor.request_id }}。结果不明时请用原单号核实。</p>
          <button class="primary" :disabled="saving">{{ saving ? '提交中…' : balanceEditor.request_id ? '用原单号核实' : `确认设为 ${balanceEditor.target_balance} 金币` }}</button>
        </form>
        <div v-if="walletInfo && balanceAdjustments.length" class="table-wrap">
          <table><thead><tr><th>调整前</th><th>调整后</th><th>操作人</th><th>原因</th><th>时间</th></tr></thead>
            <tbody><tr v-for="row in balanceAdjustments" :key="row.id"><td>{{ row.old_balance }}</td><td>{{ row.new_balance }}</td><td>{{ row.operator_id }}</td><td>{{ row.reason }}</td><td>{{ formatDate(row.created_at) }}</td></tr></tbody>
          </table>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>状态</th>
                <th>注册时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="user in users"
                :key="user.id"
              >
                <td class="mono">{{ user.id }}</td>
                <td>{{ user.username }}</td>
                <td>
                  <span
                    :class="['state', user.status === 2 ? 'blocked' : 'ok']"
                    >{{ user.status === 2 ? '已禁用' : '正常' }}</span
                  >
                </td>
                <td>{{ formatDate(user.created_at) }}</td>
                <td>
                  <button class="outline" @click="loadWallet(user.id)">查看余额</button>
                  <button
                    class="outline"
                    :disabled="!!pendingCommand"
                    @click="reviewAccount(user)"
                  >
                    {{ user.status === 2 ? '解禁' : '禁用' }}
                  </button>
                </td>
              </tr>
              <tr v-if="!users.length">
                <td
                  colspan="5"
                  class="empty"
                >
                  暂无用户数据或当前账号无权限
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section
        v-else-if="active === 'lives'"
        class="page"
      >
        <form
          v-if="priceEditor"
          class="settings"
          @submit.prevent="saveWatchPrice"
        >
          <h2>直播 {{ priceEditor.live_session_id }} · 收费设置</h2>
          <p>
            版本 {{ priceEditor.version }}。0
            金币表示免费；修改不会撤销已购买的观看权益。
          </p>
          <label
            >解锁价格（金币）<input
              v-model.number="priceEditor.unlock_price_coins"
              type="number"
              min="0"
              max="1000000"
              step="1"
              required
          /></label>
          <label
            >修改原因<input
              v-model="priceEditor.reason"
              required
              maxlength="200"
          /></label>
          <button
            class="primary"
            :disabled="saving || loading || !!pendingCommand"
          >
            保存直播价格
          </button>
          <button
            type="button"
            class="ghost"
            :disabled="saving"
            @click="priceEditor = null"
          >
            取消
          </button>
        </form>
        <div class="section-heading">
          <div>
            <h2>直播管理</h2>
            <p>处理开放中的直播间</p>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>直播 ID</th>
                <th>主播</th>
                <th>开始时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="live in lives"
                :key="live.live_session_id"
              >
                <td class="mono">{{ live.live_session_id }}</td>
                <td>{{ live.creator_id }}</td>
                <td>{{ formatDate(live.started_at) }}</td>
                <td>
                  <button
                    class="outline"
                    :disabled="loading || saving || !!pendingCommand"
                    @click="editWatchPrice(live.live_session_id)"
                  >
                    收费设置
                  </button>
                  <button
                    class="danger-link"
                    @click="closeLive(live.live_session_id)"
                  >
                    强制下播
                  </button>
                </td>
              </tr>
              <tr v-if="!lives.length">
                <td
                  colspan="4"
                  class="empty"
                >
                  暂无开放直播
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section
        v-else-if="active === 'withdrawals' || active === 'refunds'"
        class="page"
      >
        <div class="section-heading">
          <p>申请金额按当时规则保留，审核与实际打款分别登记。</p>
          <button v-if="active === 'withdrawals'" class="outline" :disabled="saving" @click="exportWithdrawals">导出 Excel CSV</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>单号 / 申请时间</th>
                <th>用户 / 收款信息</th>
                <th>金额</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in financeRows"
                :key="row.withdrawal_id || row.refund_id"
              >
                <td>
                  {{ row.withdrawal_id || row.refund_id
                  }}<small>{{ formatDate(row.created_at) }}</small>
                </td>
                <td>
                  {{ row.user_id
                  }}<small v-if="row.payout_method"
                    >{{ row.payout_method }} · {{ row.payout_name }} ·
                    {{ row.payout_account }}</small
                  ><small v-else
                    >{{ row.recharge_request_id }} · {{ row.reason }}</small
                  >
                </td>
                <td>
                  {{
                    money(
                      active === 'withdrawals' ? row.payout_fen : row.amount_fen
                    )
                  }}<small v-if="row.diamonds"
                    >{{ row.diamonds }} 钻石 · 分成
                    {{ money(row.fee_fen) }}</small
                  >
                </td>
                <td>
                  <span class="state">{{ statusLabel(row.status) }}</span
                  ><small>{{ row.payment_ref }}</small>
                </td>
                <td>
                  <button
                    v-if="row.status === 'PENDING'"
                    class="outline"
                    :disabled="!!pendingCommand"
                    @click="startReview(row, 'APPROVE')"
                  >
                    通过</button
                  ><button
                    v-if="row.status === 'PENDING'"
                    class="danger-link"
                    :disabled="!!pendingCommand"
                    @click="startReview(row, 'REJECT')"
                  >
                    驳回</button
                  ><button
                    v-if="row.status === 'APPROVED'"
                    class="outline"
                    :disabled="!!pendingCommand"
                    @click="startReview(row, 'PAID')"
                  >
                    登记打款
                  </button>
                </td>
              </tr>
              <tr v-if="!financeRows.length">
                <td
                  colspan="5"
                  class="empty"
                >
                  {{
                    loading
                      ? '正在加载…'
                      : error
                      ? '加载失败，请重试'
                      : '暂无单据'
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          v-if="nextCursor"
          class="outline"
          @click="loadMore"
        >
          加载更多
        </button>
      </section>
      <section v-else-if="active === 'orders'" class="page">
        <form class="settings" @submit.prevent="submitManualRecharge">
          <h2>人工充值入账</h2>
          <p>确认线下收款后填写账号和金币。提交后余额立即增加，并留下操作单号和原因。</p>
          <label>用户账号 ID<input v-model="manualRecharge.user_id" inputmode="numeric" required :disabled="!!manualRecharge.request_id" /></label>
          <label>入账金币<input v-model.number="manualRecharge.coins" type="number" min="1" max="100000" required :disabled="!!manualRecharge.request_id" /></label>
          <label>操作原因<input v-model="manualRecharge.reason" maxlength="40" required :disabled="!!manualRecharge.request_id" /></label>
          <p v-if="manualRecharge.request_id">操作单号：{{ manualRecharge.request_id }}。若结果未确认，请用原单号重试。</p>
          <button class="primary" :disabled="saving || loading">{{ saving ? '入账中…' : manualRecharge.request_id ? '用原单号核实' : '确认入账' }}</button>
        </form>
        <div class="table-wrap">
          <table>
            <thead><tr><th>单号</th><th>用户 / 金币</th><th>入账后余额</th><th>操作人 / 原因</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.request_id">
                <td>{{ row.request_id }}</td>
                <td>{{ row.user_id }} / {{ row.coins }}</td>
                <td>{{ row.balance_after }}</td>
                <td>{{ row.operator_id }} / {{ row.reason }}</td>
                <td>{{ formatDate(row.created_at) }}</td>
              </tr>
              <tr v-if="!rows.length"><td colspan="5" class="empty">{{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无人工充值记录' }}</td></tr>
            </tbody>
          </table>
        </div>
        <button v-if="nextCursor" class="outline" @click="loadMore">加载更多</button>
      </section>
      <section
        v-else-if="['actions', 'account-actions', 'audit'].includes(active)"
        class="page"
      >
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>单号</th>
                <th>
                  管理员 / 对象
                </th>
                <th>原因</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in rows"
                :key="row.request_id"
              >
                <td>{{ row.request_id }}</td>
                <td>
                  {{ row.user_id || row.operator_id }} /
                  {{
                    row.coins ||
                    row.live_session_id ||
                    row.subject_id ||
                    row.user_id
                  }}
                </td>
                <td>
                  {{ row.reason }}
                </td>
                <td>
                  {{ formatDate(row.created_at) }}
                  <small v-if="active === 'account-actions'"
                    >{{ row.disabled ? '禁用' : '解禁' }} ·
                    {{ row.synced ? '已同步' : '正在同步' }}</small
                  >
                  <small v-if="active === 'audit'"
                    >{{ row.kind }} · {{ row.action }} ·
                    {{ row.payment_ref }}</small
                  >
                </td>
              </tr>
              <tr v-if="!rows.length">
                <td
                  colspan="4"
                  class="empty"
                >
                  {{
                    loading
                      ? '正在加载…'
                      : error
                      ? '加载失败，请重试'
                      : '暂无记录'
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          v-if="nextCursor"
          class="outline"
          @click="loadMore"
        >
          加载更多
        </button>
      </section>
      <section
        v-else-if="active === 'chat'"
        class="page"
      >
        <div class="section-heading">
          <div>
            <h2>公屏管理</h2>
            <p>输入直播间编号查询消息。隐藏后客户端可通过撤回事件更新展示。</p>
          </div>
        </div>
        <form
          class="settings"
          @submit.prevent="loadChat()"
        >
          <label
            >直播间编号<input
              v-model="chatRoomId"
              inputmode="numeric"
              pattern="[1-9][0-9]{0,15}"
              required
          /></label>
          <button
            class="primary"
            :disabled="loading"
          >
            查询消息
          </button>
        </form>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>编号 / 时间</th>
                <th>用户</th>
                <th>消息内容</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="message in chatMessages"
                :key="message.id"
              >
                <td>
                  {{ message.id
                  }}<small>{{ formatDate(message.created_at) }}</small>
                </td>
                <td>
                  {{ message.username }}<small>ID {{ message.user_id }}</small>
                </td>
                <td>{{ message.text }}</td>
                <td>{{ message.hidden ? '已隐藏' : '可见' }}</td>
                <td>
                  <button
                    class="outline"
                    :disabled="!!pendingCommand || message.hidden"
                    @click="reviewChat(message, 'HIDE')"
                  >
                    隐藏
                  </button>
                  <button
                    class="outline"
                    :disabled="!!pendingCommand"
                    @click="reviewChat(message, 'MUTE')"
                  >
                    禁言
                  </button>
                  <button
                    class="outline"
                    :disabled="!!pendingCommand"
                    @click="reviewChat(message, 'UNMUTE')"
                  >
                    解除禁言
                  </button>
                </td>
              </tr>
              <tr v-if="!chatMessages.length">
                <td
                  colspan="5"
                  class="empty"
                >
                  {{ error ? '加载失败，请重试' : '查询后显示真实消息记录' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          v-if="chatBefore"
          class="outline"
          :disabled="loading"
          @click="loadChat(true)"
        >
          更早消息
        </button>
        <h3>最近处置记录</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th>操作人 / 用户</th>
                <th>操作</th>
                <th>原因</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="action in chatActions"
                :key="action.id"
              >
                <td>{{ formatDate(action.created_at) }}</td>
                <td>{{ action.operator_id }} / {{ action.user_id }}</td>
                <td>{{ statusLabel(action.command.action) }}</td>
                <td>{{ action.command.reason }}</td>
              </tr>
              <tr v-if="!chatActions.length">
                <td
                  colspan="4"
                  class="empty"
                >
                  暂无处置记录
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section
        v-else-if="active === 'finance'"
        class="page"
      >
        <div class="section-heading">
          <div>
            <h2>平台资金规则</h2>
            <p>
              未配置前不会开放提现。兑换与分成均向下取整至分，变更只影响新申请。
            </p>
          </div>
        </div>
        <form
          class="settings"
          @submit.prevent="saveSettings"
        >
          <label class="toggle"
            ><input
              v-model="settings.rules.withdrawals_enabled"
              type="checkbox"
            />
            开放提现</label
          >
          <div class="form-grid">
            <label
              >钻石单位<input
                v-model.number="settings.rules.diamond_units"
                type="number"
                min="1" /></label
            ><label
              >兑换金额（分）<input
                v-model.number="settings.rules.fiat_fen"
                type="number"
                min="1" /></label
            ><label
              >平台分成（基点）<input
                v-model.number="settings.rules.platform_fee_bps"
                type="number"
                min="0"
                max="10000" /></label
            ><label
              >最低提现钻石<input
                v-model.number="settings.rules.minimum_withdraw_diamonds"
                type="number"
                min="1"
            /></label>
          </div>
          <label
            >打款方式（英文标识，逗号分隔）<input
              v-model="payoutMethods"
              placeholder="bank,alipay" /></label
          ><label class="toggle"
            ><input
              v-model="settings.rules.refund_consumed_coins_allowed"
              type="checkbox"
            />
            允许已消费金币退款</label
          ><label
            >操作原因<input
              v-model="settings.reason"
              required
              maxlength="200"
              placeholder="填写本次变更原因" /></label
          ><button
            class="primary"
            :disabled="saving || !!pendingCommand || loading"
          >
            {{ saving ? '保存中…' : '保存规则' }}
          </button>
          <p class="form-note">
            当前版本 {{ settings.version }} · 修改后会写入审计记录
          </p>
        </form>
      </section>
      <div
        v-if="active === 'users'"
        class="pagination"
      >
        <button
          class="outline"
          :disabled="loading || userPage <= 1"
          @click="
            userPage--;
            refresh();
          "
        >
          上一页</button
        ><span>第 {{ userPage }} 页 · 共 {{ userTotal }} 个账号</span
        ><button
          class="outline"
          :disabled="loading || userPage * 25 >= userTotal"
          @click="
            userPage++;
            refresh();
          "
        >
          下一页
        </button>
      </div>
      <div
        v-if="['orders', 'account-actions', 'audit'].includes(active)"
        class="pagination"
      >
        <button
          class="outline"
          :disabled="loading || recordPage <= 1"
          @click="
            recordPage--;
            refresh();
          "
        >
          上一页</button
        ><span>第 {{ recordPage }} 页</span
        ><button
          class="outline"
          :disabled="loading || !recordMore"
          @click="
            recordPage++;
            refresh();
          "
        >
          下一页
        </button>
      </div>
    </main>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import axios from 'axios';
import AdminLogin from './AdminLogin.vue';
import ExtendedAdmin from './ExtendedAdmin.vue';
const apiBase = '/backend';
const notice = ref('');
const qrConfigured = ref(false);
const qrVersion = ref(0);
const referralLoaded = ref(false);
const levelsLoaded = ref(false);
const levels = reactive({
  version: 0,
  configured: false,
  viewer_thresholds: [100, 500, 2000, 10000, 50000],
  creator_thresholds: [100, 500, 2000, 10000, 50000],
  reason: '',
});
const referral = reactive({
  version: 0,
  configured: false,
  enabled: false,
  inviter_coins: 0,
  invitee_coins: 0,
  recharge_bps: 500,
  withdrawal_bps: 800,
  reason: '',
});
const rechargePercent = computed({
  get: () => referral.recharge_bps / 100,
  set: (value: number) => { referral.recharge_bps = Math.round(Number(value) * 100); },
});
const withdrawalPercent = computed({
  get: () => referral.withdrawal_bps / 100,
  set: (value: number) => { referral.withdrawal_bps = Math.round(Number(value) * 100); },
});
const priceEditor = ref<any>(null);
const admission = reactive({ version: 0, require_approval: false, reason: '' });
const initialView = window.location.pathname.split('/')[2];
const authorized = ref(false),
  loading = ref(false),
  error = ref(''),
  saving = ref(false);
const credentials = reactive({ username: '', password: '' });
const extendedViews = ['moments', 'creator-list', 'transactions', 'recharges', 'announcements', 'secretary', 'noble'];
const userSearch = ref(''), walletInfo = ref<any>(null), balanceAdjustments = ref<any[]>([]);
const balanceEditor = reactive({ user_id: '', expected_balance: 0, target_balance: 0, reason: '', request_id: '' });
try { Object.assign(balanceEditor, JSON.parse(sessionStorage.getItem('live_ops_balance_adjustment') || '{}')); }
catch { sessionStorage.removeItem('live_ops_balance_adjustment'); }
watch(balanceEditor, () => {
  if (balanceEditor.request_id) sessionStorage.setItem('live_ops_balance_adjustment', JSON.stringify(balanceEditor));
  else sessionStorage.removeItem('live_ops_balance_adjustment');
}, { deep: true });
const manualRecharge = reactive({ user_id: '', coins: 0, reason: '', request_id: '' });
try {
  Object.assign(manualRecharge, JSON.parse(sessionStorage.getItem('live_ops_manual_recharge') || '{}'));
} catch { sessionStorage.removeItem('live_ops_manual_recharge'); }
watch(manualRecharge, () => {
  if (manualRecharge.request_id) sessionStorage.setItem('live_ops_manual_recharge', JSON.stringify(manualRecharge));
  else sessionStorage.removeItem('live_ops_manual_recharge');
}, { deep: true });
const nav = [
  { key: 'overview', label: '运营总览' },
  { key: 'users', label: '用户管理' },
  { key: 'lives', label: '直播管理' },
  { key: 'announcements', label: '系统公告' },
  { key: 'secretary', label: '小秘书内容' },
  { key: 'moments', label: '动态管理' },
  { key: 'noble', label: '贵族条件与权益' },
  { key: 'creator-list', label: '主播列表与收益' },
  { key: 'transactions', label: '余额使用记录' },
  { key: 'recharges', label: '充值记录' },
  { key: 'orders', label: '人工充值' },
  { key: 'gifts', label: '礼物管理' },
  { key: 'referrals', label: '邀请奖励' },
  { key: 'levels', label: '等级条件' },
  { key: 'business-actions', label: '业务设置记录' },
  { key: 'creators', label: '主播审核' },
  { key: 'reports', label: '举报处理' },
  { key: 'chat', label: '公屏管理' },
  { key: 'withdrawals', label: '提现审核' },
  { key: 'refunds', label: '退款审核' },
  { key: 'finance', label: '资金规则' },
  { key: 'support-qr', label: '客服二维码' },
  { key: 'actions', label: '处置记录' },
  { key: 'account-actions', label: '账号处置记录' },
  { key: 'audit', label: '资金审计' },
];
const active = ref(nav.some(item => item.key === initialView) ? initialView : 'overview');
const menuOpen = ref(false);
const current = computed(
  () => nav.find((item) => item.key === active.value) || nav[0]
);
const lives = ref<any[]>([]),
  users = ref<any[]>([]),
  financeRows = ref<any[]>([]),
  rows = ref<any[]>([]),
  nextCursor = ref('');
const userPage = ref(1),
  userTotal = ref(0);
const recordPage = ref(1),
  recordMore = ref(false);
const gifts = ref<any[]>([]),
  giftEditor = ref<any>(null);
const iconUploading = ref(false);
const chatRoomId = ref(''),
  chatMessages = ref<any[]>([]),
  chatActions = ref<any[]>([]),
  chatBefore = ref('');
const settings = reactive<any>({
  version: 0,
  configured: false,
  rules: {
    withdrawals_enabled: false,
    diamond_units: null,
    fiat_fen: null,
    platform_fee_bps: null,
    minimum_withdraw_diamonds: null,
    payout_methods: [],
    refund_consumed_coins_allowed: false,
  },
  reason: '',
});
const payoutMethods = ref(''),
  review = ref<any>(null);
const pendingCommand = ref<any>(null);
try {
  pendingCommand.value = JSON.parse(
    sessionStorage.getItem('live_ops_pending') || 'null'
  );
} catch {
  sessionStorage.removeItem('live_ops_pending');
}
const request = async (url: string, config: any = {}) => {
  const response = await axios({
    url: apiBase + url,
    timeout: 15000,
    ...config,
    headers: {
      Authorization: `Bearer ${sessionStorage.getItem('live_ops_token') || ''}`,
    },
  });
  if (response.data.code !== 200)
    throw new Error(response.data.message || '请求失败');
  return response.data.data;
};
const formatDate = (value: string) =>
  value ? new Date(value).toLocaleString('zh-CN') : '—';
const money = (fen: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(
    fen / 100
  );
const statusLabel = (status: string) =>
  ({
    PENDING: '待审核',
    APPROVE: '通过审核',
    REJECT: '驳回申请',
    MARK_PAID: '登记打款',
    MUTE: '禁言',
    UNMUTE: '解除禁言',
    HIDE: '隐藏消息',
    APPROVED: '审核通过',
    PAID: '已登记打款',
    RESOLVED: '已处理',
    DISMISSED: '不予处理',
    REJECTED: '已驳回',
    CREDITED: '已到账',
  }[status] || status);
function showError(e: any) {
  error.value =
    e?.response?.data?.message || e.message || '服务连接失败，请重试';
  if (e?.response?.status === 401) {
    authorized.value = false;
    sessionStorage.removeItem('live_ops_token');
  }
}
async function login() {
  saving.value = true;
  error.value = '';
  try {
    const token = await request('/user/login', {
      method: 'POST',
      data: { username: credentials.username, password: credentials.password },
    });
    sessionStorage.setItem('live_ops_token', token);
    const access = await request('/operations/access');
    if (!access.can_manage) {
      sessionStorage.removeItem('live_ops_token');
      throw new Error('此账号没有运营权限，请由平台管理员授权');
    }
    credentials.password = '';
    authorized.value = true;
    await refresh();
  } catch (e) {
    showError(e);
  } finally {
    saving.value = false;
  }
}
async function logout() {
  try {
    await request('/user/logout', { method: 'POST' });
  } catch {}
  sessionStorage.removeItem('live_ops_token');
  authorized.value = false;
}
async function refresh() {
  if (!authorized.value) return;
  loading.value = true;
  error.value = '';
  nextCursor.value = '';
  const view = active.value;
  try {
    if (view === 'referrals') {
      referralLoaded.value = false;
      const result = await request('/operations/referrals/settings');
      if (active.value !== view) return;
      Object.assign(referral, result);
      referralLoaded.value = true;
    } else if (view === 'levels') {
      levelsLoaded.value = false;
      const result = await request('/operations/levels/settings');
      if (active.value !== view) return;
      Object.assign(levels, result);
      levelsLoaded.value = true;
    } else if (view === 'business-actions') {
      const result = await request('/operations/business-actions');
      if (active.value !== view) return;
      rows.value = result.actions;
      nextCursor.value = result.next_before_id;
    } else if (view === 'support-qr' || view === 'recharge-qr') {
      const kind = view === 'support-qr' ? 'support' : 'recharge';
      const result = await request(`/operations/qr/${kind}`);
      if (active.value !== view) return;
      qrConfigured.value = result.configured;
      qrVersion.value = Date.now();
    } else if (view === 'overview') {
      const rooms = await request('/operations/lives');
      if (active.value !== view) return;
      lives.value = rooms.lives;
    } else if (extendedViews.includes(view)) {
      return;
    } else if (view === 'chat') {
      if (chatRoomId.value) await loadChat();
    } else if (view === 'gifts') {
      const result = await request('/operations/gifts');
      if (active.value !== view) return;
      gifts.value = result.gifts;
    } else if (view === 'creators' || view === 'reports') {
      if (view === 'creators') {
        const policy = await request('/operations/admission');
        Object.assign(admission, policy);
      }
      const result = await request('/operations/' + view);
      if (active.value !== view) return;
      rows.value = result.reviews;
      nextCursor.value = result.next_before_id;
    } else if (view === 'users') {
      const result = await request('/operations/users', {
        params: { page: userPage.value, search: userSearch.value },
      });
      if (active.value !== view) return;
      users.value = result.users;
      userTotal.value = result.total;
    } else if (view === 'orders') {
      const result = await request('/operations/finance/manual-recharges');
      if (active.value !== view) return;
      rows.value = result.records;
      nextCursor.value = result.next_before_id;
    } else if (view === 'lives') {
      const result = await request('/operations/lives');
      if (active.value !== view) return;
      lives.value = result.lives;
      nextCursor.value = result.next_before_id;
    } else if (view === 'withdrawals' || view === 'refunds') {
      const result = await request('/operations/finance/' + view);
      if (active.value !== view) return;
      financeRows.value = result[view];
      nextCursor.value = result.next_before_id;
    } else if (view === 'finance') {
      const result = await request('/operations/finance/settings');
      if (active.value !== view) return;
      settings.version = result.version;
      settings.configured = result.configured;
      if (result.rules) Object.assign(settings.rules, result.rules);
      payoutMethods.value = settings.rules.payout_methods.join(',');
    } else {
      const result = await request(
        '/operations/' + (view === 'audit' ? 'finance/' : '') + view,
        { params: { page: recordPage.value } }
      );
      if (active.value !== view) return;
      rows.value = result[view.replace('-', '_')] || [];
      recordMore.value =
        result.has_more || recordPage.value * 25 < result.total;
      nextCursor.value = result.next_before_id || '';
    }
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
async function loadWallet(id: number) {
  error.value = '';
  try {
    const [wallet, history] = await Promise.all([
      request(`/operations/users/${id}/wallet`),
      request('/operations/finance/balance-adjustments', { params: { user_id: id } }),
    ]);
    walletInfo.value = wallet; balanceAdjustments.value = history.adjustments || [];
  }
  catch (e) { showError(e); }
}
function clearBalanceEditor() {
  Object.assign(balanceEditor, { user_id: '', expected_balance: 0, target_balance: 0, reason: '', request_id: '' });
}
function startBalanceEdit() {
  if (!walletInfo.value || balanceEditor.request_id) return;
  Object.assign(balanceEditor, { user_id: String(walletInfo.value.user_id), expected_balance: Number(walletInfo.value.balance_coins),
    target_balance: Number(walletInfo.value.balance_coins), reason: '', request_id: '' });
}
async function submitBalanceAdjustment() {
  if (saving.value) return;
  if (!balanceEditor.request_id) {
    const target = Number(balanceEditor.target_balance);
    if (!Number.isSafeInteger(target) || target < 0 || target > 1000000000 || target === balanceEditor.expected_balance ||
      !balanceEditor.reason.trim() || Array.from(balanceEditor.reason.trim()).length > 200) {
      error.value = '请输入不同于当前余额的目标金币数，并填写调整原因'; return;
    }
    balanceEditor.request_id = crypto.randomUUID();
  }
  saving.value = true; error.value = ''; notice.value = '';
  try {
    const result = await request('/operations/finance/balance-adjustments', { method: 'POST', data: {
      user_id: Number(balanceEditor.user_id), expected_balance: balanceEditor.expected_balance,
      target_balance: Number(balanceEditor.target_balance), reason: balanceEditor.reason.trim(), request_id: balanceEditor.request_id,
    } });
    const id = Number(balanceEditor.user_id);
    clearBalanceEditor();
    notice.value = `余额调整已确认：${result.old_balance} → ${result.new_balance} 金币`;
    await loadWallet(id);
  } catch (e: any) {
    showError(e);
    if ([400, 404, 409].includes(e?.response?.status)) balanceEditor.request_id = '';
    if (e?.response?.status === 409) {
      const conflictMessage = error.value;
      const id = Number(balanceEditor.user_id);
      clearBalanceEditor();
      await loadWallet(id);
      if (!error.value) error.value = conflictMessage;
    }
  } finally { saving.value = false; }
}
async function exportWithdrawals() {
  saving.value = true;
  error.value = '';
  try {
    const all: any[] = [];
    let cursor = '';
    do {
      const result = await request('/operations/finance/withdrawals', { params: cursor ? { before_id: cursor } : {} });
      all.push(...result.withdrawals);
      cursor = result.next_before_id || '';
      if (all.length >= 10000) throw new Error('提现记录超过 10000 条，请联系技术人员分批导出');
    } while (cursor);
    const safe = (value: any) => {
      const text = String(value ?? '').replace(/^[=+\-@\t\r\n]/, "'$&");
      return `"${text.replace(/"/g, '""')}"`;
    };
    const columns = ['提现单号', '用户 ID', '钻石', '应付分', '状态', '收款方式', '收款人', '收款账号', '提交时间', '回执号'];
    const lines = all.map(row => [row.withdrawal_id, row.user_id, row.diamonds, row.payout_fen,
      row.status, row.payout_method, row.payout_name, row.payout_account, row.created_at, row.payment_ref].map(safe).join(','));
    const url = URL.createObjectURL(new Blob(['\uFEFF', columns.map(safe).join(','), '\r\n', lines.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `主播提现-${new Date().toISOString().slice(0, 10)}.csv`; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notice.value = `已导出 ${all.length} 条提现记录`;
  } catch (e) { showError(e); } finally { saving.value = false; }
}
async function submitManualRecharge() {
  if (saving.value || loading.value) return;
  if (!manualRecharge.request_id) {
    const userId = Number(manualRecharge.user_id);
    const coins = Number(manualRecharge.coins);
    const reason = manualRecharge.reason.trim();
    if (!Number.isSafeInteger(userId) || userId < 1 || !Number.isInteger(coins) || coins < 1 || coins > 100000 || !reason || Array.from(reason).length > 40) {
      error.value = '请填写有效的账号、1 至 100000 金币和操作原因';
      return;
    }
    if (!window.confirm(`确认给账号 ${userId} 人工入账 ${coins} 金币？`)) return;
    manualRecharge.request_id = crypto.randomUUID();
  }
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    const result = await request('/operations/finance/manual-recharges', {
      method: 'POST', data: {
        request_id: manualRecharge.request_id,
        user_id: Number(manualRecharge.user_id),
        coins: Number(manualRecharge.coins),
        reason: manualRecharge.reason.trim(),
      },
    });
    notice.value = `入账已确认，账号 ${result.viewer_id} 当前余额 ${result.new_balance} 金币`;
    Object.assign(manualRecharge, { user_id: '', coins: 0, reason: '', request_id: '' });
    await refresh();
  } catch (e: any) {
    showError(e);
    if ([400, 404].includes(e?.response?.status)) manualRecharge.request_id = '';
  } finally {
    saving.value = false;
  }
}
async function uploadQr(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  error.value = '';
  notice.value = '';
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 3 * 1024 * 1024) {
    error.value = '请选择不超过 3MB 的 PNG、JPEG 或 WebP 图片';
    input.value = '';
    return;
  }
  saving.value = true;
  try {
    const kind = active.value === 'support-qr' ? 'support' : 'recharge';
    const response = await axios.put(apiBase + `/operations/qr/${kind}`, file, {
      timeout: 20000,
      headers: {
        Authorization: `Bearer ${sessionStorage.getItem('live_ops_token') || ''}`,
        'Content-Type': 'application/octet-stream',
      },
    });
    if (response.data.code !== 200) throw new Error(response.data.message || '上传失败');
    notice.value = '二维码已更新';
    await refresh();
  } catch (e) {
    showError(e);
  } finally {
    saving.value = false;
    input.value = '';
  }
}
function giftIconSrc(value: string): string {
  if (/^\/gift-icons\/[A-Za-z0-9_-]{1,32}(\?v=\d+)?$/.test(value)) return apiBase + value;
  if (/^https?:\/\//.test(value)) return value;
  return '';
}
function giftSampleIcon(giftId: string): string {
  const known = ['ROSE', 'HEART', 'GG', 'DONUT', 'COFFEE', 'CLOUD', 'PERFUME', 'RING', 'FIREWORK', 'DRUM', 'CASTLE', 'LION'];
  return `/gift-samples/${known.includes(giftId) ? giftId : 'GIFT'}.png`;
}
async function uploadGiftIcon(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  const editor = giftEditor.value;
  if (!file || !editor) return;
  error.value = '';
  if (!/^[A-Za-z0-9_-]{1,32}$/.test(editor.gift_id)) {
    error.value = '请先填写有效的礼物标识';
    input.value = '';
    return;
  }
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 3 * 1024 * 1024) {
    error.value = '请选择不超过 3MB 的 PNG、JPEG 或 WebP 图片';
    input.value = '';
    return;
  }
  iconUploading.value = true;
  try {
    const response = await axios.put(apiBase + `/operations/gifts/${editor.gift_id}/icon`, file, {
      timeout: 20000,
      headers: {
        Authorization: `Bearer ${sessionStorage.getItem('live_ops_token') || ''}`,
        'Content-Type': 'application/octet-stream',
      },
    });
    if (response.data.code !== 200) throw new Error(response.data.message || '上传失败');
    editor.icon_url = `${response.data.data.icon_url}?v=${response.data.data.updated_at}`;
    notice.value = '礼物图片已上传，请保存礼物';
  } catch (e) {
    showError(e);
  } finally {
    iconUploading.value = false;
    input.value = '';
  }
}
async function loadMore() {
  if (loading.value) return;
  loading.value = true;
  try {
    const view = active.value;
    const path = view === 'orders' ? '/operations/finance/manual-recharges'
      : '/operations/' + (['withdrawals', 'refunds'].includes(view) ? 'finance/' : '') + view;
    const result = await request(path, { params: { before_id: nextCursor.value } });
    if (view === 'lives') lives.value.push(...result.lives);
    else if (view === 'orders') rows.value.push(...result.records);
    else if (view === 'actions' || view === 'business-actions')
      rows.value.push(...result.actions);
    else if (view === 'creators' || view === 'reports')
      rows.value.push(...result.reviews);
    else financeRows.value.push(...result[view]);
    nextCursor.value = result.next_before_id;
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
function closeLive(id: string) {
  review.value = {
    title: '强制下播 · ' + id,
    path: `/operations/lives/${id}/close`,
    reason: '',
    action: 'CLOSE',
    payment_ref: '',
  };
}
function startReview(row: any, action: string) {
  const id = row.withdrawal_id || row.refund_id;
  review.value = {
    title: statusLabel(action) + ' · ' + id,
    path: `/operations/finance/${active.value}/${id}`,
    reason: '',
    action,
    payment_ref: '',
  };
}
function reviewAccount(user: any) {
  review.value = {
    title: (user.status === 2 ? '解禁账号' : '禁用账号') + ' · ' + user.id,
    path: `/operations/users/${user.id}/status`,
    reason: '',
    action: 'ACCOUNT',
    disabled: user.status !== 2,
  };
}
function reviewRequest(row: any, decision: string) {
  review.value = {
    title:
      (active.value === 'creators' ? '主播审核' : '举报处理') +
      ' · ' +
      row.review_id,
    path: `/operations/${active.value}/${row.review_id}`,
    reason: '',
    action: 'REVIEW',
    decision,
  };
}
async function submitReview() {
  const r = review.value;
  const data: any = { request_id: crypto.randomUUID(), reason: r.reason };
  if (r.action === 'CHAT') {
    Object.assign(data, r.command);
  } else if (r.action === 'REVIEW') {
    data.decision = r.decision;
  } else if (r.action === 'ACCOUNT') {
    data.disabled = r.disabled;
  } else if (r.action !== 'CLOSE') {
    data.action = r.action;
    data.payment_ref = r.payment_ref;
  }
  await commitCommand({ path: r.path, method: 'POST', data });
}
async function loadChat(older = false) {
  if (!/^[1-9][0-9]{0,15}$/.test(chatRoomId.value)) return;
  loading.value = true;
  try {
    const room = chatRoomId.value;
    const [messages, actions] = await Promise.all([
      request(`/operations/rooms/${room}/messages`, {
        params: older ? { before: chatBefore.value } : {},
      }),
      request(`/operations/rooms/${room}/chat-actions`),
    ]);
    if (chatRoomId.value !== room) return;
    chatMessages.value = older
      ? [...chatMessages.value, ...messages.messages]
      : messages.messages;
    chatBefore.value = messages.next_before || '';
    chatActions.value = actions.actions.map((row: any) => ({
      ...row,
      command:
        typeof row.command === 'string' ? JSON.parse(row.command) : row.command,
    }));
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
function reviewChat(message: any, action: string) {
  review.value = {
    title: statusLabel(action) + ' · 用户 ' + message.user_id,
    path: `/operations/rooms/${message.room_id}/chat`,
    reason: '',
    action: 'CHAT',
    command: {
      user_id: Number(message.user_id),
      action,
      ...(action === 'MUTE' ? { minutes: 30 } : {}),
      ...(action === 'HIDE' ? { message_id: String(message.id) } : {}),
    },
  };
}
async function commitCommand(command: any) {
  if (pendingCommand.value) return;
  pendingCommand.value = command;
  sessionStorage.setItem('live_ops_pending', JSON.stringify(command));
  await sendCommand();
}
async function sendCommand() {
  if (!pendingCommand.value || saving.value) return;
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    const command = pendingCommand.value;
    await request(command.path, { method: command.method, data: command.data });
    pendingCommand.value = null;
    sessionStorage.removeItem('live_ops_pending');
    review.value = null;
    settings.reason = '';
    giftEditor.value = null;
    referral.reason = '';
    levels.reason = '';
    priceEditor.value = null;
    notice.value = '操作已保存';
    await refresh();
  } catch (e: any) {
    showError(e);
    if (e.response?.status >= 400 && e.response?.status < 500) {
      pendingCommand.value = null;
      sessionStorage.removeItem('live_ops_pending');
    }
  } finally {
    saving.value = false;
  }
}
async function saveSettings() {
  settings.rules.payout_methods = payoutMethods.value
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  await commitCommand({
    path: '/operations/finance/settings',
    method: 'PUT',
    data: {
      request_id: crypto.randomUUID(),
      expected_version: settings.version,
      reason: settings.reason,
      rules: JSON.parse(JSON.stringify(settings.rules)),
    },
  });
}
async function saveReferrals() {
  await commitCommand({
    path: '/operations/referrals/settings',
    method: 'PUT',
    data: {
      request_id: crypto.randomUUID(),
      expected_version: referral.version,
      enabled: referral.enabled,
      inviter_coins: 0,
      invitee_coins: 0,
      recharge_bps: referral.recharge_bps,
      withdrawal_bps: referral.withdrawal_bps,
      reason: referral.reason,
    },
  });
}
async function saveLevels() {
  await commitCommand({
    path: '/operations/levels/settings',
    method: 'PUT',
    data: {
      request_id: crypto.randomUUID(),
      expected_version: levels.version,
      viewer_thresholds: [...levels.viewer_thresholds],
      creator_thresholds: [...levels.creator_thresholds],
      reason: levels.reason,
    },
  });
}
async function editWatchPrice(id: string) {
  loading.value = true;
  error.value = '';
  try {
    const result = await request(`/operations/lives/${id}/watch-price`);
    if (active.value === 'lives') priceEditor.value = { ...result, reason: '' };
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
async function saveWatchPrice() {
  const p = priceEditor.value;
  await commitCommand({
    path: `/operations/lives/${p.live_session_id}/watch-price`,
    method: 'PUT',
    data: {
      request_id: crypto.randomUUID(),
      expected_version: p.version,
      unlock_price_coins: p.unlock_price_coins,
      reason: p.reason,
    },
  });
}
async function saveAdmission() {
  await commitCommand({
    path: '/operations/admission',
    method: 'PUT',
    data: {
      request_id: crypto.randomUUID(),
      expected_version: admission.version,
      require_approval: admission.require_approval,
      reason: admission.reason,
    },
  });
}
function editGift(gift?: any) {
  giftEditor.value = gift
    ? { ...gift, category: gift.category || 'recommend', reason: '', existing: true }
    : {
        gift_id: '',
        display_name: '',
        coin_price: null,
        diamond_reward: null,
        match_points: null,
        enabled: false,
        effect_type: 'none',
        category: 'recommend',
        icon_url: '',
        reason: '',
        existing: false,
      };
}
function giftCategoryLabel(category: string) {
  return ({ recommend: '推荐', heart: '心意', party: '派对', premium: '典藏' } as Record<string, string>)[category] || '推荐';
}
async function saveGift() {
  if (iconUploading.value) return;
  const { existing, ...data } = giftEditor.value;
  await commitCommand({
    path: '/operations/gifts',
    method: 'PUT',
    data: { ...data, request_id: crypto.randomUUID() },
  });
}
watch(active, () => {
  window.history.replaceState(null, '', `/admin/${active.value}`);
  notice.value = '';
  priceEditor.value = null;
  review.value = null;
  financeRows.value = [];
  rows.value = [];
  userPage.value = 1;
  recordPage.value = 1;
  refresh();
});
onMounted(async () => {
  if (!sessionStorage.getItem('live_ops_token')) return;
  try {
    const result = await request('/operations/access');
    authorized.value = !!result.can_manage;
    if (authorized.value) await refresh();
    else error.value = '当前账号没有运营权限';
  } catch (e) {
    showError(e);
  }
});
</script>
<style lang="scss" scoped>
.save-notice {
  color: #146c43;
  background: #edf8f1;
  padding: 12px 16px;
  border-radius: 6px;
}
.admin-shell {
  min-height: 100vh;
  background: #f6f5f8;
  color: #282234;
  display: flex;
  font-family: 'Segoe UI', 'Microsoft YaHei', -apple-system, sans-serif;
}
.admin-shell * {
  box-sizing: border-box;
}
.settings label,
.settings input,
.command-panel input {
  min-width: 0;
}
.settings input:not([type='checkbox']),
.command-panel input {
  width: 100%;
}
.command-panel {
  padding: 24px;
  background: #fff;
  border: 1px solid #c8d6e8;
  margin-bottom: 24px;
  border-radius: 12px;
  overflow-wrap: anywhere;
}
.command-panel h2 {
  font-size: 18px;
  margin: 0 0 12px;
}
.command-panel label {
  display: grid;
  gap: 8px;
  margin: 12px 0;
}
.command-panel input {
  padding: 10px;
  border: 1px solid #c8d6e8;
  border-radius: 6px;
  font: inherit;
}
td small {
  display: block;
  margin-top: 6px;
  color: #425972;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
button:hover:not(:disabled) {
  filter: brightness(0.94);
}
:focus-visible {
  outline: 3px solid #8054da;
  outline-offset: 3px;
}
::selection {
  background: #e4d9fb;
  color: #282234;
}
input {
  caret-color: #6c45c7;
}
.table-wrap {
  scrollbar-color: #b6aacb #f6f5f8;
}
.sidebar {
  width: 220px;
  height: 100vh;
  position: sticky;
  top: 0;
  overflow-y: auto;
  background: #231d2e;
  color: #ddd6e8;
  padding: 22px 13px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-backdrop, .menu-toggle { display: none; }
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.12em;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #7750cc;
  color: #fff;
  font-size: 18px;
}
.section-label {
  font-size: 11px;
  color: #9a8cad;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  margin: 24px 10px 10px;
}
.nav-item {
  border: 0;
  background: transparent;
  color: #d0c6dd;
  text-align: left;
  padding: 9px 12px;
  border-radius: 8px;
  margin: 2px 0;
  font-size: 14px;
  cursor: pointer;
}
.nav-item span {
  display: inline-block;
  width: 24px;
  color: #7f9bc2;
}
.nav-item.active,
.nav-item:hover {
  background: #6c45c7;
  color: #fff;
}
.sidebar-foot {
  margin-top: auto;
  border-top: 1px solid #493d58;
  padding: 16px 10px;
  font-size: 12px;
  line-height: 1.8;
  color: #c6bbd5;
}
.sidebar-foot small {
  color: #6f849b;
}
.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #37c98a;
  margin-right: 7px;
}
.content {
  flex: 1;
  padding: 28px clamp(20px, 3vw, 48px);
  min-width: 0;
}
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 34px;
}
.eyebrow {
  color: #6a7f98;
  font-size: 12px;
  margin: 0 0 8px;
}
.topbar h1 {
  font-size: 30px;
  letter-spacing: -0.02em;
  margin: 0;
}
.ghost,
.outline,
.primary {
  border: 1px solid #d6e0eb;
  background: #fff;
  color: #24415f;
  padding: 10px 15px;
  border-radius: 7px;
  cursor: pointer;
  font-weight: 600;
}
.primary {
  background: #6c45c7;
  border-color: #6c45c7;
  color: #fff;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 42px;
}
.metric {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 20px;
}
.metric span,
.metric small {
  display: block;
  color: #6a7f98;
  font-size: 12px;
}
.metric strong {
  display: block;
  font-size: 32px;
  letter-spacing: -0.03em;
  margin: 14px 0 5px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  margin: 0 0 16px;
}
.section-heading h2 {
  font-size: 19px;
  margin: 0 0 5px;
}
.section-heading p {
  color: #6a7f98;
  font-size: 13px;
  margin: 0;
}
.table-wrap {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: auto;
  max-width: 100%;
  min-width: 0;
}
table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}
th {
  background: #f8fafc;
  color: #6a7f98;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
th,
td {
  padding: 15px 18px;
  border-bottom: 1px solid #eef2f6;
}
tbody tr:last-child td {
  border-bottom: 0;
}
.mono {
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, SFMono-Regular, monospace;
}
.empty {
  text-align: center;
  color: #7b8da3;
  padding: 48px;
}
.danger-link {
  border: 0;
  background: transparent;
  color: #d14f56;
  font-weight: 600;
  cursor: pointer;
}
.state {
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 5px;
}
.state.ok {
  background: #e5f7ef;
  color: #16734d;
}
.state.blocked {
  background: #fde9e9;
  color: #ad3535;
}
.settings {
  width: 100%;
  max-width: 720px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 28px;
  display: grid;
  gap: 18px;
}
.settings label {
  display: grid;
  gap: 8px;
  font-size: 13px;
  color: #425972;
}
.settings input {
  border: 1px solid #d6e0eb;
  border-radius: 6px;
  padding: 11px 12px;
  font: inherit;
  color: #152238;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.toggle {
  display: flex !important;
  align-items: center;
  gap: 9px;
}
.toggle input {
  width: 16px;
}
.form-note {
  font-size: 12px;
  color: #6a7f98;
  margin: 0;
}
.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  font-size: 13px;
}
.settings + .table-wrap {
  margin-top: 24px;
}
.user-search { display: flex; gap: 8px; margin-bottom: 16px; }
.user-search input { min-width: 220px; border: 1px solid #d6cfdf; border-radius: 5px; padding: 9px 11px; font: inherit; }
.wallet-insight { background: #f0ebf9; color: #46346a; padding: 12px 14px; border-radius: 5px; }
.balance-tools { display: flex; align-items: center; gap: 12px; margin: -6px 0 16px; color: #6d6678; font-size: 13px; }
.balance-editor { margin-bottom: 16px; }
.page > .extended-admin { margin-bottom: 28px; }
.support-qr-preview {
  width: min(260px, 100%);
  height: auto;
  object-fit: contain;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}
.gift-image-preview, .gift-table-name { display: flex; align-items: center; gap: 12px; }
.gift-image-preview { color: #605b6c; font-size: 12px; }
.gift-image-preview img { width: 72px; height: 72px; object-fit: contain; background: #f4f0fa; border: 1px solid #e2d8ef; border-radius: 6px; }
.gift-table-name img { width: 42px; height: 42px; object-fit: contain; flex: 0 0 42px; }
.error-banner {
  background: #fff1f1;
  border: 1px solid #f3c4c4;
  color: #a93d3d;
  padding: 12px 14px;
  border-radius: 7px;
  margin-top: 22px;
}
@media (max-width: 760px) {
  .sidebar {
    position: fixed;
    z-index: 30;
    width: min(280px, 82vw);
    height: 100dvh;
    transform: translateX(-100%);
    transition: transform .2s ease;
    padding: 18px 12px;
  }
  .sidebar.mobile-open { transform: translateX(0); }
  .sidebar-backdrop {
    display: block;
    position: fixed;
    z-index: 29;
    inset: 0;
    width: 100%;
    height: 100%;
    padding: 0;
    border: 0;
    background: rgba(24, 17, 37, .42);
  }
  .menu-toggle {
    display: grid;
    place-items: center;
    flex: 0 0 36px;
    width: 36px;
    height: 36px;
    border: 1px solid #d6cfdf;
    border-radius: 6px;
    background: #fff;
    color: #6c45c7;
    font-size: 19px;
    cursor: pointer;
  }
  .section-label {
    margin: 18px 8px 8px;
  }
  .sidebar-foot {
    padding: 12px 8px 0;
  }
  .brand {
    gap: 6px;
    font-size: 12px;
  }
  .nav-item {
    padding: 9px 8px;
    font-size: 13px;
  }
  .content {
    width: 100%;
    padding: 22px 14px;
  }
  .topbar {
    justify-content: flex-start;
    gap: 12px;
    margin-bottom: 24px;
  }
  .topbar > div { flex: 1; min-width: 0; }
  .user-search { flex-wrap: wrap; }
  .user-search input { min-width: 0; flex: 1 1 180px; }
  .page, .settings, .command-panel { min-width: 0; max-width: 100%; }
  .table-wrap table { width: max-content; min-width: 100%; }
  .table-wrap th, .table-wrap td { white-space: nowrap; }
  .table-wrap td small { max-width: 240px; white-space: normal; }
  .settings { padding: 18px; }
  .section-heading { flex-wrap: wrap; gap: 12px; }
  .balance-tools { flex-wrap: wrap; }
  .metric-grid {
    grid-template-columns: 1fr 1fr;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
  .topbar h1 {
    font-size: 24px;
  }
}
</style>
