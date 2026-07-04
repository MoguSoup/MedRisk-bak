import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'
import App from './App.vue'

describe('App', () => {
  beforeEach(() => {
    localStorage.clear()
    document.title = ''
  })

  it('renders Chinese brand and disclaimer', () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('MedRisk AI')
    expect(wrapper.text()).toContain('本系统仅用于教学演示')
    expect(wrapper.text()).toContain('患者演示')
    expect(wrapper.text()).toContain('医生演示')
    expect(wrapper.text()).toContain('管理员演示')
  })

  it('marks the selected demo account distinctly', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    const buttons = wrapper.findAll('.demo-accounts button')
    await buttons[2].trigger('click')
    await nextTick()

    expect(buttons[2].classes()).toContain('active')
    expect(buttons[2].attributes('aria-pressed')).toBe('true')
    expect(buttons[0].classes()).not.toContain('active')
    expect((wrapper.vm as unknown as { authForm: { username: string } }).authForm.username).toBe('admin')
  })

  it('keeps admin account creation out of public registration', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    ;(wrapper.vm as unknown as { authMode: string }).authMode = '注册'
    await nextTick()
    expect(wrapper.text()).toContain('管理员账号由后台统一创建')
    expect(wrapper.text()).toContain('患者')
    expect(wrapper.text()).toContain('医生')
    expect(wrapper.text()).toContain('发送验证码')
    expect(wrapper.text()).toContain('邮箱验证码')
  })

  it('renders centered auth card reset password flow', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    expect(wrapper.find('.login-panel').exists()).toBe(true)
    expect(wrapper.find('.login-hero').exists()).toBe(false)
    expect(wrapper.text()).toContain('忘记密码')
    ;(wrapper.vm as unknown as { authMode: string }).authMode = '重置密码'
    await nextTick()
    expect(wrapper.text()).toContain('确认新密码')
    expect(wrapper.text()).toContain('邮箱验证码')
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
    expect(wrapper.text()).toContain('患者风险记录')
    expect(wrapper.text()).toContain('报告中心')
    expect(wrapper.text()).toContain('文档管理')
    expect(wrapper.text()).toContain('图谱管理')
    expect(wrapper.text()).toContain('图谱可视化')
    expect(wrapper.text()).toContain('风险大屏')
    expect(wrapper.text()).toContain('疾病预测')
    expect(wrapper.text()).toContain('模型版本')
    expect(wrapper.text()).toContain('数据集管理')
    expect(wrapper.text()).toContain('训练任务')
    expect(wrapper.text()).toContain('模型评估')
    expect(wrapper.text()).toContain('模型反馈')
    expect(wrapper.text()).toContain('大模型配置')
    expect(wrapper.text()).not.toContain('医生控制台')
    expect(wrapper.text()).toContain('知识库与图谱')
    expect(wrapper.text()).toContain('模型与数据')
    expect(wrapper.text()).toContain('系统管理')
    expect(wrapper.text()).not.toContain('AI 辅助分析')
  })

  it('collapses and expands grouped admin sidebar', async () => {
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

    expect(wrapper.findAll('.nav-group').length).toBeGreaterThanOrEqual(4)
    expect(wrapper.find('.app-shell').classes()).not.toContain('sidebar-collapsed')

    const toggle = wrapper.find('.sidebar-toggle-codex')
    expect(toggle.exists()).toBe(true)
    expect(toggle.attributes('aria-label')).toBe('收起侧栏')

    await toggle.trigger('click')
    await nextTick()
    expect(wrapper.find('.app-shell').classes()).toContain('sidebar-collapsed')
    expect(wrapper.find('.sidebar-toggle-codex').attributes('aria-label')).toBe('展开侧栏')
    expect(localStorage.getItem('medrisk-sidebar-collapsed')).toBe('true')

    await wrapper.find('.sidebar-toggle-codex').trigger('click')
    await nextTick()
    expect(wrapper.find('.app-shell').classes()).not.toContain('sidebar-collapsed')
    expect(wrapper.find('.sidebar-toggle-codex').attributes('aria-label')).toBe('收起侧栏')
  })

  it('updates browser title with role, user and active page', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    ;(wrapper.vm as unknown as { currentUser: unknown; activeView: string }).currentUser = {
      id: 1,
      username: 'admin',
      email: 'admin@medrisk.local',
      name: 'Admin',
      role: 'ADMIN',
      status: 'ACTIVE',
    }
    ;(wrapper.vm as unknown as { activeView: string }).activeView = 'adminConsole'
    await nextTick()

    expect(document.title).toBe('MedRisk AI 疾病风险预测与智能问答系统 - 管理员 - Admin - 管理控制台')

    ;(wrapper.vm as unknown as { activeView: string }).activeView = 'users'
    await nextTick()
    expect(document.title).toBe('MedRisk AI 疾病风险预测与智能问答系统 - 管理员 - Admin - 用户管理')
  })

  it('renders admin management content without the horizontal tab bar', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    ;(wrapper.vm as unknown as { currentUser: unknown; activeView: string }).currentUser = {
      id: 1,
      username: 'admin',
      email: 'admin@medrisk.local',
      name: 'Admin',
      role: 'ADMIN',
      status: 'ACTIVE',
    }
    ;(wrapper.vm as unknown as { activeView: string }).activeView = 'models'
    await nextTick()

    expect(wrapper.find('.admin-section-stack').exists()).toBe(true)
    expect(wrapper.find('.admin-tabs').exists()).toBe(false)
    expect(wrapper.find('.el-tabs__nav').exists()).toBe(false)
  })

  it('shows audit login ip and model type controls for admins', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    const app = wrapper.vm as unknown as {
      currentUser: unknown
      activeView: string
      auditLogs: unknown[]
      modelTypeOptions: Array<{ modelType: string; label: string; available: boolean; reason?: string }>
    }
    app.currentUser = {
      id: 1,
      username: 'admin',
      email: 'admin@medrisk.local',
      name: 'Admin',
      role: 'ADMIN',
      status: 'ACTIVE',
    }
    await nextTick()
    app.auditLogs = [
      { id: 1, action: 'LOGIN', resourceType: 'USER', resourceId: '1', clientIp: '203.0.113.9', createdAt: '2026-06-28T00:00:00' }
    ]
    app.activeView = 'audit'
    await nextTick()

    const auditColumnLabels = wrapper.findAllComponents({ name: 'ElTableColumn' }).map((column) => column.props('label'))
    expect(auditColumnLabels).toContain('请求 IP')
    expect((app.auditLogs[0] as { clientIp?: string }).clientIp).toBe('203.0.113.9')

    app.activeView = 'training'
    await nextTick()
    expect(wrapper.text()).toContain('模型类型')
    expect(wrapper.text()).toContain('XGBoost 稳定基线')
    expect(wrapper.text()).toContain('树数量')
    expect(wrapper.text()).toContain('最大深度')
    expect(app.modelTypeOptions.map((item) => item.label)).toEqual([
      'XGBoost 稳定基线',
      'Logistic Regression 可解释基线',
      'Random Forest 随机森林',
      'ExtraTrees 极端随机树',
      'HistGradientBoosting 直方图提升树',
      'LightGBM 梯度提升树',
      'CatBoost 类别特征提升树',
      'TabPFN 表格基础模型',
      'TabICL 表格上下文学习模型',
      'FT-Transformer 论文模型'
    ])

    app.activeView = 'datasets'
    await nextTick()
    expect(wrapper.text()).toContain('导入公开训练数据集')
    expect(wrapper.text()).toContain('清理小样本数据集')
  })

  it('renders model-specific hyperparameter controls', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    const app = wrapper.vm as unknown as {
      currentUser: unknown
      activeView: string
      trainingForm: { modelType: string }
      modelCapabilities: Array<{ modelType: string; label: string; available: boolean }>
    }
    app.currentUser = {
      id: 1,
      username: 'admin',
      email: 'admin@medrisk.local',
      name: 'Admin',
      role: 'ADMIN',
      status: 'ACTIVE',
    }
    app.modelCapabilities = [
      { modelType: 'xgboost', label: 'XGBoost 稳定基线', available: true },
      { modelType: 'tabpfn', label: 'TabPFN 表格基础模型', available: true },
      { modelType: 'tabicl', label: 'TabICL 表格上下文学习模型', available: true },
    ]
    app.activeView = 'training'
    await nextTick()

    expect(wrapper.text()).toContain('树数量')
    expect(wrapper.text()).toContain('行采样比例')

    app.trainingForm.modelType = 'tabpfn'
    await nextTick()
    expect(wrapper.text()).toContain('最大训练样本')
    expect(wrapper.text()).toContain('集成次数')
    expect(wrapper.text()).not.toContain('树数量')

    app.trainingForm.modelType = 'tabicl'
    await nextTick()
    expect(wrapper.text()).toContain('上下文大小')
    expect(wrapper.text()).toContain('随机种子')
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
