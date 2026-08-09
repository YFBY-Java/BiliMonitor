import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildCookieHeadersByOrigin,
  classifyPageState,
  hasAuthenticatedCookies
} from '../src/page-state.js'

test('classifies captcha as user action instead of bypassing it', () => {
  assert.equal(classifyPageState({
    title: '验证码中间页',
    text: '请完成下列验证后继续',
    authenticated: false,
    qrVisible: false
  }), 'USER_ACTION_REQUIRED')
})

test('keeps a visible QR waiting when SMS and password login tabs are present', () => {
  assert.equal(classifyPageState({
    text: '扫码登录 验证码登录 密码登录',
    qrVisible: true
  }), 'WAITING')
})

test('requires user action for a standalone verification-code prompt even with a visible QR', () => {
  assert.equal(classifyPageState({
    text: '请输入验证码',
    qrVisible: true
  }), 'USER_ACTION_REQUIRED')
})

test('classifies the observable QR login states', () => {
  assert.equal(classifyPageState({ text: '二维码已过期，点击刷新' }), 'EXPIRED')
  assert.equal(classifyPageState({ text: '已扫码，请在手机上确认' }), 'SCANNED')
  assert.equal(classifyPageState({ text: '正在验证登录状态' }), 'VALIDATING')
  assert.equal(classifyPageState({ authenticated: true }), 'SUCCESS')
  assert.equal(classifyPageState({ qrVisible: true }), 'WAITING')
  assert.equal(classifyPageState({}), 'STARTING')
})

test('authentication heuristic ignores anonymous cookies but accepts session cookies', () => {
  assert.equal(hasAuthenticatedCookies([
    { name: 'ttwid', value: 'anonymous' },
    { name: 'passport_csrf_token', value: 'csrf' }
  ]), false)
  assert.equal(hasAuthenticatedCookies([
    { name: 'future_cookie', value: 'keep' },
    { name: 'sessionid_ss', value: 'authenticated-session' }
  ]), true)
})

test('cookie header builder preserves unknown names and groups every applicable domain cookie', () => {
  const cookies = [
    { name: 'sessionid', value: 'raw-session', domain: '.douyin.com', path: '/', secure: true },
    { name: 'future_cookie', value: 'future-value', domain: 'www.douyin.com', path: '/', secure: true },
    { name: 'other', value: 'other-value', domain: '.example.test', path: '/', secure: true }
  ]

  const result = buildCookieHeadersByOrigin(cookies, [
    'https://www.douyin.com',
    'https://open.douyin.com',
    'https://example.test'
  ])

  assert.equal(result['https://www.douyin.com'], 'sessionid=raw-session; future_cookie=future-value')
  assert.equal(result['https://open.douyin.com'], 'sessionid=raw-session')
  assert.equal(result['https://example.test'], 'other=other-value')
})
