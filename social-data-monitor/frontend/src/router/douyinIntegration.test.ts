import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const readSource = (relativePath: string) =>
  readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), 'utf8')

describe('Douyin system integration', () => {
  it('always registers the Douyin route without a feature flag', () => {
    const source = readSource('./index.ts')

    expect(source).toContain("path: 'douyin'")
    expect(source).not.toContain('VITE_DOUYIN_ENABLED')
    expect(source).not.toContain('douyinEnabled ?')
  })

  it('always renders the Douyin menu item', () => {
    const source = readSource('../layouts/MainLayout.vue')

    expect(source).toContain('index="/douyin"')
    expect(source).toContain('<span>抖音登录态</span>')
    expect(source).not.toContain('v-if="douyinEnabled"')
    expect(source).not.toContain('const douyinEnabled')
  })
})
