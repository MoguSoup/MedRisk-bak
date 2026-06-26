<template>
  <div ref="chartRef" class="factor-chart" aria-label="主要风险因素图表"></div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  factors: Array<{ label: string; impact: number }>
}>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

function renderChart() {
  if (!chartRef.value) return
  chart ||= echarts.init(chartRef.value)
  chart.setOption({
    grid: { left: 120, right: 18, top: 16, bottom: 24 },
    xAxis: { type: 'value', max: 1, axisLabel: { color: '#64748b' } },
    yAxis: {
      type: 'category',
      data: props.factors.map((item) => item.label),
      axisLabel: { color: '#334155', width: 108, overflow: 'truncate' }
    },
    series: [
      {
        type: 'bar',
        data: props.factors.map((item) => item.impact),
        barWidth: 14,
        itemStyle: { color: '#14b8a6', borderRadius: [0, 4, 4, 0] }
      }
    ],
    tooltip: { trigger: 'axis' }
  })
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', renderChart)
})

watch(() => props.factors, renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderChart)
  chart?.dispose()
})
</script>
