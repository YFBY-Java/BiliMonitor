function parseInteger(env, name, fallback, { min = 1, max = Number.MAX_SAFE_INTEGER } = {}) {
  const raw = env[name]
  if (raw === undefined || raw === '') {
    return fallback
  }
  const value = Number(raw)
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${name} must be an integer between ${min} and ${max}`)
  }
  return value
}

function parseBoolean(env, name, fallback) {
  const raw = env[name]
  if (raw === undefined || raw === '') {
    return fallback
  }
  if (/^(true|1|yes|on)$/iu.test(raw)) {
    return true
  }
  if (/^(false|0|no|off)$/iu.test(raw)) {
    return false
  }
  throw new Error(`${name} must be true or false`)
}

function parseUrl(env, name, fallback) {
  const value = env[name] || fallback
  try {
    return new URL(value).toString()
  } catch {
    throw new Error(`${name} must be an absolute URL`)
  }
}

function parseOrigins(env, name, fallback) {
  const raw = env[name]
  const values = raw
    ? raw.split(',').map(value => value.trim()).filter(Boolean)
    : fallback
  return values.map(value => {
    try {
      return new URL(value).origin
    } catch {
      throw new Error(`${name} contains an invalid origin: ${value}`)
    }
  })
}

export function loadWorkerConfig(env = process.env) {
  const viewport = {
    width: parseInteger(env, 'DOUYIN_WORKER_VIEWPORT_WIDTH', 1440, { min: 320, max: 7680 }),
    height: parseInteger(env, 'DOUYIN_WORKER_VIEWPORT_HEIGHT', 900, { min: 240, max: 4320 })
  }

  return Object.freeze({
    host: env.DOUYIN_WORKER_HOST || '127.0.0.1',
    port: parseInteger(env, 'DOUYIN_WORKER_PORT', 8787, { max: 65_535 }),
    token: env.SOCIAL_MONITOR_DOUYIN_WORKER_TOKEN || '',
    headless: parseBoolean(env, 'DOUYIN_WORKER_HEADLESS', true),
    loginUrl: parseUrl(env, 'DOUYIN_WORKER_LOGIN_URL', 'https://www.douyin.com/'),
    validationUrl: parseUrl(
      env,
      'DOUYIN_WORKER_VALIDATION_URL',
      'https://www.douyin.com/user/self?from_tab_name=main'
    ),
    navigationTimeoutMs: parseInteger(env, 'DOUYIN_WORKER_NAVIGATION_TIMEOUT_MS', 30_000, { min: 1_000 }),
    qrWaitMs: parseInteger(env, 'DOUYIN_WORKER_QR_WAIT_MS', 15_000, { min: 0 }),
    sessionTtlSeconds: parseInteger(env, 'DOUYIN_WORKER_SESSION_TTL_SECONDS', 180, { min: 30, max: 1_800 }),
    cleanupIntervalMs: parseInteger(env, 'DOUYIN_WORKER_CLEANUP_INTERVAL_MS', 60_000, { min: 1_000 }),
    locale: env.DOUYIN_WORKER_LOCALE || 'zh-CN',
    timezoneId: env.DOUYIN_WORKER_TIMEZONE_ID || 'Asia/Shanghai',
    viewport: Object.freeze(viewport),
    userAgent: env.DOUYIN_WORKER_USER_AGENT || undefined,
    cookieHeaderOrigins: Object.freeze(parseOrigins(
      env,
      'DOUYIN_WORKER_COOKIE_HEADER_ORIGINS',
      ['https://www.douyin.com', 'https://open.douyin.com']
    ))
  })
}
