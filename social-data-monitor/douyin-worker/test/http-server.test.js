import test from 'node:test'
import assert from 'node:assert/strict'
import { once } from 'node:events'
import { createWorkerHttpServer } from '../src/http-server.js'

test('serves health publicly and protects session APIs with the configured worker token', async t => {
  const manager = {
    create: async () => ({ workerSessionId: 'worker-1', status: 'WAITING', expiresAt: '2026-07-18T12:00:00Z' }),
    qr: async () => ({ bytes: Buffer.from('png-bytes'), contentType: 'image/png' }),
    status: async () => ({ status: 'WAITING', message: 'scan' }),
    consume: async () => ({ bundle: { cookies: [{ name: 'sessionid', value: 'raw' }] } }),
    remove: async () => undefined,
    validate: async bundle => ({ valid: true, bundle })
  }
  const server = createWorkerHttpServer({ manager, token: 'worker-secret' })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  t.after(() => server.close())
  const { port } = server.address()
  const base = `http://127.0.0.1:${port}`

  const health = await fetch(`${base}/internal/v1/health`)
  assert.equal(health.status, 200)
  assert.equal((await health.json()).data.status, 'UP')

  const unauthorized = await fetch(`${base}/internal/v1/login-sessions`, { method: 'POST' })
  assert.equal(unauthorized.status, 401)

  const created = await fetch(`${base}/internal/v1/login-sessions`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-worker-token': 'worker-secret' },
    body: JSON.stringify({ expiresInSeconds: 180 })
  })
  assert.equal(created.status, 201)
  assert.equal((await created.json()).data.workerSessionId, 'worker-1')

  const qr = await fetch(`${base}/internal/v1/login-sessions/worker-1/qr`, {
    headers: { 'x-worker-token': 'worker-secret' }
  })
  assert.equal(qr.headers.get('content-type'), 'image/png')
  assert.equal(Buffer.from(await qr.arrayBuffer()).toString(), 'png-bytes')

  const validation = await fetch(`${base}/internal/v1/web-sessions/validate`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-worker-token': 'worker-secret' },
    body: JSON.stringify({ bundle: { cookies: [] } })
  })
  assert.equal((await validation.json()).data.valid, true)
})
