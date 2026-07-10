import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import DashboardView from '@/views/dashboard/DashboardView.vue'
import BilibiliView from '@/views/bilibili/BilibiliView.vue'
import BilibiliLiveView from '@/views/bilibili-live/BilibiliLiveView.vue'
import SubjectListView from '@/views/subjects/SubjectListView.vue'
import SubjectWorkbenchView from '@/views/subjects/SubjectWorkbenchView.vue'
import PlatformView from '@/views/platform/PlatformView.vue'
import TasksView from '@/views/tasks/TasksView.vue'
import DataCenterView from '@/views/data/DataCenterView.vue'
import AnalyticsView from '@/views/analytics/AnalyticsView.vue'
import AiAnalysisView from '@/views/ai/AiAnalysisView.vue'
import IdentityView from '@/views/identity/IdentityView.vue'
import SettingsView from '@/views/settings/SettingsView.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: 'Dashboard' } },
      { path: 'bilibili', name: 'bilibili', component: BilibiliView, meta: { title: 'Bilibili' } },
      { path: 'bilibili/live', name: 'bilibili-live', component: BilibiliLiveView, meta: { title: 'Bilibili 直播' } },
      { path: 'subjects', name: 'subjects', component: SubjectListView, meta: { title: '用户监控' } },
      { path: 'subjects/:subjectId', name: 'subject-workbench', component: SubjectWorkbenchView, meta: { title: '用户监控' } },
      { path: 'platform', name: 'platform', component: PlatformView, meta: { title: '平台管理' } },
      { path: 'tasks', name: 'tasks', component: TasksView, meta: { title: '采集任务' } },
      { path: 'data', name: 'data', component: DataCenterView, meta: { title: '数据中心' } },
      { path: 'analytics', name: 'analytics', component: AnalyticsView, meta: { title: '分析看板' } },
      { path: 'ai', name: 'ai', component: AiAnalysisView, meta: { title: 'AI 分析' } },
      { path: 'identity', name: 'identity', component: IdentityView, meta: { title: 'Identity' } },
      { path: 'settings', name: 'settings', component: SettingsView, meta: { title: '系统设置' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
