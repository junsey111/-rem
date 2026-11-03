<template>
  <div
    v-if="!authorized"
    class="login-page"
  >
    <form
      class="settings login-form"
      @submit.prevent="login"
    >
      <h1>直播运营后台</h1>
      <p>使用具备运营权限的平台账号登录</p>
      <label
        >账号 ID<input
          v-model="credentials.id"
          inputmode="numeric"
          autocomplete="username"
          required
      /></label>
      <label
        >密码<input
          v-model="credentials.password"
          type="password"
          autocomplete="current-password"
          required
      /></label>
      <button
        class="primary"
        :disabled="saving"
      >
        {{ saving ? '登录中…' : '登录后台' }}
      </button>
      <p
        v-if="error"
        role="alert"
        class="error-banner"
      >
        {{ error }}
      </p>
    </form>
  </div>
  <div
    v-else
    class="admin-shell"
  >
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">L</span><span>LIVE OPS</span>
      </div>
      <p class="section-label">运营中心</p>
      <button
        v-for="item in nav"
        :key="item.key"
        :class="['nav-item', { active: active === item.key }]"
        @click="active = item.key"
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
      <section
        v-if="active === 'integrations'"
        class="settings"
      >
        <h2>服务接入状态</h2>
        <p>
          支付宝退款 / 提现：{{
            integrations.payments.enabled ? '已启用' : '未启用'
          }}
        </p>
        <p>
          实名认证：{{ integrations.identity.enabled ? '已启用' : '未启用' }}
        </p>
        <p>
          当前按接口联调交付。真实调用由服务端配置控制；未启用时不会付款或上传身份资料。
        </p>
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
        <div class="metric-grid">
          <article
            v-for="metric in metrics"
            :key="metric.label"
            class="metric"
          >
            <span>{{ metric.label }}</span
            ><strong>{{ metric.value }}</strong
            ><small>{{ metric.note }}</small>
          </article>
        </div>
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
              >邀请人奖励（金币）<input
                v-model.number="referral.inviter_coins"
                type="number"
                min="0"
                max="100000"
                step="1"
                required
            /></label>
            <label
              >受邀人奖励（金币）<input
                v-model.number="referral.invitee_coins"
                type="number"
                min="0"
                max="100000"
                step="1"
                required
            /></label>
          </div>
          <p>
            保存后立即影响新的邀请兑换。已到账奖励保留原值；保存规则不会发放金币。
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
        v-else-if="active === 'business-actions'"
        class="page"
      >
        <p>
          记录邀请奖励与直播收费的设置变更，保留操作人、原因和保存时的规则。
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
                      : '直播收费 · ' + row.target_id
                  }}
                </td>
                <td>
                  <template v-if="row.kind === 'REFERRALS'"
                    >{{ row.settings.enabled ? '已启用' : '已关闭'
                    }}<small
                      >邀请人 {{ row.settings.inviter_coins }} / 受邀人
                      {{ row.settings.invitee_coins }} 金币</small
                    ></template
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
                <th>对象 / 内容</th>
                <th>材料引用</th>
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
                <td>
                  {{ row.target_type }} · {{ row.target_id
                  }}<small>{{ row.content }}</small>
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
              :disabled="giftEditor.existing"
              pattern="[a-zA-Z0-9_-]{1,32}"
              required
              maxlength="32"
          /></label>
          <label
            >礼物名称<input
              v-model="giftEditor.display_name"
              required
              maxlength="64"
          /></label>
          <div class="form-grid">
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
            :disabled="saving || !!pendingCommand"
          >
            保存礼物</button
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
                  {{ gift.gift_id }}<small>{{ gift.display_name }}</small>
                </td>
                <td>{{ gift.coin_price }}</td>
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
                  colspan="5"
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
      <section
        v-else-if="
          ['orders', 'actions', 'account-actions', 'audit'].includes(active)
        "
        class="page"
      >
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>单号</th>
                <th>
                  {{ active === 'orders' ? '用户 / 金币' : '管理员 / 对象' }}
                </th>
                <th>{{ active === 'orders' ? '金额' : '原因' }}</th>
                <th>{{ active === 'orders' ? '状态' : '时间' }}</th>
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
                  {{ active === 'orders' ? money(row.amount_fen) : row.reason }}
                </td>
                <td>
                  {{
                    active === 'orders'
                      ? statusLabel(row.status)
                      : formatDate(row.created_at)
                  }}
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
const apiBase = '/backend';
const notice = ref('');
const referralLoaded = ref(false);
const referral = reactive({
  version: 0,
  configured: false,
  enabled: false,
  inviter_coins: 0,
  invitee_coins: 0,
  reason: '',
});
const priceEditor = ref<any>(null);
const admission = reactive({ version: 0, require_approval: false, reason: '' });
const integrations = reactive({
  payments: { enabled: false },
  identity: { enabled: false },
});
const initialView = window.location.pathname.split('/')[2];
const active = ref(
  ['users', 'finance', 'creators', 'reports', 'chat'].includes(initialView)
    ? initialView
    : 'overview'
);
const authorized = ref(false),
  loading = ref(false),
  error = ref(''),
  saving = ref(false);
const credentials = reactive({ id: '', password: '' });
const nav = [
  { key: 'overview', label: '运营总览' },
  { key: 'users', label: '账号管理' },
  { key: 'lives', label: '直播管理' },
  { key: 'orders', label: '充值订单' },
  { key: 'gifts', label: '礼物目录' },
  { key: 'referrals', label: '邀请奖励' },
  { key: 'business-actions', label: '业务设置记录' },
  { key: 'creators', label: '主播审核' },
  { key: 'reports', label: '举报处理' },
  { key: 'chat', label: '公屏管理' },
  { key: 'withdrawals', label: '提现审核' },
  { key: 'refunds', label: '退款审核' },
  { key: 'finance', label: '资金规则' },
  { key: 'integrations', label: '服务接入' },
  { key: 'actions', label: '处置记录' },
  { key: 'account-actions', label: '账号处置记录' },
  { key: 'audit', label: '资金审计' },
];
const current = computed(
  () => nav.find((item) => item.key === active.value) || nav[0]
);
const metrics = reactive([
  { label: '注册用户', value: '—', note: '平台账号' },
  { label: '直播目录', value: '—', note: '目录登记的房间数' },
  { label: '待确认充值', value: '—', note: '等待支付核实' },
  { label: '已到账充值', value: '—', note: '账本已入账的订单' },
]);
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
      data: { id: credentials.id, password: credentials.password },
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
    } else if (view === 'business-actions') {
      const result = await request('/operations/business-actions');
      if (active.value !== view) return;
      rows.value = result.actions;
      nextCursor.value = result.next_before_id;
    } else if (view === 'integrations') {
      Object.assign(integrations, await request('/operations/integrations'));
    } else if (view === 'overview') {
      const [summary, rooms] = await Promise.all([
        request('/operations/summary'),
        request('/operations/lives'),
      ]);
      if (active.value !== view) return;
      [
        summary.users,
        summary.listed_rooms,
        summary.pending_orders,
        summary.credited_orders,
      ].forEach((value, index) => (metrics[index].value = String(value)));
      lives.value = rooms.lives;
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
        params: { page: userPage.value },
      });
      if (active.value !== view) return;
      users.value = result.users;
      userTotal.value = result.total;
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
async function loadMore() {
  if (loading.value) return;
  loading.value = true;
  try {
    const view = active.value;
    const result = await request(
      '/operations/' +
        (['withdrawals', 'refunds'].includes(view) ? 'finance/' : '') +
        view,
      { params: { before_id: nextCursor.value } }
    );
    if (view === 'lives') lives.value.push(...result.lives);
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
      inviter_coins: referral.inviter_coins,
      invitee_coins: referral.invitee_coins,
      reason: referral.reason,
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
    ? { ...gift, reason: '', existing: true }
    : {
        gift_id: '',
        display_name: '',
        coin_price: null,
        diamond_reward: null,
        match_points: null,
        enabled: false,
        reason: '',
        existing: false,
      };
}
async function saveGift() {
  const { existing, ...data } = giftEditor.value;
  await commitCommand({
    path: '/operations/gifts',
    method: 'PUT',
    data: { ...data, request_id: crypto.randomUUID() },
  });
}
watch(active, () => {
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
  background: #f6f8fb;
  color: #152238;
  display: flex;
  font-family: 'Segoe UI', 'Microsoft YaHei', -apple-system, sans-serif;
}
.admin-shell *,
.login-page * {
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
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f6f8fb;
  padding: 24px;
  box-sizing: border-box;
  color: #152238;
  font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
}
.login-form {
  width: min(100%, 420px);
  box-sizing: border-box;
}
.login-form h1 {
  margin: 0;
  font-size: 24px;
}
.login-form p {
  color: #425972;
  line-height: 1.6;
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
  outline: 3px solid #2563eb;
  outline-offset: 3px;
}
::selection {
  background: #c9ddff;
  color: #152238;
}
input {
  caret-color: #2563eb;
}
.table-wrap {
  scrollbar-color: #9cb1ca #f6f8fb;
}
.sidebar {
  width: 240px;
  background: #0d1b2a;
  color: #c9d4e2;
  padding: 28px 18px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
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
  background: #3b82f6;
  color: #fff;
  font-size: 18px;
}
.section-label {
  font-size: 11px;
  color: #71839a;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  margin: 46px 10px 10px;
}
.nav-item {
  border: 0;
  background: transparent;
  color: #aab8c9;
  text-align: left;
  padding: 12px 14px;
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
  background: #1b314c;
  color: #fff;
}
.sidebar-foot {
  margin-top: auto;
  border-top: 1px solid #20354c;
  padding: 16px 10px;
  font-size: 12px;
  line-height: 1.8;
  color: #aab8c9;
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
  padding: 40px clamp(24px, 4vw, 64px);
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
  background: #2563eb;
  border-color: #2563eb;
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
.error-banner {
  background: #fff1f1;
  border: 1px solid #f3c4c4;
  color: #a93d3d;
  padding: 12px 14px;
  border-radius: 7px;
  margin-top: 22px;
}
@media (max-width: 760px) {
  .admin-shell {
    flex-direction: column;
  }
  .sidebar {
    width: auto;
    padding: 18px;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .brand,
  .sidebar-foot {
    grid-column: 1 / -1;
  }
  .section-label {
    display: none;
  }
  .sidebar-foot {
    padding: 8px 0 0;
    margin: 12px 0 0;
  }
  .content {
    padding: 28px 18px;
  }
  .topbar {
    flex-wrap: wrap;
    gap: 12px;
  }
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
