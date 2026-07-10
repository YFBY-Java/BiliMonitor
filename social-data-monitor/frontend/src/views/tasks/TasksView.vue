<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">采集任务</h1>
        <p class="page-subtitle">任务定义、执行实例、限流、重试和 checkpoint 的管理入口。</p>
      </div>
      <el-button type="primary">创建任务</el-button>
    </div>

    <el-card shadow="never">
      <template #header>任务列表 Mock</template>
      <el-table :data="tasks" border>
        <el-table-column prop="name" label="任务" />
        <el-table-column prop="platform" label="平台" width="140" />
        <el-table-column prop="dataType" label="数据类型" width="160" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import StatusTag from '@/components/status/StatusTag.vue'

const tasks = [
  { name: 'UP 主账号资料同步', platform: 'bilibili', dataType: 'account', status: 'PAUSED' },
  { name: '视频指标快照同步', platform: 'bilibili', dataType: 'interactions', status: 'PAUSED' }
]
</script>

