<template>
  <div class="dashboard-chart flat-dashboard-chart" :aria-label="title">
    <div ref="hostRef" class="dashboard-chart-canvas"></div>
    <div v-if="fallbackRows.length" class="dashboard-chart-fallback">
      <span v-for="item in fallbackRows" :key="`${item.name}-${item.value}`">
        {{ item.name }} <b>{{ item.value }}</b>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type ChartRow = { name: string; value: number; series?: string }

const props = defineProps<{
  title: string
  option: Record<string, any>
}>()

const hostRef = ref<HTMLDivElement>()
const fallbackRows = computed(() => chartRows().slice(0, 8))
let chart: echarts.ECharts | undefined
let resizeObserver: ResizeObserver | undefined

onMounted(() => {
  if (!hostRef.value) return
  chart = echarts.init(hostRef.value, undefined, { renderer: 'canvas' })
  renderChart()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(hostRef.value)
  }
  window.addEventListener('resize', resizeChart)
})

watch(() => props.option, renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  resizeObserver?.disconnect()
  chart?.dispose()
})

function renderChart() {
  if (!chart) return
  const option = withDynamicStyle(props.option || {})
  chart.setOption(option, true)
}

function resizeChart() {
  chart?.resize()
}

function withDynamicStyle(option: Record<string, any>) {
  const cloned = structuredCloneSafe(option)
  cloned.animation = cloned.animation ?? true
  cloned.animationDuration = cloned.animationDuration ?? 900
  cloned.animationEasing = cloned.animationEasing ?? 'cubicOut'
  cloned.textStyle = { color: '#dbeafe', ...(cloned.textStyle || {}) }

  const series = Array.isArray(cloned.series) ? cloned.series : []
  cloned.series = series.map((entry: any, index: number) => {
    const next = { ...entry }
    next.animation = next.animation ?? true
    if (next.type === 'line') {
      next.smooth = next.smooth ?? true
      next.symbol = next.symbol ?? 'circle'
      next.symbolSize = next.symbolSize ?? 7
      next.lineStyle = { width: 3, shadowBlur: 12, shadowColor: next.itemStyle?.color || '#38bdf8', ...(next.lineStyle || {}) }
      next.areaStyle = next.areaStyle ?? { opacity: 0.13 }
    }
    if (next.type === 'bar') {
      next.barMaxWidth = next.barMaxWidth ?? 28
      next.itemStyle = {
        borderRadius: [6, 6, 0, 0],
        color: next.itemStyle?.color || barGradient(index),
        shadowBlur: 10,
        shadowColor: 'rgba(56, 189, 248, 0.35)',
        ...(next.itemStyle || {})
      }
    }
    if (next.type === 'pie') {
      next.radius = next.radius || ['42%', '68%']
      next.avoidLabelOverlap = true
      next.label = {
        color: '#dbeafe',
        fontSize: 12,
        formatter: '{b}',
        ...(next.label || {})
      }
      next.itemStyle = { borderColor: '#06172c', borderWidth: 2, ...(next.itemStyle || {}) }
    }
    return next
  })

  return cloned
}

function barGradient(index: number) {
  const palettes = [
    ['#67e8f9', '#2563eb'],
    ['#86efac', '#0f766e'],
    ['#fde68a', '#d97706'],
    ['#fca5a5', '#dc2626']
  ]
  const [top, bottom] = palettes[index % palettes.length]
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: top },
    { offset: 1, color: bottom }
  ])
}

function structuredCloneSafe(value: Record<string, any>) {
  if (typeof structuredClone === 'function') {
    try {
      return structuredClone(value)
    } catch {
      return clonePlain(value)
    }
  }
  return clonePlain(value)
}

function clonePlain(value: Record<string, any>) {
  return JSON.parse(JSON.stringify(value || {}))
}

function chartRows(): ChartRow[] {
  const series = Array.isArray(props.option.series) ? props.option.series : []
  const xAxisData = Array.isArray(props.option.xAxis?.data) ? props.option.xAxis.data : []
  return series.flatMap((entry: any) => {
    if (Array.isArray(entry.data) && entry.data.length && typeof entry.data[0] === 'object') {
      return entry.data.map((item: any, index: number) => ({
        name: String(item.name || xAxisData[index] || entry.name || `项目 ${index + 1}`),
        value: Number(item.value || 0),
        series: entry.name
      }))
    }
    return (entry.data || []).map((value: number, index: number) => ({
      name: String(xAxisData[index] || entry.name || `项目 ${index + 1}`),
      value: Number(value || 0),
      series: entry.name
    }))
  })
}
</script>
