<template>
  <div class="three-chart-shell three-graph-scene" :aria-label="title">
    <div ref="hostRef" class="three-chart-canvas"></div>
    <div v-if="!webglReady" class="three-fallback graph-fallback">
      <strong>{{ title }}</strong>
      <span v-for="node in nodes.slice(0, 12)" :key="node.id">
        {{ node.name }} <b>{{ node.type }}</b>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import ForceGraph3D from '3d-force-graph'
import * as THREE from 'three'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { canUseWebGL } from '../utils/threeSupport'

type GraphNode = { id: string; name: string; type: string; degree?: number; sourceName?: string; visibility?: string }
type GraphRelationship = { source: string; target: string; label: string; type: string }
type ForceGraphApi = ReturnType<typeof ForceGraph3D>

const props = defineProps<{
  title: string
  nodes: GraphNode[]
  relationships: GraphRelationship[]
  nodeTypes: string[]
  layout: 'force' | 'circular'
  visualMode: string
  showLabels: boolean
}>()

const hostRef = ref<HTMLDivElement>()
const webglReady = ref(false)

let graph: ForceGraphApi | undefined
let resizeObserver: ResizeObserver | undefined

const palette = ['#3b6fb6', '#d9902f', '#4f9d69', '#8e63a9', '#c94c4c', '#3b9da6', '#7a8793', '#b07d3c', '#6b74c8', '#8aa33f']

function initGraph() {
  if (!hostRef.value || !canUseWebGL()) return
  webglReady.value = true
  graph = ForceGraph3D({ controlType: 'orbit' })(hostRef.value)
    .backgroundColor('rgba(0,0,0,0)')
    .nodeLabel((node: any) => `${node.name || node.id}<br>${node.type || 'Entity'}`)
    .nodeThreeObject((node: any) => nodeObject(node))
    .linkLabel((link: any) => link.label || link.type || '')
    .linkColor(() => 'rgba(148, 163, 184, 0.62)')
    .linkOpacity(0.42)
    .linkDirectionalParticleColor(() => '#38bdf8')
    .linkDirectionalParticleWidth(() => props.visualMode === '精简' ? 0 : 1.4)
    .linkDirectionalParticles(() => props.visualMode === '精简' ? 0 : 1)
    .showNavInfo(false)

  const charge = (graph as any).d3Force?.('charge')
  charge?.strength?.(-80)
  resizeGraph()
  renderGraph()

  if (hostRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(resizeGraph)
    resizeObserver.observe(hostRef.value)
  }
  window.addEventListener('resize', resizeGraph)
}

function renderGraph() {
  if (!graph) return
  graph.graphData({
    nodes: graphNodes(),
    links: props.relationships.map((item) => ({
      source: String(item.source),
      target: String(item.target),
      label: item.label,
      type: item.type
    }))
  })
  graph.cooldownTicks(props.layout === 'circular' ? 0 : 120)
}

function graphNodes() {
  const count = Math.max(props.nodes.length, 1)
  const radius = Math.min(90, Math.max(34, count * 1.2))
  return props.nodes.map((node, index) => {
    if (props.layout !== 'circular') {
      return { ...node, id: String(node.id) }
    }
    const angle = (index / count) * Math.PI * 2
    return {
      ...node,
      id: String(node.id),
      fx: Math.cos(angle) * radius,
      fy: Math.sin(index * 0.72) * 18,
      fz: Math.sin(angle) * radius
    }
  })
}

function colorForType(type: string) {
  const index = Math.abs(Array.from(type || 'Entity').reduce((sum, ch) => sum + ch.charCodeAt(0), 0)) % palette.length
  return palette[index]
}

function nodeObject(node: GraphNode) {
  const group = new THREE.Group()
  const size = Math.max(2, Math.min(14, 3 + Math.sqrt(Number(node.degree || 1))))
  const geometry = new THREE.SphereGeometry(size, 18, 18)
  const material = new THREE.MeshLambertMaterial({ color: colorForType(node.type), transparent: true, opacity: 0.92 })
  group.add(new THREE.Mesh(geometry, material))
  if (props.showLabels && shouldShowLabel(node)) {
    const sprite = labelSprite(node.name || node.id, colorForType(node.type))
    sprite.position.set(size + 3, size + 3, 0)
    group.add(sprite)
  }
  return group
}

function shouldShowLabel(node: GraphNode) {
  const degree = Number(node.degree || 0)
  const index = props.nodes.findIndex((item) => String(item.id) === String(node.id))
  const limit = props.visualMode === '精简' ? 18 : 36
  return degree >= 2 || index >= 0 && index < limit
}

function labelSprite(label: string, color: string) {
  const text = compactLabel(label)
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')!
  const fontSize = 34
  context.font = `${fontSize}px Microsoft YaHei, PingFang SC, Arial`
  const width = Math.ceil(context.measureText(text).width + 34)
  canvas.width = Math.max(96, width)
  canvas.height = 58
  context.font = `${fontSize}px Microsoft YaHei, PingFang SC, Arial`
  context.fillStyle = 'rgba(8, 24, 38, 0.82)'
  roundRect(context, 0, 5, canvas.width, 46, 12)
  context.fill()
  context.strokeStyle = color
  context.lineWidth = 2
  roundRect(context, 1, 6, canvas.width - 2, 44, 12)
  context.stroke()
  context.fillStyle = '#eff6ff'
  context.fillText(text, 17, 39)
  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture, transparent: true }))
  sprite.scale.set(canvas.width / 6, canvas.height / 6, 1)
  return sprite
}

function compactLabel(label: string) {
  const value = String(label || '')
  return value.length > 12 ? `${value.slice(0, 12)}...` : value
}

function roundRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  context.beginPath()
  context.moveTo(x + radius, y)
  context.arcTo(x + width, y, x + width, y + height, radius)
  context.arcTo(x + width, y + height, x, y + height, radius)
  context.arcTo(x, y + height, x, y, radius)
  context.arcTo(x, y, x + width, y, radius)
  context.closePath()
}

function resizeGraph() {
  if (!hostRef.value || !graph) return
  const rect = hostRef.value.getBoundingClientRect()
  graph.width(Math.max(rect.width, 1)).height(Math.max(rect.height, 1))
}

onMounted(initGraph)

watch(() => [props.nodes, props.relationships, props.layout, props.showLabels], renderGraph, { deep: true })
watch(() => props.visualMode, () => {
  if (!graph) return
  graph
    .linkDirectionalParticleWidth(() => props.visualMode === '精简' ? 0 : 1.4)
    .linkDirectionalParticles(() => props.visualMode === '精简' ? 0 : 1)
  renderGraph()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeGraph)
  resizeObserver?.disconnect()
  ;(graph as any)?._destructor?.()
  if (hostRef.value) hostRef.value.innerHTML = ''
})
</script>
