<template>
  <div ref="chartRef" class="training-chart" aria-label="训练过程曲线"></div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  history: Record<string, number[]>
}>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

function renderChart() {
  if (!chartRef.value) return
  chart ||= echarts.init(chartRef.value)
  const trainLogloss = props.history.trainLogloss || []
  const validLogloss = props.history.validLogloss || []
  const trainError = props.history.trainError || []
  const validError = props.history.validError || []
  const length = Math.max(trainLogloss.length, validLogloss.length, trainError.length, validError.length, 1)
  chart.setOption({
    grid: { left: 52, right: 24, top: 34, bottom: 38 },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#475569' } },
    xAxis: {
      type: 'category',
      data: Array.from({ length }, (_, index) => `${index + 1}`),
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#e2e8f0' } }
    },
    series: [
      lineSeries('训练 Logloss', trainLogloss, '#2563eb'),
      lineSeries('验证 Logloss', validLogloss, '#dc2626'),
      lineSeries('训练 Error', trainError, '#0f766e'),
      lineSeries('验证 Error', validError, '#9333ea')
    ]
  })
}

function lineSeries(name: string, data: number[], color: string) {
  return {
    name,
    type: 'line',
    smooth: true,
    showSymbol: false,
    data,
    lineStyle: { width: 2, color },
    itemStyle: { color }
  }
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', renderChart)
})

watch(() => props.history, renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderChart)
  chart?.dispose()
})
</script>
