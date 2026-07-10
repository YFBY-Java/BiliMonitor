<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">平台管理</h1>
        <p class="page-subtitle">平台 Adapter、能力矩阵、凭证和风险状态入口。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新 Adapter</el-button>
    </div>

    <el-card shadow="never">
      <template #header>已注册 Adapter</template>
      <el-table :data="adapters" border>
        <el-table-column prop="platformCode" label="平台" width="160" />
        <el-table-column label="能力">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag v-for="capability in row.capabilities" :key="capability" size="small">{{ capability }}</el-tag>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchPlatformAdapters, type PlatformAdapterView } from '@/api/platform'

const loading = ref(false)
const adapters = ref<PlatformAdapterView[]>([])

async function load() {
  loading.value = true
  try {
    adapters.value = await fetchPlatformAdapters()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

