import type { DouyinQrStatus } from '@/api/douyinAuth'

export function canRequestDouyinQrImage(status?: DouyinQrStatus): boolean {
  return status === 'WAITING'
}
