import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { once } from 'node:events'
import { chromium } from 'playwright'
import { PlaywrightDouyinDriver } from '../src/playwright-driver.js'

const mockLoginPage = `<!doctype html>
<html lang="zh-CN">
<head><meta charset="utf-8"><title>本地抖音登录模拟页</title></head>
<body>
  <div class="qrcode"><canvas id="qr" width="180" height="180" style="width:180px;height:180px"></canvas></div>
  <button id="complete" type="button">模拟手机确认</button>
  <script>
    const canvas = document.querySelector('#qr')
    const context = canvas.getContext('2d')
    context.fillStyle = '#fff'; context.fillRect(0, 0, 180, 180)
    context.fillStyle = '#000'
    for (let y = 0; y < 18; y += 1) for (let x = 0; x < 18; x += 1) {
      if ((x * 7 + y * 11) % 3 === 0) context.fillRect(x * 10, y * 10, 10, 10)
    }

    async function saveIndexedDb() {
      await new Promise((resolve, reject) => {
        const request = indexedDB.open('douyin-login-state', 1)
        request.onupgradeneeded = () => request.result.createObjectStore('records')
        request.onerror = () => reject(request.error)
        request.onsuccess = () => {
          const transaction = request.result.transaction('records', 'readwrite')
          transaction.objectStore('records').put({ raw: 'indexed-value' }, 'login')
          transaction.oncomplete = () => { request.result.close(); resolve() }
          transaction.onerror = () => reject(transaction.error)
        }
      })
    }

    document.querySelector('#complete').addEventListener('click', async () => {
      document.cookie = 'sessionid_ss=raw-session; Path=/; SameSite=Lax'
      document.cookie = 'future_cookie=future-value; Path=/; SameSite=Lax'
      localStorage.setItem('local-key', 'local-value')
      sessionStorage.setItem('session-key', 'session-value')
      await saveIndexedDb()
      document.body.innerHTML = '<main><h1>个人主页</h1><p>退出登录</p></main>'
    })
  </script>
</body>
</html>`

test('captures and restores a complete login bundle with real Chromium contexts', async t => {
  const web = createServer((_request, response) => {
    response.writeHead(200, { 'content-type': 'text/html; charset=utf-8' })
    response.end(mockLoginPage)
  })
  web.listen(0, '127.0.0.1')
  await once(web, 'listening')
  t.after(() => web.close())
  const { port } = web.address()
  const origin = `http://127.0.0.1:${port}`
  const config = {
    headless: true,
    loginUrl: `${origin}/login`,
    validationUrl: `${origin}/user/self`,
    navigationTimeoutMs: 10_000,
    qrWaitMs: 1_000,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    viewport: { width: 1440, height: 900 },
    cookieHeaderOrigins: [origin]
  }
  const driver = new PlaywrightDouyinDriver({ browserType: chromium, config })
  t.after(() => driver.close())
  const session = await driver.createSession({ workerSessionId: 'integration-session' })
  t.after(() => session.close())

  assert.equal((await session.status()).status, 'WAITING')
  const qr = await session.qr()
  assert.equal(qr.contentType, 'image/png')
  assert.ok(qr.bytes.length > 100)

  await session.page.click('#complete')
  await session.page.locator('h1').waitFor({ state: 'visible' })
  const captured = await session.status()

  assert.equal(captured.status, 'SUCCESS')
  assert.ok(captured.bundle.cookies.some(cookie => cookie.name === 'future_cookie'))
  assert.equal(captured.bundle.cookieHeadersByOrigin[origin].includes('future_cookie=future-value'), true)
  assert.deepEqual(captured.bundle.origins[0].sessionStorage, [{ name: 'session-key', value: 'session-value' }])
  assert.ok(captured.bundle.origins[0].indexedDb.length > 0)

  const validated = await driver.validateBundle(captured.bundle)
  assert.equal(validated.valid, true)
  assert.equal(validated.bundle.rawWorkerResult.validation.status, 'SUCCESS')
})
