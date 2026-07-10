<template>
  <el-dialog
    :model-value="modelValue"
    title="扫码登录 Bilibili"
    width="420px"
    class="bilibili-qr-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="stopPolling"
  >
    <div class="qr-login-body">
      <div class="qr-card" :class="{ expired: status?.status === 'EXPIRED' || status?.status === 'FAILED' }">
        <img v-if="qrDataUrl" :src="qrDataUrl" alt="Bilibili 登录二维码" />
        <el-skeleton v-else :rows="4" animated />
      </div>

      <div class="qr-status">
        <el-tag :type="statusTagType" effect="light" round>{{ statusText }}</el-tag>
        <p>{{ status?.message || '请使用 Bilibili 手机客户端扫码登录' }}</p>
        <span v-if="status?.expiresInSeconds != null && status.status !== 'SUCCESS'">
          剩余 {{ status.expiresInSeconds }} 秒
        </span>
      </div>

      <div v-if="status?.account" class="qr-account">
        <img v-if="status.account.face" :src="status.account.face" alt="" referrerpolicy="no-referrer" />
        <div>
          <strong>{{ status.account.uname || 'Bilibili 用户' }}</strong>
          <span>UID {{ status.account.mid }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="status?.status === 'EXPIRED' || status?.status === 'FAILED'"
        type="primary"
        :loading="starting"
        @click="startLogin"
      >
        重新生成二维码
      </el-button>
      <el-button v-else type="primary" :loading="starting || polling" @click="startLogin">刷新二维码</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import QRCode from 'qrcode'
import {
  fetchBilibiliQrLoginStatus,
  startBilibiliQrLogin,
  type BilibiliQrLoginStatusView
} from '@/api/bilibiliAuth'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const starting = ref(false)
const polling = ref(false)
const loginId = ref('')
const qrDataUrl = ref('')
const status = ref<BilibiliQrLoginStatusView>()
let pollTimer: number | undefined
let pollInFlight = false
let pollIntervalMillis = 1500

const statusText = computed(() => {
  if (starting.value) return '生成中'
  if (!status.value) return '等待扫码'
  if (status.value.status === 'WAITING') return '等待扫码'
  if (status.value.status === 'SCANNED') return '已扫码'
  if (status.value.status === 'SUCCESS') return '登录成功'
  if (status.value.status === 'EXPIRED') return '已过期'
  return '登录失败'
})

const statusTagType = computed<'primary' | 'success' | 'warning' | 'danger' | 'info'>(() => {
  if (!status.value) return 'primary'
  if (status.value.status === 'SUCCESS') return 'success'
  if (status.value.status === 'SCANNED') return 'warning'
  if (status.value.status === 'EXPIRED' || status.value.status === 'FAILED') return 'danger'
  return 'primary'
})

watch(
  () => props.modelValue,
  visible => {
    if (visible && !loginId.value) {
      startLogin()
    }
    if (!visible) {
      stopPolling()
    }
  }
)

async function startLogin() {
  stopPolling()
  starting.value = true
  status.value = undefined
  qrDataUrl.value = ''
  try {
    const result = await startBilibiliQrLogin()
    loginId.value = result.loginId
    pollIntervalMillis = Math.max(1000, result.pollIntervalMillis || 1500)
    qrDataUrl.value = await QRCode.toDataURL(result.qrUrl, {
      width: 240,
      margin: 2,
      errorCorrectionLevel: 'M'
    })
    status.value = {
      status: 'WAITING',
      message: '请使用 Bilibili 手机客户端扫码登录',
      expiresInSeconds: result.expiresInSeconds
    }
    schedulePoll(200)
  } finally {
    starting.value = false
  }
}

function schedulePoll(delay = pollIntervalMillis) {
  stopPolling()
  pollTimer = window.setTimeout(pollOnce, delay)
}

async function pollOnce() {
  if (!loginId.value || pollInFlight) {
    schedulePoll()
    return
  }
  pollInFlight = true
  polling.value = true
  try {
    const next = await fetchBilibiliQrLoginStatus(loginId.value)
    status.value = next
    if (next.status === 'SUCCESS') {
      stopPolling()
      emit('success')
      return
    }
    if (next.status === 'EXPIRED' || next.status === 'FAILED') {
      stopPolling()
      return
    }
    schedulePoll()
  } finally {
    polling.value = false
    pollInFlight = false
  }
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
}
</script>

<style scoped>
.qr-login-body {
  display: grid;
  gap: 16px;
  justify-items: center;
}

.qr-card {
  width: 264px;
  height: 264px;
  display: grid;
  place-items: center;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background: #f8fbff;
}

.qr-card.expired {
  opacity: 0.56;
}

.qr-card img {
  width: 240px;
  height: 240px;
}

.qr-status {
  display: grid;
  gap: 6px;
  justify-items: center;
  text-align: center;
}

.qr-status p {
  margin: 0;
  color: #334155;
  font-size: 14px;
}

.qr-status span {
  color: #667085;
  font-size: 12px;
}

.qr-account {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
}

.qr-account img {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.qr-account div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.qr-account strong,
.qr-account span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qr-account strong {
  color: #0f172a;
}

.qr-account span {
  color: #64748b;
  font-size: 12px;
}
</style>
