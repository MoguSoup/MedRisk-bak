import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeChat from './KnowledgeChat.vue'
import { request, streamConversationMessage } from '../api/client'

vi.mock('../api/client', () => ({
  request: vi.fn(),
  sendConversationMessage: vi.fn(),
  streamConversationMessage: vi.fn()
}))

const conversations = [
  { id: 1, title: 'First chat', createdAt: '2026-01-01', updatedAt: '2026-01-02' },
  { id: 2, title: 'Second chat', createdAt: '2026-01-03', updatedAt: '2026-01-04' }
]

describe('KnowledgeChat', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.mocked(request).mockImplementation(async (method, url) => {
      if (method === 'get' && url === '/llm-profiles') return []
      if (method === 'get' && url === '/conversations') return conversations
      if (method === 'get' && url === '/conversations/1') return { conversation: conversations[0], messages: [] }
      if (method === 'get' && url === '/conversations/2') return { conversation: conversations[1], messages: [] }
      if (method === 'delete' && url === '/conversations/1') return { deleted: true }
      throw new Error(`Unexpected request: ${method} ${url}`)
    })
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
  })

  it('deletes a conversation and selects the next one', async () => {
    const wrapper = mount(KnowledgeChat, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.findAll('.conversation-card')).toHaveLength(2)

    await wrapper.find('.conversation-delete').trigger('click')
    await flushPromises()

    expect(request).toHaveBeenCalledWith('delete', '/conversations/1')
    expect(wrapper.findAll('.conversation-card')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('First chat')
    expect(wrapper.text()).toContain('Second chat')
  })

  it('streams answers with automatic mode routing', async () => {
    vi.mocked(streamConversationMessage).mockImplementation(async (_id, payload, handlers) => {
      handlers.onAccepted?.({ chatMode: 'medical' })
      handlers.onAnswer?.({ chunk: '### 标题\n\n- 项目' })
      handlers.onDone?.({
        id: 99,
        conversationId: 1,
        question: payload.question,
        answer: '### 标题\n\n- 项目',
        chatMode: 'medical',
        retrievalUsed: true,
        retrievalStatus: 'success',
        fallbackUsed: false
      })
    })
    const wrapper = mount(KnowledgeChat, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('自动判断')
    await wrapper.find('textarea').setValue('糖尿病有哪些症状？')
    await wrapper.find('.composer-actions .el-button--primary').trigger('click')
    await flushPromises()

    expect(streamConversationMessage).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ question: '糖尿病有哪些症状？', outputImageRequested: false }),
      expect.any(Object)
    )
    expect(wrapper.find('.markdown-body').exists()).toBe(true)
  })
})
