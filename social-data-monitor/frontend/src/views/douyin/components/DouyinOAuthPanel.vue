<template>
  <article class="oauth-card">
    <header>
      <div>
        <span>可选登录方式 · 官方 OAuth</span>
        <h2>{{ credential ? accountName : '开放平台授权' }}</h2>
      </div>
      <el-tag :type="mode === 'live' ? 'success' : mode === 'mock' ? 'warning' : 'info'" effect="plain">
        {{ mode }}
      </el-tag>
    </header>

    <p>
      {{ credential
        ? `Access Token 到期 ${formatTime(credential.expiresAt)}`
        : mode === 'disabled'
          ? '当前未配置开放平台应用；这不影响上方 Web 扫码获取你自己的登录态。'
          : '跳转到抖音开放平台完成官方授权，单独保存 OAuth token。' }}
    </p>

    <dl>
      <div>
        <dt>状态</dt>
        <dd>{{ credential?.status || 'NONE' }}</dd>
      </div>
      <div>
        <dt>Open ID</dt>
        <dd>{{ openId }}</dd>
      </div>
      <div>
        <dt>Scope</dt>
        <dd>{{ scope }}</dd>
      </div>
    </dl>

    <div class="oauth-actions">
      <el-button type="primary" plain :disabled="mode === 'disabled'" :loading="authorizing" @click="authorize">
        {{ credential ? '重新授权' : '开始官方授权' }}
      </el-button>
      <el-button v-if="credential" :disabled="mode === 'disabled'" :loading="refreshing" @click="refreshToken">
        刷新 Token
      </el-button>
      <el-button v-if="credential" @click="emit('openCredential', 'oauth')">查看完整凭据</el-button>
      <el-button v-if="credential" @click="downloadDouyinCredential('oauth')">导出 JSON</el-button>
      <el-button v-if="credential" type="danger" plain :loading="revoking" @click="revokeCredential">撤销</el-button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  downloadDouyinCredential,
  douyinErrorMessage,
  refreshDouyinOAuth,
  revokeDouyinCredential,
  startDouyinOAuth,
  type DouyinCredentialFull,
  type DouyinCredentialKind
} from '@/api/douyinAuth'

const props = defineProps<{
  credential?: DouyinCredentialFull | null
  mode: 'disabled' | 'mock' | 'live'
}>()
const emit = defineEmits<{
  refresh: []
  openCredential: [kind: DouyinCredentialKind]
}>()

const authorizing = ref(false)
const refreshing = ref(false)
const revoking = ref(false)

const payload = computed(() => props.credential?.payload || {})
const account = computed<Record<string, unknown>>(() => {
  const value = payload.value.account
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
})
const accountName = computed(() => String(account.value.nickname || '已授权抖音账号'))
const openId = computed(() => String(payload.value.openId || '--'))
const scope = computed(() => Array.isArray(payload.value.scope) ? payload.value.scope.join(', ') : String(payload.value.scope || '--'))

async function authorize() {
  authorizing.value = true
  try {
    const started = await startDouyinOAuth()
    window.location.assign(started.authorizationUrl)
  } catch (error) {
    ElMessage.error(douyinErrorMessage(error, '无法发起抖音 OAuth 授权'))
    authorizing.value = false
  }
}

async function refreshToken() {
  refreshing.value = true
  try {
    await refreshDouyinOAuth()
    ElMessage.success('OAuth Token 已刷新并保留旧历史行')
    emit('refresh')
  } catch (error) {
    ElMessage.error(douyinErrorMessage(error, 'OAuth Token 刷新失败'))
  } finally {
    refreshing.value = false
  }
}

async function revokeCredential() {
  await ElMessageBox.confirm('撤销当前抖音 OAuth 凭据？历史记录仍会保留。', '撤销凭据', {
    type: 'warning',
    confirmButtonText: '撤销',
    cancelButtonText: '取消'
  })
  revoking.value = true
  try {
    await revokeDouyinCredential('oauth')
    ElMessage.success('OAuth 凭据已撤销')
    emit('refresh')
  } finally {
    revoking.value = false
  }
}

function formatTime(value?: string | null) {
  if (!value) return '--'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.oauth-card {
  display: grid;
  align-content: start;
  gap: 16px;
  min-width: 0;
  padding: 20px;
  border: 1px solid #dfe3e8;
  border-radius: 14px;
  background: #f9fafb;
}

.oauth-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.oauth-card header span {
  color: #667085;
  font-family: Consolas, monospace;
  font-size: 11px;
}

.oauth-card h2 {
  margin: 7px 0 0;
  color: #17181d;
  font-family: Bahnschrift, "PingFang SC", sans-serif;
  font-size: 21px;
}

.oauth-card > p {
  min-height: 42px;
  margin: 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.oauth-card dl {
  display: grid;
  gap: 8px;
  margin: 0;
}

.oauth-card dl > div {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ec;
}

.oauth-card dt {
  color: #667085;
  font-size: 12px;
}

.oauth-card dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #344054;
  font-family: Consolas, monospace;
  font-size: 12px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oauth-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
