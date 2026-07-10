<template>
  <article class="monitor-widget" :class="sizeClass">
    <header class="monitor-widget__head">
      <div class="monitor-widget__title">
        <span class="drag-handle" aria-hidden="true">::</span>
        <div>
          <h3>{{ title }}</h3>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <div class="monitor-widget__actions">
        <slot name="actions">
          <el-tag v-if="badge" :type="badgeType" effect="light" round>{{ badge }}</el-tag>
        </slot>
      </div>
    </header>
    <div class="monitor-widget__body">
      <slot />
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  title: string
  subtitle?: string
  badge?: string
  badgeType?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  size?: 'large' | 'small'
}>()

const sizeClass = computed(() => (props.size === 'large' ? 'monitor-widget--large' : 'monitor-widget--small'))
</script>

<style scoped>
.monitor-widget {
  min-width: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background: #fff;
}

.monitor-widget__head {
  min-height: 52px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px 10px;
  border-bottom: 1px solid #edf1f6;
}

.monitor-widget__title {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.monitor-widget__title > div {
  min-width: 0;
}

.monitor-widget__title h3,
.monitor-widget__title p {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-widget__title h3 {
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0;
}

.monitor-widget__title p {
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
}

.monitor-widget__actions {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  max-width: 42%;
  padding-top: 1px;
  white-space: nowrap;
}

.drag-handle {
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 1px solid #dbe4f0;
  border-radius: 6px;
  background: #fff;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
}

.monitor-widget__body {
  flex: 1;
  padding: 14px;
}
</style>
