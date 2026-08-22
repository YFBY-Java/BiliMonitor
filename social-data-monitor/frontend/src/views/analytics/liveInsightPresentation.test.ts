import { describe, expect, it } from 'vitest'

import {
  canInterpretSessionZero,
  formatInsightPercent,
  formatInsightRate,
  formatSecondsDuration,
  segmentScaleMaximum
} from './liveInsightPresentation'

describe('live insight presentation rules', () => {
  it('does not present an uncovered unknown as a zero', () => {
    expect(canInterpretSessionZero('RECEIVED_WHILE_ONLINE')).toBe(true)
    expect(canInterpretSessionZero('BOUNDARY_ONLY')).toBe(false)
    expect(canInterpretSessionZero('NO_ONLINE_COVERAGE')).toBe(false)
  })

  it('formats ratios, rates, and covered duration consistently', () => {
    expect(formatInsightPercent(0.237)).toBe('23.7%')
    expect(formatInsightPercent(null)).toBe('--')
    expect(formatInsightRate(2.345)).toBe('2.35 /分钟')
    expect(formatInsightRate(undefined)).toBe('--')
    expect(formatSecondsDuration(3_661)).toBe('1小时 1分 1秒')
  })

  it('keeps user segment bars measurable even when the sample is empty', () => {
    expect(segmentScaleMaximum([])).toBe(1)
    expect(segmentScaleMaximum([{ userCount: 3 }, { userCount: 8 }])).toBe(8)
  })
})
