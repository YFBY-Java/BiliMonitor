<template>
  <el-tag :type="type" effect="light">{{ label }}</el-tag>
</template>

<script setup lang="ts">
const props = defineProps<{ status: string }>()

const type = props.status === 'ACTIVE' || props.status === 'SUCCESS'
  ? 'success'
  : props.status === 'PAUSED'
    ? 'warning'
    : 'info'

const label = props.status
</script>

