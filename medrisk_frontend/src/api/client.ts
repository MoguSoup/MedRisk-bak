import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 8000
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('medrisk-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export async function request<T>(method: 'get' | 'post' | 'put' | 'delete', url: string, data?: unknown): Promise<T> {
  const response = await api.request({ method, url, data })
  const body = response.data
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data as T
}

export async function uploadAdminDataset(formData: FormData) {
  const response = await api.post('/admin/datasets', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  const body = response.data
  if (body.code !== 0) {
    throw new Error(body.message || '上传失败')
  }
  return body.data
}

export async function postForm<T>(url: string, formData: FormData): Promise<T> {
  const response = await api.post(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  const body = response.data
  if (body.code !== 0) {
    throw new Error(body.message || '提交失败')
  }
  return body.data as T
}

export async function putForm<T>(url: string, formData: FormData): Promise<T> {
  const response = await api.put(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  const body = response.data
  if (body.code !== 0) {
    throw new Error(body.message || '提交失败')
  }
  return body.data as T
}

export async function uploadAdminDocument(formData: FormData) {
  return postForm('/admin/documents', formData)
}

export async function sendConversationMessage<T>(conversationId: number, formData: FormData) {
  return postForm<T>(`/conversations/${conversationId}/messages`, formData)
}

export function downloadReport(reportId: number) {
  const token = localStorage.getItem('medrisk-token')
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const url = `${baseURL}/reports/${reportId}/download`
  return axios.get(url, {
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
}

export function downloadDocument(documentId: number) {
  const token = localStorage.getItem('medrisk-token')
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const url = `${baseURL}/documents/${documentId}/download`
  return axios.get(url, {
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
}
