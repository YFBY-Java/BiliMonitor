import type { BilibiliLiveSessionCoverageStatus } from '@/api/bilibiliLiveSessions'

export function canInterpretSessionZero(
  coverageStatus?: BilibiliLiveSessionCoverageStatus | null
): boolean {
  return coverageStatus === 'RECEIVED_WHILE_ONLINE'
}

export function formatInsightPercent(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '--'
  return `${(value * 100).toFixed(1)}%`
}

export function formatInsightRate(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '--'
  return `${value.toFixed(2)} /分钟`
}

export function formatSecondsDuration(value?: number | null): string {
  if (value == null || !Number.isFinite(value) || value < 0) return '--'
  const total = Math.floor(value)
  const hours = Math.floor(total / 3_600)
  const minutes = Math.floor((total % 3_600) / 60)
  const seconds = total % 60
  return [hours ? `${hours}小时` : '', minutes ? `${minutes}分` : '', `${seconds}秒`]
    .filter(Boolean)
    .join(' ')
}

export function segmentScaleMaximum(segments: Array<{ userCount: number }>): number {
  return Math.max(1, ...segments.map(segment => segment.userCount))
}
