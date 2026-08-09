import test from 'node:test'
import assert from 'node:assert/strict'
import {
  PlaywrightDouyinDriver,
  captureSessionBundle,
  findVisibleQrLocator
} from '../src/playwright-driver.js'

test('finds the visible QR image in the current Douyin animation container', async () => {
  const qrCandidate = {
    isVisible: async () => true,
    boundingBox: async () => ({ width: 180, height: 180 })
  }
  const emptyMatches = {
    count: async () => 0,
    nth: () => undefined
  }
  const page = {
    frames: () => [],
    locator: selector => selector === '#animate_qrcode_container img'
      ? { count: async () => 1, nth: () => qrCandidate }
      : emptyMatches
  }

  const result = await findVisibleQrLocator(page)

  assert.strictEqual(result, qrCandidate)
})

test('captures cookies and every supported browser storage field without a name whitelist', async () => {
  const cookie = {
    name: 'future_cookie',
    value: 'raw-value',
    domain: '.douyin.com',
    path: '/',
    expires: 2_000_000_000,
    httpOnly: true,
    secure: true,
    sameSite: 'None',
    partitionKey: 'future-partition'
  }
  const storageState = {
    cookies: [cookie],
    origins: [{
      origin: 'https://www.douyin.com',
      localStorage: [{ name: 'local-key', value: 'local-value' }],
      indexedDB: [{ name: 'db', version: 1, stores: [{ name: 'records', records: [{ value: 'raw' }] }] }]
    }]
  }
  let storageOptions
  const context = {
    cookies: async () => [cookie],
    storageState: async options => {
      storageOptions = options
      return storageState
    }
  }
  const page = {
    url: () => 'https://www.douyin.com/user/self',
    title: async () => '抖音',
    frames: () => [{
      evaluate: async () => ({
        origin: 'https://www.douyin.com',
        sessionStorage: [{ name: 'session-key', value: 'session-value' }]
      })
    }],
    evaluate: async () => ({
      userAgent: 'captured-agent',
      secChUa: 'captured-hint',
      platform: 'Win32'
    })
  }

  const bundle = await captureSessionBundle({
    context,
    page,
    config: {
      locale: 'zh-CN',
      timezoneId: 'Asia/Shanghai',
      viewport: { width: 1440, height: 900 },
      cookieHeaderOrigins: ['https://www.douyin.com', 'https://open.douyin.com']
    },
    workerSessionId: 'worker-1',
    account: { nickname: 'raw-account' },
    rawWorkerResult: { unknownProviderField: 'keep-me' },
    now: () => new Date('2026-07-18T12:00:00.000Z')
  })

  assert.deepEqual(storageOptions, { indexedDB: true })
  assert.deepEqual(bundle.cookies, [cookie])
  assert.strictEqual(bundle.storageState, storageState)
  assert.deepEqual(bundle.origins[0].localStorage, storageState.origins[0].localStorage)
  assert.deepEqual(bundle.origins[0].sessionStorage, [{ name: 'session-key', value: 'session-value' }])
  assert.deepEqual(bundle.origins[0].indexedDb, storageState.origins[0].indexedDB)
  assert.equal(bundle.cookieHeadersByOrigin['https://www.douyin.com'], 'future_cookie=raw-value')
  assert.equal(bundle.browserContext.userAgent, 'captured-agent')
  assert.equal(bundle.rawWorkerResult.unknownProviderField, 'keep-me')
  assert.equal(bundle.capturedAt, '2026-07-18T12:00:00.000Z')
})

test('validates a saved bundle in a second context and always closes that context', async () => {
  let contextOptions
  let restoredStorage
  let closed = false
  const validationPage = {
    goto: async () => undefined,
    url: () => 'https://www.douyin.com/user/self',
    title: async () => '个人主页',
    frames: () => [],
    locator: selector => ({
      first: () => ({ isVisible: async () => selector === '[data-e2e="user-name"]' }),
      innerText: async () => '个人主页 退出登录',
      count: async () => 0
    })
  }
  const validationContext = {
    addInitScript: async (_script, payload) => { restoredStorage = payload },
    newPage: async () => validationPage,
    cookies: async () => [{ name: 'sessionid_ss', value: 'raw-session', domain: '.douyin.com' }],
    close: async () => { closed = true }
  }
  const browser = {
    newContext: async options => {
      contextOptions = options
      return validationContext
    }
  }
  const browserType = { launch: async () => browser }
  const config = {
    headless: true,
    validationUrl: 'https://www.douyin.com/user/self?from_tab_name=main',
    navigationTimeoutMs: 30_000,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    viewport: { width: 1440, height: 900 }
  }
  const driver = new PlaywrightDouyinDriver({ browserType, config, now: () => new Date('2026-07-18T12:05:00.000Z') })
  const storageState = { cookies: [], origins: [] }
  const bundle = {
    storageState,
    origins: [{ origin: 'https://www.douyin.com', sessionStorage: [{ name: 'raw-key', value: 'raw-value' }] }]
  }

  const result = await driver.validateBundle(bundle)

  assert.equal(result.valid, true)
  assert.strictEqual(contextOptions.storageState, storageState)
  assert.deepEqual(restoredStorage, [{
    origin: 'https://www.douyin.com',
    values: [{ name: 'raw-key', value: 'raw-value' }]
  }])
  assert.equal(result.bundle.lastValidatedAt, '2026-07-18T12:05:00.000Z')
  assert.equal(closed, true)
})

test('rejects a stale auth-named cookie when the server renders a logged-out page', async () => {
  const validationPage = {
    goto: async () => undefined,
    url: () => 'https://www.douyin.com/user/self',
    title: async () => 'Douyin login',
    frames: () => [],
    locator: selector => ({
      first: () => ({ isVisible: async () => false }),
      innerText: async () => selector === 'body' ? 'Scan QR code to log in' : '',
      count: async () => 0
    })
  }
  const validationContext = {
    newPage: async () => validationPage,
    cookies: async () => [{ name: 'sessionid_ss', value: 'stale-session', domain: '.douyin.com' }],
    close: async () => undefined
  }
  const browserType = {
    launch: async () => ({ newContext: async () => validationContext })
  }
  const driver = new PlaywrightDouyinDriver({
    browserType,
    config: {
      headless: true,
      validationUrl: 'https://www.douyin.com/user/self',
      navigationTimeoutMs: 30_000,
      locale: 'zh-CN',
      timezoneId: 'Asia/Shanghai',
      viewport: { width: 1440, height: 900 }
    }
  })

  const result = await driver.validateBundle({ storageState: { cookies: [], origins: [] } })

  assert.equal(result.valid, false)
  assert.equal(result.details.authenticatedCookiePresent, true)
  assert.equal(result.details.identityMarker, null)
})
