<template>
  <el-dialog
    :model-value="modelValue"
    title="用自己的抖音扫码登录"
    width="500px"
    class="douyin-qr-dialog"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <div class="qr-flow">
      <ol class="signal-track" aria-label="扫码登录进度">
        <li v-for="(step, index) in steps" :key="step" :class="stepClass(index)">
          <span>{{ index + 1 }}</span>
          <small>{{ step }}</small>
        </li>
      </ol>

      <div class="qr-stage" :class="statusClass">
        <div class="qr-frame">
          <img v-if="qrObjectUrl" :src="qrObjectUrl" alt="抖音 Web 登录二维码" />
          <div v-else-if="status?.status === 'USER_ACTION_REQUIRED'" class="manual-state">
            <el-icon><WarningFilled /></el-icon>
            <strong>先完成浏览器验证</strong>
            <span>在 Worker 打开的浏览器窗口中手动完成滑块</span>
          </div>
          <el-skeleton v-else :rows="5" animated />

          <div v-if="overlayText" class="qr-overlay">
            <strong>{{ overlayText }}</strong>
          </div>
        </div>
      </div>

      <div class="status-copy" aria-live="polite">
        <el-tag :type="statusTagType" effect="light" round>{{ statusLabel }}</el-tag>
        <h3>{{ statusHeadline }}</h3>
        <p>{{ status?.message || '正在创建仅用于本次扫码的浏览器会话。' }}</p>
        <span v-if="showCountdown">二维码剩余 {{ status?.expiresInSeconds }} 秒</span>
      </div>

      <el-alert
        v-if="status?.status === 'USER_ACTION_REQUIRED'"
        type="warning"
        show-icon
        :closable="false"
        title="完成可见浏览器中的验证后不要关闭窗口；系统会继续检测并自动显示二维码。"
      />
      <el-alert v-if="pollError" type="error" show-icon :closable="false" :title="pollError" />

      <div class="login-note">
        <strong>这次保存什么</strong>
        <span>你扫码确认后的全部 Cookie、localStorage、sessionStorage、IndexedDB 和 storageState。</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="canRestart"
        type="primary"
        :loading="starting"
        @click="startLogin"
      >
        重新生成二维码
      </el-button>
      <el-button
        v-else-if="canRequestDouyinQrImage(status?.status)"
        :loading="starting || imageLoading"
        @click="loadQrImage(true)"
      >
        刷新二维码图片
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import {
  douyinErrorMessage,
  fetchDouyinWebQrImage,
  fetchDouyinWebQrStatus,
  startDouyinWebQr,
  type DouyinQrStatusView
} from '@/api/douyinAuth'
import { useQrPolling } from '../useQrPolling'
import { createRequestGeneration } from '../requestGeneration'
import { canRequestDouyinQrImage } from '../qrImagePolicy'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const steps = ['浏览器就绪', '手机扫码', '新上下文复验', '完整保存']
const starting = ref(false)
const imageLoading = ref(false)
const loginId = ref('')
const qrObjectUrl = ref('')
const pollError = ref('')
const status = ref<DouyinQrStatusView>()
const sessionRequests = createRequestGeneration()
const imageRequests = createRequestGeneration()

const polling = useQrPolling({
  poll: () => fetchDouyinWebQrStatus(loginId.value),
  intervalMs: 1500,
  onResult: async next => {
    status.value = next
    pollError.value = ''
    if (canRequestDouyinQrImage(next.status) && !qrObjectUrl.value) {
      await loadQrImage()
    }
    if (next.status === 'SUCCESS') {
      emit('success')
    }
  },
  onError: error => {
    pollError.value = douyinErrorMessage(error, '暂时无法读取扫码状态，系统会继续重试。')
  }
})

const statusLabel = computed(() => {
  if (starting.value) return '创建中'
  return {
    STARTING: '浏览器启动中',
    WAITING: '等待扫码',
    SCANNED: '已扫码',
    VALIDATING: '复验中',
    SUCCESS: '已保存',
    EXPIRED: '已过期',
    USER_ACTION_REQUIRED: '需要手动验证',
    FAILED: '登录失败'
  }[status.value?.status || 'STARTING']
})

const statusHeadline = computed(() => {
  if (status.value?.status === 'USER_ACTION_REQUIRED') return '浏览器正在等你完成验证'
  if (status.value?.status === 'SCANNED') return '已识别扫码，请在手机确认'
  if (status.value?.status === 'VALIDATING') return '正在确认登录态可以重新加载'
  if (status.value?.status === 'SUCCESS') return '你的抖音登录态已经保存'
  if (status.value?.status === 'EXPIRED') return '二维码已过期'
  if (status.value?.status === 'FAILED') return '这次扫码没有完成'
  return '打开抖音 App 扫描二维码'
})

const statusTagType = computed<'primary' | 'success' | 'warning' | 'danger' | 'info'>(() => {
  if (status.value?.status === 'SUCCESS') return 'success'
  if (status.value?.status === 'SCANNED' || status.value?.status === 'VALIDATING') return 'warning'
  if (status.value?.status === 'EXPIRED' || status.value?.status === 'FAILED') return 'danger'
  if (status.value?.status === 'USER_ACTION_REQUIRED') return 'warning'
  return 'primary'
})

const statusClass = computed(() => `is-${(status.value?.status || 'STARTING').toLowerCase()}`)
const showCountdown = computed(() =>
  status.value?.expiresInSeconds != null && !['SUCCESS', 'FAILED'].includes(status.value.status)
)
const canRestart = computed(() => ['EXPIRED', 'FAILED', 'SUCCESS'].includes(status.value?.status || ''))
const overlayText = computed(() => {
  if (status.value?.status === 'SCANNED') return '已扫码'
  if (status.value?.status === 'VALIDATING') return '正在复验'
  if (status.value?.status === 'SUCCESS') return '保存成功'
  if (status.value?.status === 'EXPIRED') return '已过期'
  return ''
})

watch(
  () => props.modelValue,
  visible => {
    if (visible && !loginId.value) void startLogin()
    if (!visible) resetSession()
  }
)

onBeforeUnmount(resetSession)

async function startLogin() {
  const requestGeneration = sessionRequests.next()
  imageRequests.invalidate()
  polling.stop()
  releaseQrObjectUrl()
  loginId.value = ''
  imageLoading.value = false
  starting.value = true
  pollError.value = ''
  status.value = undefined
  try {
    const started = await startDouyinWebQr()
    if (!props.modelValue || !sessionRequests.isCurrent(requestGeneration)) return
    loginId.value = started.loginId
    polling.setIntervalMs(Math.max(750, started.pollIntervalMs || 1500))
    status.value = {
      loginId: started.loginId,
      status: started.status,
      message: started.status === 'WAITING'
        ? '抖音登录二维码已准备好，请使用抖音 App 扫码。'
        : '浏览器会话已创建，正在定位抖音登录二维码。',
      expiresInSeconds: started.expiresInSeconds,
      rawResult: started.rawResult
    }
    polling.start(250)
    if (canRequestDouyinQrImage(started.status)) {
      void loadQrImage(false, requestGeneration)
    }
  } catch (error) {
    if (!props.modelValue || !sessionRequests.isCurrent(requestGeneration)) return
    pollError.value = douyinErrorMessage(error, '无法创建抖音扫码会话。')
    status.value = {
      loginId: '',
      status: 'FAILED',
      message: pollError.value,
      expiresInSeconds: 0,
      rawResult: {}
    }
  } finally {
    if (sessionRequests.isCurrent(requestGeneration)) starting.value = false
  }
}

async function loadQrImage(force = false, expectedSession = sessionRequests.current()) {
  if (!sessionRequests.isCurrent(expectedSession)) return
  if (!canRequestDouyinQrImage(status.value?.status)) return
  if (!loginId.value || imageLoading.value || (!force && qrObjectUrl.value)) return
  const requestedLoginId = loginId.value
  const imageGeneration = imageRequests.next()
  imageLoading.value = true
  try {
    const blob = await fetchDouyinWebQrImage(requestedLoginId)
    if (!props.modelValue ||
        !sessionRequests.isCurrent(expectedSession) ||
        !imageRequests.isCurrent(imageGeneration) ||
        loginId.value !== requestedLoginId) return
    releaseQrObjectUrl()
    qrObjectUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (!props.modelValue ||
        !sessionRequests.isCurrent(expectedSession) ||
        !imageRequests.isCurrent(imageGeneration)) return
    if (status.value?.status !== 'USER_ACTION_REQUIRED') {
      pollError.value = douyinErrorMessage(error, '二维码暂未生成，系统会继续检测。')
    }
  } finally {
    if (imageRequests.isCurrent(imageGeneration)) imageLoading.value = false
  }
}

function stepClass(index: number) {
  const rank = {
    STARTING: 0,
    USER_ACTION_REQUIRED: 0,
    WAITING: 1,
    SCANNED: 2,
    VALIDATING: 2,
    SUCCESS: 4,
    EXPIRED: 1,
    FAILED: 0
  }[status.value?.status || 'STARTING']
  return { active: index < rank, current: index === Math.min(rank, 3) }
}

function resetSession() {
  sessionRequests.invalidate()
  imageRequests.invalidate()
  polling.stop()
  releaseQrObjectUrl()
  loginId.value = ''
  status.value = undefined
  pollError.value = ''
  starting.value = false
  imageLoading.value = false
}

function handleClosed() {
  if (!props.modelValue) resetSession()
}

function releaseQrObjectUrl() {
  if (qrObjectUrl.value) URL.revokeObjectURL(qrObjectUrl.value)
  qrObjectUrl.value = ''
}
</script>

<style scoped>
.qr-flow {
  display: grid;
  gap: 18px;
}

.signal-track {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.signal-track li {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 5px;
  color: #98a2b3;
  font-size: 11px;
}

.signal-track li::before {
  content: '';
  position: absolute;
  top: 12px;
  right: 50%;
  width: 100%;
  height: 2px;
  background: #e4e7ec;
  z-index: 0;
}

.signal-track li:first-child::before {
  display: none;
}

.signal-track li span {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 25px;
  height: 25px;
  border: 1px solid #d0d5dd;
  border-radius: 50%;
  background: #fff;
  font-family: Consolas, monospace;
  font-size: 11px;
  font-weight: 800;
}

.signal-track li.active,
.signal-track li.current {
  color: #17181d;
}

.signal-track li.active::before {
  background: linear-gradient(90deg, #25f4ee, #fe2c55);
}

.signal-track li.active span,
.signal-track li.current span {
  border-color: #17181d;
  box-shadow: -2px 0 0 #25f4ee, 2px 0 0 #fe2c55;
}

.qr-stage {
  display: grid;
  place-items: center;
  min-height: 290px;
}

.qr-frame {
  position: relative;
  display: grid;
  place-items: center;
  width: 276px;
  height: 276px;
  padding: 14px;
  border: 1px solid #d0d5dd;
  border-radius: 18px;
  background: #fff;
  box-shadow: -7px 7px 0 rgba(37, 244, 238, 0.34), 7px -7px 0 rgba(254, 44, 85, 0.24);
}

.qr-frame img {
  width: 246px;
  height: 246px;
  object-fit: contain;
}

.manual-state {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 24px;
  text-align: center;
}

.manual-state .el-icon {
  color: #d97706;
  font-size: 36px;
}

.manual-state strong {
  color: #17181d;
  font-size: 16px;
}

.manual-state span {
  color: #667085;
  font-size: 13px;
  line-height: 1.55;
}

.qr-overlay {
  position: absolute;
  inset: 14px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: rgba(23, 24, 29, 0.74);
  backdrop-filter: blur(3px);
}

.qr-overlay strong {
  color: #fff;
  font-size: 20px;
  letter-spacing: 0.08em;
}

.status-copy {
  display: grid;
  justify-items: center;
  gap: 6px;
  text-align: center;
}

.status-copy h3 {
  margin: 3px 0 0;
  color: #17181d;
  font-size: 17px;
}

.status-copy p {
  margin: 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.55;
}

.status-copy > span {
  color: #667085;
  font-family: Consolas, monospace;
  font-size: 12px;
}

.login-note {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  padding: 11px 12px;
  border-left: 3px solid #25f4ee;
  background: #f7f8fa;
  color: #475467;
  font-size: 12px;
  line-height: 1.5;
}

.login-note strong {
  color: #17181d;
}

@media (max-width: 560px) {
  .qr-frame {
    width: 238px;
    height: 238px;
  }

  .qr-frame img {
    width: 208px;
    height: 208px;
  }

  .signal-track small {
    display: none;
  }

  .login-note {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .signal-track li,
  .qr-frame {
    transition: none;
  }
}
</style>
