import {
  buildCookieHeadersByOrigin,
  classifyPageState,
  hasAuthenticatedCookies
} from './page-state.js'
import { WorkerError } from './session-manager.js'

const QR_SELECTORS = Object.freeze([
  '#animate_qrcode_container img',
  '[class*="qrcode" i] canvas',
  '[class*="qr-code" i] canvas',
  '[class*="qrcode" i] img',
  '[class*="qr-code" i] img',
  'img[alt*="二维码"]',
  'img[src*="qrcode" i]',
  '[class*="scan" i] canvas'
])

const LOGIN_TRIGGER_SELECTORS = Object.freeze([
  '[data-e2e="login-button"]',
  '[class*="login-button" i]',
  'button:has-text("登录")'
])

const QR_TAB_SELECTORS = Object.freeze([
  '[role="tab"]:has-text("扫码")',
  'button:has-text("扫码登录")',
  'text=扫码登录'
])

const AUTHENTICATED_MARKER_SELECTORS = Object.freeze([
  '[data-douyin-authenticated="true"]',
  '[data-e2e="user-name"]',
  '[data-e2e="user-nickname"]',
  '[data-e2e="user-avatar"]',
  '[data-e2e="logout"]'
])

const STATE_MESSAGES = Object.freeze({
  STARTING: 'The Douyin login page is starting',
  WAITING: 'Scan the QR code with Douyin',
  SCANNED: 'QR code scanned; confirm the login on the phone',
  VALIDATING: 'Douyin is validating the login',
  EXPIRED: 'The Douyin QR code has expired',
  USER_ACTION_REQUIRED: 'Complete the visible verification in the headed browser, then poll again',
  SUCCESS: 'Douyin browser login state captured'
})

function dateToIso(now) {
  const value = now()
  return (value instanceof Date ? value : new Date(value)).toISOString()
}

async function frameSessionStorage(page) {
  const results = await Promise.all(page.frames().map(async frame => {
    try {
      return await frame.evaluate(() => ({
        origin: window.location.origin,
        sessionStorage: Object.entries(window.sessionStorage).map(([name, value]) => ({ name, value }))
      }))
    } catch {
      return undefined
    }
  }))

  const byOrigin = new Map()
  for (const result of results.filter(Boolean)) {
    if (!result.origin || result.origin === 'null') {
      continue
    }
    const current = byOrigin.get(result.origin) ?? []
    const values = new Map(current.map(item => [item.name, item]))
    for (const item of result.sessionStorage ?? []) {
      values.set(item.name, item)
    }
    byOrigin.set(result.origin, [...values.values()])
  }
  return byOrigin
}

async function captureBrowserContext(page) {
  try {
    return await page.evaluate(() => ({
      userAgent: window.navigator.userAgent,
      secChUa: window.navigator.userAgentData?.brands
        ?.map(brand => `"${brand.brand}";v="${brand.version}"`)
        .join(', ') ?? '',
      platform: window.navigator.userAgentData?.platform ?? window.navigator.platform
    }))
  } catch {
    return { userAgent: '', secChUa: '', platform: '' }
  }
}

function mergeOrigins(storageState, sessionStorageByOrigin) {
  const origins = new Map()
  for (const state of storageState?.origins ?? []) {
    origins.set(state.origin, {
      ...state,
      localStorage: state.localStorage ?? [],
      sessionStorage: sessionStorageByOrigin.get(state.origin) ?? [],
      indexedDb: state.indexedDB ?? state.indexedDb ?? []
    })
  }
  for (const [origin, sessionStorage] of sessionStorageByOrigin) {
    if (!origins.has(origin)) {
      origins.set(origin, { origin, localStorage: [], sessionStorage, indexedDb: [] })
    }
  }
  return [...origins.values()]
}

function safeOrigin(value) {
  try {
    return new URL(value).origin
  } catch {
    return undefined
  }
}

export async function captureSessionBundle({
  context,
  page,
  config,
  workerSessionId,
  account = {},
  rawWorkerResult = {},
  now = () => new Date()
}) {
  const cookies = await context.cookies()
  const storageState = await context.storageState({ indexedDB: true })
  const sessionStorageByOrigin = await frameSessionStorage(page)
  const origins = mergeOrigins(storageState, sessionStorageByOrigin)
  const capturedBrowser = await captureBrowserContext(page)
  const currentUrl = page.url()
  const title = await page.title().catch(() => '')
  const headerOrigins = [...new Set([
    ...(config.cookieHeaderOrigins ?? []),
    ...origins.map(item => item.origin),
    safeOrigin(currentUrl)
  ].filter(Boolean))]
  const capturedAt = dateToIso(now)

  return {
    version: 1,
    authType: 'DOUYIN_WEB_SESSION',
    source: 'WEB_QRCODE',
    account,
    cookies,
    cookieHeadersByOrigin: buildCookieHeadersByOrigin(cookies, headerOrigins),
    origins,
    storageState,
    browserContext: {
      ...capturedBrowser,
      locale: config.locale,
      timezoneId: config.timezoneId,
      viewport: config.viewport
    },
    rawWorkerResult: {
      ...rawWorkerResult,
      workerSessionId,
      page: { url: currentUrl, title }
    },
    capturedAt,
    lastValidatedAt: null
  }
}

async function bodyText(page) {
  try {
    return await page.locator('body').innerText({ timeout: 2_000 })
  } catch {
    return ''
  }
}

async function pageObservation(page, context) {
  const [title, text, cookies, qrLocator] = await Promise.all([
    page.title().catch(() => ''),
    bodyText(page),
    context.cookies().catch(() => []),
    findVisibleQrLocator(page)
  ])
  const state = classifyPageState({
    title,
    text,
    authenticated: hasAuthenticatedCookies(cookies),
    qrVisible: Boolean(qrLocator)
  })
  return { state, title, text, cookies, qrLocator, url: page.url() }
}

async function firstVisibleLocator(container, selector) {
  const matches = container.locator(selector)
  const count = Math.min(await matches.count().catch(() => 0), 20)
  for (let index = 0; index < count; index += 1) {
    const candidate = matches.nth(index)
    if (!await candidate.isVisible().catch(() => false)) {
      continue
    }
    const box = await candidate.boundingBox().catch(() => undefined)
    if (!box || box.width < 100 || box.height < 100) {
      continue
    }
    const ratio = box.width / box.height
    if (ratio >= 0.65 && ratio <= 1.35) {
      return candidate
    }
  }
  return undefined
}

export async function findVisibleQrLocator(page) {
  const containers = [page, ...page.frames().filter(frame => frame !== page.mainFrame?.())]
  for (const container of containers) {
    for (const selector of QR_SELECTORS) {
      const candidate = await firstVisibleLocator(container, selector)
      if (candidate) {
        return candidate
      }
    }
  }
  return undefined
}

async function clickFirstVisible(page, selectors) {
  for (const selector of selectors) {
    const locator = page.locator(selector).first()
    if (await locator.isVisible({ timeout: 500 }).catch(() => false)) {
      await locator.click({ timeout: 2_000 }).catch(() => undefined)
      return true
    }
  }
  return false
}

async function accountHints(page) {
  const selectors = [
    '[data-e2e="user-name"]',
    '[data-e2e="user-nickname"]',
    '[class*="nickname" i]'
  ]
  for (const selector of selectors) {
    const locator = page.locator(selector).first()
    if (await locator.isVisible({ timeout: 250 }).catch(() => false)) {
      const nickname = await locator.innerText().catch(() => '')
      if (nickname) {
        return { nickname, sourceSelector: selector }
      }
    }
  }
  return {}
}

function contextOptions(config, storageState) {
  return {
    ...(storageState ? { storageState } : {}),
    locale: config.locale,
    timezoneId: config.timezoneId,
    viewport: config.viewport,
    ...(config.userAgent ? { userAgent: config.userAgent } : {})
  }
}

function sessionStoragePayload(bundle) {
  return (bundle.origins ?? [])
    .filter(origin => Array.isArray(origin.sessionStorage) && origin.sessionStorage.length > 0)
    .map(origin => ({ origin: origin.origin, values: origin.sessionStorage }))
}

function restoreSessionStorage(payload) {
  const match = payload.find(item => item.origin === window.location.origin)
  for (const item of match?.values ?? []) {
    window.sessionStorage.setItem(item.name, item.value)
  }
}

function validationPageLooksLoggedOut(url, text) {
  const normalizedUrl = String(url).toLowerCase()
  return normalizedUrl.includes('passport.douyin.com') ||
    normalizedUrl.includes('/login') ||
    /登录后/u.test(text)
}

async function firstVisibleSelector(page, selectors) {
  for (const selector of selectors) {
    const locator = page.locator(selector).first()
    if (await locator.isVisible({ timeout: 250 }).catch(() => false)) {
      return selector
    }
  }
  return undefined
}

export class PlaywrightDouyinLoginSession {
  constructor({ context, page, config, workerSessionId, now = () => new Date() }) {
    this.context = context
    this.page = page
    this.config = config
    this.workerSessionId = workerSessionId
    this.now = now
    this.closed = false
  }

  async prepare() {
    this.page.setDefaultTimeout?.(this.config.navigationTimeoutMs)
    await this.page.goto(this.config.loginUrl, {
      waitUntil: 'domcontentloaded',
      timeout: this.config.navigationTimeoutMs
    })

    const initial = await pageObservation(this.page, this.context)
    if (initial.state === 'USER_ACTION_REQUIRED' || initial.state === 'SUCCESS') {
      return
    }
    if (!initial.qrLocator) {
      await clickFirstVisible(this.page, LOGIN_TRIGGER_SELECTORS)
      await clickFirstVisible(this.page, QR_TAB_SELECTORS)
    }

    const deadline = Date.now() + this.config.qrWaitMs
    while (Date.now() < deadline) {
      const observation = await pageObservation(this.page, this.context)
      if (observation.qrLocator || observation.state === 'USER_ACTION_REQUIRED' || observation.state === 'SUCCESS') {
        return
      }
      await this.page.waitForTimeout(250)
    }
  }

  async qr() {
    const observation = await pageObservation(this.page, this.context)
    if (observation.state === 'USER_ACTION_REQUIRED') {
      throw new WorkerError('USER_ACTION_REQUIRED', STATE_MESSAGES.USER_ACTION_REQUIRED, 409)
    }
    if (!observation.qrLocator) {
      throw new WorkerError('QR_NOT_AVAILABLE', 'The Douyin QR code is not currently available', 409)
    }
    return {
      bytes: await observation.qrLocator.screenshot({ type: 'png' }),
      contentType: 'image/png'
    }
  }

  async status() {
    const observation = await pageObservation(this.page, this.context)
    if (observation.state !== 'SUCCESS') {
      return {
        status: observation.state,
        message: STATE_MESSAGES[observation.state] ?? STATE_MESSAGES.STARTING,
        qrAvailable: Boolean(observation.qrLocator)
      }
    }

    const account = await accountHints(this.page)
    const bundle = await captureSessionBundle({
      context: this.context,
      page: this.page,
      config: this.config,
      workerSessionId: this.workerSessionId,
      account,
      rawWorkerResult: {
        detection: {
          state: observation.state,
          url: observation.url,
          title: observation.title,
          cookieCount: observation.cookies.length
        }
      },
      now: this.now
    })
    return { status: 'SUCCESS', message: STATE_MESSAGES.SUCCESS, bundle, account }
  }

  async close() {
    if (!this.closed) {
      this.closed = true
      await this.context.close()
    }
  }
}

export class PlaywrightDouyinDriver {
  constructor({ browserType, config, now = () => new Date() }) {
    this.browserType = browserType
    this.config = config
    this.now = now
    this.browserPromise = undefined
  }

  async #browser() {
    if (!this.browserPromise) {
      this.browserPromise = (async () => {
        const browserType = this.browserType ?? (await import('playwright')).chromium
        return browserType.launch({ headless: this.config.headless })
      })()
    }
    return this.browserPromise
  }

  async createSession({ workerSessionId }) {
    const browser = await this.#browser()
    const context = await browser.newContext(contextOptions(this.config))
    try {
      const page = await context.newPage()
      const session = new PlaywrightDouyinLoginSession({
        context,
        page,
        config: this.config,
        workerSessionId,
        now: this.now
      })
      await session.prepare()
      return session
    } catch (error) {
      await context.close()
      throw error
    }
  }

  async validateBundle(bundle) {
    const browser = await this.#browser()
    const context = await browser.newContext(contextOptions(this.config, bundle.storageState))
    const validatedAt = dateToIso(this.now)
    try {
      const storagePayload = sessionStoragePayload(bundle)
      if (storagePayload.length > 0) {
        await context.addInitScript(restoreSessionStorage, storagePayload)
      }
      const page = await context.newPage()
      await page.goto(this.config.validationUrl, {
        waitUntil: 'domcontentloaded',
        timeout: this.config.navigationTimeoutMs
      })
      await page.waitForLoadState?.('networkidle', {
        timeout: Math.min(this.config.navigationTimeoutMs, 5_000)
      }).catch(() => undefined)
      const [title, text, cookies, identityMarker, loginMarker, qrLocator] = await Promise.all([
        page.title().catch(() => ''),
        bodyText(page),
        context.cookies().catch(() => []),
        firstVisibleSelector(page, AUTHENTICATED_MARKER_SELECTORS),
        firstVisibleSelector(page, LOGIN_TRIGGER_SELECTORS),
        findVisibleQrLocator(page).catch(() => undefined)
      ])
      const authenticatedCookiePresent = hasAuthenticatedCookies(cookies)
      const pageState = classifyPageState({
        title,
        text,
        authenticated: authenticatedCookiePresent
      })
      const qrVisible = Boolean(qrLocator)
      const loggedOut = validationPageLooksLoggedOut(page.url(), text) || Boolean(loginMarker) || qrVisible
      const valid = pageState === 'SUCCESS' && Boolean(identityMarker) && !loggedOut
      const details = {
        status: valid ? 'SUCCESS' : loggedOut ? 'LOGGED_OUT' : 'IDENTITY_NOT_CONFIRMED',
        url: page.url(),
        title,
        cookieCount: cookies.length,
        authenticatedCookiePresent,
        identityMarker: identityMarker ?? null,
        loginMarker: loginMarker ?? null,
        qrVisible,
        validatedAt
      }
      return {
        valid,
        message: valid ? 'Saved Douyin browser state is reusable' : 'Saved Douyin browser state is not reusable',
        details,
        bundle: {
          ...bundle,
          lastValidatedAt: validatedAt,
          rawWorkerResult: {
            ...(bundle.rawWorkerResult ?? {}),
            validation: details
          }
        }
      }
    } catch (error) {
      return {
        valid: false,
        message: 'Saved Douyin browser state validation failed',
        details: {
          status: 'FAILED',
          errorName: error?.name ?? 'Error',
          errorMessage: error?.message ?? String(error),
          validatedAt
        },
        bundle
      }
    } finally {
      await context.close()
    }
  }

  async close() {
    if (this.browserPromise) {
      const browser = await this.browserPromise.catch(() => undefined)
      await browser?.close()
      this.browserPromise = undefined
    }
  }
}
