CREATE UNIQUE INDEX IF NOT EXISTS ux_platform_credential_bilibili_web_active
    ON platform_credential (platform_id, auth_type)
    WHERE auth_type = 'BILIBILI_WEB_COOKIE'
      AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_platform_credential_platform_auth_status
    ON platform_credential (platform_id, auth_type, status);
