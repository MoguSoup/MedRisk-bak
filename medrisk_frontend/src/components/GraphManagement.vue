<template>
  <section class="view-stack">
    <section class="panel">
      <div class="panel-title">
        <h3>知识图谱管理</h3>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
      <div class="graph-health">
        <div>
          <span>Neo4j 状态</span>
          <strong :class="{ ok: graphHealth.connected }">{{ graphHealth.connected ? '已连接' : '未连接' }}</strong>
          <p>{{ graphHealth.message || graphHealth.database || 'bolt://localhost:7687' }}</p>
        </div>
        <div class="quick-actions">
          <el-button type="primary" :loading="loading" :disabled="hasRunningJob" @click="incremental">增量构建</el-button>
          <el-button type="danger" :loading="loading" :disabled="hasRunningJob" @click="rebuild">全量重建</el-button>
        </div>
      </div>
      <div v-if="runningJob" class="graph-job-progress">
        <div>
          <strong>{{ runningJob.jobType }} · {{ runningJob.status }}</strong>
          <span>{{ runningJob.message }}</span>
        </div>
        <el-progress :percentage="runningJob.progress || 0" :stroke-width="12" />
      </div>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>文档构建状态</h3>
        <el-tag type="info">仅处理 MedRisk 写入的图谱数据</el-tag>
      </div>
      <el-table v-loading="loading" :data="documents" empty-text="暂无文档">
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column label="图谱状态" width="180">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.graphError"
              effect="dark"
              :content="row.graphError"
              placement="top"
            >
              <el-tag type="danger">{{ row.graphStatus }}</el-tag>
            </el-tooltip>
            <el-tag v-else :type="row.graphStatus === '已构建' ? 'success' : row.graphStatus === '构建失败' ? 'danger' : 'info'">
              {{ row.graphStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间（北京时间）" min-width="190">
          <template #default="{ row }">{{ formatBeijingTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text type="primary" :loading="loading" :disabled="hasRunningJob" @click="sync(row.id)">同步</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>构建任务</h3>
        <el-tag>{{ jobs.length }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="jobs" empty-text="暂无任务">
        <el-table-column prop="jobType" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="进度" width="170">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column prop="nodesCreated" label="节点" width="80" />
        <el-table-column prop="relationshipsCreated" label="关系" width="80" />
        <el-table-column prop="message" label="消息" min-width="260" />
        <el-table-column label="时间（北京时间）" width="190">
          <template #default="{ row }">{{ formatBeijingTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { apiErrorMessage, request } from '../api/client'

type KnowledgeDocument = { id: number; title: string; graphStatus: string; graphError?: string; createdAt: string }
type GraphJob = {
  id: number
  jobType: string
  status: string
  progress: number
  nodesCreated: number
  relationshipsCreated: number
  processedDocuments?: number
  totalDocuments?: number
  failedDocuments?: number
  message: string
  createdAt: string
}
type GraphHealth = { connected?: boolean; status?: string; message?: string; database?: string }

const props = defineProps<{ health?: GraphHealth }>()
const emit = defineEmits<{ (event: 'refresh-health'): void }>()
const graphHealth = computed(() => props.health || {})
const documents = ref<KnowledgeDocument[]>([])
const jobs = ref<GraphJob[]>([])
const loading = ref(false)
let pollTimer: ReturnType<typeof setInterval> | undefined

const runningJob = computed(() => jobs.value.find((job) => job.status === '运行中') || null)
const hasRunningJob = computed(() => Boolean(runningJob.value))

onMounted(async () => {
  await loadAll()
  syncPolling()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

async function loadAll(showLoading = true) {
  emit('refresh-health')
  if (showLoading) loading.value = true
  try {
    const [docResult, jobResult] = await Promise.allSettled([
      request<KnowledgeDocument[]>('get', '/documents'),
      request<GraphJob[]>('get', '/admin/knowledge-graph/jobs')
    ])
    const errors: string[] = []
    if (docResult.status === 'fulfilled') {
      documents.value = docResult.value
    } else {
      const message = apiErrorMessage(docResult.reason, '文档列表加载失败')
      if (message) errors.push(message)
    }
    if (jobResult.status === 'fulfilled') {
      jobs.value = jobResult.value
    } else {
      const message = apiErrorMessage(jobResult.reason, '图谱任务加载失败')
      if (message) errors.push(message)
    }
    if (errors.length) {
      ElMessage.error(Array.from(new Set(errors)).join('；'))
    }
    syncPolling()
  } finally {
    if (showLoading) loading.value = false
  }
}

async function run(action: () => Promise<GraphJob>, message: string) {
  loading.value = true
  try {
    const job = await action()
    jobs.value = [job, ...jobs.value.filter((item) => item.id !== job.id)]
    await loadAll(false)
    syncPolling()
    ElMessage.success(message)
  } catch (error) {
    const errorMessage = apiErrorMessage(error, '图谱任务失败')
    if (errorMessage) ElMessage.error(errorMessage)
  } finally {
    loading.value = false
  }
}

function syncPolling() {
  const shouldPoll = jobs.value.some((job) => job.status === '运行中')
  if (shouldPoll && !pollTimer) {
    pollTimer = setInterval(async () => {
      await loadAll(false)
      if (!jobs.value.some((job) => job.status === '运行中') && pollTimer) {
        clearInterval(pollTimer)
        pollTimer = undefined
        await loadAll(false)
      }
    }, 1500)
  }
  if (!shouldPoll && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

async function sync(id: number) {
  await run(() => request<GraphJob>('post', `/admin/knowledge-graph/sync/${id}`), '文档同步任务已启动')
}

async function incremental() {
  await run(() => request<GraphJob>('post', '/admin/knowledge-graph/incremental'), '增量构建任务已启动')
}

async function rebuild() {
  await ElMessageBox.confirm('全量重建会清理 MedRisk 已写入的 Neo4j 数据并重新构建，确认继续？', '全量重建', { type: 'warning' })
  await run(() => request<GraphJob>('post', '/admin/knowledge-graph/rebuild'), '全量重建任务已启动')
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
