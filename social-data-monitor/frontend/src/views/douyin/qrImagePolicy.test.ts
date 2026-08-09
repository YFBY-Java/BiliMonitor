import { describe, expect, it } from 'vitest'
import { canRequestDouyinQrImage } from './qrImagePolicy'
import type { DouyinQrStatus } from '@/api/douyinAuth'

describe('Douyin QR image request policy', () => {
  it('allows image requests only while the worker is waiting for a scan', () => {
    expect(canRequestDouyinQrImage('WAITING')).toBe(true)
    const blocked: Array<DouyinQrStatus | undefined> = [
      undefined,
      'STARTING',
      'SCANNED',
      'VALIDATING',
      'SUCCESS',
      'EXPIRED',
      'USER_ACTION_REQUIRED',
      'FAILED'
    ]
    for (const status of blocked) expect(canRequestDouyinQrImage(status)).toBe(false)
  })
})
