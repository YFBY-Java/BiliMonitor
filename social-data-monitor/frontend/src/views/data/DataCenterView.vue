<template>
  <section class="page">
    <div>
      <h1 class="page-title">数据中心</h1>
      <p class="page-subtitle">统一账号、内容、评论、弹幕、趋势和指标快照。</p>
    </div>

    <el-card shadow="never">
      <el-tabs model-value="content">
        <el-tab-pane label="内容" name="content"><PlaceholderTable /></el-tab-pane>
        <el-tab-pane label="评论" name="comment"><PlaceholderTable /></el-tab-pane>
        <el-tab-pane label="弹幕" name="danmaku"><PlaceholderTable /></el-tab-pane>
        <el-tab-pane label="趋势" name="trend"><PlaceholderTable /></el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import PlaceholderTable from '@/components/tables/PlaceholderTable.vue'
</script>

