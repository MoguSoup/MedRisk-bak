<template>
  <section class="view-stack graph-visualization-view">
    <section class="panel graph-toolbar">
      <div class="panel-title">
        <h3>图谱可视化</h3>
        <el-button :icon="Refresh" @click="loadGraph">刷新</el-button>
      </div>
      <el-form label-position="top" class="admin-form-grid">
        <el-form-item label="关键词">
          <el-input v-model="keyword" clearable placeholder="搜索疾病、症状、治疗方法" />
        </el-form-item>
        <el-form-item label="布局">
          <el-segmented v-model="layout" :options="['force', 'circular']" />
        </el-form-item>
        <el-form-item label="展示模式">
          <el-segmented v-model="visualMode" :options="['精简', '标准']" />
        </el-form-item>
        <el-form-item label="节点文字">
          <el-switch v-model="showLabels" active-text="显示" inactive-text="隐藏" />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-select v-model="selectedNodeTypes" multiple collapse-tags clearable placeholder="全部节点类型">
            <el-option v-for="item in nodeTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="关系类型">
          <el-select v-model="selectedRelationshipTypes" multiple collapse-tags clearable placeholder="全部关系类型">
            <el-option v-for="item in relationshipTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="sourceName" clearable placeholder="全部来源">
            <el-option v-for="item in sourceNames" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="visibility" clearable placeholder="全部可见范围">
            <el-option label="公开" value="PUBLIC" />
            <el-option v-if="role !== 'PATIENT'" label="医生专用" value="DOCTOR_ONLY" />
            <el-option v-if="role === 'ADMIN'" label="管理员" value="ADMIN_ONLY" />
            <el-option v-if="role !== 'PATIENT'" label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
      </el-form>
    </section>

    <div class="metric-grid compact">
      <div class="metric-card mini">
        <span>节点</span>
        <strong>{{ summary.nodeCount || 0 }}</strong>
      </div>
      <div class="metric-card mini teal">
        <span>关系</span>
        <strong>{{ summary.relationshipCount || 0 }}</strong>
      </div>
      <div class="metric-card mini purple">
        <span>节点类型</span>
        <strong>{{ nodeTypes.length }}</strong>
      </div>
      <div class="metric-card mini warn">
        <span>关系类型</span>
        <strong>{{ relationshipTypes.length }}</strong>
      </div>
      <div class="metric-card mini">
        <span>来源</span>
        <strong>{{ sourceNames.length }}</strong>
      </div>
    </div>

    <section class="panel graph-visual-panel">
      <ThreeGraphScene
        title="Medical knowledge graph"
        :nodes="nodes"
        :relationships="relationships"
        :node-types="nodeTypes"
        :layout="layout"
        :visual-mode="visualMode"
        :show-labels="showLabels"
      />
      <p v-if="summary.message" class="muted-line">{{ summary.message }}</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import ThreeGraphScene from './ThreeGraphScene.vue'
import { request } from '../api/client'

const props = defineProps<{ role: string }>()

type GraphNode = { id: string; name: string; type: string; description?: string; degree?: number; sourceName?: string; visibility?: string }
type GraphRelationship = { source: string; target: string; label: string; type: string; sourceName?: string; visibility?: string }

const keyword = ref('')
const layout = ref<'force' | 'circular'>('force')
const visualMode = ref<'精简' | '标准'>('精简')
const showLabels = ref(true)
const selectedNodeTypes = ref<string[]>([])
const selectedRelationshipTypes = ref<string[]>([])
const sourceName = ref('')
const visibility = ref('')
const nodes = ref<GraphNode[]>([])
const relationships = ref<GraphRelationship[]>([])
const nodeTypes = ref<string[]>([])
const relationshipTypes = ref<string[]>([])
const sourceNames = ref<string[]>([])
const summary = ref<Record<string, any>>({})
const role = props.role
let loadTimer: number | undefined

onMounted(loadGraph)
watch([keyword, selectedNodeTypes, selectedRelationshipTypes, sourceName, visibility, visualMode], scheduleLoadGraph)

onBeforeUnmount(() => {
  if (loadTimer) window.clearTimeout(loadTimer)
})

function scheduleLoadGraph() {
  if (loadTimer) window.clearTimeout(loadTimer)
  loadTimer = window.setTimeout(loadGraph, 300)
}

async function loadGraph() {
  const params = new URLSearchParams()
  if (keyword.value) params.set('keyword', keyword.value)
  selectedNodeTypes.value.forEach((item) => params.append('nodeTypes', item))
  selectedRelationshipTypes.value.forEach((item) => params.append('relationshipTypes', item))
  if (sourceName.value) params.set('sourceName', sourceName.value)
  if (visibility.value) params.set('visibility', visibility.value)
  params.set('limit', visualMode.value === '精简' ? '80' : '160')
  const endpoint = role === 'ADMIN' ? '/admin/knowledge-graph/visualization' : '/knowledge-graph/visualization'
  const data = await request<Record<string, any>>('get', `${endpoint}?${params.toString()}`)
  nodes.value = data.nodes || []
  relationships.value = data.relationships || []
  nodeTypes.value = data.nodeTypes || []
  relationshipTypes.value = data.relationshipTypes || []
  sourceNames.value = data.sourceNames || []
  summary.value = data.summary || {}
}

</script>
