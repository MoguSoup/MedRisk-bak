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
          <el-button type="primary" :loading="loading" @click="incremental">增量构建</el-button>
          <el-button type="danger" :loading="loading" @click="rebuild">全量重建</el-button>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>文档构建状态</h3>
        <el-tag type="info">仅处理 MedRisk 写入的图谱数据</el-tag>
      </div>
      <el-table :data="documents" empty-text="暂无文档">
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="graphStatus" label="图谱状态" width="120" />
        <el-table-column prop="createdAt" label="上传时间" min-width="170" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text type="primary" :loading="loading" @click="sync(row.id)">同步</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>构建任务</h3>
        <el-tag>{{ jobs.length }} 条</el-tag>
      </div>
      <el-table :data="jobs" empty-text="暂无任务">
        <el-table-column prop="jobType" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="progress" label="进度" width="90" />
        <el-table-column prop="nodesCreated" label="节点" width="80" />
        <el-table-column prop="relationshipsCreated" label="关系" width="80" />
        <el-table-column prop="message" label="消息" min-width="260" />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { request } from '../api/client'

type KnowledgeDocument = { id: number; title: string; graphStatus: string; createdAt: string }
type GraphJob = { id: number; jobType: string; status: string; progress: number; nodesCreated: number; relationshipsCreated: number; message: string; createdAt: string }
type GraphHealth = { connected?: boolean; status?: string; message?: string; database?: string }

const props = defineProps<{ health?: GraphHealth }>()
const emit = defineEmits<{ (event: 'refresh-health'): void }>()
const graphHealth = computed(() => props.health || {})
const documents = ref<KnowledgeDocument[]>([])
const jobs = ref<GraphJob[]>([])
const loading = ref(false)

onMounted(loadAll)

async function loadAll() {
  emit('refresh-health')
  const [docData, jobData] = await Promise.all([
    request<KnowledgeDocument[]>('get', '/documents'),
    request<GraphJob[]>('get', '/admin/knowledge-graph/jobs')
  ])
  documents.value = docData
  jobs.value = jobData
}

async function run(action: () => Promise<unknown>, message: string) {
  loading.value = true
  try {
    await action()
    await loadAll()
    ElMessage.success(message)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图谱任务失败')
  } finally {
    loading.value = false
  }
}

async function sync(id: number) {
  await run(() => request('post', `/admin/knowledge-graph/sync/${id}`), '文档同步任务完成')
}

async function incremental() {
  await run(() => request('post', '/admin/knowledge-graph/incremental'), '增量构建完成')
}

async function rebuild() {
  await ElMessageBox.confirm('全量重建会清理 MedRisk 已写入的 Neo4j 数据并重新构建，确认继续？', '全量重建', { type: 'warning' })
  await run(() => request('post', '/admin/knowledge-graph/rebuild'), '全量重建完成')
}
</script>
