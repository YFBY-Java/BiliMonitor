<script setup>
import NavBar from './views/NavBar.vue';
</script>

<template>
  <!-- 整体布局容器 -->
  <div class="layout-demo">
    <a-layout style="height: 100vh; overflow: hidden;">
      <!-- 布局头部，包含导航栏组件 -->
      <a-layout-header :height="50" :padding-bottom="0">
        <NavBar />
      </a-layout-header>
      <!-- 布局主体内容区域，用于展示路由对应的视图 -->
      <a-layout-content>
        <RouterView />
      </a-layout-content>
    </a-layout>
  </div>
</template>

<style scoped>
/* 确保根元素和body占满全屏 */
html, body {
  height: 100%;
  margin: 0;
  overflow: hidden; /* 禁止滚动 */
}

.layout-demo {
  height: 100%;
  background-color: #F4F4F4;
  font-family: 'Roboto', sans-serif;
  overflow: hidden; /* 禁止滚动 */
}

.layout-demo :deep(.arco-layout-header),
.layout-demo :deep(.arco-layout-footer),
.layout-demo :deep(.arco-layout-sider-children),
.layout-demo :deep(.arco-layout-content) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  font-size: 16px;
  font-stretch: condensed;
  text-align: center;
}

.layout-demo :deep(.arco-layout-header),
.layout-demo :deep(.arco-layout-footer) {
  height: 64px;
  background-color: #E0E0E0;
  color: #FFFFFF;
  border-radius: 4px;
}

.layout-demo :deep(.arco-layout-sider) {
  width: 100px;
  background-color: #E8F4FF;
  border-radius: 4px;
}

.layout-demo :deep(.arco-layout-content) {
  background-color: #F8F8F8;
  border: 1px solid #DCDCDC;
  overflow: hidden; /* 禁止内容滚动 */
}

.layout-demo :deep(.arco-layout-header) a:hover,
.layout-demo :deep(.arco-layout-sider) a:hover {
  background-color: #F0F0F0;
  color: #333333;
}
</style>
