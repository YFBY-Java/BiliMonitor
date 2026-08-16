import { describe, expect, it } from 'vitest'

import panelSource from './BilibiliLiveSessionPanel.vue?raw'

function cssRule(selector: string): string {
  const start = panelSource.indexOf(`${selector} {`)
  expect(start, `missing CSS rule ${selector}`).toBeGreaterThanOrEqual(0)
  const end = panelSource.indexOf('\n}', start)
  expect(end, `unterminated CSS rule ${selector}`).toBeGreaterThan(start)
  return panelSource.slice(start, end + 2)
}

describe('Bilibili live session identity row layout', () => {
  it('uses fixed aligned columns with extra room for nickname and amount', () => {
    expect(panelSource).toContain(
      'grid-template-columns: 28px 300px 160px minmax(240px, 1fr) 132px;'
    )
    expect(panelSource).toContain('grid-template-areas: "rank name uid stats money";')
    expect(cssRule('.session-user-name')).toContain('grid-area: name;')
    expect(cssRule('.session-user-uid')).toContain('grid-area: uid;')
    expect(panelSource).toContain('grid-template-columns: 76px 64px 76px;')
    expect(panelSource).toContain('font-variant-numeric: tabular-nums;')
  })

  it('keeps the full nickname and identifier discoverable without wrapping the row', () => {
    expect(panelSource).toContain(':title="userName(user)"')
    expect(panelSource).toContain(':title="userIdentityText(user)"')
    expect(panelSource).toContain('class="session-user-name"')
    expect(panelSource).toContain('class="session-user-uid"')
    expect(cssRule('.session-user-name,\n.session-user-uid')).toContain('white-space: nowrap;')
    expect(cssRule('.session-user-name,\n.session-user-uid')).toContain('text-overflow: ellipsis;')
  })

  it('does not reuse the parent user identity class', () => {
    expect(panelSource).not.toContain('class="user-identity"')
    expect(panelSource).not.toContain('class="session-user-identity"')
  })

  it('keeps every identity record in the same aligned single-row layout', () => {
    expect(panelSource).not.toContain('@container session-user-list')
    expect(panelSource).not.toContain('"rank name money"')
    expect(cssRule('.user-rank')).toContain('grid-area: rank;')
    expect(panelSource).toContain('.user-stats {\n  grid-area: stats;')
    expect(panelSource).toContain('.user-money {\n  grid-area: money;')
  })
})
