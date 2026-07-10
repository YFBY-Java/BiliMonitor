<template>
  <section class="page">
    <div>
      <h1 class="page-title">Identity</h1>
      <p class="page-subtitle">同一人跨平台账号聚合的未来模块，MVP 阶段只预留模型和边界。</p>
    </div>

    <el-card shadow="never">
      <el-alert
        title="身份聚合不会在 MVP 自动合并账号"
        type="info"
        description="后续通过 person_profile、platform_identity、identity_link_candidate 和 identity_merge_audit 支持人工确认与审计。"
        show-icon
        :closable="false"
      />
    </el-card>
  </section>
</template>

