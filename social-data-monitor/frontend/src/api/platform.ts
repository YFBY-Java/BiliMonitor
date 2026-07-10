import { getData } from './http'

export interface PlatformAdapterView {
  platformCode: string
  capabilities: string[]
}

export async function fetchPlatformAdapters(): Promise<PlatformAdapterView[]> {
  try {
    return await getData<PlatformAdapterView[]>('/api/platforms/adapters')
  } catch {
    return [
      {
        platformCode: 'bilibili',
        capabilities: ['ACCOUNT_PROFILE', 'CONTENT_LIST', 'COMMENT_LIST', 'DANMAKU_LIST']
      }
    ]
  }
}

