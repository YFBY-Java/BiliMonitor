<template>
  <section class="subject-header">
    <div class="subject-profile">
      <div class="subject-avatar" :class="{ live: workbench.bilibiliLiveRoom?.liveStatus === 1 }">
        <img
          v-if="avatarUrl && !avatarBroken"
          :src="avatarUrl"
          alt=""
          referrerpolicy="no-referrer"
          @error="avatarBroken = true"
        />
        <span v-else>{{ fallbackLetter }}</span>
      </div>
      <div class="subject-copy">
        <h1>{{ workbench.subject.displayName }}</h1>
        <div class="subject-meta">
          <span v-if="workbench.bilibiliUser">Bilibili UID {{ workbench.bilibiliUser.mid }}</span>
          <span v-if="workbench.bilibiliLiveRoom" :class="{ green: workbench.bilibiliLiveRoom.liveStatus === 1 }">
            {{ liveStatusText }}
          </span>
          <span>{{ workbench.summary.enabledModuleCount }}/{{ workbench.summary.totalModuleCount }} 模块</span>
          <span v-if="workbench.danmu.enabled" :class="{ green: workbench.danmu.status === 'connected' }">
            {{ danmuStatusText }}
          </span>
        </div>
      </div>
    </div>
    <div class="health-panel">
      <span>采集健康</span>
      <div class="health-score">
        <strong>{{ Math.round(workbench.summary.healthScore ?? 0) }}</strong>
        <el-tag :type="healthTagType" effect="light" round>{{ healthText }}</el-tag>
      </div>
      <div class="health-bar"><i :style="{ width: `${Math.max(0, Math.min(100, workbench.summary.healthScore ?? 0))}%` }"></i></div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SubjectWorkbench } from '@/api/subjects'

const props = defineProps<{ workbench: SubjectWorkbench }>()
const avatarBroken = ref(false)

const avatarUrl = computed(() =>
  props.workbench.subject.avatarUrl ||
  props.workbench.bilibiliUser?.avatarUrl ||
  props.workbench.bilibiliLiveRoom?.faceUrl
)

watch(avatarUrl, () => {
  avatarBroken.value = false
})

const fallbackLetter = computed(() => props.workbench.subject.displayName.slice(0, 1).toUpperCase() || 'U')

const liveStatusText = computed(() => {
  const status = props.workbench.bilibiliLiveRoom?.liveStatus
  if (status === 1) return '直播中'
  if (status === 2) return '轮播中'
  if (status === 0) return '未开播'
  return '直播未绑定'
})

const healthText = computed(() => {
  const score = props.workbench.summary.healthScore ?? 0
  if (score >= 85) return '正常'
  if (score >= 65) return '关注'
  return '异常'
})

const healthTagType = computed<'success' | 'warning' | 'danger'>(() => {
  const score = props.workbench.summary.healthScore ?? 0
  if (score >= 85) return 'success'
  if (score >= 65) return 'warning'
  return 'danger'
})

const danmuStatusText = computed(() => {
  const status = props.workbench.danmu.status
  if (status === 'connected') return props.workbench.bilibiliLiveRoom?.liveStatus === 1 ? '弹幕采集中' : '弹幕监听中'
  if (status === 'waiting') return '弹幕待监听'
  if (status === 'error') return '弹幕连接异常'
  if (status === 'missing_live_room') return '弹幕未绑定直播间'
  return '弹幕已启用'
})
</script>

<style scoped>
.subject-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 224px;
  align-items: center;
  gap: 12px;
  padding: 9px 14px;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background: #fff;
}

.subject-profile {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.subject-avatar {
  position: relative;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  overflow: hidden;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f6df6, #06b6d4);
  color: #fff;
  font-size: 16px;
  font-weight: 900;
}

.subject-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.subject-avatar.live::after {
  content: "";
  position: absolute;
  right: 2px;
  bottom: 3px;
  width: 10px;
  height: 10px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #10b981;
}

.subject-copy {
  min-width: 0;
}

.subject-copy h1 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0;
  line-height: 1.15;
}

.subject-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 5px;
}

.subject-meta span {
  min-height: 21px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border: 1px solid #dbe4f0;
  border-radius: 999px;
  background: #fff;
  color: #475467;
  font-size: 12px;
  white-space: nowrap;
}

.subject-meta .green {
  border-color: #bdebd9;
  background: #e8f8f2;
  color: #047857;
}

.health-panel {
  min-width: 0;
  padding-left: 14px;
  border-left: 1px solid #e6ebf2;
}

.health-panel > span {
  color: #667085;
  font-size: 12px;
}

.health-score {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 2px;
}

.health-score strong {
  color: #0f172a;
  font-size: 24px;
  font-weight: 900;
  line-height: 1;
}

.health-bar {
  height: 5px;
  margin-top: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2f7;
}

.health-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #10b981, #22c55e);
}

@media (max-width: 1180px) {
  .subject-header {
    grid-template-columns: 1fr;
  }

  .health-panel {
    padding-left: 0;
    padding-top: 14px;
    border-left: none;
    border-top: 1px solid #e6ebf2;
  }
}
</style>
