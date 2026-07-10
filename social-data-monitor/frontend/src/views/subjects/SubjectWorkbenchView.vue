<template>
  <div class="page subject-workbench-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户监控</h1>
        <p class="page-subtitle">Bilibili MVP / 指定用户工作台</p>
      </div>
      <div class="header-actions">
        <el-button @click="router.push('/subjects')">返回列表</el-button>
        <el-button @click="openBindingDialog">绑定资源</el-button>
        <el-button :loading="loading" @click="loadWorkbench">刷新</el-button>
        <el-button type="primary" @click="loadWorkbench">刷新数据</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && !workbench" :rows="8" animated />
    <el-alert v-else-if="error" :title="error" type="error" show-icon />

    <template v-else-if="workbench">
      <SubjectHeader :workbench="workbench" />

      <section class="kpi-grid">
        <el-card class="kpi-card" shadow="never">
          <div class="kpi-head">
            <span>总粉丝数</span>
            <el-tag v-if="workbench.summary.followerDelta24h != null" type="success" effect="light" round>
              {{ formatSigned(workbench.summary.followerDelta24h) }}
            </el-tag>
          </div>
          <strong>{{ formatCompactNumber(workbench.summary.followerCount) }}</strong>
          <small>今日 {{ formatSigned(workbench.summary.followerDelta24h) }}</small>
        </el-card>
        <el-card class="kpi-card" shadow="never">
          <div class="kpi-head">
            <span>直播间热度</span>
            <el-tag type="danger" effect="light" round>峰值</el-tag>
          </div>
          <strong>{{ formatCompactNumber(workbench.summary.onlineCount) }}</strong>
          <small>峰值 {{ formatCompactNumber(workbench.summary.onlinePeak24h) }}</small>
        </el-card>
        <el-card class="kpi-card" shadow="never">
          <div class="kpi-head">
            <span>弹幕速率</span>
            <el-tag :type="danmuKpiTagType" effect="light" round>{{ danmuKpiTagText }}</el-tag>
          </div>
          <strong>{{ workbench.summary.danmuPerMinute == null ? '--' : `${workbench.summary.danmuPerMinute} /min` }}</strong>
          <small>近 5 分钟 {{ workbench.summary.danmuLast5Minutes ?? '--' }} 条</small>
        </el-card>
        <el-card class="kpi-card" shadow="never">
          <div class="kpi-head">
            <span>最近成功采集</span>
            <el-tag effect="light" round>{{ workbench.summary.enabledModuleCount }}/{{ workbench.summary.totalModuleCount }}</el-tag>
          </div>
          <strong>{{ formatRelativeTime(workbench.summary.lastSuccessAt) }}</strong>
          <small>下一轮 {{ formatDateTime(workbench.summary.nextCollectAt) }}</small>
        </el-card>
      </section>

      <el-alert
        v-if="!workbench.bilibiliUser && !workbench.bilibiliLiveRoom"
        title="该对象还没有绑定 B站粉丝监控或直播间监控。可以先在专项页添加监控，再回到这里绑定。"
        type="warning"
        show-icon
        :closable="false"
      />

      <SubjectWidgetBoard :workbench="workbench" :trend="trend" @refresh="loadWorkbench" />
    </template>

    <el-dialog v-model="bindingDialogVisible" title="绑定 B站监控资源" width="560px">
      <el-form label-position="top">
        <el-form-item label="粉丝监控用户">
          <el-select v-model="bindingForm.bilibiliUserMonitorId" clearable filterable placeholder="选择已有 UID">
            <el-option
              v-for="user in bilibiliUsers"
              :key="user.id"
              :label="`${user.nickname} · UID ${user.mid}`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="直播间监控">
          <el-select v-model="bindingForm.bilibiliLiveRoomMonitorId" clearable filterable placeholder="选择已有直播间">
            <el-option
              v-for="room in liveRooms"
              :key="room.id"
              :label="`${room.uname} · 房间 ${room.roomId}`"
              :value="room.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="弹幕模块">
          <el-switch v-model="bindingForm.danmuEnabled" active-text="启用弹幕监控" inactive-text="关闭" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingBinding" @click="saveBinding">保存绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchBilibiliMonitorUsers, type BilibiliMonitorUser } from '@/api/bilibili'
import { fetchBilibiliLiveRooms, type BilibiliLiveRoom } from '@/api/bilibiliLive'
import {
  fetchSubjectTrends,
  fetchSubjectWorkbench,
  updateSubjectBilibiliBinding,
  type SubjectTrend,
  type SubjectWorkbench
} from '@/api/subjects'
import SubjectHeader from './components/SubjectHeader.vue'
import SubjectWidgetBoard from './components/SubjectWidgetBoard.vue'
import { formatCompactNumber, formatDateTime, formatRelativeTime, formatSigned } from './formatters'

const route = useRoute()
const router = useRouter()

const subjectId = computed(() => Number(route.params.subjectId))
const loading = ref(false)
const error = ref('')
const workbench = ref<SubjectWorkbench>()
const trend = ref<SubjectTrend>()
const bindingDialogVisible = ref(false)
const savingBinding = ref(false)
const bilibiliUsers = ref<BilibiliMonitorUser[]>([])
const liveRooms = ref<BilibiliLiveRoom[]>([])

const bindingForm = reactive({
  bilibiliUserMonitorId: undefined as number | undefined,
  bilibiliLiveRoomMonitorId: undefined as number | undefined,
  danmuEnabled: true
})

const danmuKpiTagText = computed(() => {
  if (!workbench.value?.danmu.enabled) return '未启用'
  if (workbench.value.danmu.status === 'connected') return workbench.value.bilibiliLiveRoom?.liveStatus === 1 ? '实时' : '监听'
  if (workbench.value.danmu.status === 'error') return '异常'
  return '等待'
})

const danmuKpiTagType = computed<'primary' | 'success' | 'warning' | 'danger' | 'info'>(() => {
  if (!workbench.value?.danmu.enabled) return 'info'
  if (workbench.value.danmu.status === 'connected') return 'success'
  if (workbench.value.danmu.status === 'error') return 'danger'
  return 'primary'
})

async function loadWorkbench() {
  if (!Number.isFinite(subjectId.value)) return
  loading.value = true
  error.value = ''
  try {
    const [nextWorkbench, nextTrend] = await Promise.all([
      fetchSubjectWorkbench(subjectId.value),
      fetchSubjectTrends(subjectId.value)
    ])
    workbench.value = nextWorkbench
    trend.value = nextTrend
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '加载用户监控工作台失败'
  } finally {
    loading.value = false
  }
}

async function openBindingDialog() {
  if (!workbench.value) return
  const [users, rooms] = await Promise.all([fetchBilibiliMonitorUsers(), fetchBilibiliLiveRooms()])
  bilibiliUsers.value = users
  liveRooms.value = rooms
  bindingForm.bilibiliUserMonitorId = workbench.value.bilibiliBinding?.bilibiliUserMonitorId
  bindingForm.bilibiliLiveRoomMonitorId = workbench.value.bilibiliBinding?.bilibiliLiveRoomMonitorId
  bindingForm.danmuEnabled = workbench.value.bilibiliBinding?.danmuEnabled ?? true
  bindingDialogVisible.value = true
}

async function saveBinding() {
  savingBinding.value = true
  try {
    await updateSubjectBilibiliBinding(subjectId.value, {
      bilibiliUserMonitorId: bindingForm.bilibiliUserMonitorId,
      bilibiliLiveRoomMonitorId: bindingForm.bilibiliLiveRoomMonitorId,
      danmuEnabled: bindingForm.danmuEnabled
    })
    bindingDialogVisible.value = false
    ElMessage.success('绑定已保存')
    await loadWorkbench()
  } finally {
    savingBinding.value = false
  }
}

watch(subjectId, loadWorkbench)
onMounted(loadWorkbench)
</script>

<style scoped>
.subject-workbench-page {
  max-width: 1240px;
  margin: 0 auto;
}

.header-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.kpi-card {
  min-width: 0;
  border-radius: 8px;
}

.kpi-card :deep(.el-card__body) {
  padding: 9px 14px 10px;
}

.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 7px;
}

.kpi-head span {
  color: #667085;
  font-size: 12px;
  line-height: 1.2;
}

.kpi-head :deep(.el-tag) {
  height: 22px;
  padding: 0 9px;
  line-height: 20px;
}

.kpi-card strong {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-size: 21px;
  font-weight: 900;
  letter-spacing: 0;
  line-height: 1.08;
}

.kpi-card small {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #047857;
  font-size: 12px;
}

@media (max-width: 1180px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
