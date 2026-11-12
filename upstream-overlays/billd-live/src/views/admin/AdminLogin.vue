<template>
  <main class="ops-login">
    <header class="login-brand">
      <span
        class="login-brand-mark"
        aria-hidden="true"
      >
        <svg
          viewBox="0 0 32 32"
          fill="none"
        >
          <rect
            x="5"
            y="7"
            width="17"
            height="19"
            rx="5"
            fill="currentColor"
          />
          <path
            d="m23 12 5-3v14l-5-3V12Z"
            fill="currentColor"
            opacity=".75"
          />
          <path
            d="m12 12 6 4-6 4v-8Z"
            fill="#8050dc"
          />
        </svg>
      </span>
      <span class="login-brand-name">现场</span>
      <span
        class="login-brand-divider"
        aria-hidden="true"
      ></span>
      <span class="login-brand-label">运营后台</span>
    </header>

    <div class="login-layout">
      <section
        class="login-welcome"
        aria-labelledby="login-welcome-title"
      >
        <p class="welcome-eyebrow"><span></span>连接热爱，遇见精彩</p>
        <h1 id="login-welcome-title">每一场精彩，<br /><em>都在现场。</em></h1>
        <p class="welcome-description">
          从一场直播，到每一次互动。<br />让内容、用户与热爱，在这里相遇。
        </p>

        <div
          class="login-art"
          aria-hidden="true"
        >
          <div class="art-orbit orbit-one"></div>
          <div class="art-orbit orbit-two"></div>
          <div class="art-glow"></div>
          <div class="art-broadcast">
            <svg
              viewBox="0 0 112 112"
              fill="none"
            >
              <rect
                x="21"
                y="27"
                width="57"
                height="58"
                rx="18"
                fill="url(#login-camera)"
              />
              <path
                d="m84 43 17-10v44L84 67V43Z"
                fill="white"
                fill-opacity=".85"
              />
              <path
                d="m43 43 20 13-20 13V43Z"
                fill="#8050dc"
              />
              <defs>
                <linearGradient
                  id="login-camera"
                  x1="21"
                  y1="27"
                  x2="78"
                  y2="85"
                  gradientUnits="userSpaceOnUse"
                >
                  <stop stop-color="white" />
                  <stop
                    offset="1"
                    stop-color="#e4d5ff"
                  />
                </linearGradient>
              </defs>
            </svg>
          </div>
          <div class="art-label art-label-live">
            <span class="art-live-dot"></span>直播运营<span
              class="art-live-bars"
              ><i></i><i></i><i></i><i></i
            ></span>
          </div>
          <div class="art-label art-label-content">
            <span class="art-content-icon">▤</span
            ><span>内容管理<small>记录每一份精彩</small></span>
          </div>
          <div class="art-caption"><span></span>精彩，始终在线</div>
        </div>
      </section>

      <section
        class="login-panel"
        aria-labelledby="login-form-title"
      >
        <div class="form-heading">
          <span class="form-eyebrow">现场 · 管理入口</span>
          <h2 id="login-form-title">欢迎回来</h2>
          <p>登录运营后台，开启今天的工作</p>
        </div>
        <form
          class="login-form"
          :aria-busy="saving"
          @submit.prevent="!saving && emit('submit')"
        >
          <div class="login-field">
            <label for="ops-login-username">管理员账号</label>
            <div class="login-input">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden="true"
              >
                <circle
                  cx="12"
                  cy="8"
                  r="3.5"
                />
                <path d="M5 20v-2a7 7 0 0 1 14 0v2" />
              </svg>
              <input
                id="ops-login-username"
                :value="username"
                name="username"
                autocomplete="username"
                placeholder="请输入管理员账号"
                :disabled="saving"
                required
                @input="updateUsername"
              />
            </div>
          </div>
          <div class="login-field">
            <label for="ops-login-password">登录密码</label>
            <div class="login-input">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden="true"
              >
                <rect
                  x="5"
                  y="10"
                  width="14"
                  height="11"
                  rx="3"
                />
                <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
              </svg>
              <input
                id="ops-login-password"
                :value="password"
                :type="passwordVisible ? 'text' : 'password'"
                name="password"
                autocomplete="current-password"
                placeholder="请输入登录密码"
                :disabled="saving"
                required
                @input="updatePassword"
              />
              <button
                class="password-visibility"
                type="button"
                :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                :aria-pressed="passwordVisible"
                :disabled="saving"
                @click="passwordVisible = !passwordVisible"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"
                  />
                  <circle
                    cx="12"
                    cy="12"
                    r="2.5"
                  />
                  <path
                    v-if="!passwordVisible"
                    d="m4 3 16 18"
                  />
                </svg>
              </button>
            </div>
          </div>
          <p
            v-if="error"
            class="login-error"
            role="alert"
          >
            <span aria-hidden="true">!</span>{{ error }}
          </p>
          <button
            class="login-submit"
            type="submit"
            :disabled="saving"
          >
            <span
              v-if="saving"
              class="login-spinner"
              aria-hidden="true"
            ></span>
            <span>{{ saving ? '正在登录…' : '登录后台' }}</span>
            <svg
              v-if="!saving"
              viewBox="0 0 24 24"
              fill="none"
              aria-hidden="true"
            >
              <path d="M5 12h14m-5-5 5 5-5 5" />
            </svg>
          </button>
          <p class="login-note">
            <svg
              viewBox="0 0 20 20"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="m10 2 6 2.5v5.3c0 3.7-6 7.2-6 7.2S4 13.5 4 9.8V4.5L10 2Z"
              />
              <path d="m7.5 9.5 1.7 1.7 3.3-3.5" /></svg
            >使用具备运营权限的平台账号登录
          </p>
        </form>
      </section>
    </div>
    <footer class="login-footer">现场<span>·</span>让每一份精彩被看见</footer>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';

defineProps<{
  username: string;
  password: string;
  saving: boolean;
  error: string;
}>();
const emit = defineEmits<{
  (event: 'update:username', value: string): void;
  (event: 'update:password', value: string): void;
  (event: 'submit'): void;
}>();
const passwordVisible = ref(false);
function updateUsername(event: Event) {
  emit('update:username', (event.target as HTMLInputElement).value);
}
function updatePassword(event: Event) {
  emit('update:password', (event.target as HTMLInputElement).value);
}
</script>

<style scoped lang="scss">
.ops-login {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100svh;
  padding: 36px clamp(24px, 5vw, 80px) 22px;
  overflow: hidden;
  color: #29213e;
  background: radial-gradient(ellipse at 8% 62%, #e1d4fa 0, transparent 48%),
    radial-gradient(ellipse at 95% 0%, #e6ddfa 0, transparent 45%),
    linear-gradient(125deg, #f7f3fd, #faf9fd 56%, #f0eafa);
  font-family: 'Segoe UI', 'Microsoft YaHei', -apple-system, sans-serif;
}
.ops-login,
.ops-login * {
  box-sizing: border-box;
}
.ops-login::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: linear-gradient(#8258c906 1px, transparent 1px),
    linear-gradient(90deg, #8258c906 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(to bottom, #000, transparent 85%);
  pointer-events: none;
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
}
.login-brand-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  color: #fff;
  background: linear-gradient(145deg, #ae7af4, #7441cf);
  box-shadow: 0 6px 16px #8655d72b, inset 0 1px 0 #ffffff66;
}
.login-brand-mark svg {
  width: 30px;
  height: 30px;
}
.login-brand-name {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 3px;
}
.login-brand-divider {
  width: 1px;
  height: 19px;
  margin: 0 2px;
  background: #d6cce6;
}
.login-brand-label {
  color: #776b8c;
  font-size: 14px;
  letter-spacing: 2px;
}
.login-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.85fr);
  align-items: center;
  gap: clamp(36px, 7vw, 96px);
  width: 100%;
  max-width: 1040px;
  flex: 1;
  margin: 0 auto;
  padding: 54px 0 48px;
}
.login-welcome {
  min-width: 0;
}
.welcome-eyebrow {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 22px;
  color: #80639f;
  font-size: 13px;
  letter-spacing: 2px;
}
.welcome-eyebrow > span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9764df;
  box-shadow: 0 0 0 4px #9c6bdf14;
}
.login-welcome h1 {
  margin: 0;
  font-size: clamp(36px, 4.5vw, 56px);
  font-weight: 800;
  line-height: 1.4;
  letter-spacing: -1px;
}
.login-welcome h1 em {
  color: #8853d7;
  font-style: normal;
}
.welcome-description {
  margin: 21px 0 0;
  color: #897d99;
  font-size: 14px;
  line-height: 1.9;
  letter-spacing: 0.5px;
}
.login-art {
  position: relative;
  width: 100%;
  max-width: 440px;
  height: 240px;
  margin: 26px 0 -30px -20px;
}
.art-orbit {
  position: absolute;
  left: 49%;
  top: 50%;
  width: 350px;
  height: 150px;
  border: 1px solid #b89ad84d;
  border-radius: 50%;
  transform: translate(-50%, -50%) rotate(-18deg);
}
.orbit-two {
  width: 280px;
  height: 200px;
  transform: translate(-50%, -50%) rotate(28deg);
  border-color: #b99fda30;
}
.art-glow {
  position: absolute;
  width: 240px;
  height: 160px;
  top: 40px;
  left: 90px;
  border-radius: 50%;
  background: radial-gradient(ellipse, #b085e340, #cbb3ea05 70%);
}
.art-broadcast {
  position: absolute;
  display: grid;
  place-items: center;
  left: 145px;
  top: 35px;
  width: 138px;
  height: 138px;
  border: 1px solid #dfc8ff;
  border-radius: 36px;
  transform: rotate(-12deg);
  background: linear-gradient(140deg, #c497f6, #9160db 62%, #7640c4);
  box-shadow: 0 22px 35px -12px #7540b54a, inset 0 2px 1px #ffffff77,
    inset 0 -5px 10px #6c2bb640;
}
.art-broadcast svg {
  width: 110px;
  height: 110px;
  transform: rotate(12deg);
}
.art-label {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 16px;
  border: 1px solid #ffffffc9;
  border-radius: 14px;
  color: #605071;
  background: linear-gradient(135deg, #ffffffed, #ffffffa8);
  box-shadow: 0 10px 30px #66428c0c;
  font-size: 12px;
}
.art-label-live {
  top: 28px;
  left: 14px;
  transform: rotate(-4deg);
}
.art-live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9964df;
  box-shadow: 0 0 0 3px #9964df12;
}
.art-live-bars {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-left: 8px;
}
.art-live-bars i {
  display: block;
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: #a073e0;
}
.art-live-bars i:nth-child(2) {
  height: 9px;
}
.art-live-bars i:nth-child(3) {
  height: 18px;
}
.art-live-bars i:nth-child(4) {
  height: 11px;
}
.art-label-content {
  right: 0;
  bottom: 46px;
  transform: rotate(4deg);
}
.art-content-icon {
  display: grid;
  place-items: center;
  width: 33px;
  height: 33px;
  background: #eee5fb;
  color: #9a6ad5;
  border-radius: 10px;
  font-size: 22px;
}
.art-label-content small {
  display: block;
  margin-top: 5px;
  color: #a196ad;
  font-size: 10px;
}
.art-caption {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 7px;
  left: 53px;
  bottom: 28px;
  color: #9a85b3;
  font-size: 10px;
  letter-spacing: 2px;
}
.art-caption > span {
  width: 17px;
  height: 1px;
  background: #b49bce;
}
.login-panel {
  width: 100%;
  max-width: 420px;
  justify-self: end;
  border: 1px solid #ffffffed;
  border-radius: 26px;
  padding: 38px 36px 28px;
  background: linear-gradient(145deg, #ffffffed, #ffffffb8);
  box-shadow: 0 24px 70px -20px #63418e22, 0 1px 4px #73549a05;
}
.form-eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border: 1px solid #e8def6;
  border-radius: 7px;
  color: #9465c8;
  background: #f6f0fd;
  font-size: 11px;
  letter-spacing: 1px;
}
.form-heading h2 {
  margin: 20px 0 8px;
  color: #322641;
  font-size: 28px;
  line-height: 1.4;
  font-weight: 700;
  letter-spacing: 1px;
}
.form-heading p {
  margin: 0;
  color: #84738f;
  font-size: 12px;
  line-height: 1.8;
}
.login-form {
  display: grid;
  gap: 23px;
  margin-top: 32px;
}
.login-field {
  display: grid;
  gap: 9px;
  min-width: 0;
}
.login-field label {
  color: #5c506a;
  font-size: 12px;
  font-weight: 600;
}
.login-input {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  height: 50px;
  padding: 0 14px;
  border: 1px solid #e7e0ef;
  border-radius: 11px;
  background: #faf8fd;
  transition: border-color 0.18s, box-shadow 0.18s, background 0.18s;
}
.login-input:focus-within {
  border-color: #a57bdc;
  background: #fff;
  box-shadow: 0 0 0 3px #a57bdc12;
}
.login-input svg {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  stroke: #b09bc6;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.login-input input {
  width: 0;
  min-width: 0;
  height: 100%;
  flex: 1;
  padding: 0;
  border: 0;
  outline: 0;
  border-radius: 0;
  background: transparent;
  color: #40324f;
  font-family: inherit;
  font-size: 14px;
  caret-color: #8853d7;
  box-shadow: none;
}
.login-input input::placeholder {
  color: #b5aabd;
  font-size: 12px;
}
.login-input input:disabled {
  opacity: 0.6;
  cursor: wait;
}
.password-visibility {
  display: grid;
  place-items: center;
  width: 30px;
  height: 32px;
  margin-right: -5px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}
.password-visibility:hover {
  background: #f0e9f9;
}
.password-visibility svg {
  width: 18px;
  height: 18px;
}
.login-submit {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 49px;
  margin-top: 5px;
  padding: 13px 20px;
  border: 1px solid transparent;
  border-radius: 11px;
  background: linear-gradient(105deg, #a06be5, #7c47ce);
  color: #fff;
  box-shadow: 0 8px 20px #8853d728, inset 0 1px 0 #ffffff20;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  transition: box-shadow 0.18s, transform 0.18s;
}
.login-submit:hover:not(:disabled) {
  box-shadow: 0 10px 24px #8853d740;
  transform: translateY(-1px);
}
.login-submit:active:not(:disabled) {
  transform: translateY(0);
}
.login-submit:disabled {
  opacity: 0.65;
  cursor: wait;
}
.login-submit svg {
  width: 18px;
  height: 18px;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.login-note {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  margin: -5px 0 0;
  color: #8d7e9a;
  font-size: 10px;
  line-height: 1.8;
}
.login-note svg {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  stroke: #b5a4c6;
  stroke-width: 1.3;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.login-error {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  margin: -5px 0 -8px;
  padding: 11px 12px;
  border: 1px solid #e2d4f1;
  border-radius: 9px;
  background: #f4eefb;
  color: #7b4aad;
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.login-error > span {
  display: grid;
  place-items: center;
  flex: 0 0 16px;
  height: 16px;
  margin-top: 2px;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 10px;
  font-weight: 700;
}
.login-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #ffffff55;
  border-top-color: #fff;
  border-radius: 50%;
  animation: login-spin 0.8s linear infinite;
}
.login-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 11px;
  margin-top: auto;
  color: #ae9ebd;
  font-size: 10px;
  letter-spacing: 1.5px;
  line-height: 1.8;
}
.login-footer > span {
  color: #c8bbd5;
}
.ops-login button:focus-visible {
  outline: 3px solid #a57bdc66;
  outline-offset: 3px;
}
@keyframes login-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 900px) {
  .login-layout {
    max-width: 420px;
    grid-template-columns: minmax(0, 1fr);
    flex: 0 0 auto;
    align-content: start;
    gap: 28px;
    padding: 44px 0 36px;
  }
  .login-welcome h1 {
    font-size: 33px;
    line-height: 1.45;
  }
  .login-welcome h1 br {
    display: none;
  }
  .welcome-eyebrow {
    margin-bottom: 11px;
    font-size: 11px;
  }
  .welcome-description,
  .login-art {
    display: none;
  }
  .login-panel {
    max-width: none;
    justify-self: stretch;
  }
}
@media (max-width: 480px) {
  .ops-login {
    padding: 26px 22px 18px;
  }
  .login-brand {
    gap: 10px;
  }
  .login-brand-mark {
    width: 38px;
    height: 38px;
    border-radius: 11px;
  }
  .login-brand-mark svg {
    width: 26px;
    height: 26px;
  }
  .login-brand-name {
    font-size: 23px;
  }
  .login-brand-label {
    font-size: 12px;
    letter-spacing: 1px;
  }
  .login-layout {
    padding-top: 36px;
    gap: 24px;
  }
  .login-welcome h1 {
    font-size: clamp(25px, 6.8vw, 32px);
    letter-spacing: -0.8px;
  }
  .login-panel {
    padding: 27px 24px 23px;
    border-radius: 22px;
  }
  .form-heading h2 {
    margin-top: 16px;
    font-size: 25px;
  }
  .login-form {
    margin-top: 26px;
    gap: 21px;
  }
  .login-input input {
    font-size: 16px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .login-input,
  .login-submit {
    transition: none;
  }
  .login-spinner {
    animation-duration: 2s;
  }
}
</style>
