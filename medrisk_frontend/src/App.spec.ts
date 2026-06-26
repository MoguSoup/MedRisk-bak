import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import App from './App.vue'

describe('App', () => {
  it('renders Chinese brand and disclaimer', () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('MedRisk AI')
    expect(wrapper.text()).toContain('本系统仅用于教学演示')
    expect(wrapper.text()).toContain('患者演示')
    expect(wrapper.text()).toContain('医生演示')
    expect(wrapper.text()).toContain('管理员演示')
  })

  it('keeps admin account creation out of public registration', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    ;(wrapper.vm as unknown as { authMode: string }).authMode = '注册'
    await nextTick()
    expect(wrapper.text()).toContain('管理员账号由后台统一创建')
    expect(wrapper.text()).toContain('患者')
    expect(wrapper.text()).toContain('医生')
  })
  it('renders graph rag navigation for admins', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    ;(wrapper.vm as unknown as { currentUser: unknown }).currentUser = {
      id: 1,
      username: 'admin',
      email: 'admin@medrisk.local',
      name: 'Admin',
      role: 'ADMIN',
      status: 'ACTIVE',
    }
    await nextTick()

    expect(wrapper.text()).toContain('智能问答')
    expect(wrapper.text()).toContain('文档管理')
    expect(wrapper.text()).toContain('图谱管理')
    expect(wrapper.text()).toContain('图谱可视化')
    expect(wrapper.text()).not.toContain('AI 辅助分析')
  })

  it('separates knowledge navigation by role', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    ;(wrapper.vm as unknown as { currentUser: unknown }).currentUser = {
      id: 2,
      username: 'doctor',
      email: 'doctor@medrisk.local',
      name: 'Doctor',
      role: 'DOCTOR',
      status: 'ACTIVE',
    }
    await nextTick()

    expect(wrapper.text()).toContain('智能问答')
    expect(wrapper.text()).toContain('文档管理')
    expect(wrapper.text()).toContain('疾病信息')
    expect(wrapper.text()).toContain('病历管理')
    expect(wrapper.text()).toContain('图谱可视化')
    expect(wrapper.text()).not.toContain('图谱管理')
    expect(wrapper.text()).not.toContain('数据源管理')
    expect(wrapper.text()).not.toContain('数据集管理')
    expect(wrapper.text()).not.toContain('个人中心')

    ;(wrapper.vm as unknown as { currentUser: unknown }).currentUser = {
      id: 3,
      username: 'patient',
      email: 'patient@medrisk.local',
      name: 'Patient',
      role: 'PATIENT',
      status: 'ACTIVE',
    }
    await nextTick()

    expect(wrapper.text()).toContain('智能问答')
    expect(wrapper.text()).not.toContain('文档管理')
    expect(wrapper.text()).not.toContain('疾病信息')
    expect(wrapper.text()).not.toContain('病历管理')
    expect(wrapper.text()).not.toContain('图谱可视化')
    expect(wrapper.text()).not.toContain('用户管理')
    expect(wrapper.text()).not.toContain('数据源管理')
  })

  it('opens personal info from the avatar entry', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    ;(wrapper.vm as unknown as { currentUser: unknown }).currentUser = {
      id: 3,
      username: 'patient',
      email: 'patient@medrisk.local',
      name: 'Patient',
      role: 'PATIENT',
      status: 'ACTIVE',
    }
    await nextTick()

    expect(wrapper.text()).not.toContain('个人中心')
    await wrapper.find('.avatar-entry').trigger('click')
    await nextTick()

    expect((wrapper.vm as unknown as { activeView: string }).activeView).toBe('profile')
    expect(wrapper.text()).toContain('个人信息')
    expect(wrapper.text()).toContain('保存资料')
  })
})
