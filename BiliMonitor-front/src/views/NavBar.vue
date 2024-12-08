<template>
  <!-- 整体菜单演示容器 -->
  <div class="menu-demo">
    <!-- 水平模式的菜单组件 -->
    <a-menu
        mode="horizontal"
        :default-selected-keys="['1']"
        class="menu"
    >
      <!-- 第一个菜单项，禁用状态，用于占位等特殊用途 -->
      <a-menu-item key="0" :style="{ padding: 0 }" disabled>
        <div
            :style="{
            width: '80px',
            height: '30px',
            borderRadius: '8px',
            background: 'var(--color-fill-3)',
            cursor: 'text',
            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)',
          }"
        />
      </a-menu-item>
      <!-- 首页菜单项，点击可导航到根路径 -->
      <a-menu-item key="/" @click="navTo('/')">首页</a-menu-item>
      <!-- 粉丝数监控菜单项，点击导航到对应路径 -->
      <a-menu-item key="2" @click="navTo('/fans')">B站粉丝数监控</a-menu-item>
      <!-- 待开发的菜单项，处于禁用状态 -->
      <a-menu-item key="3" disabled>待开发</a-menu-item>
    </a-menu>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';
import { ref } from 'vue';

// 获取 Vue Router 实例
const router = useRouter();

// 定义导航函数，用于跳转到指定的 URL
const navTo = (url) => {
  router.push(url);
};

// 用于控制菜单项选中状态（示例中仅以首页为例）
const isActive = ref(false);
const handleClick = () => {
  isActive.value = true;
};
</script>

<style scoped>
/* 整体菜单演示容器的样式 */
.menu-demo {
  width: 100%;
  background-color: #a089bb;
  font-family: 'Roboto', sans-serif;
  transition: background-color 0.3s ease-in-out;
}

/* 菜单的通用样式设置 */
.menu {
  background: #FFFFFF;
  border: 1px solid rgba(160, 140, 200, 0.4);
  box-shadow: 0 8px 20px rgba(180, 160, 220, 0.3);
  border-radius: 12px;
  padding: 0 20px;
  transition: box-shadow 0.3s ease-in-out;
}

/* 菜单项的通用样式 */
.a-menu-item {
  font-size: 16px;
  font-weight: 500;
  padding: 0 20px;
  transition: all 0.3s ease-in-out;
  color: #888888;
  line-height: 1.5;
  text-shadow: 0 1px 2px rgba(192, 147, 228, 0.8);
}

/* 菜单项鼠标悬停时的样式，增加交互效果 */
.a-menu-item:hover {
  background-color: rgba(200, 180, 250, 0.3);
  color: #B299FF;
  border-radius: 8px;
  transform: scale(1.05);
  box-shadow: 0 0 8px rgba(200, 180, 250, 0.3), 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 菜单项被激活（按下）时的样式 */
.a-menu-item:active {
  background-color: rgba(200, 180, 250, 0.4);
  color: #9977FF;
  transform: scale(0.98);
  box-shadow: 0 0 4px rgba(200, 180, 250, 0.3), 0 1px 2px rgba(0, 0, 0, 0.1);
  opacity: 0.9;
}

/* 禁用状态的菜单项样式 */
.a-menu-item[disabled] {
  color: #DDDDDD;
  cursor: not-allowed;
  transition: color 0.3s ease-in-out;
}

.a-menu-item[disabled]:hover {
  background-color: transparent;
  color: #CCCCCC;
}

/* 第一个菜单项的左边距设置为0，去除默认间距 */
.a-menu-item:first-child {
  margin-left: 0;
}

/* 最后一个菜单项的右边距设置为0，去除默认间距 */
.a-menu-item:last-child {
  margin-right: 0;
}

/* 除第一个菜单项外，其他菜单项的左边距设置为16px，增加间隔 */
.a-menu-item:not(:first-child) {
  margin-left: 16px;
}

/* 用于控制菜单项选中状态的类名样式（示例中仅以首页为例） */
.active-item {
  background-color: rgba(200, 180, 250, 0.4);
}
</style>