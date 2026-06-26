<template>
  <section class="view-stack">
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
        <el-form-item label="节点类型">
          <el-select v-model="selectedNodeTypes" multiple collapse-tags clearable>
            <el-option v-for="item in nodeTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="关系类型">
          <el-select v-model="selectedRelationshipTypes" multiple collapse-tags clearable>
            <el-option v-for="item in relationshipTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="sourceName" clearable>
            <el-option v-for="item in sourceNames" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="visibility" clearable>
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
      <DashboardChart title="医疗健康知识图谱" :option="chartOption" />
      <p v-if="summary.message" class="muted-line">{{ summary.message }}</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import DashboardChart from './DashboardChart.vue'
import { request } from '../api/client'

const props = defineProps<{ role: string }>()

type GraphNode = { id: string; name: string; type: string; description?: string; degree?: number; sourceName?: string; visibility?: string }
type GraphRelationship = { source: string; target: string; label: string; type: string; sourceName?: string; visibility?: string }

const keyword = ref('')
const layout = ref<'force' | 'circular'>('force')
const visualMode = ref<'精简' | '标准'>('精简')
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

const sciPalette = ['#3b6fb6', '#d9902f', '#4f9d69', '#8e63a9', '#c94c4c', '#3b9da6', '#7a8793', '#b07d3c', '#6b74c8', '#8aa33f']
const typeSymbol: Record<string, string> = {
  Disease: 'circle',
  Symptom: 'diamond',
  Exam: 'rect',
  Examination: 'rect',
  Drug: 'triangle',
  Treatment: 'path://M0,-12 L10,-6 L10,6 L0,12 L-10,6 L-10,-6 Z',
  Department: 'roundRect',
  Document: 'rect',
  PatientSynthetic: 'circle',
  Patient: 'circle',
  RiskFactor: 'pin',
  BodyPart: 'roundRect',
  TimePoint: 'arrow'
}

function colorForType(type: string) {
  const index = Math.abs(Array.from(type || 'Entity').reduce((sum, ch) => sum + ch.charCodeAt(0), 0)) % sciPalette.length
  return sciPalette[index]
}

function symbolForType(type: string) {
  return typeSymbol[type] || 'circle'
}

const categories = computed(() => nodeTypes.value.map((name) => ({
  name,
  itemStyle: { color: colorForType(name) }
})))

const chartOption = computed(() => ({
  backgroundColor: '#ffffff',
  tooltip: {
    backgroundColor: 'rgba(255,255,255,0.96)',
    borderColor: '#d1d5db',
    borderWidth: 1,
    textStyle: { color: '#111827', fontSize: 12 },
    formatter: (params: any) => {
      if (params.dataType === 'edge') return `${params.data.label || params.data.type}<br/>${params.data.sourceName || ''}`
      return `${params.data.name}<br/>${params.data.type}<br/>来源：${params.data.sourceName || '-'}<br/>可见性：${params.data.visibility || 'PUBLIC'}<br/>连接数：${params.data.value || 0}`
    }
  },
  legend: [{ data: nodeTypes.value, bottom: 0, type: 'scroll', textStyle: { color: '#111827' } }],
  series: [
    {
      type: 'graph',
      layout: layout.value,
      roam: true,
      draggable: visualMode.value === '标准',
      animation: visualMode.value === '标准',
      animationDurationUpdate: visualMode.value === '标准' ? 450 : 0,
      categories: categories.value,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: visualMode.value === '精简' ? 5 : 8,
      label: { show: true, position: 'right', formatter: '{b}', color: '#111827', fontSize: 11 },
      emphasis: { focus: 'adjacency', lineStyle: { color: '#1f4e79', width: 2.4 }, label: { color: '#000000', fontWeight: 600 } },
      lineStyle: { color: 'rgba(75, 85, 99, 0.34)', width: 1, curveness: 0.08 },
      force: {
        repulsion: visualMode.value === '精简' ? 260 : 420,
        gravity: visualMode.value === '精简' ? 0.12 : 0.08,
        edgeLength: visualMode.value === '精简' ? 95 : 125,
        friction: 0.62
      },
      data: nodes.value.map((node) => ({
        id: node.id,
        name: node.name,
        category: node.type,
        value: node.degree || 0,
        symbol: symbolForType(node.type),
        symbolSize: Math.max(20, Math.min(48, 20 + Math.sqrt(node.degree || 1) * 7)),
        type: node.type,
        description: node.description,
        sourceName: node.sourceName,
        visibility: node.visibility,
        itemStyle: {
          color: node.type.includes('Patient') ? '#ffffff' : colorForType(node.type),
          borderColor: colorForType(node.type),
          borderWidth: node.type.includes('Patient') ? 2.4 : 1.2
        },
        label: { color: '#111827' }
      })),
      links: relationships.value.map((rel) => ({
        source: rel.source,
        target: rel.target,
        name: rel.label,
        label: { show: false, formatter: rel.label, color: '#111827', fontSize: 10 },
        emphasis: { label: { show: true, color: '#111827', fontWeight: 600 } },
        type: rel.type,
        sourceName: rel.sourceName,
        visibility: rel.visibility
      }))
    }
  ]
}))
</script>
