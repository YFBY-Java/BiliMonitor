CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    resource_type VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES sys_user(id),
    action VARCHAR(120) NOT NULL,
    target_type VARCHAR(120),
    target_id VARCHAR(160),
    ip VARCHAR(80),
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS platform (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS platform_capability (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    capability_code VARCHAR(120) NOT NULL,
    data_type VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, capability_code, data_type)
);

CREATE TABLE IF NOT EXISTS platform_credential (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    auth_type VARCHAR(80) NOT NULL,
    encrypted_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at TIMESTAMPTZ,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS platform_account (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    credential_id BIGINT REFERENCES platform_credential(id),
    external_account_id VARCHAR(160) NOT NULL,
    display_name VARCHAR(160),
    profile_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_sync_at TIMESTAMPTZ,
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, external_account_id)
);

CREATE TABLE IF NOT EXISTS platform_rate_limit_state (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    account_id BIGINT REFERENCES platform_account(id) ON DELETE CASCADE,
    endpoint_key VARCHAR(160) NOT NULL,
    next_allowed_at TIMESTAMPTZ,
    state_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, account_id, endpoint_key)
);

CREATE TABLE IF NOT EXISTS collect_task (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    account_id BIGINT REFERENCES platform_account(id) ON DELETE CASCADE,
    data_type VARCHAR(80) NOT NULL,
    schedule_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    cron VARCHAR(120),
    interval_seconds INTEGER,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 0,
    next_run_at TIMESTAMPTZ,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_collect_task_status_next_run
    ON collect_task (status, next_run_at);

CREATE TABLE IF NOT EXISTS collect_task_instance (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES collect_task(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    attempt INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    error_message TEXT,
    metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_collect_task_instance_task_status
    ON collect_task_instance (task_id, status, started_at);

CREATE TABLE IF NOT EXISTS task_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES collect_task(id) ON DELETE CASCADE,
    checkpoint_key VARCHAR(160) NOT NULL,
    cursor_value TEXT,
    last_success_at TIMESTAMPTZ,
    state_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (task_id, checkpoint_key)
);

CREATE TABLE IF NOT EXISTS api_call_log (
    id BIGSERIAL PRIMARY KEY,
    task_instance_id BIGINT REFERENCES collect_task_instance(id) ON DELETE SET NULL,
    platform_id BIGINT REFERENCES platform(id) ON DELETE SET NULL,
    endpoint_key VARCHAR(160) NOT NULL,
    status_code INTEGER,
    duration_ms INTEGER,
    error_type VARCHAR(80),
    retryable BOOLEAN NOT NULL DEFAULT false,
    request_meta_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_meta_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_api_call_log_platform_created
    ON api_call_log (platform_id, created_at);

CREATE TABLE IF NOT EXISTS raw_payload (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    account_id BIGINT REFERENCES platform_account(id) ON DELETE SET NULL,
    data_type VARCHAR(80) NOT NULL,
    external_id VARCHAR(200),
    payload_json JSONB NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    task_instance_id BIGINT REFERENCES collect_task_instance(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_raw_payload_dedup
    ON raw_payload (platform_id, data_type, (COALESCE(external_id, '')), payload_hash);

CREATE TABLE IF NOT EXISTS social_account (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    external_id VARCHAR(160) NOT NULL,
    name VARCHAR(160),
    avatar_url TEXT,
    profile_url TEXT,
    verified BOOLEAN NOT NULL DEFAULT false,
    follower_count BIGINT,
    following_count BIGINT,
    raw_payload_id BIGINT REFERENCES raw_payload(id) ON DELETE SET NULL,
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, external_id)
);

CREATE TABLE IF NOT EXISTS social_content (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    external_id VARCHAR(200) NOT NULL,
    author_account_id BIGINT REFERENCES social_account(id) ON DELETE SET NULL,
    content_type VARCHAR(80) NOT NULL,
    title VARCHAR(300),
    content_text TEXT,
    url TEXT,
    published_at TIMESTAMPTZ,
    metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    raw_payload_id BIGINT REFERENCES raw_payload(id) ON DELETE SET NULL,
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, external_id)
);

CREATE INDEX IF NOT EXISTS idx_social_content_published
    ON social_content (platform_id, published_at);

CREATE TABLE IF NOT EXISTS social_comment (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    external_id VARCHAR(200) NOT NULL,
    content_id BIGINT REFERENCES social_content(id) ON DELETE CASCADE,
    author_external_id VARCHAR(200),
    parent_external_id VARCHAR(200),
    comment_text TEXT,
    published_at TIMESTAMPTZ,
    metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    raw_payload_id BIGINT REFERENCES raw_payload(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, external_id)
);

CREATE TABLE IF NOT EXISTS social_danmaku (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    content_id BIGINT REFERENCES social_content(id) ON DELETE CASCADE,
    external_id VARCHAR(200),
    danmaku_text TEXT NOT NULL,
    video_time_ms INTEGER,
    mode VARCHAR(40),
    color VARCHAR(40),
    sent_at TIMESTAMPTZ,
    raw_payload_id BIGINT REFERENCES raw_payload(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_social_danmaku_content_time
    ON social_danmaku (content_id, video_time_ms);

CREATE TABLE IF NOT EXISTS social_interaction (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,
    interaction_type VARCHAR(80) NOT NULL,
    count_value BIGINT NOT NULL DEFAULT 0,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_social_interaction_entity
    ON social_interaction (entity_type, entity_id, interaction_type, captured_at);

CREATE TABLE IF NOT EXISTS social_metric_snapshot (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,
    metric_key VARCHAR(120) NOT NULL,
    metric_value NUMERIC(20, 4) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_metric_snapshot_lookup
    ON social_metric_snapshot (entity_type, entity_id, metric_key, captured_at);

CREATE TABLE IF NOT EXISTS metric_hourly_summary (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,
    metric_key VARCHAR(120) NOT NULL,
    summary_hour TIMESTAMPTZ NOT NULL,
    value NUMERIC(20, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (entity_type, entity_id, metric_key, summary_hour)
);

CREATE TABLE IF NOT EXISTS metric_daily_summary (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,
    metric_key VARCHAR(120) NOT NULL,
    summary_date DATE NOT NULL,
    value NUMERIC(20, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (entity_type, entity_id, metric_key, summary_date)
);

CREATE TABLE IF NOT EXISTS trend_topic (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    keyword VARCHAR(240) NOT NULL,
    rank_value INTEGER,
    score NUMERIC(20, 4),
    region VARCHAR(80),
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_trend_topic_platform_time
    ON trend_topic (platform_id, captured_at, rank_value);

CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL,
    version VARCHAR(40) NOT NULL,
    template_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

CREATE TABLE IF NOT EXISTS ai_job (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(160) NOT NULL,
    capability VARCHAR(120) NOT NULL,
    provider VARCHAR(80),
    model VARCHAR(120),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    prompt_template_id BIGINT REFERENCES prompt_template(id) ON DELETE SET NULL,
    input_hash VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_job_status_created
    ON ai_job (status, created_at);

CREATE TABLE IF NOT EXISTS ai_result (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES ai_job(id) ON DELETE CASCADE,
    summary TEXT,
    sentiment VARCHAR(80),
    labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    score_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_version VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS person_profile (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    remark TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS platform_identity (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT REFERENCES person_profile(id) ON DELETE SET NULL,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    platform_account_id BIGINT REFERENCES platform_account(id) ON DELETE SET NULL,
    external_id VARCHAR(200) NOT NULL,
    confidence NUMERIC(6, 4),
    source VARCHAR(80) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, external_id)
);

CREATE TABLE IF NOT EXISTS identity_link_candidate (
    id BIGSERIAL PRIMARY KEY,
    left_identity_id BIGINT NOT NULL REFERENCES platform_identity(id) ON DELETE CASCADE,
    right_identity_id BIGINT NOT NULL REFERENCES platform_identity(id) ON DELETE CASCADE,
    score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    reason_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (left_identity_id, right_identity_id)
);

CREATE TABLE IF NOT EXISTS identity_merge_audit (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT REFERENCES sys_user(id),
    action VARCHAR(80) NOT NULL,
    before_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    after_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO platform (code, name, status)
VALUES ('bilibili', 'Bilibili', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;
