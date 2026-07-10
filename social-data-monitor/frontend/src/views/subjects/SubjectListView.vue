<template>
  <div class="page subjects-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户监控</h1>
        <p class="page-subtitle">输入 B站 UID 后自动创建粉丝监控和直播间监控，并聚合到一个指定用户工作台。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <el-card class="create-card" shadow="never">
      <el-form class="create-form" label-position="top" @submit.prevent>
        <el-form-item label="B站 UID">
          <el-input v-model.trim="form.mid" placeholder="例如 401742377" inputmode="numeric" clearable />
        </el-form-item>
        <el-form-item label="工作台名称">
          <el-input v-model.trim="form.displayName" placeholder="可留空，默认使用 B站昵称" clearable />
        </el-form-item>
        <el-form-item label="弹幕模块">
          <el-switch v-model="form.danmuEnabled" active-text="启用" inactive-text="关闭" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="createFromForm">创建工作台</el-button>
        </el-form-item>
      </el-form>
      <div class="create-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>提交后会先创建或复用该 UID 的粉丝监控，再尝试创建或复用同 UID 的直播间监控；若用户没有公开直播间，会保留粉丝监控继续创建工作台。</span>
      </div>
    </el-card>

    <div class="metric-grid">
      <el-card class="metric-card" shadow="never">
        <div class="metric-label">监控对象</div>
        <div class="metric-value">{{ subjects.length }}</div>
      </el-card>
      <el-card class="metric-card" shadow="never">
        <div class="metric-label">已绑定粉丝</div>
        <div class="metric-value">{{ boundFollowerCount }}</div>
      </el-card>
      <el-card class="metric-card" shadow="never">
        <div class="metric-label">已绑定直播</div>
        <div class="metric-value">{{ boundLiveCount }}</div>
      </el-card>
      <el-card class="metric-card" shadow="never">
        <div class="metric-label">弹幕监控</div>
        <div class="metric-value">{{ danmuEnabledCount }}</div>
      </el-card>
    </div>

    <el-card class="subject-table-card" shadow="never">
      <template #header>
        <div class="card-title">
          <strong>用户监控对象</strong>
          <span>点击一行进入工作台查看粉丝、直播热度和弹幕指标。</span>
        </div>
      </template>

      <el-table v-if="subjects.length" :data="subjects" row-key="id" @row-click="openSubject">
        <el-table-column label="对象" min-width="240">
          <template #default="{ row }">
            <div class="subject-cell">
              <div class="avatar">
                <img v-if="row.avatarUrl" :src="row.avatarUrl" alt="" referrerpolicy="no-referrer" />
                <span v-else>{{ row.displayName.slice(0, 1) }}</span>
              </div>
              <div>
                <strong>{{ row.displayName }}</strong>
                <span>{{ row.remark || 'Bilibili 指定用户工作台' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="B站绑定" min-width="260">
          <template #default="{ row }">
            <div class="binding-tags">
              <el-tag v-if="row.bilibiliBinding?.mid" effect="light">UID {{ row.bilibiliBinding.mid }}</el-tag>
              <el-tag v-if="row.bilibiliBinding?.bilibiliUserMonitorId" type="primary" effect="light">粉丝监控</el-tag>
              <el-tag v-if="row.bilibiliBinding?.roomId" type="success" effect="light">房间 {{ row.bilibiliBinding.roomId }}</el-tag>
              <el-tag v-if="row.bilibiliBinding?.danmuEnabled" type="success" effect="light">弹幕已启用</el-tag>
              <span v-if="!row.bilibiliBinding">未绑定</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="健康" width="110">
          <template #default="{ row }">
            <el-tag :type="healthType(row.healthScore)" effect="light" round>{{ Math.round(row.healthScore ?? 0) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近成功" width="150">
          <template #default="{ row }">{{ formatRelativeTime(row.lastSuccessAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.monitorStatus === 'ACTIVE' ? 'success' : 'info'" effect="light">
              {{ row.monitorStatus === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openSubject(row)">打开</el-button>
            <el-button size="small" type="danger" plain @click.stop="removeSubject(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="还没有用户监控对象">
        <el-button type="primary" @click="createFromForm">创建第一个工作台</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createSubject, deleteSubject, fetchSubjects, type Subject } from '@/api/subjects'
import { formatRelativeTime } from './formatters'

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const subjects = ref<Subject[]>([])

const form = reactive({
  displayName: '',
  mid: '',
  danmuEnabled: true
})

const boundFollowerCount = computed(() => subjects.value.filter((item) => item.bilibiliBinding?.bilibiliUserMonitorId).length)
const boundLiveCount = computed(() => subjects.value.filter((item) => item.bilibiliBinding?.bilibiliLiveRoomMonitorId).length)
const danmuEnabledCount = computed(() => subjects.value.filter((item) => item.bilibiliBinding?.danmuEnabled).length)

async function loadAll() {
  loading.value = true
  try {
    subjects.value = await fetchSubjects()
  } finally {
    loading.value = false
  }
}

async function createFromForm() {
  const mid = Number(form.mid)
  if (!Number.isSafeInteger(mid) || mid <= 0) {
    ElMessage.warning('请输入有效的 B站 UID')
    return
  }
  creating.value = true
  try {
    const created = await createSubject({
      displayName: form.displayName.trim() || undefined,
      mid,
      danmuEnabled: form.danmuEnabled
    })
    ElMessage.success('用户监控工作台已创建')
    await router.push(`/subjects/${created.id}`)
  } catch (exception) {
    ElMessage.error(exception instanceof Error ? exception.message : '工作台创建失败')
  } finally {
    creating.value = false
  }
}

function openSubject(row: Subject) {
  router.push(`/subjects/${row.id}`)
}

async function removeSubject(row: Subject) {
  await ElMessageBox.confirm(`确认删除“${row.displayName}”？不会删除原 B站粉丝/直播监控历史数据。`, '删除监控对象', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteSubject(row.id)
  ElMessage.success('已删除')
  await loadAll()
}

function healthType(score?: number) {
  if ((score ?? 0) >= 85) return 'success'
  if ((score ?? 0) >= 65) return 'warning'
  return 'info'
}

onMounted(loadAll)
</script>

<style scoped>
.subjects-page {
  max-width: 1240px;
  margin: 0 auto;
}

.header-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.create-card,
.subject-table-card {
  border-radius: 8px;
}

.create-form {
  display: grid;
  grid-template-columns: minmax(180px, 0.9fr) minmax(220px, 1.1fr) 150px 150px;
  gap: 14px;
  align-items: end;
}

.create-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.create-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #667085;
  font-size: 13px;
  line-height: 1.55;
}

.create-tip .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: #2f6df6;
}

.card-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.card-title strong {
  color: #0f172a;
  font-size: 16px;
}

.card-title span {
  color: #667085;
  font-size: 13px;
}

.subject-cell {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  overflow: hidden;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f6df6, #06b6d4);
  color: #fff;
  font-weight: 900;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.subject-cell strong,
.subject-cell span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subject-cell strong {
  color: #0f172a;
}

.subject-cell span {
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
}

.binding-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  color: #98a2b3;
}

@media (max-width: 1180px) {
  .create-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
