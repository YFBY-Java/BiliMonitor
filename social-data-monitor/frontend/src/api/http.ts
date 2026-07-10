import axios from 'axios'

export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  timestamp: string
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000
})

export async function getData<T>(url: string): Promise<T> {
  const response = await http.get<ApiResponse<T>>(url)
  return response.data.data
}

export async function postData<T, B = unknown>(url: string, body: B): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, body)
  return response.data.data
}

export async function patchData<T, B = unknown>(url: string, body: B): Promise<T> {
  const response = await http.patch<ApiResponse<T>>(url, body)
  return response.data.data
}

export async function putData<T, B = unknown>(url: string, body: B): Promise<T> {
  const response = await http.put<ApiResponse<T>>(url, body)
  return response.data.data
}

export async function deleteData<T = void>(url: string): Promise<T> {
  const response = await http.delete<ApiResponse<T>>(url)
  return response.data.data
}
