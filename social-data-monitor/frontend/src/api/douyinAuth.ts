import { deleteData, getData, http, postData } from './http'
import axios from 'axios'

export type DouyinCredentialKind = 'oauth' | 'web'
export type DouyinQrStatus =
  | 'STARTING'
  | 'WAITING'
  | 'SCANNED'
  | 'VALIDATING'
  | 'SUCCESS'
  | 'EXPIRED'
  | 'USER_ACTION_REQUIRED'
  | 'FAILED'

export interface DouyinCredentialFull {
  credentialId: number
  authType: 'DOUYIN_OAUTH2' | 'DOUYIN_WEB_SESSION'
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'INVALID'
  expiresAt?: string | null
  createdAt: string
  updatedAt: string
  payload: Record<string, unknown>
}

export interface DouyinAuthStatus {
  enabled: boolean
  oauthMode: 'disabled' | 'mock' | 'live'
  workerAvailable: boolean
  workerStatus: string
  pollIntervalMs: number
  oauthCredential?: DouyinCredentialFull | null
  webCredential?: DouyinCredentialFull | null
  workerRawResult: Record<string, unknown>
}

export interface DouyinQrStart {
  loginId: string
  status: DouyinQrStatus
  imageUrl: string
  expiresInSeconds: number
  pollIntervalMs: number
  expiresAt: string
  rawResult: Record<string, unknown>
}

export interface DouyinQrStatusView {
  loginId: string
  status: DouyinQrStatus
  message: string
  expiresInSeconds: number
  rawResult: Record<string, unknown>
  credential?: DouyinCredentialFull | null
}

export interface DouyinValidationResult {
  valid: boolean
  message: string
  credential?: DouyinCredentialFull | null
  rawResult: Record<string, unknown>
}

export interface DouyinOAuthStart {
  loginId: string
  mode: 'mock' | 'live'
  authorizationUrl: string
  state: string
  expiresInSeconds: number
}

export interface DouyinStoredCredential {
  credentialId: number
  platformId: number
  authType: string
  status: string
  payload: Record<string, unknown>
  expiresAt?: string | null
  createdAt: string
  updatedAt: string
}

const BASE_URL = '/api/douyin/auth'

export function fetchDouyinAuthStatus(): Promise<DouyinAuthStatus> {
  return getData<DouyinAuthStatus>(`${BASE_URL}/status`)
}

export function startDouyinWebQr(): Promise<DouyinQrStart> {
  return postData<DouyinQrStart, Record<string, never>>(`${BASE_URL}/web/qr/start`, {})
}

export async function fetchDouyinWebQrImage(loginId: string): Promise<Blob> {
  const response = await http.get<Blob>(
    `${BASE_URL}/web/qr/${encodeURIComponent(loginId)}/image`,
    { responseType: 'blob' }
  )
  return response.data
}

export function fetchDouyinWebQrStatus(loginId: string): Promise<DouyinQrStatusView> {
  return getData<DouyinQrStatusView>(`${BASE_URL}/web/qr/${encodeURIComponent(loginId)}/status`)
}

export function validateDouyinWebCredential(): Promise<DouyinValidationResult> {
  return postData<DouyinValidationResult, Record<string, never>>(`${BASE_URL}/web/validate`, {})
}

export function startDouyinOAuth(): Promise<DouyinOAuthStart> {
  return postData<DouyinOAuthStart, Record<string, never>>(`${BASE_URL}/oauth/start`, {})
}

export function refreshDouyinOAuth(): Promise<DouyinStoredCredential> {
  return postData<DouyinStoredCredential, Record<string, never>>(`${BASE_URL}/oauth/refresh`, {})
}

export function fetchDouyinCredential(kind: DouyinCredentialKind): Promise<DouyinCredentialFull> {
  return getData<DouyinCredentialFull>(`${BASE_URL}/credentials/${kind}`)
}

export function revokeDouyinCredential(kind: DouyinCredentialKind): Promise<void> {
  return deleteData<void>(`${BASE_URL}/credentials/${kind}`)
}

export async function downloadDouyinCredential(kind: DouyinCredentialKind): Promise<void> {
  const response = await http.get<Blob>(`${BASE_URL}/credentials/${kind}/export`, { responseType: 'blob' })
  const objectUrl = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = `douyin-${kind}-credential.json`
  anchor.click()
  URL.revokeObjectURL(objectUrl)
}

export function douyinErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: unknown; error?: { message?: unknown } } | undefined
    const message = data?.message ?? data?.error?.message
    if (typeof message === 'string' && message.trim()) return message
    if (error.message) return error.message
  }
  if (error instanceof Error && error.message) return error.message
  return fallback
}
