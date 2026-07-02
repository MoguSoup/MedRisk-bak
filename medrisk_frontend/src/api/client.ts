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

export async function postForm<T>(url: string, formData: FormData, timeoutMs = 20000): Promise<T> {
  const response = await api.post(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: timeoutMs
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
  return postForm<T>(`/conversations/${conversationId}/messages`, formData, 45000)
}

type StreamHandlers = {
  onAccepted?: (data: any) => void
  onReasoning?: (data: any) => void
  onAnswer?: (data: any) => void
  onMetadata?: (data: any) => void
  onDone?: (data: any) => void
  onError?: (data: any) => void
}

export async function streamConversationMessage(
  conversationId: number,
  payload: {
    question: string
    modelProfileId?: number | null
    reasoningEnabled?: boolean
    chatMode?: 'daily' | 'medical'
    outputImageRequested?: boolean
    imageBase64?: string
    imageContentType?: string
  },
  handlers: StreamHandlers
) {
  const token = localStorage.getItem('medrisk-token')
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const response = await fetch(`${baseURL}/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  })
  if (!response.ok || !response.body) {
    throw new Error(`问答流式接口不可用：${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      dispatchSseBlock(block, handlers)
    }
  }
  if (buffer.trim()) {
    dispatchSseBlock(buffer, handlers)
  }
}

function dispatchSseBlock(block: string, handlers: StreamHandlers) {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return
  let data: any = dataLines.join('\n')
  try {
    data = JSON.parse(data)
  } catch {
    data = { text: data }
  }
  if (event === 'accepted') handlers.onAccepted?.(data)
  else if (event === 'reasoning') handlers.onReasoning?.(data)
  else if (event === 'answer') handlers.onAnswer?.(data)
  else if (event === 'metadata') handlers.onMetadata?.(data)
  else if (event === 'done') handlers.onDone?.(data)
  else if (event === 'error') handlers.onError?.(data)
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
