<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="min(820px, 94vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="credential" class="credential-shell">
      <header class="credential-head">
        <div>
          <span>{{ credential.authType }}</span>
          <strong>#{{ credential.credentialId }} · {{ credential.status }}</strong>
          <small>更新于 {{ formatTime(credential.updatedAt) }}</small>
        </div>
        <div class="credential-actions">
          <el-button @click="copyText(fullJson)">复制全部 JSON</el-button>
          <el-button type="primary" @click="emit('export')">下载原始 JSON</el-button>
        </div>
      </header>

      <el-tabs v-model="activeTab" class="credential-tabs">
        <el-tab-pane v-for="section in sections" :key="section.key" :label="section.label" :name="section.key">
          <div class="raw-panel">
            <div class="raw-toolbar">
              <div>
                <strong>{{ section.label }}</strong>
                <span>{{ section.description }}</span>
              </div>
              <el-button size="small" @click="copyText(section.text)">复制当前字段</el-button>
            </div>
            <pre>{{ section.text }}</pre>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <el-empty v-else description="没有可显示的登录态" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { DouyinCredentialFull } from '@/api/douyinAuth'

const props = defineProps<{
  modelValue: boolean
  credential?: DouyinCredentialFull
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  export: []
}>()

const activeTab = ref('account')
const payload = computed<Record<string, unknown>>(() => props.credential?.payload || {})
const origins = computed<Record<string, unknown>[]>(() =>
  Array.isArray(payload.value.origins) ? payload.value.origins as Record<string, unknown>[] : []
)
const fullJson = computed(() => pretty(props.credential || {}))
const drawerTitle = computed(() =>
  props.credential?.authType === 'DOUYIN_OAUTH2' ? '完整抖音 OAuth 登录态' : '完整抖音 Web 登录态'
)

const sections = computed(() => [
  section('account', '账号信息', 'Worker 或 OAuth 返回的账号原始字段。', payload.value.account || {}),
  section('headers', 'Cookie Header', '按目标 Origin 生成的完整 Cookie Header。', payload.value.cookieHeadersByOrigin || {}),
  section('cookies', 'Cookies JSON', '浏览器上下文内捕获的全部 Cookie 及其属性。', payload.value.cookies || []),
  section('local', 'localStorage', '逐 Origin 保存的 localStorage。', storageByOrigin('localStorage')),
  section('session', 'sessionStorage', '逐 Origin 保存的 sessionStorage。', storageByOrigin('sessionStorage')),
  section('indexeddb', 'IndexedDB', 'Playwright 可序列化导出的 IndexedDB。', storageByOrigin('indexedDb')),
  section('storage', 'storageState', 'Playwright 原始 storageState，可用于恢复新 Context。', payload.value.storageState || {}),
  section('browser', '浏览器上下文', 'User-Agent、语言、时区、视口与平台信息。', payload.value.browserContext || {}),
  section('worker', 'Worker 原始结果', '捕获与第二 Context 复验时的 Worker 原始结果。', payload.value.rawWorkerResult || {}),
  section('full', '完整合并 JSON', '数据库解密后的完整凭据对象。', props.credential || {})
])

watch(
  () => props.credential?.credentialId,
  () => { activeTab.value = 'account' }
)

function storageByOrigin(field: string) {
  return origins.value.map(origin => ({
    origin: origin.origin,
    [field]: origin[field] || []
  }))
}

function section(key: string, label: string, description: string, value: unknown) {
  return { key, label, description, text: pretty(value) }
}

function pretty(value: unknown) {
  return JSON.stringify(value ?? null, null, 2)
}

async function copyText(text: string) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制原始字段')
}

function formatTime(value?: string) {
  if (!value) return '--'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.credential-shell {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.credential-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 15px;
  border: 1px solid #dfe3e8;
  border-left: 4px solid #25f4ee;
  border-radius: 10px;
  background: #f7f8fa;
}

.credential-head > div:first-child {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.credential-head span,
.credential-head small {
  color: #667085;
  font-family: Consolas, monospace;
  font-size: 11px;
}

.credential-head strong {
  color: #17181d;
  font-size: 15px;
}

.credential-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.credential-tabs {
  min-width: 0;
}

.raw-panel {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.raw-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.raw-toolbar > div {
  display: grid;
  gap: 3px;
}

.raw-toolbar strong {
  color: #17181d;
  font-size: 14px;
}

.raw-toolbar span {
  color: #667085;
  font-size: 12px;
}

.raw-panel pre {
  min-height: 360px;
  max-height: calc(100vh - 280px);
  margin: 0;
  overflow: auto;
  padding: 14px;
  border: 1px solid #dfe3e8;
  border-radius: 10px;
  background: #111318;
  color: #d7fdfb;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  tab-size: 2;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 640px) {
  .credential-head,
  .raw-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .credential-actions {
    justify-content: flex-start;
  }

  .raw-panel pre {
    min-height: 280px;
  }
}
</style>
