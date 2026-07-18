<template>
  <article class="auth-card web-card">
    <div class="card-rail" aria-hidden="true"><span /><span /></div>
    <header class="card-header">
      <div>
        <span class="card-kicker">主要登录方式 · Web QR</span>
        <h2>{{ credential ? accountName : '扫码获取你自己的抖音登录态' }}</h2>
        <p>
          {{ credential
            ? `已保存 ${cookieCount} 个 Cookie；最后复验 ${formatTime(lastValidatedAt)}`
            : '由独立浏览器打开抖音登录页，你在手机确认后完整保存浏览器会话。' }}
        </p>
      </div>
      <div class="worker-state" :class="{ online: workerAvailable }">
        <span />
        Worker {{ workerAvailable ? '在线' : '不可用' }}
      </div>
    </header>

    <div class="session-facts">
      <div>
        <span>登录态</span>
        <strong>{{ credential?.status || '尚未保存' }}</strong>
      </div>
      <div>
        <span>Cookie</span>
        <strong>{{ credential ? cookieCount : '--' }}</strong>
      </div>
      <div>
        <span>新 Context 复验</span>
        <strong>{{ lastValidatedAt ? '通过' : '未执行' }}</strong>
      </div>
      <div>
        <span>Worker 状态</span>
        <strong>{{ workerStatus || 'DOWN' }}</strong>
      </div>
    </div>

    <div class="card-actions">
      <el-button type="primary" :disabled="!workerAvailable" @click="qrVisible = true">
        {{ credential ? '重新扫码' : '开始扫码' }}
      </el-button>
      <el-button v-if="credential" :loading="validating" :disabled="!workerAvailable" @click="validateCredential">
        重新加载校验
      </el-button>
      <el-button v-if="credential" @click="emit('openCredential', 'web')">查看完整登录态</el-button>
      <el-button v-if="credential" @click="downloadDouyinCredential('web')">导出 JSON</el-button>
      <el-button v-if="credential" type="danger" plain :loading="revoking" @click="revokeCredential">
        撤销
      </el-button>
    </div>

    <p v-if="!workerAvailable" class="worker-hint">
      先启动项目内的 douyin-worker；原项目和 B 站功能不受影响。
    </p>

    <DouyinQrLoginDialog v-model="qrVisible" @success="handleSuccess" />
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  downloadDouyinCredential,
  douyinErrorMessage,
  revokeDouyinCredential,
  validateDouyinWebCredential,
  type DouyinCredentialFull,
  type DouyinCredentialKind
} from '@/api/douyinAuth'
import DouyinQrLoginDialog from './DouyinQrLoginDialog.vue'

const props = defineProps<{
  credential?: DouyinCredentialFull | null
  workerAvailable: boolean
  workerStatus: string
}>()
const emit = defineEmits<{
  refresh: []
  openCredential: [kind: DouyinCredentialKind]
}>()

const qrVisible = ref(false)
const validating = ref(false)
const revoking = ref(false)

const account = computed<Record<string, unknown>>(() => {
  const value = props.credential?.payload.account
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
})
const accountName = computed(() => String(account.value.nickname || account.value.name || '抖音账号'))
const cookies = computed<unknown[]>(() =>
  Array.isArray(props.credential?.payload.cookies) ? props.credential?.payload.cookies as unknown[] : []
)
const cookieCount = computed(() => cookies.value.length)
const lastValidatedAt = computed(() => {
  const value = props.credential?.payload.lastValidatedAt
  return typeof value === 'string' ? value : undefined
})

async function handleSuccess() {
  ElMessage.success('你的抖音 Web 登录态已完整保存')
  emit('refresh')
}

async function validateCredential() {
  validating.value = true
  try {
    const result = await validateDouyinWebCredential()
    if (result.valid) {
      ElMessage.success(result.message || '登录态可重新加载')
    } else {
      ElMessage.error(result.message || '当前登录态已不可用')
    }
    emit('refresh')
  } catch (error) {
    ElMessage.error(douyinErrorMessage(error, '登录态校验失败'))
  } finally {
    validating.value = false
  }
}

async function revokeCredential() {
  await ElMessageBox.confirm('撤销当前抖音 Web 登录态？历史记录仍会保留。', '撤销登录态', {
    type: 'warning',
    confirmButtonText: '撤销',
    cancelButtonText: '取消'
  })
  revoking.value = true
  try {
    await revokeDouyinCredential('web')
    ElMessage.success('Web 登录态已撤销')
    emit('refresh')
  } finally {
    revoking.value = false
  }
}

function formatTime(value?: string) {
  if (!value) return '--'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.auth-card {
  position: relative;
  display: grid;
  gap: 18px;
  min-width: 0;
  padding: 22px;
  overflow: hidden;
  border: 1px solid #dfe3e8;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(16, 24, 40, 0.06);
}

.card-rail {
  position: absolute;
  inset: 0 auto 0 0;
  display: grid;
  grid-template-columns: repeat(2, 3px);
}

.card-rail span:first-child {
  background: #25f4ee;
}

.card-rail span:last-child {
  background: #fe2c55;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.card-header > div:first-child {
  min-width: 0;
}

.card-kicker {
  color: #667085;
  font-family: Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.card-header h2 {
  margin: 7px 0 0;
  color: #17181d;
  font-family: Bahnschrift, "PingFang SC", sans-serif;
  font-size: clamp(20px, 2vw, 27px);
  line-height: 1.15;
}

.card-header p {
  max-width: 680px;
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.worker-state {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex: 0 0 auto;
  padding: 6px 9px;
  border: 1px solid #e4e7ec;
  border-radius: 999px;
  color: #667085;
  background: #f9fafb;
  font-family: Consolas, monospace;
  font-size: 11px;
}

.worker-state > span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #98a2b3;
}

.worker-state.online {
  color: #027a48;
  border-color: #abefc6;
  background: #ecfdf3;
}

.worker-state.online > span {
  background: #12b76a;
  box-shadow: 0 0 0 3px rgba(18, 183, 106, 0.12);
}

.session-facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid #e4e7ec;
  border-radius: 10px;
  overflow: hidden;
}

.session-facts > div {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px 13px;
  background: #f9fafb;
}

.session-facts > div + div {
  border-left: 1px solid #e4e7ec;
}

.session-facts span {
  color: #667085;
  font-size: 11px;
}

.session-facts strong {
  overflow: hidden;
  color: #17181d;
  font-family: Consolas, monospace;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.worker-hint {
  margin: -5px 0 0;
  color: #b54708;
  font-size: 12px;
}

@media (max-width: 860px) {
  .card-header {
    flex-direction: column;
  }

  .session-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .session-facts > div:nth-child(3) {
    border-left: none;
    border-top: 1px solid #e4e7ec;
  }

  .session-facts > div:nth-child(4) {
    border-top: 1px solid #e4e7ec;
  }
}

@media (max-width: 520px) {
  .auth-card {
    padding: 18px 16px;
  }

  .session-facts {
    grid-template-columns: 1fr;
  }

  .session-facts > div + div {
    border-top: 1px solid #e4e7ec;
    border-left: none;
  }
}
</style>
