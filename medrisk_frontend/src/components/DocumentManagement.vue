<template>
  <section class="view-stack">
    <section v-if="canSubmit" class="panel">
      <div class="panel-title">
        <h3>{{ isAdmin ? '文档上传' : '提交文档草稿' }}</h3>
        <el-tag type="info">TXT / PDF / DOCX</el-tag>
      </div>
      <el-form label-position="top" class="admin-form-grid">
        <el-form-item label="文档标题">
          <el-input v-model="title" placeholder="默认使用文件名" />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="可见性">
          <el-select v-model="visibility">
            <el-option label="公开" value="PUBLIC" />
            <el-option label="医生专用" value="DOCTOR_ONLY" />
            <el-option label="管理员" value="ADMIN_ONLY" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识文档">
          <el-upload :auto-upload="false" :limit="1" :on-change="handleFile" :on-remove="clearFile" accept=".txt,.pdf,.docx">
            <el-button :icon="Upload">选择文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="操作">
          <el-button type="primary" :loading="loading" @click="upload">上传并提取</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>文档中心</h3>
        <div class="table-actions">
          <el-input v-model="keyword" clearable placeholder="搜索标题或内容" @keyup.enter="loadDocuments" />
          <el-button :icon="Refresh" @click="loadDocuments">查询</el-button>
        </div>
      </div>
      <el-table :data="documents" empty-text="暂无知识文档" @row-click="selected = $event">
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="fileType" label="类型" width="90" />
        <el-table-column label="可见性" width="110">
          <template #default="{ row }"><el-tag>{{ row.visibilityLabel || visibilityText(row.visibility) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="graphStatus" label="图谱状态" width="110" />
        <el-table-column prop="sourceName" label="来源" min-width="160" show-overflow-tooltip />
        <el-table-column prop="userName" label="上传人" width="120" />
        <el-table-column prop="createdAt" label="上传时间" min-width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="download(row)">下载</el-button>
            <el-button v-if="isAdmin" text type="danger" @click.stop="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="selected" class="panel document-detail">
      <div class="panel-title">
        <h3>{{ selected.title }}</h3>
        <div class="table-actions">
          <el-tag>{{ selected.visibilityLabel || visibilityText(selected.visibility) }}</el-tag>
          <el-tag type="info">{{ selected.graphStatus }}</el-tag>
        </div>
      </div>
      <p v-if="selected.sourceName" class="muted-line">
        来源：<a v-if="selected.sourceUrl" :href="selected.sourceUrl" target="_blank" rel="noreferrer">{{ selected.sourceName }}</a>
        <span v-else>{{ selected.sourceName }}</span>
        <span v-if="selected.sourceLicense"> · {{ selected.sourceLicense }}</span>
      </p>
      <p class="muted-line">{{ selected.summary || '暂无摘要' }}</p>
      <pre>{{ selected.content || '暂无可预览文本' }}</pre>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { onMounted, ref } from 'vue'
import { Refresh, Upload } from '@element-plus/icons-vue'
import { downloadDocument, postForm, request, uploadAdminDocument } from '../api/client'

const props = defineProps<{ role: string }>()

type KnowledgeDocument = {
  id: number
  title: string
  fileType: string
  graphStatus: string
  visibility?: string
  visibilityLabel?: string
  summary?: string
  content?: string
  sourceName?: string
  sourceUrl?: string
  sourceLicense?: string
  userName?: string
  createdAt?: string
}

const documents = ref<KnowledgeDocument[]>([])
const selected = ref<KnowledgeDocument | null>(null)
const title = ref('')
const keyword = ref('')
const selectedFile = ref<File | null>(null)
const loading = ref(false)
const isAdmin = props.role === 'ADMIN'
const isDoctor = props.role === 'DOCTOR'
const canSubmit = isAdmin || isDoctor
const visibility = ref('PUBLIC')

onMounted(loadDocuments)

async function loadDocuments() {
  const query = keyword.value ? `?keyword=${encodeURIComponent(keyword.value)}` : ''
  documents.value = await request<KnowledgeDocument[]>('get', `/documents${query}`)
  selected.value ||= documents.value[0] || null
}

function handleFile(file: UploadFile) {
  selectedFile.value = file.raw || null
}

function clearFile() {
  selectedFile.value = null
}

async function upload() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文档')
    return
  }
  loading.value = true
  try {
    const form = new FormData()
    if (title.value.trim()) form.append('title', title.value.trim())
    if (isAdmin) form.append('visibility', visibility.value)
    form.append('file', selectedFile.value)
    if (isAdmin) {
      await uploadAdminDocument(form)
    } else {
      await postForm('/doctor/documents', form)
    }
    title.value = ''
    selectedFile.value = null
    await loadDocuments()
    ElMessage.success('文档已上传')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    loading.value = false
  }
}

async function download(row: KnowledgeDocument) {
  const response = await downloadDocument(row.id)
  const blobUrl = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = `${row.title}.${row.fileType || 'txt'}`
  link.click()
  URL.revokeObjectURL(blobUrl)
}

async function remove(id: number) {
  await ElMessageBox.confirm('确认删除该知识文档？', '删除文档', { type: 'warning' })
  await request('delete', `/admin/documents/${id}`)
  documents.value = documents.value.filter((item) => item.id !== id)
  if (selected.value?.id === id) selected.value = documents.value[0] || null
  ElMessage.success('文档已删除')
}

function visibilityText(value?: string) {
  if (value === 'DOCTOR_ONLY') return '医生专用'
  if (value === 'ADMIN_ONLY') return '管理员'
  if (value === 'DRAFT') return '草稿'
  return '公开'
}
</script>
