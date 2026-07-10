<template>
  <section class="page">
    <div>
      <h1 class="page-title">系统设置</h1>
      <p class="page-subtitle">用户、角色、权限、审计和本地开发配置。</p>
    </div>

    <el-card shadow="never">
      <template #header>配置占位</template>
      <PlaceholderForm />
    </el-card>
  </section>
</template>

<script setup lang="ts">
import PlaceholderForm from '@/components/forms/PlaceholderForm.vue'
</script>

