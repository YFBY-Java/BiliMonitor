export function formatCompactNumber(value?: number | null, empty = '--') {
  if (value == null || !Number.isFinite(value)) return empty
  const abs = Math.abs(value)
  const sign = value < 0 ? '-' : ''
  if (abs >= 100000000) return `${sign}${trim(abs / 100000000, 2)}亿`
  if (abs >= 10000) return `${sign}${trim(abs / 10000, 1)}万`
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(value)
}

export function formatSigned(value?: number | null, empty = '0') {
  if (value == null || !Number.isFinite(value)) return empty
  const prefix = value > 0 ? '+' : ''
  return `${prefix}${formatCompactNumber(value, empty)}`
}

export function formatDateTime(value?: string | null, empty = '--') {
  if (!value) return empty
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return empty
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(date)
}

export function formatRelativeTime(value?: string | null, empty = '--') {
  if (!value) return empty
  const date = new Date(value)
  const diff = Date.now() - date.getTime()
  if (!Number.isFinite(diff)) return empty
  if (diff < 0) return formatDateTime(value)
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return `${Math.max(1, seconds)} 秒前`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

export function trim(value: number, digits: number) {
  return value.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1')
}
