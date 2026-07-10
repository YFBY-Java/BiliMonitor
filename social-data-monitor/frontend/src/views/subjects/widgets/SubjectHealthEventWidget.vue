<template>
  <MonitorWidgetShell :title="`${workbench.subject.displayName} 采集健康与事件`" badge="正常" badge-type="success" size="large">
    <template #actions>
      <div class="health-actions">
        <el-tag :type="healthType" effect="light" round>{{ healthText }}</el-tag>
        <el-button circle size="small" title="更多">
          <el-icon><MoreFilled /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="health-stats">
      <div>
        <span>启用模块</span>
        <strong>{{ workbench.summary.enabledModuleCount }}/{{ workbench.summary.totalModuleCount }}</strong>
      </div>
      <div>
        <span>下一轮采集</span>
        <strong>{{ formatDateTime(workbench.summary.nextCollectAt) }}</strong>
      </div>
      <div>
        <span>最近成功</span>
        <strong>{{ formatRelativeTime(workbench.summary.lastSuccessAt) }}</strong>
      </div>
    </div>

    <div class="event-list">
      <div v-for="event in workbench.recentEvents" :key="`${event.eventType}-${event.occurredAt}`" class="event-row">
        <i :class="event.level"></i>
        <div>
          <strong>{{ event.title }}</strong>
          <span>{{ event.description || event.source }}</span>
        </div>
        <time>{{ formatRelativeTime(event.occurredAt) }}</time>
      </div>
    </div>
  </MonitorWidgetShell>
</template>

<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { SubjectWorkbench } from '@/api/subjects'
import MonitorWidgetShell from '../components/MonitorWidgetShell.vue'
import { formatDateTime, formatRelativeTime } from '../formatters'

const props = defineProps<{ workbench: SubjectWorkbench }>()

const healthText = computed(() => {
  const score = props.workbench.summary.healthScore ?? 0
  if (score >= 85) return '正常'
  if (score >= 65) return '关注'
  return '异常'
})

const healthType = computed<'success' | 'warning' | 'danger'>(() => {
  const score = props.workbench.summary.healthScore ?? 0
  if (score >= 85) return 'success'
  if (score >= 65) return 'warning'
  return 'danger'
})
</script>

<style scoped>
.health-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.health-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.health-stats div {
  min-width: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}

.health-stats span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.health-stats strong {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.event-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.event-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
}

.event-row i {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 50%;
  background: #2f6df6;
  box-shadow: 0 0 0 4px #e8efff;
}

.event-row i.success {
  background: #10b981;
  box-shadow: 0 0 0 4px #e8f8f2;
}

.event-row i.warning {
  background: #f59e0b;
  box-shadow: 0 0 0 4px #fff5df;
}

.event-row i.error {
  background: #ef4444;
  box-shadow: 0 0 0 4px #fee2e2;
}

.event-row strong,
.event-row span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-row strong {
  color: #0f172a;
  font-size: 13px;
}

.event-row span,
.event-row time {
  color: #667085;
  font-size: 12px;
}

.event-row time {
  white-space: nowrap;
}

@media (max-width: 1180px) {
  .health-stats {
    grid-template-columns: 1fr;
  }
}
</style>
