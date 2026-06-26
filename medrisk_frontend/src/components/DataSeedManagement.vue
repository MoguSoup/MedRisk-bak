<template>
  <section class="view-stack">
    <section class="panel">
      <div class="panel-title">
        <h3>数据源 / 演示数据包</h3>
        <div class="table-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadStatus">刷新</el-button>
          <el-button type="primary" :icon="Upload" :loading="loading" @click="importPack">补齐演示数据</el-button>
          <el-button type="success" :icon="Connection" :loading="loading" @click="rebuildPack">重建并写入图谱</el-button>
        </div>
      </div>
      <div class="metric-grid compact">
        <div class="metric-card mini">
          <span>疾病</span>
          <strong>{{ counts.diseases || 0 }}/{{ targets.diseases || 50 }}</strong>
        </div>
        <div class="metric-card mini teal">
          <span>文档</span>
          <strong>{{ counts.documents || 0 }}/{{ targets.documents || 100 }}</strong>
        </div>
        <div class="metric-card mini purple">
          <span>合成病历</span>
          <strong>{{ counts.medicalCases || 0 }}/{{ targets.medicalCases || 50 }}</strong>
        </div>
        <div class="metric-card mini warn">
          <span>数据集</span>
          <strong>{{ counts.datasets || 0 }}/{{ targets.datasets || 6 }}</strong>
        </div>
      </div>
      <p class="disclaimer">{{ lastRun?.message || '演示数据仅用于教学和 GraphRAG 检索效果展示，不作为真实诊疗知识库。' }}</p>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>公开数据源</h3>
        <el-tag type="info">{{ sources.length }} 个来源</el-tag>
      </div>
      <el-table :data="sources" empty-text="暂无来源">
        <el-table-column prop="name" label="来源" min-width="180" />
        <el-table-column prop="license" label="授权 / 使用说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="链接" min-width="260">
          <template #default="{ row }">
            <a :href="row.url" target="_blank" rel="noreferrer">{{ row.url }}</a>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>导入运行记录</h3>
        <el-tag>{{ lastRun?.status || '未运行' }}</el-tag>
      </div>
      <el-table :data="recentRuns" empty-text="暂无导入记录">
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="diseaseCount" label="疾病" width="80" />
        <el-table-column prop="documentCount" label="文档" width="80" />
        <el-table-column prop="caseCount" label="病历" width="80" />
        <el-table-column prop="datasetCount" label="数据集" width="90" />
        <el-table-column prop="graphRelationshipCount" label="图谱关系" width="100" />
        <el-table-column prop="message" label="消息" min-width="240" show-overflow-tooltip />
        <el-table-column prop="finishedAt" label="完成时间" min-width="170" />
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Connection, Refresh, Upload } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { request } from '../api/client'

type SeedRun = Record<string, any>
type SeedStatus = {
  counts?: Record<string, number>
  targets?: Record<string, number>
  sources?: Array<Record<string, string>>
  lastRun?: SeedRun | null
  recentRuns?: SeedRun[]
}

const loading = ref(false)
const status = ref<SeedStatus>({})
const counts = computed(() => status.value.counts || {})
const targets = computed(() => status.value.targets || {})
const sources = computed(() => status.value.sources || [])
const lastRun = computed(() => status.value.lastRun || null)
const recentRuns = computed(() => status.value.recentRuns || [])

onMounted(loadStatus)

async function loadStatus() {
  loading.value = true
  try {
    status.value = await request<SeedStatus>('get', '/admin/data-seeds/status')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据源状态加载失败')
  } finally {
    loading.value = false
  }
}

async function importPack() {
  await runSeed('/admin/data-seeds/import', '演示数据已补齐')
}

async function rebuildPack() {
  await runSeed('/admin/data-seeds/rebuild-demo-pack', '演示数据已重建，图谱写入任务已尝试执行')
}

async function runSeed(endpoint: string, message: string) {
  loading.value = true
  try {
    await request('post', endpoint)
    await loadStatus()
    ElMessage.success(message)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '演示数据操作失败')
  } finally {
    loading.value = false
  }
}
</script>
