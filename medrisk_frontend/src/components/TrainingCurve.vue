<template>
  <section class="training-report" aria-label="Training evaluation report">
    <div class="training-report-metrics">
      <span v-for="item in metricCards" :key="item.label">
        <small>{{ item.label }}</small>
        <strong>{{ item.value }}</strong>
      </span>
    </div>
    <div ref="chartRef" class="training-report-chart"></div>
    <div class="training-report-bottom">
      <div>
        <h4>Confusion Matrix</h4>
        <div class="confusion-grid">
          <span></span><b>Pred 0</b><b>Pred 1</b>
          <b>True 0</b><strong>{{ confusion[0][0] }}</strong><strong>{{ confusion[0][1] }}</strong>
          <b>True 1</b><strong>{{ confusion[1][0] }}</strong><strong>{{ confusion[1][1] }}</strong>
        </div>
      </div>
      <div class="training-report-note">
        <h4>Evaluation Note</h4>
        <p>{{ note }}</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  history: Record<string, number[]>
  metrics?: Record<string, any>
}>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
let resizeObserver: ResizeObserver | undefined

const metricCards = computed(() => [
  { label: 'Accuracy', value: metric(props.metrics?.accuracy) },
  { label: 'Sensitivity', value: metric(props.metrics?.sensitivity ?? props.metrics?.recall) },
  { label: 'Specificity', value: metric(props.metrics?.specificity) },
  { label: 'F1', value: metric(props.metrics?.f1) },
  { label: 'ROC-AUC', value: metric(props.metrics?.auc) },
  { label: 'PR-AUC', value: metric(props.metrics?.prAuc) },
  { label: 'LogLoss', value: metric(props.metrics?.logLoss) },
  { label: 'Brier', value: metric(props.metrics?.brierScore) }
])

const confusion = computed(() => {
  const matrix = props.metrics?.confusionMatrix
  if (Array.isArray(matrix) && matrix.length >= 2 && Array.isArray(matrix[0]) && Array.isArray(matrix[1])) {
    return [
      [Number(matrix[0][0] || 0), Number(matrix[0][1] || 0)],
      [Number(matrix[1][0] || 0), Number(matrix[1][1] || 0)]
    ]
  }
  return [[0, 0], [0, 0]]
})

const note = computed(() => {
  const sampleCount = props.metrics?.sampleCount
  const dataset = props.metrics?.evaluationDataset || props.metrics?.datasetSource
  if (sampleCount && dataset) return `External evaluation on ${sampleCount} samples from ${dataset}.`
  if (sampleCount) return `Hold-out validation on ${sampleCount} samples.`
  return 'Curves are shown when the model service returns probability-based evaluation data.'
})

onMounted(() => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, undefined, { renderer: 'canvas' })
  render()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartRef.value)
  }
  window.addEventListener('resize', resize)
})

watch(() => [props.history, props.metrics], render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  resizeObserver?.disconnect()
  chart?.dispose()
})

function render() {
  if (!chart) return
  chart.setOption(chartOption(), true)
}

function resize() {
  chart?.resize()
}

function chartOption() {
  const roc = curveData(props.metrics?.rocCurve, 'fpr', 'tpr')
  const pr = curveData(props.metrics?.prCurve, 'recall', 'precision')
  const cal = curveData(props.metrics?.calibrationCurve, 'predicted', 'observed')
  const historySeries = historyData()
  const hasProbabilityCurves = roc.length || pr.length || cal.length
  const series = hasProbabilityCurves
    ? [
        line('ROC', roc, '#2563eb'),
        line('PR', pr, '#059669'),
        line('Calibration', cal, '#dc2626')
      ]
    : historySeries

  return {
    animation: true,
    animationDuration: 800,
    color: ['#2563eb', '#059669', '#dc2626', '#7c3aed'],
    tooltip: { trigger: 'axis' },
    legend: { top: 4, textStyle: { color: '#334155', fontWeight: 600 } },
    grid: { left: 48, right: 22, top: 48, bottom: 42 },
    xAxis: {
      type: 'value',
      min: 0,
      max: hasProbabilityCurves ? 1 : undefined,
      name: hasProbabilityCurves ? 'Rate' : 'Iteration',
      nameTextStyle: { color: '#64748b' },
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#e2e8f0' } }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: hasProbabilityCurves ? 1 : undefined,
      name: hasProbabilityCurves ? 'Score' : 'Value',
      nameTextStyle: { color: '#64748b' },
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#e2e8f0' } }
    },
    series
  }
}

function historyData() {
  const configs = [
    ['Train LogLoss', props.history.trainLogloss || [], '#2563eb'],
    ['Valid LogLoss', props.history.validLogloss || [], '#dc2626'],
    ['Train Error', props.history.trainError || [], '#059669'],
    ['Valid Error', props.history.validError || [], '#7c3aed']
  ] as const
  return configs
    .filter(([, values]) => values.length)
    .map(([name, values, color]) => line(name, values.map((value, index) => [index + 1, Number(value)]), color))
}

function curveData(rows: any, xKey: string, yKey: string) {
  if (!Array.isArray(rows)) return []
  return rows.map((row) => [Number(row[xKey] || 0), Number(row[yKey] || 0)])
}

function line(name: string, data: number[][], color: string) {
  return {
    name,
    type: 'line',
    smooth: true,
    showSymbol: false,
    data,
    lineStyle: { width: 3, color },
    areaStyle: { opacity: 0.08, color },
    emphasis: { focus: 'series' }
  }
}

function metric(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return String(value)
  return parsed.toFixed(3).replace(/0+$/, '').replace(/\.$/, '')
}
</script>

<style scoped>
.training-report {
  display: grid;
  gap: 16px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
}

.training-report-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(104px, 1fr));
  gap: 10px;
}

.training-report-metrics span {
  min-height: 68px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.training-report-metrics small {
  color: #64748b;
  font-size: 12px;
}

.training-report-metrics strong {
  color: #0f2742;
  font-size: 22px;
}

.training-report-chart {
  height: 300px;
  min-height: 300px;
}

.training-report-bottom {
  display: grid;
  grid-template-columns: minmax(180px, 260px) minmax(0, 1fr);
  gap: 16px;
}

.training-report-bottom h4 {
  margin: 0 0 10px;
  color: #0f2742;
  font-size: 15px;
}

.confusion-grid {
  display: grid;
  grid-template-columns: 76px repeat(2, 1fr);
  border: 1px solid #dbeafe;
  border-radius: 8px;
  overflow: hidden;
}

.confusion-grid > * {
  min-height: 40px;
  display: grid;
  place-items: center;
  border-right: 1px solid #dbeafe;
  border-bottom: 1px solid #dbeafe;
  color: #334155;
  background: #ffffff;
}

.confusion-grid > :nth-child(3n) {
  border-right: 0;
}

.confusion-grid > :nth-last-child(-n + 3) {
  border-bottom: 0;
}

.confusion-grid b {
  background: #eff6ff;
  font-weight: 700;
}

.confusion-grid strong {
  color: #0f766e;
  font-size: 18px;
}

.training-report-note {
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.training-report-note p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

@media (max-width: 760px) {
  .training-report-bottom {
    grid-template-columns: 1fr;
  }
}
</style>
