import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import GraphVisualization from './GraphVisualization.vue'
import { request } from '../api/client'

vi.mock('../api/client', () => ({
  request: vi.fn(async () => ({
    nodes: [
      { id: '1', name: 'Disease A', type: 'Disease', degree: 12, sourceName: 'Demo', visibility: 'PUBLIC' },
      { id: '2', name: 'Symptom B', type: 'Symptom', degree: 5, sourceName: 'Demo', visibility: 'PUBLIC' },
      { id: '3', name: 'Drug C', type: 'Drug', degree: 3, sourceName: 'Demo', visibility: 'PUBLIC' }
    ],
    relationships: [
      { source: '1', target: '2', label: 'shows', type: 'SHOWS_SYMPTOM', sourceName: 'Demo', visibility: 'PUBLIC' }
    ],
    nodeTypes: ['Disease', 'Symptom', 'Drug'],
    relationshipTypes: ['shows'],
    sourceNames: ['Demo'],
    summary: { nodeCount: 3, relationshipCount: 1 }
  }))
}))

describe('GraphVisualization', () => {
  it('uses compact defaults and renders the Three graph fallback without WebGL', async () => {
    const wrapper = mount(GraphVisualization, {
      props: { role: 'ADMIN' },
      global: { plugins: [ElementPlus] }
    })

    await flushPromises()
    await nextTick()

    expect(request).toHaveBeenCalledWith('get', expect.stringContaining('limit=80'))
    expect(wrapper.find('.three-graph-scene').exists()).toBe(true)
    expect(wrapper.find('.graph-fallback').exists()).toBe(true)
    expect(wrapper.text()).toContain('Disease A')
    expect(wrapper.text()).toContain('Symptom')
  })
})
