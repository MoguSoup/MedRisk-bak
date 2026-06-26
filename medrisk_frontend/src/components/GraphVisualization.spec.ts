import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { defineComponent, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import GraphVisualization from './GraphVisualization.vue'
import { request } from '../api/client'

vi.mock('../api/client', () => ({
  request: vi.fn(async () => ({
    nodes: [
      { id: '1', name: '冠心病', type: 'Disease', degree: 12, sourceName: 'Demo', visibility: 'PUBLIC' },
      { id: '2', name: '胸痛', type: 'Symptom', degree: 5, sourceName: 'Demo', visibility: 'PUBLIC' },
      { id: '3', name: '阿司匹林', type: 'Drug', degree: 3, sourceName: 'Demo', visibility: 'PUBLIC' }
    ],
    relationships: [
      { source: '1', target: '2', label: '表现症状', type: 'SHOWS_SYMPTOM', sourceName: 'Demo', visibility: 'PUBLIC' }
    ],
    nodeTypes: ['Disease', 'Symptom', 'Drug'],
    relationshipTypes: ['表现症状'],
    sourceNames: ['Demo'],
    summary: { nodeCount: 3, relationshipCount: 1 }
  }))
}))

const DashboardChartStub = defineComponent({
  name: 'DashboardChart',
  props: {
    title: String,
    option: Object
  },
  template: '<div class="dashboard-chart-stub" />'
})

describe('GraphVisualization', () => {
  it('uses compact defaults and readable sci-style graph symbols', async () => {
    const wrapper = mount(GraphVisualization, {
      props: { role: 'ADMIN' },
      global: {
        plugins: [ElementPlus],
        stubs: { DashboardChart: DashboardChartStub }
      }
    })

    await flushPromises()
    await nextTick()

    expect(request).toHaveBeenCalledWith('get', expect.stringContaining('limit=80'))
    const option = wrapper.findComponent(DashboardChartStub).props('option') as any
    const series = option.series[0]
    expect(series.label.color).toBe('#111827')
    expect(series.links[0].label.show).toBe(false)
    expect(series.data.map((item: any) => item.symbol)).toEqual(['circle', 'diamond', 'triangle'])
    expect(new Set(series.data.map((item: any) => item.itemStyle.color)).size).toBeGreaterThan(1)
  })
})
