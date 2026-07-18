<template>
  <section class="page douyin-page">
    <header class="douyin-hero">
      <div class="hero-copy">
        <span class="hero-kicker">DOUYIN / AUTH STATE</span>
        <h1>我的抖音登录态</h1>
        <p>用自己的抖音扫码，把完整浏览器会话保存到项目里，后续接口统一从凭据 Provider 读取。</p>
      </div>
      <div class="hero-status">
        <div :class="{ online: status?.workerAvailable }">
          <span>Worker</span>
          <strong>{{ status?.workerStatus || 'DOWN' }}</strong>
        </div>
        <div :class="{ online: !!status?.webCredential }">
          <span>Web 会话</span>
          <strong>{{ status?.webCredential?.status || 'NONE' }}</strong>
        </div>
        <el-button :icon="Refresh" :loading="loading" circle aria-label="刷新状态" @click="loadStatus" />
      </div>
    </header>

    <div class="auth-signal" aria-label="Web 扫码登录链路">
      <div :class="{ done: status?.workerAvailable }">
        <span>01</span><strong>隔离浏览器</strong><small>Worker 创建 Context</small>
      </div>
      <div :class="{ done: !!status?.webCredential }">
        <span>02</span><strong>手机确认</strong><small>自己的抖音扫码</small>
      </div>
      <div :class="{ done: webValidated }">
        <span>03</span><strong>重新加载</strong><small>第二 Context 复验</small>
      </div>
      <div :class="{ done: !!status?.webCredential }">
        <span>04</span><strong>完整保存</strong><small>原始字段入库</small>
      </div>
    </div>

    <el-alert v-if="loadError" type="error" show-icon :closable="false" :title="loadError" />

    <div class="auth-grid" v-loading="loading && !status">
      <DouyinWebAuthPanel
        :credential="status?.webCredential"
        :worker-available="status?.workerAvailable || false"
        :worker-status="status?.workerStatus || 'DOWN'"
        @refresh="loadStatus"
        @open-credential="openCredential"
      />
      <DouyinOAuthPanel
        :credential="status?.oauthCredential"
        :mode="status?.oauthMode || 'disabled'"
        @refresh="loadStatus"
        @open-credential="openCredential"
      />
    </div>

    <DouyinCredentialDrawer
      v-model="drawerVisible"
      :credential="selectedCredential"
      @export="selectedKind && downloadDouyinCredential(selectedKind)"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  downloadDouyinCredential,
  douyinErrorMessage,
  fetchDouyinAuthStatus,
  fetchDouyinCredential,
  type DouyinAuthStatus,
  type DouyinCredentialFull,
  type DouyinCredentialKind
} from '@/api/douyinAuth'
import DouyinCredentialDrawer from './components/DouyinCredentialDrawer.vue'
import DouyinOAuthPanel from './components/DouyinOAuthPanel.vue'
import DouyinWebAuthPanel from './components/DouyinWebAuthPanel.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const status = ref<DouyinAuthStatus>()
const drawerVisible = ref(false)
const selectedKind = ref<DouyinCredentialKind>()
const selectedCredential = ref<DouyinCredentialFull>()

const webValidated = computed(() => {
  const value = status.value?.webCredential?.payload.lastValidatedAt
  return typeof value === 'string' && value.length > 0
})

onMounted(async () => {
  if (route.query.oauth === 'success') {
    ElMessage.success('抖音 OAuth 登录态已保存')
    const query = { ...route.query }
    delete query.oauth
    await router.replace({ query })
  }
  await loadStatus()
})

async function loadStatus() {
  loading.value = true
  loadError.value = ''
  try {
    status.value = await fetchDouyinAuthStatus()
  } catch (error) {
    loadError.value = douyinErrorMessage(error, '无法读取抖音登录态，请确认后端已启用 douyin profile。')
  } finally {
    loading.value = false
  }
}

async function openCredential(kind: DouyinCredentialKind) {
  try {
    selectedKind.value = kind
    selectedCredential.value = await fetchDouyinCredential(kind)
    drawerVisible.value = true
  } catch (error) {
    ElMessage.error(douyinErrorMessage(error, '无法读取完整抖音登录态'))
  }
}
</script>

<style scoped>
.douyin-page {
  --ink: #17181d;
  --muted: #667085;
  --line: #dfe3e8;
  gap: 18px;
  max-width: 1480px;
  margin: 0 auto;
}

.douyin-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  min-height: 150px;
  padding: 24px 26px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background:
    linear-gradient(112deg, rgba(37, 244, 238, 0.09), transparent 38%),
    linear-gradient(292deg, rgba(254, 44, 85, 0.07), transparent 34%),
    #fff;
}

.douyin-hero::after {
  content: '';
  position: absolute;
  right: -44px;
  bottom: -68px;
  width: 210px;
  height: 130px;
  border: 18px solid rgba(23, 24, 29, 0.04);
  border-radius: 50%;
  transform: rotate(-14deg);
  pointer-events: none;
}

.hero-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.hero-kicker {
  color: #475467;
  font-family: Consolas, monospace;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.hero-copy h1 {
  margin: 10px 0 0;
  color: var(--ink);
  font-family: Bahnschrift, "PingFang SC", sans-serif;
  font-size: clamp(29px, 3.2vw, 44px);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1;
}

.hero-copy p {
  max-width: 740px;
  margin: 13px 0 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.6;
}

.hero-status {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.hero-status > div {
  display: grid;
  gap: 3px;
  min-width: 102px;
  padding: 9px 11px;
  border: 1px solid #e4e7ec;
  border-radius: 9px;
  background: rgba(249, 250, 251, 0.9);
}

.hero-status span {
  color: #667085;
  font-size: 10px;
}

.hero-status strong {
  color: #344054;
  font-family: Consolas, monospace;
  font-size: 12px;
}

.hero-status > div.online {
  border-color: #abefc6;
  background: #ecfdf3;
}

.hero-status > div.online strong {
  color: #027a48;
}

.auth-signal {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}

.auth-signal > div {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 13px 15px;
}

.auth-signal > div + div {
  border-left: 1px solid var(--line);
}

.auth-signal span {
  grid-row: 1 / span 2;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: #98a2b3;
  background: #f2f4f7;
  font-family: Consolas, monospace;
  font-size: 11px;
  font-weight: 800;
}

.auth-signal strong {
  overflow: hidden;
  color: #344054;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.auth-signal small {
  overflow: hidden;
  color: #98a2b3;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.auth-signal > div.done span {
  color: var(--ink);
  background: linear-gradient(135deg, rgba(37, 244, 238, 0.5), rgba(254, 44, 85, 0.32));
}

.auth-signal > div.done strong {
  color: var(--ink);
}

.auth-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(310px, 0.8fr);
  gap: 16px;
  min-height: 360px;
}

@media (max-width: 1120px) {
  .douyin-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .auth-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 780px) {
  .auth-signal {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .auth-signal > div:nth-child(3) {
    border-top: 1px solid var(--line);
    border-left: none;
  }

  .auth-signal > div:nth-child(4) {
    border-top: 1px solid var(--line);
  }
}

@media (max-width: 560px) {
  .douyin-hero {
    padding: 20px 17px;
  }

  .hero-status {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .hero-status > div {
    min-width: 0;
    flex: 1 1 110px;
  }

  .auth-signal {
    grid-template-columns: 1fr;
  }

  .auth-signal > div + div,
  .auth-signal > div:nth-child(3),
  .auth-signal > div:nth-child(4) {
    border-top: 1px solid var(--line);
    border-left: none;
  }
}
</style>
