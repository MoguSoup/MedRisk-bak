<template>
  <section class="knowledge-chat">
    <aside class="chat-sidebar panel">
      <div class="panel-title">
        <h3>会话</h3>
        <el-button type="primary" :icon="Plus" @click="createConversation">新建</el-button>
      </div>
      <div class="conversation-list">
        <article v-for="item in conversations" :key="item.id" class="conversation-card" :class="{ active: item.id === activeId }">
          <button class="conversation-open" type="button" @click="openConversation(item.id)">
            <strong>{{ item.title }}</strong>
            <span>{{ formatBeijingTime(item.updatedAt || item.createdAt) }}</span>
          </button>
          <el-button
            class="conversation-rename"
            type="primary"
            text
            :icon="Edit"
            aria-label="Rename conversation"
            @click.stop="renameConversation(item)"
          />
          <el-button
            class="conversation-delete"
            type="danger"
            text
            :icon="Delete"
            aria-label="Delete conversation"
            @click.stop="deleteConversation(item.id)"
          />
        </article>
      </div>
    </aside>

    <section class="panel chat-main">
      <div class="panel-title">
        <h3>智能问答</h3>
        <el-tag type="success">自动判断 · 文本 / 图片多模态</el-tag>
      </div>

      <div class="chat-model-bar">
        <el-select v-model="selectedModelProfileId" placeholder="选择问答大模型" class="chat-model-select">
          <el-option
            v-for="profile in llmProfiles"
            :key="profile.id"
            :label="`${profile.displayName} · ${profile.modelName}`"
            :value="profile.id"
          />
        </el-select>
        <el-switch
          v-model="reasoningEnabled"
          :disabled="!currentModelProfile?.reasoningSupported"
          active-text="推理模式"
          inactive-text="普通模式"
        />
        <el-tag type="info" effect="plain">自动识别日常聊天 / 医学问答 / 图片输入</el-tag>
      </div>

      <div ref="messageListRef" class="message-list">
        <div v-if="messages.length === 0" class="empty-state">
          选择或新建会话后，输入医疗健康问题开始问答。
        </div>
        <article v-for="message in messages" :key="message.id" class="qa-message">
          <div class="question">
            <strong>问</strong>
            <p>{{ message.question }}</p>
            <img v-if="message.imageUrl" :src="message.imageUrl" alt="问答图片" />
          </div>
          <div class="answer">
            <strong>答</strong>
            <SafeMarkdown v-if="message.answer" :content="message.answer" />
            <p v-else class="stream-status">{{ message.statusText || '模型思考中...' }}</p>
          </div>
          <details v-if="message.reasoningContent" class="reasoning-panel" :open="message.status === 'streaming'">
            <summary>思考过程</summary>
            <SafeMarkdown :content="message.reasoningContent" />
          </details>
          <div class="qa-model-meta">
            <el-tag size="small" :type="message.fallbackUsed ? 'warning' : 'success'">{{ message.fallbackUsed ? '本地兜底' : '模型生成' }}</el-tag>
            <el-tag size="small" :type="isMedicalMessage(message) ? 'success' : 'info'">{{ isMedicalMessage(message) ? '医学问答' : '日常聊天' }}</el-tag>
            <el-tag v-if="isMedicalMessage(message)" size="small" :type="message.retrievalStatus === 'success' ? 'success' : 'warning'">
              {{ retrievalStatusText(message.retrievalStatus) }}
            </el-tag>
            <span>{{ message.provider || 'local-fallback' }} · {{ message.usedModel || 'local-fallback' }}</span>
            <span v-if="message.statusText">{{ message.statusText }}</span>
          </div>
          <div v-if="isMedicalMessage(message)" class="evidence-row">
            <el-tag v-for="keyword in message.keywords || []" :key="keyword" size="small">{{ keyword }}</el-tag>
          </div>
          <div v-if="isMedicalMessage(message) && message.evidenceSources?.length" class="evidence-sources">
            <span v-for="item in message.evidenceSources" :key="`${item.type}-${item.title}`">
              {{ item.type }} · {{ item.title }}
            </span>
          </div>
          <div v-if="message.generatedImages?.length" class="generated-image-grid">
            <a v-for="url in message.generatedImages" :key="url" :href="url" target="_blank" rel="noreferrer">
              <img :src="url" alt="MedRisk 生成图片" />
            </a>
          </div>
          <div v-if="isMedicalMessage(message)" class="evidence-grid">
            <div v-if="message.relatedEntities?.length">
              <span>相关实体</span>
              <p>{{ message.relatedEntities.map((item: any) => evidenceName(item, 'name')).join('、') }}</p>
            </div>
            <div v-if="message.diseaseInfoMatches?.length">
              <span>疾病档案</span>
              <p>{{ message.diseaseInfoMatches.map((item: any) => evidenceName(item, 'diseaseName')).join('、') }}</p>
            </div>
            <div v-if="message.diseaseCaseMatches?.length">
              <span>病历案例</span>
              <p>{{ message.diseaseCaseMatches.map((item: any) => evidenceName(item, 'caseTitle')).join('、') }}</p>
            </div>
          </div>
        </article>
      </div>

      <div class="chat-composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="4"
          :placeholder="questionPlaceholder"
          @keydown.enter.exact.prevent="send"
          @keydown.enter.shift.exact.stop
        />
        <div class="composer-actions">
          <span v-if="selectedImage" class="selected-image-chip">
            {{ selectedImage.name }}
            <button type="button" @click="clearImage">移除</button>
          </span>
          <el-upload :auto-upload="false" :limit="1" :show-file-list="false" :on-change="handleImage" :on-remove="clearImage" accept="image/*">
            <el-button :icon="Picture">图片</el-button>
          </el-upload>
          <el-button type="primary" :icon="Promotion" :loading="loading" @click="send">发送</el-button>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { computed, nextTick, onMounted, ref } from 'vue'
import { Delete, Edit, Picture, Plus, Promotion } from '@element-plus/icons-vue'
import { apiErrorMessage, isAuthExpiredError, request, streamConversationMessage } from '../api/client'
import SafeMarkdown from './SafeMarkdown.vue'

type Conversation = { id: number; title: string; createdAt: string; updatedAt: string }
type ConversationDetail = { conversation: Conversation; messages: QaMessage[] }
type ChatMode = 'daily' | 'medical'
type QaMessage = {
  id: number
  conversationId: number
  question: string
  answer: string
  imageUrl?: string
  keywords?: string[]
  usedModel?: string
  provider?: string
  modelProfileId?: number
  chatMode?: ChatMode
  retrievalUsed?: boolean
  retrievalStatus?: string
  outputImageRequested?: boolean
  generatedImages?: string[]
  reasoningEnabled?: boolean
  reasoningContent?: string
  fallbackUsed?: boolean
  status?: 'streaming' | 'done' | 'error'
  statusText?: string
  evidenceSources?: Array<{ type: string; title: string; summary?: string }>
  relatedEntities?: Array<Record<string, unknown>>
  diseaseInfoMatches?: Array<Record<string, unknown>>
  diseaseCaseMatches?: Array<Record<string, unknown>>
}
type LlmProfile = {
  id: number
  displayName: string
  provider: string
  baseUrl: string
  modelName: string
  reasoningSupported?: boolean
  reasoningProtocol?: string
  enabled?: boolean
  defaultProfile?: boolean
}

const conversations = ref<Conversation[]>([])
const messages = ref<QaMessage[]>([])
const activeId = ref<number | null>(null)
const question = ref('')
const selectedImage = ref<File | null>(null)
const loading = ref(false)
const messageListRef = ref<HTMLElement | null>(null)
const llmProfiles = ref<LlmProfile[]>([])
const selectedModelProfileId = ref<number | null>(null)
const reasoningEnabled = ref(false)
const currentModelProfile = computed(() => llmProfiles.value.find((item) => item.id === selectedModelProfileId.value) || null)
const questionPlaceholder = computed(() => '可直接闲聊，或询问医学/平台问题；上传图片后自动调用多模态模型。')

onMounted(async () => {
  await Promise.all([loadLlmProfiles(), loadConversations()])
})

async function loadLlmProfiles() {
  try {
    llmProfiles.value = await request<LlmProfile[]>('get', '/llm-profiles')
    const defaultProfile = llmProfiles.value.find((item) => item.defaultProfile) || llmProfiles.value[0]
    selectedModelProfileId.value = defaultProfile?.id || null
    reasoningEnabled.value = Boolean(defaultProfile?.reasoningSupported)
  } catch {
    llmProfiles.value = []
    selectedModelProfileId.value = null
    reasoningEnabled.value = false
  }
}

async function loadConversations() {
  conversations.value = await request<Conversation[]>('get', '/conversations')
  if (!activeId.value && conversations.value[0]) {
    await openConversation(conversations.value[0].id)
  }
}

async function createConversation() {
  const detail = await request<ConversationDetail>('post', '/conversations', { title: '新的医疗问答' })
  conversations.value = [detail.conversation, ...conversations.value]
  activeId.value = detail.conversation.id
  messages.value = detail.messages
  await nextTick()
  scrollMessagesToEnd()
}

async function openConversation(id: number) {
  const detail = await request<ConversationDetail>('get', `/conversations/${id}`)
  activeId.value = id
  messages.value = detail.messages
  await nextTick()
  scrollMessagesToEnd()
}

async function deleteConversation(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该会话及其问答记录？', '删除会话', { type: 'warning' })
    await request('delete', `/conversations/${id}`)
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (activeId.value === id) {
      const nextConversation = conversations.value[0]
      activeId.value = null
      messages.value = []
      if (nextConversation) await openConversation(nextConversation.id)
    }
    ElMessage.success('会话已删除')
  } catch (error) {
    if (error !== 'cancel') {
      const errorMessage = apiErrorMessage(error, '会话删除失败')
      if (errorMessage) ElMessage.error(errorMessage)
    }
  }
}

async function renameConversation(item: Conversation) {
  try {
    const result = await ElMessageBox.prompt('请输入新的会话名称', '重命名会话', {
      inputValue: item.title,
      inputPlaceholder: '例如：糖尿病用药咨询',
      inputValidator: (value) => Boolean(value && value.trim()),
      inputErrorMessage: '会话名称不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const title = String(result.value || '').trim()
    if (!title || title === item.title) return
    const detail = await request<ConversationDetail>('put', `/conversations/${item.id}`, { title })
    conversations.value = conversations.value.map((row) => (row.id === item.id ? detail.conversation : row))
    if (activeId.value === item.id) {
      messages.value = detail.messages
    }
    ElMessage.success('会话已重命名')
  } catch (error) {
    if (error !== 'cancel') {
      const errorMessage = apiErrorMessage(error, '会话重命名失败')
      if (errorMessage) ElMessage.error(errorMessage)
    }
  }
}

function handleImage(file: UploadFile) {
  selectedImage.value = file.raw || null
}

function clearImage() {
  selectedImage.value = null
}

async function send() {
  if (loading.value) return
  const questionText = question.value.trim()
  if (!questionText) {
    ElMessage.warning('请输入问题')
    return
  }
  if (!activeId.value) {
    await createConversation()
  }
  if (!activeId.value) return
  loading.value = true
  try {
    const imagePayload = selectedImage.value ? await fileToImagePayload(selectedImage.value) : null
    await sendStream(questionText, imagePayload)
  } catch (error) {
    const errorMessage = chatErrorMessage(error)
    if (errorMessage) ElMessage.error(errorMessage)
  } finally {
    loading.value = false
  }
}

type ImagePayload = { base64: string; contentType: string; previewUrl: string }

async function sendStream(questionText: string, imagePayload: ImagePayload | null = null) {
  if (!activeId.value) return
  const conversationId = activeId.value
  const tempId = -Date.now()
  const inferredMode = inferChatMode(questionText, Boolean(imagePayload))
  const outputImageRequested = shouldRequestImageOutput(questionText)
  const tempMessage: QaMessage = {
    id: tempId,
    conversationId,
    question: questionText,
    answer: '',
    imageUrl: imagePayload?.previewUrl,
    provider: currentModelProfile.value?.provider,
    usedModel: currentModelProfile.value?.modelName,
    modelProfileId: selectedModelProfileId.value || undefined,
    reasoningEnabled: reasoningEnabled.value,
    chatMode: inferredMode,
    retrievalUsed: inferredMode === 'medical',
    retrievalStatus: inferredMode === 'medical' ? 'pending' : 'not_requested',
    outputImageRequested,
    generatedImages: [],
    reasoningContent: '',
    fallbackUsed: false,
    status: 'streaming',
    statusText: imagePayload ? '发送成功，正在进行多模态识别' : '发送成功，模型思考中'
  }
  messages.value = [...messages.value, tempMessage]
  question.value = ''
  selectedImage.value = null
  ElMessage.success('发送成功')
  await nextTick()
  scrollMessagesToEnd()
  await streamConversationMessage(
    conversationId,
    {
      question: questionText,
      modelProfileId: selectedModelProfileId.value,
      reasoningEnabled: reasoningEnabled.value,
      outputImageRequested,
      imageBase64: imagePayload?.base64,
      imageContentType: imagePayload?.contentType
    },
    {
      onAccepted: (data) => updateTempMessage(tempId, {
        chatMode: data.chatMode || inferredMode,
        imageUrl: data.imageUrl || imagePayload?.previewUrl,
        statusText: data.imageProvided
          ? '请求已接收，正在识别图片并检索证据'
          : (data.chatMode || inferredMode) === 'medical' ? '请求已接收，正在检索证据' : '请求已接收，正在组织回答'
      }),
      onReasoning: (data) => {
        appendTempMessage(tempId, 'reasoningContent', data.chunk || data.text || '')
        updateTempMessage(tempId, { statusText: '推理中' })
      },
      onAnswer: (data) => {
        appendTempMessage(tempId, 'answer', data.chunk || data.text || '')
        updateTempMessage(tempId, { statusText: '输出中' })
      },
      onMetadata: (data) => {
        const patch: Partial<QaMessage> = {}
        ;(['provider', 'usedModel', 'modelProfileId', 'reasoningEnabled', 'fallbackUsed', 'chatMode', 'retrievalUsed', 'retrievalStatus', 'keywords', 'evidenceSources', 'relatedEntities', 'diseaseInfoMatches', 'diseaseCaseMatches', 'imageUrl'] as const)
          .forEach((key) => {
            if (key in data) {
              ;(patch as Record<string, unknown>)[key] = data[key]
            }
          })
        if ('outputImageRequested' in data) patch.outputImageRequested = data.outputImageRequested
        if ('generatedImages' in data) patch.generatedImages = data.generatedImages
        if (data.imageGenerationStatus === 'running') patch.statusText = '正在生成图片'
        if (data.imageGenerationStatus === 'success') patch.statusText = '图片已生成'
        if (data.imageGenerationStatus === 'failed') patch.statusText = '图片生成降级'
        updateTempMessage(tempId, patch)
      },
      onDone: (data) => {
        const finalMessage = { ...data, status: 'done', statusText: '已完成' } as QaMessage
        messages.value = messages.value.map((item) => (item.id === tempId ? finalMessage : item))
      },
      onError: (data) => updateTempMessage(tempId, { status: 'error', statusText: data.message || '发送失败', fallbackUsed: true })
    }
  )
  await loadConversations()
  await nextTick()
  scrollMessagesToEnd()
}

function fileToImagePayload(file: File): Promise<ImagePayload> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve({
      base64: String(reader.result || ''),
      contentType: file.type || 'image/jpeg',
      previewUrl: String(reader.result || '')
    })
    reader.onerror = () => reject(new Error('图片读取失败，请重新选择图片'))
    reader.readAsDataURL(file)
  })
}

function inferChatMode(text: string, imageProvided: boolean): ChatMode {
  if (imageProvided) return 'medical'
  const normalized = text.toLowerCase()
  return [
    'medrisk', '医疗', '医学', '健康', '疾病', '症状', '检查', '治疗', '药', '风险', '预测',
    '报告', '图谱', '知识库', '病历', '病例', '模型', '训练', '患者', '医生', '糖尿病', '高血压',
    '冠心病', '胸痛', '咳嗽', '发热', 'medicine', 'health', 'disease', 'risk', 'symptom'
  ].some((keyword) => normalized.includes(keyword)) ? 'medical' : 'daily'
}

function shouldRequestImageOutput(text: string) {
  const normalized = text.toLowerCase()
  return ['生成图片', '输出图片', '画图', '配图', '示意图', 'generate image', 'draw', 'picture'].some((keyword) => normalized.includes(keyword))
}

function updateTempMessage(id: number, patch: Partial<QaMessage>) {
  messages.value = messages.value.map((item) => (item.id === id ? { ...item, ...patch } : item))
  nextTick(scrollMessagesToEnd)
}

function appendTempMessage(id: number, key: 'answer' | 'reasoningContent', chunk: string) {
  if (!chunk) return
  messages.value = messages.value.map((item) => (item.id === id ? { ...item, [key]: `${item[key] || ''}${chunk}` } : item))
  nextTick(scrollMessagesToEnd)
}

function isMedicalMessage(message: QaMessage) {
  return (message.chatMode || 'medical') === 'medical'
}

function retrievalStatusText(status?: string) {
  if (status === 'success') return '已检索'
  if (status === 'degraded') return '检索降级'
  if (status === 'blocked') return '范围拦截'
  if (status === 'pending') return '检索中'
  return '未检索'
}

function scrollMessagesToEnd() {
  const element = messageListRef.value
  if (!element) return
  element.scrollTop = element.scrollHeight
}

function chatErrorMessage(error: unknown) {
  if (isAuthExpiredError(error)) return ''
  const message = error instanceof Error ? error.message : ''
  if (message.toLowerCase().includes('timeout')) {
    return '问答模型响应超时，请稍后重试或检查模型服务状态'
  }
  if (message.includes('Network Error')) {
    return '问答接口暂时不可达，请检查后端或网络连接'
  }
  if (message) {
    return message
  }
  return '问答发送失败，请稍后重试'
}

function evidenceName(item: any, key: string) {
  const source = item.sourceName ? ` · ${item.sourceName}` : ''
  const visibility = item.visibilityLabel || visibilityText(item.visibility)
  return `${item[key] || '-'}（${visibility}${source}）`
}

function visibilityText(value?: string) {
  if (value === 'DOCTOR_ONLY') return '医生专用'
  if (value === 'ADMIN_ONLY') return '管理员'
  if (value === 'DRAFT') return '草稿'
  return '公开'
}

function formatBeijingTime(value?: string) {
  if (!value) return '-'
  const hasZone = /[zZ]|[+-]\d{2}:?\d{2}$/.test(value)
  if (!hasZone) {
    const match = value.match(/^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2}))?)?/)
    if (match) {
      return `${match[1]}-${match[2]}-${match[3]} ${match[4] || '00'}:${match[5] || '00'}:${match[6] || '00'}`
    }
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).formatToParts(date)
  const map = Object.fromEntries(parts.map((item) => [item.type, item.value]))
  return `${map.year}-${map.month}-${map.day} ${map.hour}:${map.minute}:${map.second}`
}
</script>
