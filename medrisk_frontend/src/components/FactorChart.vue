<template>
  <div class="sci-factor-chart" aria-label="Risk factor chart">
    <div class="sci-factor-axis">
      <span>0</span>
      <span>25%</span>
      <span>50%</span>
      <span>75%</span>
      <span>100%</span>
    </div>
    <div class="sci-factor-plot">
      <div
        v-for="(item, index) in normalizedFactors"
        :key="item.label"
        class="sci-factor-row"
        :style="{ '--factor-color': palette[index % palette.length] }"
      >
        <span class="sci-factor-label">{{ item.label }}</span>
        <div class="sci-factor-track">
          <i :style="{ width: `${item.percent}%` }"></i>
        </div>
        <strong>{{ item.percent }}%</strong>
      </div>
    </div>
    <div class="sci-factor-note">Top contributing variables ranked by normalized model attribution.</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  factors: Array<{ label: string; impact: number }>
}>()

const palette = ['#2563eb', '#0f766e', '#7c3aed', '#dc2626', '#d97706', '#0891b2']

const normalizedFactors = computed(() => {
  const rows = props.factors || []
  const max = Math.max(...rows.map((item) => Number(item.impact) || 0), 1)
  return rows.slice(0, 8).map((item) => ({
    label: item.label,
    percent: Math.max(1, Math.round(((Number(item.impact) || 0) / max) * 100))
  }))
})
</script>
