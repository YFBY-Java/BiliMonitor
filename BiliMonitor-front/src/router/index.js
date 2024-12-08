import { createRouter, createWebHistory } from 'vue-router'

import Home from "@/views/Home.vue";


const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: Home,
    },
    {
      path: '/fans',
      name: 'fans',
      component: () => import('../views/Fans/FansMonitor.vue'),  // 引入粉丝数监控页面
    },
  ],
})

export default router
