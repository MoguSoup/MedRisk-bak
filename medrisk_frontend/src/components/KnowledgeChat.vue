<template>
  <section class="knowledge-chat">
    <aside class="chat-sidebar panel">
      <div class="panel-title">
        <h3>会话</h3>
        <el-button type="primary" :icon="Plus" @click="createConversation">新建</el-button>
      </div>
      <div class="conversation-list">
        <button v-for="item in conversations" :key="item.id" :class="{ active: item.id === activeId }" @click="openConversation(item.id)">
          <strong>{{ item.title }}</strong>
          <span>{{ item.updatedAt || item.createdAt }}</span>
        </button>
      </div>
    </aside>

    <section class="panel chat-main">
      <div class="panel-title">
        <h3>智能问答</h3>
        <el-tag type="success">GraphRAG + 疾病档案 + 病历案例</el-tag>
      </div>

      <div class="message-list">
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
            <p>{{ message.answer }}</p>
          </div>
          <div class="evidence-row">
            <el-tag v-for="keyword in message.keywords || []" :key="keyword" size="small">{{ keyword }}</el-tag>
          </div>
          <div class="evidence-grid">
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
        <el-input v-model="question" type="textarea" :rows="4" placeholder="例如：糖尿病有哪些症状？高血压患者应如何管理？" />
        <div class="composer-actions">
          <el-upload :auto-upload="false" :limit="1" :on-change="handleImage" :on-remove="clearImage" accept="image/*">
            <el-button :icon="Picture">图片</el-button>
          </el-upload>
          <el-button type="primary" :icon="Promotion" :loading="loading" @click="send">发送</el-button>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, type UploadFile } from 'element-plus'
import { onMounted, ref } from 'vue'
import { Picture, Plus, Promotion } from '@element-plus/icons-vue'
import { request, sendConversationMessage } from '../api/client'

type Conversation = { id: number; title: string; createdAt: string; updatedAt: string }
type ConversationDetail = { conversation: Conversation; messages: QaMessage[] }
type QaMessage = {
  id: number
  conversationId: number
  question: string
  answer: string
  imageUrl?: string
  keywords?: string[]
  relatedEntities?: Array<Record<string, unknown>>
  diseaseInfoMatches?: Array<Record<string, unknown>>
  diseaseCaseMatches?: Array<Record<string, unknown>>
}

const conversations = ref<Conversation[]>([])
const messages = ref<QaMessage[]>([])
const activeId = ref<number | null>(null)
const question = ref('')
const selectedImage = ref<File | null>(null)
const loading = ref(false)

onMounted(loadConversations)

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
}

async function openConversation(id: number) {
  const detail = await request<ConversationDetail>('get', `/conversations/${id}`)
  activeId.value = id
  messages.value = detail.messages
}

function handleImage(file: UploadFile) {
  selectedImage.value = file.raw || null
}

function clearImage() {
  selectedImage.value = null
}

async function send() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  if (!activeId.value) {
    await createConversation()
  }
  if (!activeId.value) return
  loading.value = true
  try {
    const form = new FormData()
    form.append('question', question.value.trim())
    if (selectedImage.value) form.append('image', selectedImage.value)
    const message = await sendConversationMessage<QaMessage>(activeId.value, form)
    messages.value = [...messages.value, message]
    question.value = ''
    selectedImage.value = null
    await loadConversations()
    activeId.value = message.conversationId as number
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '问答失败')
  } finally {
    loading.value = false
  }
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
</script>
