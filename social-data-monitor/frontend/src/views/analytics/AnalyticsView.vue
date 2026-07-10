<template>
  <section class="page">
    <div>
      <h1 class="page-title">分析看板</h1>
      <p class="page-subtitle">指标快照、小时汇总、日汇总和内容 TopN 的展示区域。</p>
    </div>

    <div class="metric-grid">
      <el-card class="metric-card" shadow="never"><div class="metric-label">快照指标</div><div class="metric-value">0</div></el-card>
      <el-card class="metric-card" shadow="never"><div class="metric-label">小时汇总</div><div class="metric-value">0</div></el-card>
      <el-card class="metric-card" shadow="never"><div class="metric-label">日汇总</div><div class="metric-value">0</div></el-card>
      <el-card class="metric-card" shadow="never"><div class="metric-label">告警事件</div><div class="metric-value">0</div></el-card>
    </div>
  </section>
</template>

