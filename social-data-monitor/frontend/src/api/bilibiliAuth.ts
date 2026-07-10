import { deleteData, getData, postData } from './http'

export type BilibiliQrLoginStatus = 'WAITING' | 'SCANNED' | 'EXPIRED' | 'SUCCESS' | 'FAILED'
export type BilibiliCredentialStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'INVALID' | 'NONE'

export interface BilibiliAccount {
  mid?: number
  uname?: string
  face?: string
  level?: number
  vipStatus?: number
}

export interface BilibiliQrLoginStart {
  loginId: string
  qrUrl: string
  expiresInSeconds: number
  pollIntervalMillis: number
}

export interface BilibiliQrLoginStatusView {
  status: BilibiliQrLoginStatus
  message: string
  expiresInSeconds?: number
  account?: BilibiliAccount
  credential?: BilibiliCredentialFull
}

export interface BilibiliCookieFull {
  name: string
  value: string
  domain?: string
  path?: string
  expiresAt?: string
  httpOnly?: boolean
  secure?: boolean
  sameSite?: string
}

export interface BilibiliCredentialFull {
  credentialId: number
  account?: BilibiliAccount
  cookieHeader: string
  cookies: BilibiliCookieFull[]
  csrf?: string
  refreshToken?: string
  expiresAt?: string
  rawPayload: Record<string, unknown>
}

export interface BilibiliAuthStatus {
  loggedIn: boolean
  credentialId?: number
  account?: BilibiliAccount
  lastValidatedAt?: string
  lastRefreshCheckedAt?: string
  expiresAt?: string
  status: BilibiliCredentialStatus
  credential?: BilibiliCredentialFull
}

export interface BilibiliAuthRefreshResult {
  refreshed: boolean
  loggedIn: boolean
  message: string
  account?: BilibiliAccount
}

const BASE_URL = '/api/bilibili/auth'

export function startBilibiliQrLogin(): Promise<BilibiliQrLoginStart> {
  return postData<BilibiliQrLoginStart, Record<string, never>>(`${BASE_URL}/qr/start`, {})
}

export function fetchBilibiliQrLoginStatus(loginId: string): Promise<BilibiliQrLoginStatusView> {
  return getData<BilibiliQrLoginStatusView>(`${BASE_URL}/qr/${encodeURIComponent(loginId)}/status`)
}

export function fetchBilibiliAuthStatus(): Promise<BilibiliAuthStatus> {
  return getData<BilibiliAuthStatus>(`${BASE_URL}/status`)
}

export function refreshBilibiliAuth(): Promise<BilibiliAuthRefreshResult> {
  return postData<BilibiliAuthRefreshResult, Record<string, never>>(`${BASE_URL}/refresh`, {})
}

export function fetchBilibiliCredential(): Promise<BilibiliCredentialFull> {
  return getData<BilibiliCredentialFull>(`${BASE_URL}/credential`)
}

export function revokeBilibiliAuth(): Promise<void> {
  return deleteData<void>(BASE_URL)
}
