const CHALLENGE_PATTERNS = [
  /安全验证/u,
  /完成.{0,8}验证/u,
  /captcha/i,
  /verify you are human/i
]

const EXPIRED_PATTERNS = [
  /二维码.{0,8}(过期|失效)/u,
  /重新扫码/u,
  /expired/i
]

const SCANNED_PATTERNS = [
  /已扫码/u,
  /扫码成功/u,
  /请在手机.{0,8}确认/u,
  /scanned/i
]

const VALIDATING_PATTERNS = [
  /正在验证/u,
  /登录中/u,
  /validating/i
]

const AUTHENTICATED_COOKIE_NAMES = new Set([
  'sessionid',
  'sessionid_ss',
  'sid_guard'
])

function matchesAny(value, patterns) {
  return patterns.some(pattern => pattern.test(value))
}

export function classifyPageState({
  title = '',
  text = '',
  authenticated = false,
  qrVisible = false
} = {}) {
  const observableText = `${title}\n${text}`

  if (matchesAny(observableText, CHALLENGE_PATTERNS)) {
    return 'USER_ACTION_REQUIRED'
  }
  if (authenticated) {
    return 'SUCCESS'
  }
  if (matchesAny(observableText, EXPIRED_PATTERNS)) {
    return 'EXPIRED'
  }
  if (matchesAny(observableText, SCANNED_PATTERNS)) {
    return 'SCANNED'
  }
  if (matchesAny(observableText, VALIDATING_PATTERNS)) {
    return 'VALIDATING'
  }
  if (qrVisible) {
    return 'WAITING'
  }
  return 'STARTING'
}

export function hasAuthenticatedCookies(cookies = []) {
  return cookies.some(cookie =>
    AUTHENTICATED_COOKIE_NAMES.has(String(cookie?.name ?? '').toLowerCase()) &&
      typeof cookie?.value === 'string' &&
      cookie.value.length > 0)
}

function cookieAppliesToOrigin(cookie, origin) {
  let url
  try {
    url = new URL(origin)
  } catch {
    return false
  }

  const hostname = url.hostname.toLowerCase()
  const cookieDomain = String(cookie?.domain ?? '').replace(/^\./u, '').toLowerCase()
  if (!cookieDomain || (hostname !== cookieDomain && !hostname.endsWith(`.${cookieDomain}`))) {
    return false
  }
  if (cookie?.secure === true && url.protocol !== 'https:') {
    return false
  }

  const cookiePath = typeof cookie?.path === 'string' && cookie.path.length > 0
    ? cookie.path
    : '/'
  const requestPath = url.pathname || '/'
  return requestPath.startsWith(cookiePath) || cookiePath === '/'
}

export function buildCookieHeadersByOrigin(cookies = [], origins = []) {
  return Object.fromEntries(origins.map(origin => {
    const header = cookies
      .filter(cookie => cookieAppliesToOrigin(cookie, origin))
      .map(cookie => `${cookie.name}=${cookie.value}`)
      .join('; ')
    return [origin, header]
  }))
}
