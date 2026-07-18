INSERT INTO platform (code, name, status)
VALUES ('douyin', '抖音', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS douyin_auth_session (
    login_id UUID PRIMARY KEY,
    flow_type VARCHAR(32) NOT NULL,
    provider_mode VARCHAR(32) NOT NULL,
    worker_session_id VARCHAR(160),
    state TEXT,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    error_message TEXT,
    raw_result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (flow_type IN ('OAUTH_LOGIN', 'OAUTH_REFRESH', 'WEB_QR')),
    CHECK (provider_mode IN ('disabled', 'mock', 'live')),
    CHECK (status IN (
        'STARTING', 'WAITING', 'SCANNED', 'VALIDATING', 'SUCCESS',
        'EXPIRED', 'USER_ACTION_REQUIRED', 'FAILED'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_douyin_auth_session_state
    ON douyin_auth_session (state)
    WHERE state IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_douyin_auth_session_worker_session
    ON douyin_auth_session (worker_session_id)
    WHERE worker_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_douyin_auth_session_status_expires
    ON douyin_auth_session (status, expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_platform_credential_douyin_active
    ON platform_credential (platform_id, auth_type)
    WHERE auth_type IN ('DOUYIN_OAUTH2', 'DOUYIN_WEB_SESSION')
      AND status = 'ACTIVE';
