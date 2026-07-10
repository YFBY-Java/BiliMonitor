<template>
  <section class="auth-panel">
    <div class="auth-copy">
      <span class="eyebrow">Bilibili 登录态</span>
      <h2>{{ status?.loggedIn ? accountName : '未配置登录态' }}</h2>
      <p>
        {{ status?.loggedIn
          ? `UID ${status.account?.mid || '--'} · 最后校验 ${formatTime(status.lastValidatedAt)}`
          : '扫码登录后，后端会加密保存 Web Cookie，并可用于后续需要登录态的接口。' }}
      </p>
    </div>

    <div v-if="status?.loggedIn" class="auth-account">
      <img v-if="status.account?.face" :src="status.account.face" alt="" referrerpolicy="no-referrer" />
      <div v-else class="avatar-fallback">{{ accountName.slice(0, 1).toUpperCase() }}</div>
      <div>
        <strong>{{ accountName }}</strong>
        <span>{{ status.status }} · 到期 {{ formatTime(status.expiresAt) }}</span>
      </div>
    </div>

    <div class="auth-actions">
      <el-button type="primary" :loading="loading" @click="qrDialogVisible = true">
        {{ status?.loggedIn ? '重新扫码' : '扫码登录' }}
      </el-button>
      <el-button :loading="refreshing" @click="refreshStatus">刷新校验</el-button>
      <el-button v-if="status?.loggedIn" @click="openCredential">查看完整登录态</el-button>
      <el-button v-if="status?.loggedIn" type="danger" plain :loading="revoking" @click="revokeCredential">
        移除登录态
      </el-button>
    </div>
  </section>

  <BilibiliQrLoginDialog v-model="qrDialogVisible" @success="handleLoginSuccess" />

  <el-drawer v-model="credentialDrawerVisible" title="完整 Bilibili 登录态" size="620px">
    <div v-if="credential" class="credential-drawer">
      <div class="credential-toolbar">
        <div>
          <strong>完整字段</strong>
          <span>复制当前抽屉内登录态的全部可用字段</span>
        </div>
        <el-button type="primary" plain @click="copyAllCredentialFields">
          一键复制全部
        </el-button>
      </div>

      <el-alert
        type="warning"
        show-icon
        :closable="false"
        title="这里按项目要求展示完整原文登录态，请只在可信本机环境查看和复制。"
      />

      <div class="credential-field">
        <div class="field-head">
          <span>Cookie Header</span>
          <el-button size="small" @click="copyText(credential.cookieHeader)">复制</el-button>
        </div>
        <pre>{{ credential.cookieHeader }}</pre>
      </div>

      <div class="credential-field">
        <div class="field-head">
          <span>bili_jct / CSRF</span>
          <el-button size="small" @click="copyText(credential.csrf || '')">复制</el-button>
        </div>
        <pre>{{ credential.csrf || '--' }}</pre>
      </div>

      <div class="credential-field">
        <div class="field-head">
          <span>refresh_token</span>
          <el-button size="small" @click="copyText(credential.refreshToken || '')">复制</el-button>
        </div>
        <pre>{{ credential.refreshToken || '--' }}</pre>
      </div>

      <div class="credential-field">
        <div class="field-head">
          <span>Raw Payload</span>
          <el-button size="small" @click="copyText(rawPayloadText)">复制</el-button>
        </div>
        <pre>{{ rawPayloadText }}</pre>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchBilibiliAuthStatus,
  fetchBilibiliCredential,
  refreshBilibiliAuth,
  revokeBilibiliAuth,
  type BilibiliAuthStatus,
  type BilibiliCredentialFull
} from '@/api/bilibiliAuth'
import BilibiliQrLoginDialog from './BilibiliQrLoginDialog.vue'

const loading = ref(false)
const refreshing = ref(false)
const revoking = ref(false)
const status = ref<BilibiliAuthStatus>()
const credential = ref<BilibiliCredentialFull>()
const qrDialogVisible = ref(false)
const credentialDrawerVisible = ref(false)

const accountName = computed(() => status.value?.account?.uname || 'Bilibili 用户')
const rawPayloadText = computed(() => JSON.stringify(credential.value?.rawPayload || {}, null, 2))
const allCredentialText = computed(() => {
  const value = credential.value
  if (!value) return ''

  return [
    '完整 Bilibili 登录态',
    '',
    '[基础信息]',
    `credentialId: ${value.credentialId}`,
    `expiresAt: ${value.expiresAt || ''}`,
    '',
    '[账号]',
    `mid: ${value.account?.mid ?? ''}`,
    `uname: ${value.account?.uname || ''}`,
    `face: ${value.account?.face || ''}`,
    `level: ${value.account?.level ?? ''}`,
    `vipStatus: ${value.account?.vipStatus ?? ''}`,
    '',
    '[Cookie Header]',
    value.cookieHeader || '',
    '',
    '[bili_jct / CSRF]',
    value.csrf || '',
    '',
    '[refresh_token]',
    value.refreshToken || '',
    '',
    '[Cookies]',
    JSON.stringify(value.cookies || [], null, 2),
    '',
    '[Raw Payload]',
    rawPayloadText.value
  ].join('\n')
})

onMounted(loadStatus)

async function loadStatus() {
  loading.value = true
  try {
    status.value = await fetchBilibiliAuthStatus()
  } finally {
    loading.value = false
  }
}

async function refreshStatus() {
  refreshing.value = true
  try {
    if (status.value?.loggedIn) {
      const result = await refreshBilibiliAuth()
      ElMessage.success(result.message || '登录态已校验')
    }
    await loadStatus()
  } finally {
    refreshing.value = false
  }
}

async function handleLoginSuccess() {
  ElMessage.success('Bilibili 登录态已保存')
  await loadStatus()
}

async function openCredential() {
  credential.value = await fetchBilibiliCredential()
  credentialDrawerVisible.value = true
}

async function revokeCredential() {
  await ElMessageBox.confirm('确认移除当前 Bilibili 登录态？已有采集历史不会删除。', '移除登录态', {
    type: 'warning',
    confirmButtonText: '移除',
    cancelButtonText: '取消'
  })
  revoking.value = true
  try {
    await revokeBilibiliAuth()
    credential.value = undefined
    credentialDrawerVisible.value = false
    ElMessage.success('登录态已移除')
    await loadStatus()
  } finally {
    revoking.value = false
  }
}

async function copyText(text: string) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

async function copyAllCredentialFields() {
  await copyText(allCredentialText.value)
}

function formatTime(value?: string) {
  if (!value) return '--'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.auth-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent-soft) 58%, transparent), transparent 48%),
    color-mix(in srgb, var(--surface) 94%, transparent);
  box-shadow: var(--shadow);
}

.auth-copy,
.auth-account {
  min-width: 0;
}

.eyebrow {
  display: inline-flex;
  margin-bottom: 4px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 800;
}

.auth-copy h2 {
  margin: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.auth-copy p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.45;
}

.auth-account {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 260px;
  padding: 8px 10px;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface-strong) 84%, transparent);
}

.auth-account img,
.avatar-fallback {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.auth-account img {
  object-fit: cover;
}

.avatar-fallback {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #2f6df6, #06b6d4);
  color: #fff;
  font-weight: 900;
}

.auth-account div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.auth-account strong,
.auth-account span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.auth-account strong {
  color: var(--text);
  font-size: 14px;
}

.auth-account span {
  color: var(--muted);
  font-size: 12px;
}

.auth-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.credential-drawer {
  display: grid;
  gap: 14px;
}

.credential-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(64, 158, 255, 0.1), rgba(255, 255, 255, 0)),
    #f8fafc;
}

.credential-toolbar div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.credential-toolbar strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 900;
}

.credential-toolbar span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.credential-field {
  display: grid;
  gap: 7px;
}

.field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.field-head span {
  color: #0f172a;
  font-weight: 800;
}

.credential-field pre {
  max-height: 220px;
  margin: 0;
  overflow: auto;
  padding: 10px;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #0f172a;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 1180px) {
  .auth-panel {
    grid-template-columns: 1fr;
  }

  .auth-actions {
    justify-content: flex-start;
  }
}
</style>

