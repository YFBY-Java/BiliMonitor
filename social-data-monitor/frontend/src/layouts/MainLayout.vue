<template>
  <el-container class="shell">
    <el-aside width="236px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">SD</div>
        <div>
          <strong>Social Data Monitor</strong>
          <span>数据监控后台</span>
        </div>
      </div>
      <el-menu router :default-active="activeMenu" class="menu">
        <el-menu-item index="/dashboard"><el-icon><DataLine /></el-icon><span>Dashboard</span></el-menu-item>
        <el-menu-item index="/bilibili"><el-icon><VideoPlay /></el-icon><span>Bilibili</span></el-menu-item>
        <el-menu-item index="/bilibili/live"><el-icon><Monitor /></el-icon><span>直播监控</span></el-menu-item>
        <el-menu-item index="/subjects"><el-icon><User /></el-icon><span>用户监控</span></el-menu-item>
        <el-menu-item index="/platform"><el-icon><Connection /></el-icon><span>平台管理</span></el-menu-item>
        <el-menu-item index="/tasks"><el-icon><List /></el-icon><span>采集任务</span></el-menu-item>
        <el-menu-item index="/data"><el-icon><FolderOpened /></el-icon><span>数据中心</span></el-menu-item>
        <el-menu-item index="/analytics"><el-icon><TrendCharts /></el-icon><span>分析看板</span></el-menu-item>
        <el-menu-item index="/ai"><el-icon><Cpu /></el-icon><span>AI 分析</span></el-menu-item>
        <el-menu-item index="/identity"><el-icon><User /></el-icon><span>Identity</span></el-menu-item>
        <el-menu-item index="/settings"><el-icon><Setting /></el-icon><span>系统设置</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="content-shell">
      <el-header class="header">
        <div>
          <strong>{{ $route.meta.title }}</strong>
          <span class="header-subtitle">模块化单体 / Bilibili MVP</span>
        </div>
        <el-tag type="success" effect="light">Dev</el-tag>
      </el-header>
      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import {
  Connection,
  Cpu,
  DataLine,
  FolderOpened,
  List,
  Monitor,
  Setting,
  TrendCharts,
  User,
  VideoPlay
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => {
  if (route.path.startsWith('/subjects')) return '/subjects'
  return route.path
})
</script>

<style scoped>
.shell {
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
}

.sidebar {
  flex: 0 0 236px;
  height: 100vh;
  overflow-y: auto;
  background: #fff;
  border-right: 1px solid #e5e7eb;
}

.content-shell {
  min-width: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid #eef0f4;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: #fff;
  background: #1677ff;
  font-weight: 800;
}

.brand span {
  display: block;
  margin-top: 2px;
  color: #667085;
  font-size: 12px;
}

.menu {
  border-right: none;
}

.header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.header-subtitle {
  margin-left: 12px;
  color: #667085;
  font-size: 13px;
}

.main {
  background: #f5f7fb;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

@media (max-width: 760px) {
  .sidebar {
    width: 72px !important;
  }

  .brand {
    justify-content: center;
    padding: 0;
  }

  .brand > div:not(.brand-mark),
  .menu span {
    display: none;
  }

  .menu :deep(.el-menu-item) {
    justify-content: center;
    padding: 0 !important;
  }

  .header-subtitle {
    display: none;
  }
}

@media (max-width: 520px) {
  .header {
    padding: 0 12px;
  }

  .main {
    padding: 14px 12px;
  }
}
</style>
