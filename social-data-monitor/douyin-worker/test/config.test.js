import test from 'node:test'
import assert from 'node:assert/strict'
import { loadWorkerConfig } from '../src/config.js'

test('loads safe local defaults without starting a browser', () => {
  const config = loadWorkerConfig({})

  assert.equal(config.host, '127.0.0.1')
  assert.equal(config.port, 8787)
  assert.equal(config.headless, true)
  assert.equal(config.loginUrl, 'https://www.douyin.com/')
  assert.equal(config.locale, 'zh-CN')
  assert.equal(config.timezoneId, 'Asia/Shanghai')
  assert.deepEqual(config.viewport, { width: 1440, height: 900 })
})

test('parses explicit worker values and rejects malformed numeric configuration', () => {
  const config = loadWorkerConfig({
    DOUYIN_WORKER_HOST: '0.0.0.0',
    DOUYIN_WORKER_PORT: '9887',
    DOUYIN_WORKER_HEADLESS: 'false',
    DOUYIN_WORKER_SESSION_TTL_SECONDS: '240',
    DOUYIN_WORKER_VIEWPORT_WIDTH: '1600',
    DOUYIN_WORKER_VIEWPORT_HEIGHT: '1000',
    SOCIAL_MONITOR_DOUYIN_WORKER_TOKEN: 'raw-token'
  })

  assert.equal(config.host, '0.0.0.0')
  assert.equal(config.port, 9887)
  assert.equal(config.headless, false)
  assert.equal(config.sessionTtlSeconds, 240)
  assert.deepEqual(config.viewport, { width: 1600, height: 1000 })
  assert.equal(config.token, 'raw-token')
  assert.throws(() => loadWorkerConfig({ DOUYIN_WORKER_PORT: 'not-a-number' }), /DOUYIN_WORKER_PORT/u)
})
