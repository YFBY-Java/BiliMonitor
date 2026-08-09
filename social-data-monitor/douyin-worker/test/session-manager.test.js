import test from 'node:test'
import assert from 'node:assert/strict'
import { SessionManager, WorkerError } from '../src/session-manager.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((next, fail) => {
    resolve = next
    reject = fail
  })
  return { promise, resolve, reject }
}

const rawBundle = {
  version: 1,
  authType: 'DOUYIN_WEB_SESSION',
  cookies: [{ name: 'future_cookie', value: 'raw-value', futureAttribute: 'keep-me' }],
  cookieHeadersByOrigin: { 'https://www.douyin.com': 'future_cookie=raw-value' },
  origins: [{ origin: 'https://www.douyin.com', localStorage: [], sessionStorage: [], indexedDb: [] }],
  storageState: { cookies: [], origins: [] },
  browserContext: { locale: 'zh-CN', unknownContextField: 'keep-me' },
  rawWorkerResult: { unknownProviderField: 'keep-me' }
}

test('registers a starting session before browser initialization completes', async () => {
  const pending = deferred()
  const manager = new SessionManager({
    driver: { createSession: () => pending.promise, validateBundle: async () => ({ valid: false }) },
    ttlSeconds: 180,
    now: () => 1_000
  })

  const raced = await Promise.race([
    manager.create().then(value => ({ kind: 'started', value })),
    new Promise(resolve => setImmediate(() => resolve({ kind: 'blocked' })))
  ])

  assert.equal(raced.kind, 'started')
  assert.equal(raced.value.status, 'STARTING')
  assert.equal((await manager.status(raced.value.workerSessionId)).status, 'STARTING')
  await assert.rejects(() => manager.qr(raced.value.workerSessionId), error =>
    error instanceof WorkerError &&
    error.code === 'SESSION_NOT_READY' &&
    error.statusCode === 409)
})

test('reports initialization failures from the asynchronous driver', async () => {
  const pending = deferred()
  const manager = new SessionManager({
    driver: { createSession: () => pending.promise, validateBundle: async () => ({ valid: false }) },
    ttlSeconds: 180,
    now: () => 1_000
  })
  const started = await manager.create()

  pending.reject(new Error('browser failed to start'))
  await new Promise(resolve => setImmediate(resolve))

  assert.deepEqual(await manager.status(started.workerSessionId), {
    status: 'FAILED',
    message: 'browser failed to start'
  })
})

test('remove closes a handle that arrives after removal exactly once', async () => {
  const pending = deferred()
  let closes = 0
  const manager = new SessionManager({
    driver: { createSession: () => pending.promise, validateBundle: async () => ({ valid: false }) },
    ttlSeconds: 180,
    now: () => 1_000
  })
  const started = await manager.create()

  const removal = manager.remove(started.workerSessionId).then(
    () => ({ kind: 'removed' }),
    error => ({ kind: 'failed', error })
  )
  pending.resolve({ close: async () => { closes += 1 } })
  await new Promise(resolve => setImmediate(resolve))

  assert.equal((await removal).kind, 'removed')
  assert.equal(closes, 1)
  await assert.rejects(() => manager.status(started.workerSessionId), error =>
    error instanceof WorkerError && error.code === 'SESSION_NOT_FOUND')
})

test('cleanup closes a handle that arrives after pending session expiry exactly once', async () => {
  const pending = deferred()
  let closes = 0
  let now = 1_000
  const manager = new SessionManager({
    driver: { createSession: () => pending.promise, validateBundle: async () => ({ valid: false }) },
    ttlSeconds: 2,
    now: () => now
  })
  await manager.create()
  now = 3_001

  const cleanup = manager.cleanupExpired().then(
    () => ({ kind: 'cleaned' }),
    error => ({ kind: 'failed', error })
  )
  pending.resolve({ close: async () => { closes += 1 } })
  await new Promise(resolve => setImmediate(resolve))

  assert.equal((await cleanup).kind, 'cleaned')
  assert.equal(closes, 1)
})

test('closeAll closes a handle that arrives after shutdown exactly once', async () => {
  const pending = deferred()
  let closes = 0
  const manager = new SessionManager({
    driver: { createSession: () => pending.promise, validateBundle: async () => ({ valid: false }) },
    ttlSeconds: 180,
    now: () => 1_000
  })
  await manager.create()

  await manager.closeAll()
  pending.resolve({ close: async () => { closes += 1 } })
  await new Promise(resolve => setImmediate(resolve))

  assert.equal(closes, 1)
})

test('consume returns every field from the successful validated bundle', async () => {
  const handle = {
    qr: async () => Buffer.from('png'),
    status: async () => ({ status: 'SUCCESS', message: 'validated', bundle: rawBundle, account: {} }),
    close: async () => undefined
  }
  const driver = {
    createSession: async () => handle,
    validateBundle: async bundle => ({ valid: true, bundle })
  }
  const manager = new SessionManager({ driver, ttlSeconds: 180, now: () => 1_000 })

  const started = await manager.create()
  const status = await manager.status(started.workerSessionId)
  const consumed = await manager.consume(started.workerSessionId)

  assert.equal(status.status, 'SUCCESS')
  assert.deepEqual(consumed.bundle, rawBundle)
  assert.deepEqual((await manager.consume(started.workerSessionId)).bundle, rawBundle)
})

test('expires sessions using the injected clock and closes the browser context', async () => {
  let now = 10_000
  let closed = false
  const handle = {
    qr: async () => Buffer.from('png'),
    status: async () => ({ status: 'WAITING', message: 'scan' }),
    close: async () => { closed = true }
  }
  const manager = new SessionManager({
    driver: { createSession: async () => handle, validateBundle: async () => ({ valid: false }) },
    ttlSeconds: 2,
    now: () => now
  })
  const started = await manager.create()
  now = 12_001

  const status = await manager.status(started.workerSessionId)

  assert.equal(status.status, 'EXPIRED')
  assert.equal(closed, true)
  await assert.rejects(() => manager.qr('missing'), error =>
    error instanceof WorkerError && error.code === 'SESSION_NOT_FOUND')
})

test('manual action state can be checked again after the user handles the visible challenge', async () => {
  let checks = 0
  const handle = {
    qr: async () => Buffer.from('png'),
    status: async () => {
      checks += 1
      return checks === 1
        ? { status: 'USER_ACTION_REQUIRED', message: 'solve in headed browser' }
        : { status: 'WAITING', message: 'scan', qrAvailable: true }
    },
    close: async () => undefined
  }
  const manager = new SessionManager({
    driver: { createSession: async () => handle, validateBundle: async () => ({ valid: false }) },
    ttlSeconds: 180
  })
  const started = await manager.create()

  assert.equal((await manager.status(started.workerSessionId)).status, 'USER_ACTION_REQUIRED')
  assert.equal((await manager.status(started.workerSessionId)).status, 'WAITING')
})
