<script setup>
</script>

<template>
  <div class="home">
    <h1>
      首页
    </h1>
  </div>
</template>

<style scoped>
/* 给页面设置背景图 */
.home {
  background-image: url('@/assets/image/圣痕.png');
  background-size: contain; /* 让背景图适应屏幕大小，保持比例 */
  background-position: center; /* 背景图居中 */
  background-repeat: no-repeat; /* 防止背景图重复 */
  height: 100vh; /* 背景图覆盖整个视口 */
  display: flex;
  justify-content: center; /* 居中对齐内容 */
  align-items: center; /* 垂直居中 */
}
</style>
