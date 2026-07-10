<template>
  <el-tag round>{{ name }}</el-tag>
</template>

<script setup lang="ts">
defineProps<{ name: string }>()
</script>

