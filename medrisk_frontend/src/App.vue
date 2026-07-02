<template>
  <main v-if="!currentUser" class="login-shell">
    <section class="login-visual">
      <HeroThreeScene />
      <div class="login-visual-content">
        <p>MedRisk AI</p>
        <h1>疾病风险预测与智能辅助诊断系统</h1>
        <span>融合知识图谱、预测模型与医学问答，支持风险识别、报告生成和临床教学演示。</span>
        <div class="login-feature-list">
          <strong>实时检测</strong>
          <strong>智能分析</strong>
          <strong>数据统计</strong>
        </div>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-brand">
        <img class="login-brand-wordmark login-brand-hbut" :src="hbutWordmark" alt="湖北工业大学" />
        <span class="login-brand-divider"></span>
        <img class="login-brand-wordmark login-brand-zhuopu" :src="zhuopuLogo" alt="成都环朴科技有限公司" />
      </div>
      <h1>MedRisk AI</h1>
      <p class="login-subtitle">欢迎使用疾病风险预测平台</p>
      <el-segmented v-if="authMode !== '重置密码'" v-model="authMode" :options="['登录', '注册']" />
      <p v-else class="reset-mode-title">重置密码</p>
      <el-form label-position="top" class="auth-form">
        <el-form-item v-if="authMode !== '重置密码'" label="用户名">
          <el-input v-model="authForm.username" placeholder="admin / doctor / patient" />
        </el-form-item>
        <el-form-item v-if="authMode === '注册'" label="邮箱">
          <div class="code-input-row">
            <el-input v-model="authForm.email" placeholder="user@example.com" />
            <el-button :loading="codeSending" :disabled="registerCodeCooldown > 0" @click="sendRegisterCode">
              {{ registerCodeCooldown > 0 ? `${registerCodeCooldown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="authMode === '注册'" label="邮箱验证码">
          <el-input v-model="authForm.emailCode" placeholder="请输入 6 位验证码" maxlength="6" />
        </el-form-item>
        <el-form-item v-if="authMode === '注册'" label="姓名">
          <el-input v-model="authForm.name" placeholder="请输入姓名或匿名编号" />
        </el-form-item>
        <el-form-item v-if="authMode === '注册'" label="角色">
          <el-select v-model="authForm.role">
            <el-option label="患者" value="PATIENT" />
            <el-option label="医生" value="DOCTOR" />
          </el-select>
          <small>管理员账号由后台统一创建，默认演示账号为 admin / 123456。</small>
        </el-form-item>
        <el-form-item v-if="authMode === '重置密码'" label="邮箱">
          <div class="code-input-row">
            <el-input v-model="resetForm.email" placeholder="注册邮箱" />
            <el-button :loading="codeSending" :disabled="resetCodeCooldown > 0" @click="sendResetCode">
              {{ resetCodeCooldown > 0 ? `${resetCodeCooldown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="authMode === '重置密码'" label="邮箱验证码">
          <el-input v-model="resetForm.emailCode" placeholder="请输入 6 位验证码" maxlength="6" />
        </el-form-item>
        <el-form-item v-if="authMode !== '重置密码'" label="密码">
          <el-input v-model="authForm.password" type="password" show-password placeholder="默认演示密码 123456" />
        </el-form-item>
        <el-form-item v-if="authMode === '重置密码'" label="新密码">
          <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item v-if="authMode === '重置密码'" label="确认新密码">
          <el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-button type="primary" :icon="Lock" :loading="loading" @click="submitAuth">
          {{ authMode === '登录' ? '登录系统' : authMode === '注册' ? '创建账号' : '重置密码' }}
        </el-button>
      </el-form>
      <div v-if="authMode === '登录'" class="auth-link-row">
        <button class="forgot-link" type="button" @click="authMode = '重置密码'">忘记密码？</button>
        <button class="register-link" type="button" @click="authMode = '注册'">注册新用户</button>
      </div>
      <button v-else-if="authMode === '重置密码'" class="forgot-link" type="button" @click="authMode = '登录'">返回登录</button>
      <p class="disclaimer login-disclaimer">{{ disclaimer }}</p>
      <div class="demo-accounts">
        <button :class="{ active: selectedDemoAccount === 'patient' }" :aria-pressed="selectedDemoAccount === 'patient'" @click="useDemoAccount('patient')">患者演示</button>
        <button :class="{ active: selectedDemoAccount === 'doctor' }" :aria-pressed="selectedDemoAccount === 'doctor'" @click="useDemoAccount('doctor')">医生演示</button>
        <button :class="{ active: selectedDemoAccount === 'admin' }" :aria-pressed="selectedDemoAccount === 'admin'" @click="useDemoAccount('admin')">管理员演示</button>
      </div>
    </section>
  </main>

  <main v-else class="app-shell" :class="{ 'sidebar-collapsed': sidebarCollapsed }" :style="appShellStyle">
    <button
      class="sidebar-toggle-codex"
      type="button"
      :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
      :title="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
      @click="toggleSidebar"
    >
      <el-icon><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
    </button>
    <aside class="sidebar">
      <div class="sidebar-scroll">
        <nav class="nav-list">
          <section v-for="group in visibleNavGroups" :key="group.key" class="nav-group">
            <button class="nav-group-toggle" type="button" :title="group.label" @click="toggleNavGroup(group.key)">
              <span class="nav-group-label">{{ group.label }}</span>
              <el-icon class="nav-group-chevron" :class="{ open: isNavGroupExpanded(group.key) }"><ArrowDown /></el-icon>
            </button>
            <div v-show="isNavGroupExpanded(group.key)" class="nav-group-items">
              <button
                v-for="item in group.items"
                :key="item.key"
                class="nav-item"
                :class="{ active: activeView === item.key }"
                :title="item.label"
                @click="changeView(item.key)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </button>
            </div>
          </section>
        </nav>
      </div>
      <button
        v-if="!sidebarCollapsed"
        class="sidebar-resize-handle"
        type="button"
        aria-label="调整侧栏宽度"
        title="拖动调整侧栏宽度"
        @pointerdown="startSidebarResize"
      ></button>
    </aside>

    <section class="workspace" :class="{ 'workspace-chat': activeView === 'qa' }">
      <header class="topbar">
        <div>
          <p class="eyebrow">教学演示系统</p>
          <h2>{{ currentTitle }}</h2>
        </div>
        <div class="topbar-brand-center">
          <span class="topbar-logo-side topbar-hbut-side">
            <img class="topbar-wordmark" :src="hbutWordmark" alt="湖北工业大学" />
          </span>
          <span class="topbar-brand-title">MedRisk AI</span>
          <span class="topbar-logo-side topbar-zp-side">
            <img class="topbar-zhuopu" :src="zhuopuHorizontalLogo" alt="成都环朴科技有限公司" />
          </span>
        </div>
        <div class="user-chip">
          <button class="avatar-entry" type="button" title="个人信息" @click="changeView('profile')">
            <el-avatar :size="36" :src="currentUser.avatarUrl || undefined">{{ userInitial }}</el-avatar>
            <span>{{ currentUser.name }}</span>
          </button>
          <el-tag>{{ roleText(currentUser.role) }}</el-tag>
          <el-button :icon="SwitchButton" text @click="logout">退出</el-button>
        </div>
      </header>

      <section v-if="activeView === 'dashboard'" class="view-stack">
        <div class="metric-grid">
          <div class="metric-card">
            <span>预测记录</span>
            <strong>{{ history.length }}</strong>
            <small>当前可见历史</small>
          </div>
          <div class="metric-card warn">
            <span>高风险记录</span>
            <strong>{{ highRiskCount }}</strong>
            <small>需重点解释</small>
          </div>
          <div class="metric-card teal">
            <span>模型数量</span>
            <strong>{{ models.length || 5 }}</strong>
            <small>多病种启用</small>
          </div>
          <div class="metric-card purple">
            <span>报告数量</span>
            <strong>{{ reports.length }}</strong>
            <small>可预览下载</small>
          </div>
        </div>
        <section class="panel">
          <div class="panel-title">
            <h3>最近预测</h3>
            <el-button text @click="changeView('predict')">开始预测</el-button>
          </div>
          <el-table :data="history.slice(0, 6)" empty-text="暂无预测记录">
            <el-table-column prop="diseaseName" label="病种" width="120" />
            <el-table-column label="风险等级" width="120">
              <template #default="{ row }"><el-tag :type="riskType(row.riskLabel)">{{ riskText(row.riskLabel) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="概率">
              <template #default="{ row }">{{ percent(row.riskProbability) }}</template>
            </el-table-column>
            <el-table-column prop="modelVersion" label="模型版本" min-width="180" />
          </el-table>
        </section>
      </section>

      <section v-if="activeView === 'doctorConsole'" class="view-stack">
        <div class="metric-grid">
          <div class="metric-card">
            <span>患者预测记录</span>
            <strong>{{ doctorSummary.predictionCount || history.length }}</strong>
            <small>医生可见全量记录</small>
          </div>
          <div class="metric-card warn">
            <span>高风险随访</span>
            <strong>{{ doctorSummary.highRiskCount || highRiskCount }}</strong>
            <small>建议优先复核</small>
          </div>
          <div class="metric-card teal">
            <span>报告总数</span>
            <strong>{{ doctorSummary.reportCount || reports.length }}</strong>
            <small>辅助分析报告</small>
          </div>
          <div class="metric-card purple">
            <span>患者人数</span>
            <strong>{{ doctorSummary.patientCount || '-' }}</strong>
            <small>按患者名称去重</small>
          </div>
        </div>
        <section class="panel">
          <div class="panel-title">
            <h3>高风险患者队列</h3>
            <el-button :icon="Refresh" @click="loadDoctorConsole">刷新</el-button>
          </div>
          <el-table :data="doctorHighRiskRows" empty-text="暂无高风险记录">
            <el-table-column prop="patientName" label="患者" min-width="130" />
            <el-table-column prop="diseaseName" label="病种" width="120" />
            <el-table-column label="风险概率" width="120">
              <template #default="{ row }">{{ percent(row.riskProbability) }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="170" />
          </el-table>
        </section>
      </section>

      <section v-if="activeView === 'predict'" class="view-stack">
        <section class="panel">
          <div class="panel-title">
            <h3>多病种风险预测</h3>
            <el-tag type="info">结构化指标 + 模型解释</el-tag>
          </div>
          <el-tabs v-model="selectedDisease">
            <el-tab-pane v-for="disease in diseaseConfigs" :key="disease.key" :name="disease.key" :label="disease.label" />
          </el-tabs>
          <el-form label-position="top" class="predict-form">
            <el-form-item label="患者姓名或编号">
              <el-input v-model="predictionForm.patientName" placeholder="请输入患者姓名、编号或匿名代号" />
            </el-form-item>
            <el-form-item v-for="field in activeDisease.fields" :key="field.key" :label="field.label">
              <el-switch v-if="field.type === 'boolean'" v-model="predictionForm[field.key]" active-text="是" inactive-text="否" />
              <el-input-number
                v-else
                v-model="predictionForm[field.key]"
                :min="field.min"
                :max="field.max"
                :step="field.step || 1"
                controls-position="right"
              />
              <small>{{ field.hint }}</small>
            </el-form-item>
          </el-form>
          <div class="action-row">
            <el-button type="primary" :icon="FirstAidKit" :loading="loading" @click="submitPrediction">提交预测</el-button>
            <el-button @click="fillDemo">填入演示数据</el-button>
          </div>
        </section>

        <section v-if="predictionResult" class="result-layout">
          <div class="risk-panel" :class="predictionResult.riskLabel">
            <span>{{ predictionResult.diseaseName }}</span>
            <strong>{{ riskText(predictionResult.riskLabel) }}</strong>
            <el-progress :percentage="Math.round(predictionResult.riskProbability * 100)" :stroke-width="12" />
            <p>置信度 {{ percent(predictionResult.confidence) }} · {{ predictionResult.modelVersion }}</p>
          </div>
          <section class="panel">
            <div class="panel-title">
              <h3>主要风险因素</h3>
              <el-button type="success" :icon="Document" @click="generateReport(predictionResult.recordId)">生成报告</el-button>
            </div>
            <FactorChart :factors="predictionResult.topFactors" />
            <ul class="recommendations">
              <li v-for="item in predictionResult.recommendations" :key="item">{{ item }}</li>
            </ul>
            <p class="disclaimer">{{ predictionResult.disclaimer }}</p>
          </section>
        </section>
      </section>

      <section v-if="activeView === 'history'" class="panel">
        <div class="panel-title">
          <h3>预测历史</h3>
          <el-button :icon="DataAnalysis" @click="loadHistory">刷新</el-button>
        </div>
        <el-table :data="history" empty-text="暂无历史记录">
          <el-table-column prop="recordId" label="编号" width="80" />
          <el-table-column prop="diseaseName" label="病种" width="120" />
          <el-table-column label="风险等级" width="120">
            <template #default="{ row }"><el-tag :type="riskType(row.riskLabel)">{{ riskText(row.riskLabel) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="风险概率" width="120">
            <template #default="{ row }">{{ percent(row.riskProbability) }}</template>
          </el-table-column>
          <el-table-column prop="modelVersion" label="模型版本" min-width="190" />
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button text type="primary" @click="generateReport(row.recordId)">生成报告</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="activeView === 'reports'" class="view-stack">
        <section class="panel">
          <div class="panel-title">
            <h3>报告中心</h3>
            <el-button :icon="Document" @click="loadReports">刷新</el-button>
          </div>
          <el-table :data="reports" empty-text="暂无报告">
            <el-table-column prop="id" label="编号" width="80" />
            <el-table-column prop="reportTitle" label="标题" min-width="220" />
            <el-table-column prop="createdAt" label="生成时间" width="190" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button text type="primary" @click="selectedReport = row">预览</el-button>
                <el-button text type="success" @click="download(row.id)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
        <section v-if="selectedReport" class="report-preview" v-html="selectedReport.reportHtml"></section>
      </section>

      <KnowledgeChat v-if="activeView === 'qa'" />

      <DocumentManagement v-if="activeView === 'documents'" :role="currentUser.role" />

      <DiseaseInfo v-if="activeView === 'diseases'" :role="currentUser.role" />

      <MedicalCaseManagement v-if="activeView === 'medicalCases'" :role="currentUser.role" />

      <section v-if="activeView === 'patientRecords'" class="panel">
        <div class="panel-title">
          <h3>患者风险记录</h3>
          <el-tag type="warning">医生可见全量演示记录</el-tag>
        </div>
        <el-table :data="history" empty-text="暂无患者记录">
          <el-table-column prop="diseaseName" label="病种" width="120" />
          <el-table-column label="风险等级" width="120">
            <template #default="{ row }"><el-tag :type="riskType(row.riskLabel)">{{ riskText(row.riskLabel) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="modelVersion" label="模型版本" />
          <el-table-column label="建议">
            <template #default="{ row }">{{ row.recommendations?.[0] || '请结合病史与线下检查综合判断。' }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="activeView === 'adminConsole'" class="view-stack">
        <div class="metric-grid">
          <div class="metric-card">
            <span>系统用户</span>
            <strong>{{ adminSummary.userCount || 0 }}</strong>
            <small>患者 {{ adminSummary.patientCount || 0 }} · 医生 {{ adminSummary.doctorCount || 0 }}</small>
          </div>
          <div class="metric-card warn">
            <span>高风险记录</span>
            <strong>{{ adminSummary.highRiskCount || 0 }}</strong>
            <small>全平台风险追踪</small>
          </div>
          <div class="metric-card teal">
            <span>训练任务</span>
            <strong>{{ adminSummary.trainingJobCount || 0 }}</strong>
            <small>运行中 {{ adminSummary.runningJobCount || 0 }}</small>
          </div>
          <div class="metric-card purple">
            <span>启用模型</span>
            <strong>{{ adminSummary.activeModelCount || 0 }}</strong>
            <small>模型总数 {{ adminSummary.modelCount || 0 }}</small>
          </div>
        </div>
        <section class="panel console-grid">
          <div>
            <div class="panel-title">
              <h3>管理控制台</h3>
              <el-button :icon="Refresh" :loading="loading" @click="loadAdminConsole">刷新</el-button>
            </div>
            <div class="quick-actions">
              <el-button type="primary" :icon="User" @click="changeView('users')">用户管理</el-button>
              <el-button type="success" :icon="DataAnalysis" @click="changeView('graphVisualization')">图谱可视化</el-button>
              <el-button type="warning" :icon="Upload" @click="changeView('datasets')">数据集管理</el-button>
              <el-button type="info" :icon="VideoPlay" @click="changeView('training')">训练任务</el-button>
              <el-button :icon="Document" @click="changeView('dataSeeds')">数据源管理</el-button>
            </div>
          </div>
          <div>
            <h3 class="compact-title">训练任务状态</h3>
            <div class="status-list">
              <span v-for="item in adminSummary.trainingStatus || []" :key="item.name">
                {{ item.name }} <strong>{{ item.value }}</strong>
              </span>
            </div>
          </div>
        </section>
        <section class="panel">
          <div class="panel-title">
            <h3>最近审计日志</h3>
            <el-button text @click="changeView('audit')">查看全部</el-button>
          </div>
          <el-table :data="adminRecentAuditLogs" empty-text="暂无审计日志">
            <el-table-column prop="action" label="动作" min-width="160" />
            <el-table-column prop="resourceType" label="资源" width="130" />
            <el-table-column prop="resourceId" label="资源 ID" width="110" />
            <el-table-column label="登录 IP" width="140">
              <template #default="{ row }">{{ row.clientIp || '-' }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" min-width="170" />
          </el-table>
        </section>
      </section>

      <section v-if="activeView === 'visualization'" class="screen-board">
        <header class="screen-topline">
          <h2>医疗疾病数据分析大屏可视化系统</h2>
          <time>{{ screenNow }}</time>
        </header>

        <div class="screen-frame">
          <div class="screen-platform-title">可视化平台</div>
          <div class="screen-layout">
            <section class="screen-panel screen-panel-age">
              <h4>风险等级分布</h4>
              <DashboardChart title="风险等级分布" :option="riskChartOption" />
            </section>

            <section class="screen-panel screen-panel-info">
              <h4>疾病数据信息</h4>
              <div class="screen-data-cells">
                <span v-for="card in screenCards" :key="card.label">
                  <small>{{ card.label }}</small>
                  <strong>{{ card.value }}</strong>
                </span>
              </div>
            </section>

            <section class="screen-panel screen-panel-ring">
              <h4>用户角色环形图</h4>
              <DashboardChart title="用户角色环形图" :option="activeUserChartOption" />
            </section>

            <section class="screen-panel screen-panel-bars">
              <h4>疾病类型分布</h4>
              <DashboardChart title="疾病类型分布" :option="diseaseChartOption" />
            </section>

            <section class="screen-panel screen-panel-gender">
              <h4>模型运行指标</h4>
              <DashboardChart title="模型运行指标" :option="modelMetricChartOption" />
            </section>

            <section class="screen-panel screen-panel-keywords">
              <h4>疾病关键词云图</h4>
              <div class="screen-keywords">
                <span v-for="word in screenKeywords" :key="word">{{ word }}</span>
              </div>
            </section>

            <section class="screen-panel screen-panel-table">
              <h4>病例列表</h4>
              <div class="screen-case-table">
                <span>编号</span>
                <span>疾病</span>
                <span>患者</span>
                <span>风险</span>
                <template v-if="screenCaseRows.length">
                  <template v-for="row in screenCaseRows" :key="row.id">
                    <b>{{ row.id }}</b>
                    <b>{{ row.diseaseName }}</b>
                    <b>{{ row.patientName }}</b>
                    <b>{{ row.risk }} {{ row.probability }}</b>
                  </template>
                </template>
                <b v-else class="screen-table-empty">暂无病例数据</b>
              </div>
            </section>

            <section class="screen-panel screen-panel-trend">
              <h4>近 7 天预测与报告趋势</h4>
              <DashboardChart title="近 7 天预测与报告趋势" :option="trendChartOption" />
            </section>
          </div>
          <el-button class="screen-refresh" :icon="Refresh" :loading="loading" @click="loadVisualization">刷新</el-button>
        </div>
      </section>

      <GraphManagement v-if="activeView === 'graphManagement'" :health="neo4jHealth" @refresh-health="loadNeo4jHealth" />

      <GraphVisualization v-if="activeView === 'graphVisualization'" :role="currentUser.role" />

      <DataSeedManagement v-if="activeView === 'dataSeeds'" />

      <section v-if="activeView === 'users'" class="view-stack">
        <section class="panel">
          <div class="panel-title">
            <h3>用户管理</h3>
            <el-button :icon="Refresh" :loading="loading" @click="loadAdminUsers">刷新</el-button>
          </div>
          <el-form label-position="top" class="admin-form-grid">
            <el-form-item label="搜索">
              <el-input v-model="userFilters.keyword" placeholder="用户名、姓名、邮箱或手机号" />
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="userFilters.role" clearable placeholder="全部角色">
                <el-option label="患者" value="PATIENT" />
                <el-option label="医生" value="DOCTOR" />
                <el-option label="管理员" value="ADMIN" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="userFilters.status" clearable placeholder="全部状态">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="禁用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item label="筛选">
              <el-button type="primary" :icon="DataAnalysis" @click="loadAdminUsers">查询</el-button>
            </el-form-item>
          </el-form>
          <el-form label-position="top" class="admin-form-grid user-edit-form">
            <el-form-item label="用户名">
              <el-input v-model="userForm.username" placeholder="如：doctor_01" />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="userForm.name" placeholder="请输入真实姓名或演示姓名" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="userForm.email" placeholder="name@example.com" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="userForm.phone" placeholder="可选：请输入手机号" />
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="userForm.role" placeholder="请选择账号角色">
                <el-option label="患者" value="PATIENT" />
                <el-option label="医生" value="DOCTOR" />
                <el-option label="管理员" value="ADMIN" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="userForm.status" placeholder="请选择账号状态">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="禁用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="!userEditingId" label="初始密码">
              <el-input v-model="userForm.password" type="password" show-password placeholder="默认 123456" />
            </el-form-item>
            <el-form-item label="操作">
              <div class="action-row">
                <el-button type="primary" :icon="Edit" :loading="loading" @click="submitUser">{{ userEditingId ? '保存用户' : '新增用户' }}</el-button>
                <el-button @click="resetUserForm">清空</el-button>
              </div>
            </el-form-item>
          </el-form>
          <el-table :data="adminUsers" empty-text="暂无用户">
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="name" label="姓名" min-width="120" />
            <el-table-column prop="email" label="邮箱" min-width="180" />
            <el-table-column prop="phone" label="手机号" min-width="120" />
            <el-table-column label="角色" width="90">
              <template #default="{ row }">{{ roleText(row.role) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ userStatusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="lastLoginAt" label="最近登录" min-width="170" />
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" :icon="Edit" @click="editUser(row)">编辑</el-button>
                <el-button text :type="row.status === 'ACTIVE' ? 'warning' : 'success'" :disabled="row.id === currentUser?.id" @click="toggleUserStatus(row)">
                  {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
                </el-button>
                <el-button text type="info" @click="resetUserPassword(row.id)">重置密码</el-button>
                <el-button text type="danger" :icon="Delete" :disabled="row.id === currentUser?.id" @click="deleteUser(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="disclaimer">{{ disclaimer }}</p>
        </section>
      </section>

      <section v-if="adminManagementViews.includes(activeView)" class="view-stack">
        <section class="panel">
          <div class="panel-title">
            <h3>管理员模型训练管理</h3>
            <el-button :icon="Refresh" :loading="loading" @click="loadAdmin">刷新</el-button>
          </div>
          <div class="admin-section-stack">
            <div v-if="activeView === 'modelManagement' || activeView === 'models'" class="admin-section-panel">
              <el-table :data="models" empty-text="暂无模型指标">
                <el-table-column prop="diseaseName" label="病种" width="110" />
                <el-table-column prop="modelName" label="模型" min-width="160" />
                <el-table-column label="类型" width="120">
                  <template #default="{ row }">{{ modelTypeText(row.modelType) }}</template>
                </el-table-column>
                <el-table-column prop="version" label="版本" min-width="210" />
                <el-table-column label="状态" width="92">
                  <template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '未启用' }}</el-tag></template>
                </el-table-column>
                <el-table-column label="AUC" width="86">
                  <template #default="{ row }">{{ metric(row.metrics?.auc) }}</template>
                </el-table-column>
                <el-table-column label="Recall" width="96">
                  <template #default="{ row }">{{ metric(row.metrics?.recall) }}</template>
                </el-table-column>
                <el-table-column label="F1" width="86">
                  <template #default="{ row }">{{ metric(row.metrics?.f1) }}</template>
                </el-table-column>
                <el-table-column label="评估数据集" min-width="190">
                  <template #default="{ row }">
                    <div class="model-source-cell">
                      <strong>{{ row.evaluationDatasetName || row.metrics?.evaluationDataset || row.metrics?.datasetSource || '-' }}</strong>
                      <small>{{ row.evaluationDatasetSource || (row.metrics?.sampleCount ? `${Number(row.metrics.sampleCount).toLocaleString()} 样本` : row.metrics?.validationType || '') }}</small>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" :icon="CircleCheck" :disabled="row.active" @click="activateAdminModel(row.id)">启用</el-button>
                    <el-button text type="success" :icon="DataAnalysis" @click="prepareEvaluation(row.id)">评估</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <p class="disclaimer">{{ disclaimer }}</p>
            </div>

            <div v-if="activeView === 'modelManagement' || activeView === 'datasets'" class="admin-section-panel">
              <el-form label-position="top" class="admin-form-grid">
                <el-form-item label="数据集名称">
                  <el-input v-model="datasetForm.name" placeholder="如：心脏病训练集 2026" />
                </el-form-item>
                <el-form-item label="病种">
                  <el-select v-model="datasetForm.diseaseType">
                    <el-option v-for="item in diseaseOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="说明">
                  <el-input v-model="datasetForm.description" placeholder="数据来源、脱敏方式、适用范围" />
                </el-form-item>
                <el-form-item label="CSV / ZIP 文件">
                  <el-upload :auto-upload="false" :limit="1" accept=".csv,.zip" :on-change="handleDatasetFile" :on-remove="clearDatasetFile">
                    <el-button :icon="Upload">选择文件</el-button>
                  </el-upload>
                </el-form-item>
              </el-form>
              <div class="action-row">
                <el-button type="primary" :icon="Upload" :loading="loading" @click="submitDataset">上传并校验</el-button>
                <el-button :icon="Download" :loading="loading" @click="importPublicDatasets">导入公开训练数据集</el-button>
              </div>
              <el-table :data="datasets" empty-text="暂无数据集">
                <el-table-column prop="name" label="名称" min-width="170" />
                <el-table-column label="病种" width="110">
                  <template #default="{ row }">{{ diseaseText(row.diseaseType) }}</template>
                </el-table-column>
                <el-table-column prop="sampleCount" label="样本数" width="90" />
                <el-table-column label="特征列" min-width="210">
                  <template #default="{ row }">{{ row.featureColumns?.join('、') || '-' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'VALID' ? 'success' : row.status === 'INVALID' ? 'danger' : 'info'">{{ datasetStatus(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="sourceName" label="来源" min-width="150" show-overflow-tooltip />
                <el-table-column label="可见性" width="110">
                  <template #default="{ row }">{{ visibilityText(row.visibility) }}</template>
                </el-table-column>
                <el-table-column prop="validationMessage" label="校验信息" min-width="180" />
                <el-table-column label="操作" width="170" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" :icon="Refresh" @click="validateDataset(row.id)">校验</el-button>
                    <el-button text type="danger" :icon="Delete" @click="deleteDataset(row.id)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <p class="disclaimer">{{ disclaimer }}</p>
            </div>

            <div v-if="activeView === 'modelManagement' || activeView === 'training'" class="admin-section-panel">
              <el-form label-position="top" class="admin-form-grid">
                <el-form-item label="训练数据集">
                  <el-select v-model="trainingForm.datasetId" placeholder="请选择已校验数据集">
                    <el-option v-for="item in validDatasets" :key="item.id" :label="`${item.name} · ${diseaseText(item.diseaseType)}`" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="模型名称">
                  <el-input v-model="trainingForm.modelName" placeholder="如：心脏病 RandomForest 公开评估模型" />
                </el-form-item>
                <el-form-item label="模型类型">
                  <el-select v-model="trainingForm.modelType" placeholder="请选择训练模型类型">
                    <el-option
                      v-for="item in modelTypeOptions"
                      :key="item.modelType"
                      :label="item.label"
                      :value="item.modelType"
                      :disabled="!item.available"
                    >
                      <span>{{ item.label }}</span>
                      <small v-if="!item.available"> {{ item.reason }}</small>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="训练后评估数据集">
                  <el-select v-model="trainingForm.evaluationDatasetId" clearable placeholder="可选：选择同病种公开评估集">
                    <el-option v-for="item in evaluationDatasetsForTraining" :key="item.id" :label="`${item.name} · ${item.sampleCount || 0} 样本`" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="usesEstimatorCount(trainingForm.modelType)" label="树数量/迭代数量">
                  <el-input-number v-model="trainingForm.nEstimators" :min="10" :max="1000" :step="10" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesTreeDepth(trainingForm.modelType)" label="最大深度">
                  <el-input-number v-model="trainingForm.maxDepth" :min="1" :max="12" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesLearningRate(trainingForm.modelType)" label="学习率">
                  <el-input-number v-model="trainingForm.learningRate" :min="0.001" :max="1" :step="0.01" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesSampling(trainingForm.modelType)" label="行采样比例">
                  <el-input-number v-model="trainingForm.subsample" :min="0.1" :max="1" :step="0.05" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesSampling(trainingForm.modelType)" label="列采样比例">
                  <el-input-number v-model="trainingForm.colsampleBytree" :min="0.1" :max="1" :step="0.05" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesRegularization(trainingForm.modelType)" label="L2 正则">
                  <el-input-number v-model="trainingForm.regLambda" :min="0" :max="20" :step="0.1" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="trainingForm.modelType === 'xgboost'" label="最小子节点权重">
                  <el-input-number v-model="trainingForm.minChildWeight" :min="0" :max="20" :step="0.1" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesAdvancedSampleLimit(trainingForm.modelType)" label="最大训练样本">
                  <el-input-number v-model="trainingForm.maxTrainSamples" :min="64" :max="50000" :step="256" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesDevice(trainingForm.modelType)" label="设备">
                  <el-select v-model="trainingForm.device" placeholder="自动选择 CPU/CUDA">
                    <el-option label="自动" value="auto" />
                    <el-option label="CPU" value="cpu" />
                    <el-option label="CUDA" value="cuda" />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="trainingForm.modelType === 'tabpfn'" label="集成次数">
                  <el-input-number v-model="trainingForm.ensembleSize" :min="1" :max="32" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesContextSize(trainingForm.modelType)" label="上下文大小">
                  <el-input-number v-model="trainingForm.contextSize" :min="128" :max="8192" :step="128" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="usesSeed(trainingForm.modelType)" label="随机种子">
                  <el-input-number v-model="trainingForm.seed" :min="1" :max="999999" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="trainingForm.modelType === 'logistic_regression'" label="正则强度 C">
                  <el-input-number v-model="trainingForm.cValue" :min="0.001" :max="100" :step="0.1" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="trainingForm.modelType === 'logistic_regression'" label="最大迭代次数">
                  <el-input-number v-model="trainingForm.maxIterations" :min="50" :max="2000" :step="50" controls-position="right" />
                </el-form-item>
                <el-form-item v-if="['logistic_regression', 'random_forest', 'extra_trees'].includes(trainingForm.modelType)" label="类别权重">
                  <el-select v-model="trainingForm.classWeight" placeholder="请选择类别权重策略">
                    <el-option label="自动平衡" value="balanced" />
                    <el-option label="不加权" value="none" />
                  </el-select>
                </el-form-item>
                <el-form-item label="验证/测试比例">
                  <el-input-number v-model="trainingForm.testSize" :min="0.1" :max="0.5" :step="0.05" controls-position="right" />
                </el-form-item>
              </el-form>
              <div class="action-row">
                <el-button type="primary" :icon="VideoPlay" :loading="loading" @click="createTrainingJob">创建训练任务</el-button>
              </div>
              <el-table :data="trainingJobs" empty-text="暂无训练任务">
                <el-table-column prop="modelName" label="模型" min-width="150" />
                <el-table-column label="类型" width="120">
                  <template #default="{ row }">{{ modelTypeText(row.modelType) }}</template>
                </el-table-column>
                <el-table-column prop="datasetName" label="数据集" min-width="150" />
                <el-table-column prop="evaluationDatasetName" label="评估集" min-width="150" />
                <el-table-column label="病种" width="100">
                  <template #default="{ row }">{{ diseaseText(row.diseaseType) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }"><el-tag :type="trainingStatusType(row.trainStatus)">{{ row.trainStatus }}</el-tag></template>
                </el-table-column>
                <el-table-column label="进度" min-width="150">
                  <template #default="{ row }"><el-progress :percentage="row.progress || 0" /></template>
                </el-table-column>
                <el-table-column label="当前 Loss" width="110">
                  <template #default="{ row }">{{ metric(row.currentLoss) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="230" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" :icon="Refresh" @click="refreshTrainingJob(row.id)">状态</el-button>
                    <el-button text type="success" :icon="DataAnalysis" @click="loadTrainingHistory(row.id)">曲线</el-button>
                    <el-button text type="warning" :icon="CloseBold" :disabled="isTrainingDone(row.trainStatus)" @click="stopTrainingJob(row.id)">终止</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="selectedTrainingJob" class="admin-detail">
                <div class="metric-grid compact">
                  <div class="metric-card mini">
                    <span>Accuracy</span>
                    <strong>{{ metric(selectedTrainingJob.metrics?.accuracy) }}</strong>
                  </div>
                  <div class="metric-card mini teal">
                    <span>Recall</span>
                    <strong>{{ metric(selectedTrainingJob.metrics?.recall) }}</strong>
                  </div>
                  <div class="metric-card mini warn">
                    <span>F1</span>
                    <strong>{{ metric(selectedTrainingJob.metrics?.f1) }}</strong>
                  </div>
                  <div class="metric-card mini purple">
                    <span>AUC</span>
                    <strong>{{ metric(selectedTrainingJob.metrics?.auc) }}</strong>
                  </div>
                </div>
                <p class="muted-line">模型类型：{{ modelTypeText(selectedTrainingJob.modelType) }}</p>
                <TrainingCurve :history="trainingHistory.history" :metrics="selectedTrainingJob.metrics" />
                <p class="muted-line">{{ selectedTrainingJob.message || '训练日志将在任务运行时同步显示。' }}</p>
              </div>
              <p class="disclaimer">{{ disclaimer }}</p>
            </div>

            <div v-if="activeView === 'modelManagement' || activeView === 'evaluations'" class="admin-section-panel">
              <el-form label-position="top" class="admin-form-grid">
                <el-form-item label="模型版本">
                  <el-select v-model="evaluationForm.modelVersionId" placeholder="选择模型版本">
                    <el-option v-for="item in models" :key="item.id" :label="`${item.diseaseName} · ${item.version}`" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="评估数据集">
                  <el-select v-model="evaluationForm.datasetId" placeholder="选择数据集">
                    <el-option v-for="item in validDatasets" :key="item.id" :label="`${item.name} · ${diseaseText(item.diseaseType)}`" :value="item.id" />
                  </el-select>
                </el-form-item>
              </el-form>
              <div class="action-row">
                <el-button type="primary" :icon="DataAnalysis" :loading="loading" @click="createEvaluation">开始评估</el-button>
              </div>
              <el-table :data="evaluations" empty-text="暂无评估记录">
                <el-table-column prop="modelVersion" label="模型版本" min-width="210" />
                <el-table-column prop="datasetName" label="数据集" min-width="150" />
                <el-table-column label="Accuracy" width="100">
                  <template #default="{ row }">{{ metric(row.metrics?.accuracy) }}</template>
                </el-table-column>
                <el-table-column label="Precision" width="100">
                  <template #default="{ row }">{{ metric(row.metrics?.precision) }}</template>
                </el-table-column>
                <el-table-column label="Recall" width="95">
                  <template #default="{ row }">{{ metric(row.metrics?.recall) }}</template>
                </el-table-column>
                <el-table-column label="F1" width="86">
                  <template #default="{ row }">{{ metric(row.metrics?.f1) }}</template>
                </el-table-column>
                <el-table-column label="混淆矩阵" width="160">
                  <template #default="{ row }">{{ confusionText(row.metrics?.confusionMatrix) }}</template>
                </el-table-column>
              </el-table>
              <p class="disclaimer">{{ disclaimer }}</p>
            </div>

            <div v-if="activeView === 'modelManagement' || activeView === 'feedback'" class="admin-section-panel">
              <el-form label-position="top" class="admin-form-grid feedback-form">
                <el-form-item label="关联模型">
                  <el-select v-model="feedbackForm.modelVersionId" clearable placeholder="可选">
                    <el-option v-for="item in models" :key="item.id" :label="`${item.diseaseName} · ${item.version}`" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="关联评估">
                  <el-select v-model="feedbackForm.evaluationId" clearable placeholder="可选">
                    <el-option v-for="item in evaluations" :key="item.id" :label="`${item.modelVersion} · ${item.datasetName}`" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="问题类型">
                  <el-select v-model="feedbackForm.problemType">
                    <el-option label="训练数据" value="训练数据" />
                    <el-option label="模型效果" value="模型效果" />
                    <el-option label="部署启用" value="部署启用" />
                    <el-option label="报告解释" value="报告解释" />
                  </el-select>
                </el-form-item>
                <el-form-item label="优先级">
                  <el-select v-model="feedbackForm.priority">
                    <el-option label="高" value="高" />
                    <el-option label="中" value="中" />
                    <el-option label="低" value="低" />
                  </el-select>
                </el-form-item>
                <el-form-item label="处理状态">
                  <el-select v-model="feedbackForm.status">
                    <el-option label="待处理" value="待处理" />
                    <el-option label="处理中" value="处理中" />
                    <el-option label="已解决" value="已解决" />
                  </el-select>
                </el-form-item>
                <el-form-item label="反馈内容" class="wide-form-item">
                  <el-input v-model="feedbackForm.content" type="textarea" :rows="3" placeholder="记录模型问题、数据质量、评估结论或后续处理建议" />
                </el-form-item>
              </el-form>
              <div class="action-row">
                <el-button type="primary" :icon="Edit" :loading="loading" @click="submitFeedback">{{ feedbackEditingId ? '保存反馈' : '新增反馈' }}</el-button>
                <el-button v-if="feedbackEditingId" @click="resetFeedbackForm">取消编辑</el-button>
              </div>
              <el-table :data="feedbackList" empty-text="暂无反馈记录">
                <el-table-column prop="problemType" label="类型" width="110" />
                <el-table-column prop="priority" label="优先级" width="90" />
                <el-table-column prop="status" label="状态" width="100" />
                <el-table-column prop="modelVersion" label="模型版本" min-width="190" />
                <el-table-column prop="content" label="内容" min-width="260" />
                <el-table-column label="操作" width="150" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" :icon="Edit" @click="editFeedback(row)">编辑</el-button>
                    <el-button text type="danger" :icon="Delete" @click="deleteFeedback(row.id)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <p class="disclaimer">{{ disclaimer }}</p>
            </div>

            <div v-if="activeView === 'modelManagement' || activeView === 'llmProfiles'" class="admin-section-panel">
              <div class="panel-title inline">
                <h4>大模型配置</h4>
              </div>
              <el-form label-position="top" class="admin-form-grid">
                <el-form-item label="显示名称">
                  <el-input v-model="llmProfileForm.displayName" placeholder="如：阿里云百炼 Qwen Plus" />
                </el-form-item>
                <el-form-item label="Provider">
                  <el-input v-model="llmProfileForm.provider" placeholder="dashscope / deepseek / openai-compatible" />
                </el-form-item>
                <el-form-item label="Base URL" class="wide-form-item">
                  <el-input v-model="llmProfileForm.baseUrl" placeholder="https://.../compatible-mode/v1" />
                </el-form-item>
                <el-form-item label="模型名称">
                  <el-input v-model="llmProfileForm.modelName" placeholder="qwen-plus / deepseek-reasoner / gpt-4o-mini" />
                </el-form-item>
                <el-form-item label="API Key">
                  <el-input v-model="llmProfileForm.apiKey" type="password" show-password placeholder="新增或更换时填写；编辑保留可为空" />
                </el-form-item>
                <el-form-item label="推理协议">
                  <el-select v-model="llmProfileForm.reasoningProtocol" placeholder="选择推理协议">
                    <el-option label="百炼 / Qwen enable_thinking" value="bailian" />
                    <el-option label="DeepSeek reasoning_content" value="deepseek" />
                    <el-option label="OpenAI-compatible" value="openai" />
                    <el-option label="无推理" value="none" />
                  </el-select>
                </el-form-item>
                <el-form-item label="能力开关">
                  <div class="switch-row">
                    <el-switch v-model="llmProfileForm.reasoningSupported" active-text="支持推理" />
                    <el-switch v-model="llmProfileForm.enabled" active-text="启用" />
                    <el-switch v-model="llmProfileForm.defaultProfile" active-text="默认" />
                  </div>
                </el-form-item>
              </el-form>
              <div class="action-row">
                <el-button type="primary" :icon="CircleCheck" :loading="loading" @click="submitLlmProfile">{{ llmProfileEditingId ? '保存配置' : '新增配置' }}</el-button>
                <el-button @click="resetLlmProfileForm">清空</el-button>
              </div>
              <el-table :data="llmProfiles" empty-text="暂无大模型配置">
                <el-table-column prop="displayName" label="显示名称" min-width="180" />
                <el-table-column prop="provider" label="Provider" width="130" />
                <el-table-column prop="modelName" label="模型" min-width="150" />
                <el-table-column prop="baseUrl" label="Base URL" min-width="280" show-overflow-tooltip />
                <el-table-column label="Key" width="130">
                  <template #default="{ row }">{{ row.maskedApiKey || '-' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="120">
                  <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
                </el-table-column>
                <el-table-column label="推理" width="120">
                  <template #default="{ row }">{{ row.reasoningSupported ? row.reasoningProtocol || '支持' : '不支持' }}</template>
                </el-table-column>
                <el-table-column label="默认" width="90">
                  <template #default="{ row }"><el-tag v-if="row.defaultProfile" type="success">默认</el-tag><span v-else>-</span></template>
                </el-table-column>
                <el-table-column label="操作" width="160" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" :icon="Edit" @click="editLlmProfile(row)">编辑</el-button>
                    <el-button text type="warning" @click="disableLlmProfile(row.id)">停用</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>

          </div>
        </section>
      </section>

      <section v-if="activeView === 'audit'" class="view-stack audit-view">
        <section class="panel audit-panel">
          <div class="panel-title">
            <h3>审计日志</h3>
            <el-button :icon="Refresh" :loading="loading" @click="loadAdmin">刷新</el-button>
          </div>
          <el-table :data="auditLogs" empty-text="暂无审计日志" table-layout="auto">
            <el-table-column prop="action" label="动作" min-width="220" />
            <el-table-column prop="resourceType" label="资源" min-width="160" />
            <el-table-column prop="resourceId" label="资源 ID" min-width="150" />
            <el-table-column label="请求 IP" min-width="170">
              <template #default="{ row }">{{ row.clientIp || '-' }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" min-width="210" />
          </el-table>
        </section>
      </section>

      <section v-if="activeView === 'profile'" class="view-stack">
        <section class="panel profile-panel">
          <div class="panel-title">
            <h3>个人信息</h3>
            <el-tag>{{ roleText(currentUser.role) }}</el-tag>
          </div>
          <div class="profile-layout">
            <div class="profile-avatar-card">
              <el-avatar :size="96" :src="currentUser.avatarUrl || undefined">{{ userInitial }}</el-avatar>
              <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="handleAvatarUpload">
                <el-button :icon="Upload">上传头像</el-button>
              </el-upload>
              <span>{{ currentUser.username }}</span>
            </div>
            <el-form label-position="top" class="profile-edit-form">
              <el-form-item label="用户名">
                <el-input :model-value="currentUser.username" disabled placeholder="当前登录用户名" />
              </el-form-item>
              <el-form-item label="姓名">
                <el-input v-model="profileForm.name" placeholder="请输入姓名或显示名称" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="profileForm.email" placeholder="name@example.com" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="profileForm.phone" placeholder="可选：请输入手机号" />
              </el-form-item>
              <el-form-item label="账号状态">
                <el-input :model-value="userStatusText(currentUser.status)" disabled />
              </el-form-item>
              <el-form-item label="最近登录">
                <el-input :model-value="currentUser.lastLoginAt || '首次登录'" disabled />
              </el-form-item>
              <el-button type="primary" :icon="CircleCheck" @click="submitProfile">保存资料</el-button>
            </el-form>
          </div>
          <el-form label-position="top" class="password-form">
            <div class="panel-title inline">
              <h4>修改密码</h4>
            </div>
            <div class="admin-form-grid">
              <el-form-item label="旧密码">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 6 位新密码" />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
              </el-form-item>
            </div>
            <el-button :icon="Lock" @click="submitPassword">更新密码</el-button>
          </el-form>
          <p class="disclaimer">{{ disclaimer }}</p>
        </section>
      </section>

      <el-dialog v-model="reportDialogVisible" title="生成风险报告" width="720px">
        <p class="muted-line">可选同步智能问答内容，系统会按结构化模板写入报告，缺失项自动填“无”。</p>
        <el-form label-position="top" class="report-sync-form">
          <el-form-item label="问诊会话">
            <el-select
              v-model="reportConversationId"
              clearable
              placeholder="可选：选择要同步的问诊会话"
              @change="loadReportConversationMessages"
            >
              <el-option v-for="item in reportConversations" :key="item.id" :label="item.title" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="reportMessages.length" label="问答记录">
            <el-checkbox-group v-model="selectedQaMessageIds" class="report-message-checks">
              <el-checkbox v-for="item in reportMessages" :key="item.id" :label="item.id">
                {{ item.question }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item v-if="reportMessages.length" label="推理过程">
            <el-switch v-model="includeReasoningInReport" active-text="写入报告" inactive-text="不写入" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="reportDialogVisible = false">取消</el-button>
          <el-button @click="submitReportGeneration(false)">不包含问诊生成</el-button>
          <el-button type="primary" :loading="loading" @click="submitReportGeneration(true)">生成报告</el-button>
        </template>
      </el-dialog>

      <footer class="app-footer">{{ disclaimer }}</footer>
    </section>
  </main>
</template>

<script setup lang="ts">
import {
  ArrowDown,
  ChatDotRound,
  CircleCheck,
  CloseBold,
  DataAnalysis,
  Delete,
  Document,
  Download,
  Edit,
  Expand,
  FirstAidKit,
  Fold,
  Lock,
  Monitor,
  Refresh,
  SwitchButton,
  TrendCharts,
  Upload,
  User,
  VideoPlay
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { downloadReport, postForm, request, uploadAdminDataset } from './api/client'
import DashboardChart from './components/DashboardChart.vue'
import DataSeedManagement from './components/DataSeedManagement.vue'
import DiseaseInfo from './components/DiseaseInfo.vue'
import DocumentManagement from './components/DocumentManagement.vue'
import FactorChart from './components/FactorChart.vue'
import GraphManagement from './components/GraphManagement.vue'
import GraphVisualization from './components/GraphVisualization.vue'
import HeroThreeScene from './components/HeroThreeScene.vue'
import KnowledgeChat from './components/KnowledgeChat.vue'
import MedicalCaseManagement from './components/MedicalCaseManagement.vue'
import TrainingCurve from './components/TrainingCurve.vue'
import hbutWordmark from './assets/brand/hbut-wordmark-cn.png'
import zhuopuLogo from './assets/brand/zhuopu-logo-circuit-wordmark.png'
import zhuopuHorizontalLogo from './assets/brand/zhuopu-logo-circuit-horizontal.png'

type UserInfo = {
  id: number
  username: string
  email: string
  name: string
  phone?: string
  avatarUrl?: string
  role: string
  status: string
  createdAt?: string
  updatedAt?: string
  lastLoginAt?: string
}
type GraphHealth = { connected?: boolean; status?: string; message?: string; database?: string }
type NavItem = { key: string; label: string; icon: unknown; roles: string[] }
type NavGroup = { key: string; label: string; roles: string[]; itemKeys: string[] }
type VisibleNavGroup = { key: string; label: string; roles: string[]; items: NavItem[] }
type RiskLabel = 'low' | 'medium' | 'high'
type Factor = { name: string; label: string; value: unknown; impact: number; direction: string }
type Prediction = {
  recordId: number
  diseaseType: string
  diseaseName: string
  patientName?: string
  riskLabel: RiskLabel
  riskProbability: number
  confidence: number
  modelVersion: string
  topFactors: Factor[]
  recommendations: string[]
  disclaimer: string
  createdAt: string
}
type Report = { id: number; predictionId: number; reportTitle: string; reportHtml: string; createdAt: string }
type MetricMap = Record<string, unknown>
type ModelVersion = {
  id: number
  diseaseType: string
  diseaseName: string
  modelName: string
  modelType: string
  version: string
  metrics: MetricMap
  hyperparameters?: MetricMap
  evaluationDatasetName?: string
  evaluationDatasetSource?: string
  evaluationDatasetUrl?: string
  active: boolean
  createdAt: string
}
type AuditLog = { id: number; action: string; resourceType: string; resourceId: string; clientIp?: string; createdAt: string }
type NameValue = { name: string; value: number }
type ModelCapability = { modelType: string; label: string; available: boolean; reason?: string; category?: string; hyperparameters?: Record<string, unknown> }
type AdminUser = UserInfo
type AdminSummary = {
  userCount?: number
  patientCount?: number
  doctorCount?: number
  adminCount?: number
  disabledUserCount?: number
  predictionCount?: number
  highRiskCount?: number
  reportCount?: number
  modelCount?: number
  activeModelCount?: number
  datasetCount?: number
  trainingJobCount?: number
  runningJobCount?: number
  pendingFeedbackCount?: number
  trainingStatus?: NameValue[]
  recentAuditLogs?: AuditLog[]
}
type VisualizationData = {
  summary?: AdminSummary
  riskDistribution?: NameValue[]
  diseaseDistribution?: NameValue[]
  predictionTrend?: Array<{ date: string; predictions: number; reports: number }>
  modelMetrics?: Array<{ diseaseName: string; modelName: string; modelType?: string; version: string; auc?: number; recall?: number; f1?: number }>
  activeUsers?: NameValue[]
}
type DoctorSummary = {
  predictionCount?: number
  highRiskCount?: number
  reportCount?: number
  patientCount?: number
  riskDistribution?: NameValue[]
  diseaseDistribution?: NameValue[]
  recentHighRisk?: Array<Record<string, unknown>>
}
type FieldConfig = { key: string; label: string; min?: number; max?: number; step?: number; hint: string; type?: 'number' | 'boolean' }
type TrainingDataset = {
  id: number
  name: string
  diseaseType: string
  description?: string
  fileName: string
  filePath: string
  fileType: string
  status: string
  sampleCount?: number
  featureColumns: string[]
  validationMessage?: string
  uploadedBy: number
  sourceName?: string
  sourceUrl?: string
  sourceLicense?: string
  sourceRecordId?: string
  visibility?: string
  createdAt: string
  updatedAt: string
}
type TrainingJob = {
  id: number
  datasetId: number
  datasetName: string
  evaluationDatasetId?: number
  evaluationDatasetName?: string
  evaluationDatasetSource?: string
  evaluationDatasetUrl?: string
  userId: number
  diseaseType: string
  modelName: string
  modelType: string
  trainStatus: string
  progress: number
  currentLoss?: number
  trainEpoch: number
  learningRate: number
  testSize: number
  hyperparameters?: MetricMap
  modelVersion?: string
  modelPath?: string
  historyPath?: string
  metadataPath?: string
  metrics: MetricMap
  message?: string
  createdAt: string
  updatedAt: string
}
type TrainingHistory = { taskId: number | string; history: Record<string, number[]> }
type ModelEvaluation = {
  id: number
  modelVersionId: number
  modelVersion: string
  datasetId: number
  datasetName: string
  metrics: MetricMap
  predictions: Array<Record<string, unknown>>
  createdAt: string
}
type ModelFeedback = {
  id: number
  modelVersionId?: number
  modelVersion?: string
  evaluationId?: number
  userId: number
  problemType: string
  priority: string
  status: string
  content: string
  metricsSnapshot: MetricMap
  createdAt: string
  updatedAt: string
}
type LlmProfile = {
  id: number
  displayName: string
  provider: string
  baseUrl: string
  modelName: string
  maskedApiKey?: string
  hasApiKey?: boolean
  reasoningSupported?: boolean
  reasoningProtocol?: string
  enabled?: boolean
  defaultProfile?: boolean
  createdAt?: string
  updatedAt?: string
}
type ReportConversation = { id: number; title: string; createdAt: string; updatedAt: string }
type ReportQaMessage = {
  id: number
  conversationId: number
  question: string
  answer: string
  reasoningContent?: string
  usedModel?: string
  provider?: string
}

const disclaimer = '本系统仅用于教学演示和健康风险提示，不能替代医生诊断。'
const loading = ref(false)
const authMode = ref('登录')
const selectedDemoAccount = ref('')
const currentUser = ref<UserInfo | null>(null)
const activeView = ref('dashboard')
const selectedDisease = ref('diabetes')
const predictionResult = ref<Prediction | null>(null)
const history = ref<Prediction[]>([])
const reports = ref<Report[]>([])
const selectedReport = ref<Report | null>(null)
const models = ref<ModelVersion[]>([])
const auditLogs = ref<AuditLog[]>([])
const adminUsers = ref<AdminUser[]>([])
const adminSummary = ref<AdminSummary>({})
const neo4jHealth = ref<GraphHealth>({})
const visualization = ref<VisualizationData>({})
const doctorSummary = ref<DoctorSummary>({})
const datasets = ref<TrainingDataset[]>([])
const trainingJobs = ref<TrainingJob[]>([])
const evaluations = ref<ModelEvaluation[]>([])
const feedbackList = ref<ModelFeedback[]>([])
const llmProfiles = ref<LlmProfile[]>([])
const reportDialogVisible = ref(false)
const pendingReportRecordId = ref<number | null>(null)
const reportConversations = ref<ReportConversation[]>([])
const reportMessages = ref<ReportQaMessage[]>([])
const reportConversationId = ref<number | null>(null)
const selectedQaMessageIds = ref<number[]>([])
const includeReasoningInReport = ref(false)
const modelCapabilities = ref<ModelCapability[]>([
  { modelType: 'xgboost', label: 'XGBoost 稳定基线', available: true },
  { modelType: 'logistic_regression', label: 'Logistic Regression 可解释基线', available: true },
  { modelType: 'random_forest', label: 'Random Forest 随机森林', available: true },
  { modelType: 'extra_trees', label: 'ExtraTrees 极端随机树', available: true },
  { modelType: 'hist_gradient_boosting', label: 'HistGradientBoosting 直方图提升树', available: true },
  { modelType: 'lightgbm', label: 'LightGBM 梯度提升树', available: false, reason: '等待模型服务能力检查' },
  { modelType: 'catboost', label: 'CatBoost 类别特征提升树', available: false, reason: '等待模型服务能力检查' },
  { modelType: 'tabpfn', label: 'TabPFN 表格基础模型', available: false, reason: '等待模型服务能力检查' },
  { modelType: 'tabicl', label: 'TabICL 表格上下文学习模型', available: false, reason: '等待模型服务能力检查' },
  { modelType: 'ft_transformer', label: 'FT-Transformer 论文模型', available: false, reason: '等待模型服务能力检查' }
])
const selectedDatasetFile = ref<File | null>(null)
const selectedTrainingJobId = ref<number | null>(null)
const trainingHistory = ref<TrainingHistory>({ taskId: '', history: {} })
const feedbackEditingId = ref<number | null>(null)
const llmProfileEditingId = ref<number | null>(null)
const authForm = reactive({
  username: 'admin',
  email: 'admin@medrisk.local',
  name: '管理员',
  role: 'ADMIN',
  password: '123456',
  emailCode: ''
})
const resetForm = reactive({
  email: '',
  emailCode: '',
  newPassword: '',
  confirmPassword: ''
})
const codeSending = ref(false)
const registerCodeCooldown = ref(0)
const resetCodeCooldown = ref(0)
const screenNow = ref(formatScreenTime(new Date()))
let registerCodeTimer: number | undefined
let resetCodeTimer: number | undefined
let screenClockTimer: number | undefined

const userFilters = reactive({
  keyword: '',
  role: '',
  status: ''
})

const userForm = reactive({
  username: '',
  email: '',
  name: '',
  phone: '',
  role: 'PATIENT',
  status: 'ACTIVE',
  password: '123456'
})
const userEditingId = ref<number | null>(null)

const profileForm = reactive({
  email: '',
  name: '',
  phone: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const predictionForm = reactive<Record<string, string | number | boolean>>({
  patientName: '演示患者',
  age: 62,
  bmi: 29.5,
  glucose: 8.2,
  bloodPressure: 152,
  cholesterol: 6.3,
  smoker: true,
  familyHistory: true,
  chestPain: true,
  maxHeartRate: 118,
  creatinine: 128,
  urea: 9.2,
  albumin: 3,
  hemoglobin: 112,
  diabetesHistory: true,
  bilirubin: 34,
  alt: 92,
  ast: 88,
  alcoholUse: true,
  heartDiseaseHistory: true
})

const datasetForm = reactive({
  name: '结构化风险训练集',
  diseaseType: 'heart',
  description: 'CSV 必须包含 label 目标列，其他列为结构化特征。'
})

const trainingForm = reactive<{
  datasetId?: number
  evaluationDatasetId?: number
  modelName: string
  modelType: string
  epochs: number
  nEstimators: number
  maxDepth: number
  learningRate: number
  subsample: number
  colsampleBytree: number
  regLambda: number
  minChildWeight: number
  testSize: number
  maxTrainSamples: number
  device: string
  ensembleSize: number
  contextSize: number
  seed: number
  cValue: number
  maxIterations: number
  classWeight: string
}>({
  datasetId: undefined,
  evaluationDatasetId: undefined,
  modelName: 'XGBoost 风险分类模型',
  modelType: 'xgboost',
  epochs: 80,
  nEstimators: 80,
  maxDepth: 3,
  learningRate: 0.05,
  subsample: 0.9,
  colsampleBytree: 0.9,
  regLambda: 1,
  minChildWeight: 1,
  testSize: 0.2,
  maxTrainSamples: 10000,
  device: 'auto',
  ensembleSize: 8,
  contextSize: 2048,
  seed: 42,
  cValue: 1,
  maxIterations: 300,
  classWeight: 'balanced'
})

const evaluationForm = reactive<{ modelVersionId?: number; datasetId?: number }>({
  modelVersionId: undefined,
  datasetId: undefined
})

const feedbackForm = reactive<{
  modelVersionId?: number
  evaluationId?: number
  problemType: string
  priority: string
  status: string
  content: string
}>({
  modelVersionId: undefined,
  evaluationId: undefined,
  problemType: '模型效果',
  priority: '中',
  status: '待处理',
  content: ''
})

const llmProfileForm = reactive({
  displayName: '阿里云百炼 Qwen',
  provider: 'dashscope',
  baseUrl: '',
  modelName: 'qwen-plus',
  apiKey: '',
  reasoningSupported: true,
  reasoningProtocol: 'bailian',
  enabled: true,
  defaultProfile: false
})

const trainingPollers = new Map<number, number>()
let neo4jHealthTimer: number | undefined

const adminManagementViews = ['modelManagement', 'models', 'datasets', 'training', 'evaluations', 'feedback', 'llmProfiles']
const navItems: NavItem[] = [
  { key: 'dashboard', label: '仪表盘', icon: DataAnalysis, roles: ['PATIENT'] },
  { key: 'doctorConsole', label: '医生控制台', icon: Monitor, roles: ['DOCTOR'] },
  { key: 'patientRecords', label: '患者风险记录', icon: TrendCharts, roles: ['DOCTOR'] },
  { key: 'predict', label: '疾病预测', icon: FirstAidKit, roles: ['PATIENT', 'DOCTOR', 'ADMIN'] },
  { key: 'history', label: '我的历史', icon: TrendCharts, roles: ['PATIENT'] },
  { key: 'reports', label: '报告中心', icon: Document, roles: ['PATIENT', 'DOCTOR'] },
  { key: 'qa', label: '智能问答', icon: ChatDotRound, roles: ['PATIENT', 'DOCTOR', 'ADMIN'] },
  { key: 'documents', label: '文档管理', icon: Document, roles: ['DOCTOR', 'ADMIN'] },
  { key: 'diseases', label: '疾病信息', icon: FirstAidKit, roles: ['DOCTOR', 'ADMIN'] },
  { key: 'medicalCases', label: '病历管理', icon: TrendCharts, roles: ['DOCTOR', 'ADMIN'] },
  { key: 'adminConsole', label: '管理控制台', icon: Monitor, roles: ['ADMIN'] },
  { key: 'graphManagement', label: '图谱管理', icon: CircleCheck, roles: ['ADMIN'] },
  { key: 'graphVisualization', label: '图谱可视化', icon: DataAnalysis, roles: ['DOCTOR', 'ADMIN'] },
  { key: 'visualization', label: '风险大屏', icon: DataAnalysis, roles: ['ADMIN'] },
  { key: 'users', label: '用户管理', icon: User, roles: ['ADMIN'] },
  { key: 'dataSeeds', label: '数据源管理', icon: Document, roles: ['ADMIN'] },
  { key: 'modelManagement', label: '模型训练管理', icon: VideoPlay, roles: ['ADMIN'] },
  { key: 'models', label: '模型版本', icon: CircleCheck, roles: ['ADMIN'] },
  { key: 'datasets', label: '数据集管理', icon: Upload, roles: ['ADMIN'] },
  { key: 'training', label: '训练任务', icon: VideoPlay, roles: ['ADMIN'] },
  { key: 'evaluations', label: '模型评估', icon: DataAnalysis, roles: ['ADMIN'] },
  { key: 'feedback', label: '模型反馈', icon: Edit, roles: ['ADMIN'] },
  { key: 'llmProfiles', label: '大模型配置', icon: ChatDotRound, roles: ['ADMIN'] },
  { key: 'audit', label: '审计日志', icon: Document, roles: ['ADMIN'] }
]
const navGroups: NavGroup[] = [
  { key: 'patient-main', label: '健康服务', roles: ['PATIENT'], itemKeys: ['dashboard', 'predict', 'history', 'reports', 'qa'] },
  { key: 'doctor-main', label: '诊疗工作台', roles: ['DOCTOR'], itemKeys: ['doctorConsole', 'patientRecords', 'predict', 'reports', 'qa'] },
  { key: 'doctor-knowledge', label: '知识库', roles: ['DOCTOR'], itemKeys: ['documents', 'diseases', 'medicalCases', 'graphVisualization'] },
  { key: 'admin-overview', label: '总览与分析', roles: ['ADMIN'], itemKeys: ['adminConsole', 'visualization', 'predict', 'qa'] },
  { key: 'admin-knowledge', label: '知识库与图谱', roles: ['ADMIN'], itemKeys: ['documents', 'diseases', 'medicalCases', 'graphManagement', 'graphVisualization', 'dataSeeds'] },
  { key: 'admin-models', label: '模型与数据', roles: ['ADMIN'], itemKeys: ['models', 'datasets', 'training', 'evaluations', 'feedback', 'llmProfiles'] },
  { key: 'admin-system', label: '系统管理', roles: ['ADMIN'], itemKeys: ['users', 'audit'] }
]
const sidebarCollapsed = ref(readBooleanSetting('medrisk-sidebar-collapsed', false))
const sidebarWidth = ref(readNumberSetting('medrisk-sidebar-width', 268, 220, 340))
const expandedNavGroupKeys = ref(readStringListSetting('medrisk-nav-groups', navGroups.map((group) => group.key)))
const appShellStyle = computed(() => ({ '--sidebar-width': `${sidebarWidth.value}px` }))

const diseaseConfigs = [
  {
    key: 'diabetes',
    label: '糖尿病',
    fields: [
      numberField('age', '年龄', 18, 90, '单位：岁'),
      numberField('glucose', '空腹血糖', 3, 16, '单位：mmol/L', 0.1),
      numberField('bmi', 'BMI', 16, 45, '单位：kg/m²', 0.1),
      numberField('bloodPressure', '收缩压', 80, 220, '单位：mmHg'),
      boolField('familyHistory', '糖尿病家族史', '直系亲属是否有相关病史')
    ]
  },
  {
    key: 'heart',
    label: '心脏病',
    fields: [
      numberField('age', '年龄', 18, 90, '单位：岁'),
      numberField('cholesterol', '总胆固醇', 2, 10, '单位：mmol/L', 0.1),
      numberField('bloodPressure', '收缩压', 80, 220, '单位：mmHg'),
      numberField('maxHeartRate', '最大心率', 60, 220, '运动测试或估算值'),
      boolField('chestPain', '胸痛症状', '近期是否存在胸痛或胸闷'),
      boolField('smoker', '吸烟', '当前是否吸烟')
    ]
  },
  {
    key: 'kidney',
    label: '慢性肾病',
    fields: [
      numberField('creatinine', '血肌酐', 30, 280, '单位：umol/L'),
      numberField('urea', '尿素', 1, 25, '单位：mmol/L', 0.1),
      numberField('albumin', '尿蛋白等级', 0, 4, '0-4 级'),
      numberField('hemoglobin', '血红蛋白', 70, 180, '单位：g/L'),
      numberField('bloodPressure', '收缩压', 80, 220, '单位：mmHg'),
      boolField('diabetesHistory', '糖尿病史', '是否有糖尿病既往史')
    ]
  },
  {
    key: 'liver',
    label: '肝病',
    fields: [
      numberField('bilirubin', '总胆红素', 3, 120, '单位：umol/L'),
      numberField('alt', 'ALT', 5, 300, '单位：U/L'),
      numberField('ast', 'AST', 5, 300, '单位：U/L'),
      numberField('albumin', '白蛋白', 20, 60, '单位：g/L'),
      boolField('alcoholUse', '饮酒', '近期是否规律饮酒')
    ]
  },
  {
    key: 'stroke',
    label: '中风',
    fields: [
      numberField('age', '年龄', 18, 95, '单位：岁'),
      numberField('bloodPressure', '收缩压', 80, 230, '单位：mmHg'),
      numberField('glucose', '血糖', 3, 18, '单位：mmol/L', 0.1),
      numberField('bmi', 'BMI', 16, 45, '单位：kg/m²', 0.1),
      boolField('smoker', '吸烟', '当前是否吸烟'),
      boolField('heartDiseaseHistory', '心脏病史', '是否有心脏病既往史')
    ]
  }
]

const diseaseOptions = diseaseConfigs.map((item) => ({ label: item.label, value: item.key }))
const activeDisease = computed(() => diseaseConfigs.find((item) => item.key === selectedDisease.value) || diseaseConfigs[0])
const visibleNavItems = computed(() => {
  const role = currentUser.value?.role
  return navItems.filter((item) => role && hasRoleAccess(role, item.roles))
})
const visibleNavGroups = computed<VisibleNavGroup[]>(() => {
  const role = currentUser.value?.role
  if (!role) return []
  return navGroups
    .filter((group) => role === 'ADMIN' ? group.roles.includes('ADMIN') : hasRoleAccess(role, group.roles))
    .map((group) => ({
      key: group.key,
      label: group.label,
      roles: group.roles,
      items: group.itemKeys
        .map((key) => navItems.find((item) => item.key === key))
        .filter((item): item is NavItem => Boolean(item && (role === 'ADMIN' ? item.roles.includes('ADMIN') : hasRoleAccess(role, item.roles))))
    }))
    .filter((group) => group.items.length > 0)
})
const currentTitle = computed(() => activeView.value === 'profile' ? '个人信息' : navItems.find((item) => item.key === activeView.value)?.label || 'MedRisk AI')
const userInitial = computed(() => currentUser.value?.name?.trim()?.slice(0, 1) || currentUser.value?.username?.slice(0, 1)?.toUpperCase() || 'U')
const highRiskCount = computed(() => history.value.filter((item) => item.riskLabel === 'high').length)
const validDatasets = computed(() => datasets.value.filter((item) => item.status === 'VALID'))
const selectedTrainingDataset = computed(() => validDatasets.value.find((item) => item.id === trainingForm.datasetId) || null)
const evaluationDatasetsForTraining = computed(() => {
  const diseaseType = selectedTrainingDataset.value?.diseaseType
  return validDatasets.value.filter((item) => item.id !== trainingForm.datasetId && (!diseaseType || item.diseaseType === diseaseType))
})
const modelTypeOptions = computed(() => modelCapabilities.value.length ? modelCapabilities.value : [
  { modelType: 'xgboost', label: 'XGBoost 稳定基线', available: true }
])
const selectedTrainingJob = computed(() => trainingJobs.value.find((item) => item.id === selectedTrainingJobId.value) || null)
const adminRecentAuditLogs = computed(() => adminSummary.value.recentAuditLogs || auditLogs.value.slice(0, 8))
const doctorHighRiskRows = computed(() => {
  const rows = doctorSummary.value.recentHighRisk?.length
    ? doctorSummary.value.recentHighRisk
    : history.value.filter((item) => item.riskLabel === 'high').slice(0, 8)
  return rows.map((row) => {
    const item = row as Record<string, unknown>
    return {
      patientName: String(item.patientName || '演示患者'),
      diseaseName: String(item.diseaseName || '-'),
      riskProbability: Number(item.riskProbability || 0),
      createdAt: String(item.createdAt || '')
    }
  })
})
const screenCards = computed(() => {
  const summary = visualization.value.summary || adminSummary.value
  return [
    { label: '用户总数', value: summary.userCount || 0 },
    { label: '预测记录', value: summary.predictionCount || 0 },
    { label: '报告数量', value: summary.reportCount || 0 },
    { label: '训练任务', value: summary.trainingJobCount || 0 },
    { label: '待处理反馈', value: summary.pendingFeedbackCount || 0 }
  ]
})
const activeUserChartOption = computed(() => pieOption(visualization.value.activeUsers || [], ['#38bdf8', '#34d399', '#fbbf24']))
const screenCaseRows = computed(() => history.value.slice(0, 4).map((item, index) => ({
  id: item.recordId || index + 1,
  diseaseName: item.diseaseName || item.diseaseType || '-',
  patientName: item.patientName || '演示患者',
  risk: riskText(item.riskLabel),
  probability: percent(item.riskProbability || 0)
})))
const screenKeywords = computed(() => {
  const diseaseWords = (visualization.value.diseaseDistribution || []).map((item) => item.name)
  const riskWords = (visualization.value.riskDistribution || []).map((item) => item.name)
  return [...diseaseWords, ...riskWords, '知识图谱', '智能问答', '风险预测'].filter(Boolean).slice(0, 12)
})
const riskChartOption = computed(() => pieOption(visualization.value.riskDistribution || [], ['#22c55e', '#f59e0b', '#ef4444']))
const diseaseChartOption = computed(() => pieOption(visualization.value.diseaseDistribution || [], ['#38bdf8', '#34d399', '#fbbf24', '#f87171', '#a78bfa']))
const trendChartOption = computed(() => {
  const trend = visualization.value.predictionTrend || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#cbd5e1' } },
    grid: { left: 42, right: 18, top: 42, bottom: 34 },
    xAxis: { type: 'category', data: trend.map((item) => item.date), axisLabel: { color: '#cbd5e1' } },
    yAxis: { type: 'value', axisLabel: { color: '#cbd5e1' }, splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } } },
    series: [
      lineSeriesForScreen('预测', trend.map((item) => item.predictions), '#38bdf8'),
      lineSeriesForScreen('报告', trend.map((item) => item.reports), '#34d399')
    ]
  }
})
const modelMetricChartOption = computed(() => {
  const rows = visualization.value.modelMetrics || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#cbd5e1' } },
    grid: { left: 46, right: 18, top: 44, bottom: 54 },
    xAxis: { type: 'category', data: rows.map((item) => item.diseaseName), axisLabel: { color: '#cbd5e1' } },
    yAxis: { type: 'value', min: 0, max: 1, axisLabel: { color: '#cbd5e1' }, splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } } },
    series: [
      barSeriesForScreen('AUC', rows.map((item) => Number(item.auc || 0)), '#38bdf8'),
      barSeriesForScreen('Recall', rows.map((item) => Number(item.recall || 0)), '#34d399'),
      barSeriesForScreen('F1', rows.map((item) => Number(item.f1 || 0)), '#fbbf24')
    ]
  }
})
onMounted(async () => {
  screenClockTimer = window.setInterval(() => {
    screenNow.value = formatScreenTime(new Date())
  }, 1000)
  const token = localStorage.getItem('medrisk-token')
  if (token) {
    try {
      currentUser.value = await request<UserInfo>('get', '/auth/me')
      syncProfileForm()
      activeView.value = defaultViewForRole(currentUser.value.role)
      await loadInitialData()
    } catch {
      localStorage.removeItem('medrisk-token')
    }
  }
})

onBeforeUnmount(() => {
  trainingPollers.forEach((timer) => window.clearInterval(timer))
  trainingPollers.clear()
  if (registerCodeTimer) window.clearInterval(registerCodeTimer)
  if (resetCodeTimer) window.clearInterval(resetCodeTimer)
  if (screenClockTimer) window.clearInterval(screenClockTimer)
  stopNeo4jHealthPolling()
})

watch(authMode, (mode) => {
  if (mode !== '登录') selectedDemoAccount.value = ''
  if (mode === '注册' && authForm.role === 'ADMIN') {
    authForm.role = 'PATIENT'
  }
})

watch(() => trainingForm.datasetId, () => {
  if (trainingForm.evaluationDatasetId && !evaluationDatasetsForTraining.value.some((item) => item.id === trainingForm.evaluationDatasetId)) {
    trainingForm.evaluationDatasetId = undefined
  }
})

watch(() => trainingForm.modelType, (modelType) => {
  if (modelType === 'xgboost') {
    trainingForm.modelName = 'XGBoost 风险分类模型'
    trainingForm.epochs = trainingForm.nEstimators
  } else if (modelType === 'logistic_regression') {
    trainingForm.modelName = 'Logistic Regression 可解释风险模型'
    trainingForm.epochs = 1
  } else if (modelType === 'random_forest') {
    trainingForm.modelName = 'Random Forest 风险分类模型'
    trainingForm.epochs = trainingForm.nEstimators
  } else if (modelType === 'extra_trees') {
    trainingForm.modelName = 'ExtraTrees 风险分类模型'
    trainingForm.epochs = trainingForm.nEstimators
  } else if (modelType === 'hist_gradient_boosting') {
    trainingForm.modelName = 'HistGradientBoosting 风险分类模型'
    trainingForm.epochs = trainingForm.nEstimators
  } else if (modelType === 'lightgbm') {
    trainingForm.modelName = 'LightGBM 风险分类模型'
    trainingForm.epochs = trainingForm.nEstimators
  } else if (modelType === 'catboost') {
    trainingForm.modelName = 'CatBoost 风险分类模型'
    trainingForm.epochs = trainingForm.nEstimators
  } else if (modelType === 'tabpfn') {
    trainingForm.modelName = 'TabPFN 表格基础模型'
    trainingForm.epochs = 1
  } else if (modelType === 'tabicl') {
    trainingForm.modelName = 'TabICL 表格上下文学习模型'
    trainingForm.epochs = 1
  } else if (modelType === 'ft_transformer') {
    trainingForm.modelName = 'FT-Transformer 表格深度模型'
    trainingForm.epochs = 30
  }
})

watch(sidebarCollapsed, (value) => {
  localStorage.setItem('medrisk-sidebar-collapsed', String(value))
})

watch(sidebarWidth, (value) => {
  localStorage.setItem('medrisk-sidebar-width', String(value))
})

watch(expandedNavGroupKeys, (value) => {
  localStorage.setItem('medrisk-nav-groups', JSON.stringify(value))
}, { deep: true })

watch([currentUser, currentTitle], updateDocumentTitle, { immediate: true })

watch(() => currentUser.value?.role, (role) => {
  if (role === 'ADMIN' && localStorage.getItem('medrisk-token')) {
    startNeo4jHealthPolling()
  } else {
    stopNeo4jHealthPolling()
    neo4jHealth.value = {}
  }
}, { immediate: true })

function readBooleanSetting(key: string, fallback: boolean) {
  try {
    const value = localStorage.getItem(key)
    return value === null ? fallback : value === 'true'
  } catch {
    return fallback
  }
}

function readStringListSetting(key: string, fallback: string[]) {
  try {
    const value = localStorage.getItem(key)
    if (!value) return fallback
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : fallback
  } catch {
    return fallback
  }
}

function readNumberSetting(key: string, fallback: number, min: number, max: number) {
  try {
    const value = Number(localStorage.getItem(key))
    if (!Number.isFinite(value)) return fallback
    return Math.min(max, Math.max(min, value))
  } catch {
    return fallback
  }
}

function isNavGroupExpanded(key: string) {
  return sidebarCollapsed.value || expandedNavGroupKeys.value.includes(key)
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function startSidebarResize(event: PointerEvent) {
  if (sidebarCollapsed.value) return
  const startX = event.clientX
  const startWidth = sidebarWidth.value
  document.body.classList.add('is-resizing-sidebar')

  const move = (moveEvent: PointerEvent) => {
    sidebarWidth.value = Math.min(420, Math.max(220, startWidth + moveEvent.clientX - startX))
  }
  const stop = () => {
    document.body.classList.remove('is-resizing-sidebar')
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', stop)
  }

  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', stop)
  event.preventDefault()
}

function toggleNavGroup(key: string) {
  if (sidebarCollapsed.value) {
    sidebarCollapsed.value = false
    if (!expandedNavGroupKeys.value.includes(key)) {
      expandedNavGroupKeys.value = [...expandedNavGroupKeys.value, key]
    }
    return
  }
  if (expandedNavGroupKeys.value.includes(key)) {
    expandedNavGroupKeys.value = expandedNavGroupKeys.value.filter((item) => item !== key)
  } else {
    expandedNavGroupKeys.value = [...expandedNavGroupKeys.value, key]
  }
}

function hasRoleAccess(role: string, allowedRoles: string[]) {
  if (role === 'ADMIN') return allowedRoles.some((item) => ['ADMIN', 'DOCTOR', 'PATIENT'].includes(item))
  if (role === 'DOCTOR') return allowedRoles.includes('DOCTOR')
  return allowedRoles.includes(role)
}

async function loadNeo4jHealth() {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') return
  if (!localStorage.getItem('medrisk-token')) return
  try {
    neo4jHealth.value = await request<GraphHealth>('get', '/admin/knowledge-graph/health')
  } catch (error) {
    neo4jHealth.value = {
      connected: false,
      status: 'DOWN',
      message: error instanceof Error ? error.message : 'Neo4j 连接检查失败'
    }
  }
}

function startNeo4jHealthPolling() {
  stopNeo4jHealthPolling()
  void loadNeo4jHealth()
  neo4jHealthTimer = window.setInterval(() => {
    void loadNeo4jHealth()
  }, 10000)
}

function stopNeo4jHealthPolling() {
  if (neo4jHealthTimer) {
    window.clearInterval(neo4jHealthTimer)
    neo4jHealthTimer = undefined
  }
}

function updateDocumentTitle() {
  if (!currentUser.value) {
    document.title = 'MedRisk AI'
    return
  }
  const displayName = currentUser.value.name || currentUser.value.username
  document.title = `MedRisk AI - ${roleText(currentUser.value.role)} - ${displayName} - ${currentTitle.value}`
}

function numberField(key: string, label: string, min: number, max: number, hint: string, step = 1): FieldConfig {
  return { key, label, min, max, hint, step, type: 'number' }
}

function boolField(key: string, label: string, hint: string): FieldConfig {
  return { key, label, hint, type: 'boolean' }
}

function useDemoAccount(username: string) {
  authMode.value = '登录'
  selectedDemoAccount.value = username
  authForm.username = username
  authForm.password = '123456'
  authForm.emailCode = ''
  authForm.email = `${username}@medrisk.local`
  authForm.name = username === 'admin' ? '管理员' : username === 'doctor' ? '演示医生' : '演示患者'
  authForm.role = username === 'admin' ? 'ADMIN' : username === 'doctor' ? 'DOCTOR' : 'PATIENT'
}

function defaultViewForRole(role: string) {
  if (role === 'ADMIN') return 'adminConsole'
  if (role === 'DOCTOR') return 'doctorConsole'
  return 'dashboard'
}

function canAccessView(view: string) {
  if (view === 'profile') return Boolean(currentUser.value)
  const role = currentUser.value?.role
  return Boolean(role && navItems.some((item) => item.key === view && hasRoleAccess(role, item.roles)))
}

async function loadInitialData() {
  if (!currentUser.value) return
  if (currentUser.value.role === 'ADMIN') {
    await Promise.all([loadAdminConsole(), loadAdmin(), loadNeo4jHealth()])
    return
  }
  if (currentUser.value.role === 'DOCTOR') {
    await Promise.all([loadDoctorConsole(), loadReports()])
    return
  }
  await Promise.all([loadHistory(), loadReports()])
}

async function submitAuth() {
  if (authMode.value === '重置密码') {
    await submitPasswordReset()
    return
  }
  loading.value = true
  try {
    const endpoint = authMode.value === '登录' ? '/auth/login' : '/auth/register'
    const payload = { ...authForm, role: authMode.value === '注册' && authForm.role === 'ADMIN' ? 'PATIENT' : authForm.role }
    const data = await request<{ token: string; user: UserInfo }>('post', endpoint, payload)
    localStorage.setItem('medrisk-token', data.token)
    currentUser.value = data.user
    syncProfileForm()
    activeView.value = defaultViewForRole(data.user.role)
    ElMessage.success('登录成功')
    await loadInitialData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '认证失败')
  } finally {
    loading.value = false
  }
}

function logout() {
  localStorage.removeItem('medrisk-token')
  stopNeo4jHealthPolling()
  currentUser.value = null
  neo4jHealth.value = {}
  predictionResult.value = null
  activeView.value = 'dashboard'
}

function syncProfileForm() {
  if (!currentUser.value) return
  profileForm.email = currentUser.value.email || ''
  profileForm.name = currentUser.value.name || ''
  profileForm.phone = currentUser.value.phone || ''
}

async function submitProfile() {
  if (!profileForm.name || !profileForm.email) {
    ElMessage.warning('请填写姓名和邮箱')
    return
  }
  loading.value = true
  try {
    currentUser.value = await request<UserInfo>('put', '/auth/me', { ...profileForm })
    syncProfileForm()
    ElMessage.success('个人资料已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料保存失败')
  } finally {
    loading.value = false
  }
}

async function handleAvatarUpload(file: UploadFile) {
  if (!file.raw) return
  const form = new FormData()
  form.append('avatar', file.raw)
  try {
    currentUser.value = await postForm<UserInfo>('/auth/me/avatar', form)
    syncProfileForm()
    ElMessage.success('头像已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '头像上传失败')
  }
}

async function submitPassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写旧密码和新密码')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  try {
    currentUser.value = await request<UserInfo>('post', '/auth/me/password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    ElMessage.success('密码已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码更新失败')
  }
}

async function changeView(view: string) {
  if (!canAccessView(view)) {
    activeView.value = defaultViewForRole(currentUser.value?.role || 'PATIENT')
    view = activeView.value
  }
  activeView.value = view
  if (view === 'profile') {
    syncProfileForm()
    return
  }
  if (view === 'history' || view === 'patientRecords' || view === 'dashboard') await loadHistory()
  if (view === 'doctorConsole') await loadDoctorConsole()
  if (view === 'reports') await loadReports()
  if (view === 'adminConsole') await loadAdminConsole()
  if (view === 'users') await loadAdminUsers()
  if (view === 'audit') await loadAdmin()
  if (adminManagementViews.includes(view)) await loadAdmin()
}

async function submitPrediction() {
  loading.value = true
  try {
    const result = await request<Prediction>('post', `/predict/${selectedDisease.value}`, { ...predictionForm })
    predictionResult.value = result
    ElMessage.success('预测完成')
    await loadHistory()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预测失败')
  } finally {
    loading.value = false
  }
}

function fillDemo() {
  Object.assign(predictionForm, {
    patientName: '演示患者',
    age: 62,
    bmi: 29.5,
    glucose: 8.2,
    bloodPressure: 152,
    cholesterol: 6.3,
    smoker: true,
    familyHistory: true,
    chestPain: true,
    maxHeartRate: 118
  })
}

async function loadHistory() {
  if (!currentUser.value) return
  history.value = await request<Prediction[]>('get', '/history/predictions')
}

async function loadReports() {
  if (!currentUser.value) return
  reports.value = await request<Report[]>('get', '/reports')
  selectedReport.value ||= reports.value[0] || null
}

async function loadDoctorConsole() {
  if (!currentUser.value || !hasRoleAccess(currentUser.value.role, ['DOCTOR'])) return
  const [summary] = await Promise.all([
    request<DoctorSummary>('get', '/doctor/console/summary'),
    loadHistory()
  ])
  doctorSummary.value = summary
}

async function loadAdminConsole() {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') return
  adminSummary.value = await request<AdminSummary>('get', '/admin/console/summary')
  auditLogs.value = adminSummary.value.recentAuditLogs || auditLogs.value
}

async function loadVisualization() {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') return
  visualization.value = await request<VisualizationData>('get', '/admin/visualization')
  if (visualization.value.summary) adminSummary.value = visualization.value.summary
}

async function loadAdminUsers() {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') return
  const params = new URLSearchParams()
  if (userFilters.keyword) params.set('keyword', userFilters.keyword)
  if (userFilters.role) params.set('role', userFilters.role)
  if (userFilters.status) params.set('status', userFilters.status)
  const query = params.toString()
  adminUsers.value = await request<AdminUser[]>('get', `/admin/users${query ? `?${query}` : ''}`)
}

async function loadAdmin() {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') return
  try {
    const [modelData, auditData, datasetData, jobData, evaluationData, feedbackData, capabilityData, llmProfileData] = await Promise.all([
      request<ModelVersion[]>('get', '/admin/models'),
      request<AuditLog[]>('get', '/admin/audit-logs'),
      request<TrainingDataset[]>('get', '/admin/datasets'),
      request<TrainingJob[]>('get', '/admin/training-jobs'),
      request<ModelEvaluation[]>('get', '/admin/model-evaluations'),
      request<ModelFeedback[]>('get', '/admin/model-feedback'),
      request<ModelCapability[]>('get', '/admin/model-capabilities'),
      request<LlmProfile[]>('get', '/admin/llm-profiles')
    ])
    models.value = modelData
    auditLogs.value = auditData
    datasets.value = datasetData
    trainingJobs.value = jobData
    evaluations.value = evaluationData
    feedbackList.value = feedbackData
    modelCapabilities.value = capabilityData
    llmProfiles.value = llmProfileData
    trainingJobs.value.filter((item) => !isTrainingDone(item.trainStatus)).forEach((item) => pollTrainingJob(item.id))
  } catch {
    models.value = []
    auditLogs.value = []
    datasets.value = []
    trainingJobs.value = []
    evaluations.value = []
    feedbackList.value = []
    llmProfiles.value = []
  }
}

async function sendRegisterCode() {
  if (!authForm.email) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  await sendEmailCode('/auth/register/code', authForm.email, registerCodeCooldown)
}

async function sendResetCode() {
  if (!resetForm.email) {
    ElMessage.warning('请先填写注册邮箱')
    return
  }
  await sendEmailCode('/auth/password/forgot', resetForm.email, resetCodeCooldown)
}

async function sendEmailCode(endpoint: string, email: string, cooldown: typeof registerCodeCooldown) {
  codeSending.value = true
  try {
    await request('post', endpoint, { email })
    ElMessage.success('验证码已发送，请查收邮箱')
    startCodeCooldown(cooldown)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '验证码发送失败')
  } finally {
    codeSending.value = false
  }
}

function startCodeCooldown(cooldown: typeof registerCodeCooldown) {
  cooldown.value = 60
  if (cooldown === registerCodeCooldown && registerCodeTimer) window.clearInterval(registerCodeTimer)
  if (cooldown === resetCodeCooldown && resetCodeTimer) window.clearInterval(resetCodeTimer)
  const timer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      window.clearInterval(timer)
    }
  }, 1000)
  if (cooldown === registerCodeCooldown) registerCodeTimer = timer
  if (cooldown === resetCodeCooldown) resetCodeTimer = timer
}

async function submitPasswordReset() {
  if (!resetForm.email || !resetForm.emailCode || !resetForm.newPassword) {
    ElMessage.warning('请填写邮箱、验证码和新密码')
    return
  }
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await request('post', '/auth/password/reset', {
      email: resetForm.email,
      emailCode: resetForm.emailCode,
      newPassword: resetForm.newPassword
    })
    ElMessage.success('密码已重置，请使用新密码登录')
    authMode.value = '登录'
    authForm.email = resetForm.email
    resetForm.emailCode = ''
    resetForm.newPassword = ''
    resetForm.confirmPassword = ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码重置失败')
  } finally {
    loading.value = false
  }
}

async function submitUser() {
  if (!userForm.username || !userForm.email || !userForm.name) {
    ElMessage.warning('请填写用户名、姓名和邮箱')
    return
  }
  loading.value = true
  try {
    const payload = {
      username: userForm.username,
      email: userForm.email,
      name: userForm.name,
      phone: userForm.phone,
      role: userForm.role,
      status: userForm.status,
      password: userEditingId.value ? undefined : userForm.password
    }
    const user = userEditingId.value
      ? await request<AdminUser>('put', `/admin/users/${userEditingId.value}`, payload)
      : await request<AdminUser>('post', '/admin/users', payload)
    adminUsers.value = [user, ...adminUsers.value.filter((item) => item.id !== user.id)]
    resetUserForm()
    await loadAdminConsole()
    ElMessage.success('用户已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户保存失败')
  } finally {
    loading.value = false
  }
}

function editUser(row: AdminUser) {
  userEditingId.value = row.id
  userForm.username = row.username
  userForm.email = row.email
  userForm.name = row.name
  userForm.phone = row.phone || ''
  userForm.role = row.role
  userForm.status = row.status
  userForm.password = ''
}

function resetUserForm() {
  userEditingId.value = null
  userForm.username = ''
  userForm.email = ''
  userForm.name = ''
  userForm.phone = ''
  userForm.role = 'PATIENT'
  userForm.status = 'ACTIVE'
  userForm.password = '123456'
}

async function toggleUserStatus(row: AdminUser) {
  try {
    const nextStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    const updated = await request<AdminUser>('post', `/admin/users/${row.id}/status`, { status: nextStatus })
    adminUsers.value = adminUsers.value.map((item) => (item.id === updated.id ? updated : item))
    await loadAdminConsole()
    ElMessage.success(nextStatus === 'ACTIVE' ? '用户已启用' : '用户已禁用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户状态更新失败')
  }
}

async function resetUserPassword(id: number) {
  try {
    await ElMessageBox.confirm('确认将该用户密码重置为 123456？', '重置密码', { type: 'warning' })
    await request('post', `/admin/users/${id}/reset-password`, { password: '123456' })
    ElMessage.success('密码已重置为 123456')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '密码重置失败')
    }
  }
}

async function deleteUser(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该用户？已有业务记录的用户请改为禁用。', '删除用户', { type: 'warning' })
    await request('delete', `/admin/users/${id}`)
    adminUsers.value = adminUsers.value.filter((item) => item.id !== id)
    await loadAdminConsole()
    ElMessage.success('用户已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '用户删除失败')
    }
  }
}

function handleDatasetFile(file: UploadFile) {
  selectedDatasetFile.value = file.raw || null
}

function clearDatasetFile() {
  selectedDatasetFile.value = null
}

async function submitDataset() {
  if (!selectedDatasetFile.value) {
    ElMessage.warning('请先选择 CSV 或 ZIP 数据集文件')
    return
  }
  loading.value = true
  try {
    const formData = new FormData()
    formData.append('name', datasetForm.name)
    formData.append('diseaseType', datasetForm.diseaseType)
    formData.append('description', datasetForm.description)
    formData.append('file', selectedDatasetFile.value)
    await uploadAdminDataset(formData)
    selectedDatasetFile.value = null
    ElMessage.success('数据集已上传并完成校验')
    await loadAdmin()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据集上传失败')
  } finally {
    loading.value = false
  }
}

async function validateDataset(id: number) {
  try {
    const dataset = await request<TrainingDataset>('post', `/admin/datasets/${id}/validate`)
    upsertDataset(dataset)
    ElMessage.success('数据集校验完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据集校验失败')
  }
}

async function deleteDataset(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该数据集？已被训练任务或评估引用的数据集不会被删除。', '删除数据集', { type: 'warning' })
    await request('delete', `/admin/datasets/${id}`)
    datasets.value = datasets.value.filter((item) => item.id !== id)
    ElMessage.success('数据集已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '数据集删除失败')
    }
  }
}

async function importPublicDatasets() {
  loading.value = true
  try {
    const imported = await request<TrainingDataset[]>('post', '/admin/datasets/import-public')
    imported.forEach(upsertDataset)
    ElMessage.success(`已导入 ${imported.length} 个公开训练/评估数据集`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '公开数据集导入失败')
  } finally {
    loading.value = false
  }
}

function trainingHyperparameters() {
  if (trainingForm.modelType === 'logistic_regression') {
    return {
      cValue: trainingForm.cValue,
      maxIterations: trainingForm.maxIterations,
      classWeight: trainingForm.classWeight,
      testSize: trainingForm.testSize
    }
  }
  if (['random_forest', 'extra_trees'].includes(trainingForm.modelType)) {
    trainingForm.epochs = trainingForm.nEstimators
    return {
      nEstimators: trainingForm.nEstimators,
      maxDepth: trainingForm.maxDepth,
      classWeight: trainingForm.classWeight,
      seed: trainingForm.seed,
      testSize: trainingForm.testSize
    }
  }
  if (trainingForm.modelType === 'hist_gradient_boosting') {
    trainingForm.epochs = trainingForm.nEstimators
    return {
      maxIterations: trainingForm.nEstimators,
      maxDepth: trainingForm.maxDepth,
      learningRate: trainingForm.learningRate,
      regLambda: trainingForm.regLambda,
      seed: trainingForm.seed,
      testSize: trainingForm.testSize
    }
  }
  if (['lightgbm', 'catboost'].includes(trainingForm.modelType)) {
    trainingForm.epochs = trainingForm.nEstimators
    return {
      nEstimators: trainingForm.nEstimators,
      maxDepth: trainingForm.maxDepth,
      learningRate: trainingForm.learningRate,
      subsample: trainingForm.subsample,
      colsampleBytree: trainingForm.colsampleBytree,
      regLambda: trainingForm.regLambda,
      seed: trainingForm.seed,
      testSize: trainingForm.testSize
    }
  }
  if (trainingForm.modelType === 'tabpfn') {
    return {
      maxTrainSamples: trainingForm.maxTrainSamples,
      device: trainingForm.device,
      ensembleSize: trainingForm.ensembleSize,
      testSize: trainingForm.testSize
    }
  }
  if (trainingForm.modelType === 'tabicl') {
    return {
      contextSize: trainingForm.contextSize,
      maxTrainSamples: trainingForm.maxTrainSamples,
      device: trainingForm.device,
      seed: trainingForm.seed,
      testSize: trainingForm.testSize
    }
  }
  if (trainingForm.modelType === 'ft_transformer') {
    return {
      maxTrainSamples: trainingForm.maxTrainSamples,
      contextSize: trainingForm.contextSize,
      learningRate: trainingForm.learningRate,
      device: trainingForm.device,
      seed: trainingForm.seed,
      testSize: trainingForm.testSize
    }
  }
  trainingForm.epochs = trainingForm.nEstimators
  return {
    nEstimators: trainingForm.nEstimators,
    maxDepth: trainingForm.maxDepth,
    learningRate: trainingForm.learningRate,
    subsample: trainingForm.subsample,
    colsampleBytree: trainingForm.colsampleBytree,
    regLambda: trainingForm.regLambda,
    minChildWeight: trainingForm.minChildWeight,
    testSize: trainingForm.testSize
  }
}

async function createTrainingJob() {
  if (!trainingForm.datasetId) {
    ElMessage.warning('请选择已校验的数据集')
    return
  }
  const capability = modelCapabilities.value.find((item) => item.modelType === trainingForm.modelType)
  if (capability && !capability.available) {
    ElMessage.warning(capability.reason || '该模型类型当前不可用')
    return
  }
  loading.value = true
  try {
    const hyperparameters = trainingHyperparameters()
    const job = await request<TrainingJob>('post', '/admin/training-jobs', {
      datasetId: trainingForm.datasetId,
      evaluationDatasetId: trainingForm.evaluationDatasetId || null,
      modelName: trainingForm.modelName,
      modelType: trainingForm.modelType,
      epochs: trainingForm.epochs,
      learningRate: usesLearningRate(trainingForm.modelType) ? trainingForm.learningRate : 0,
      testSize: trainingForm.testSize,
      hyperparameters
    })
    upsertTrainingJob(job)
    selectedTrainingJobId.value = job.id
    await loadTrainingHistory(job.id)
    if (!isTrainingDone(job.trainStatus)) pollTrainingJob(job.id)
    ElMessage.success('训练任务已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '训练任务创建失败')
  } finally {
    loading.value = false
  }
}

async function refreshTrainingJob(id: number) {
  try {
    const job = await request<TrainingJob>('get', `/admin/training-jobs/${id}/status`)
    upsertTrainingJob(job)
    selectedTrainingJobId.value = id
    await loadTrainingHistory(id)
    if (isTrainingDone(job.trainStatus)) clearTrainingPoller(id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '训练状态刷新失败')
  }
}

async function stopTrainingJob(id: number) {
  try {
    const job = await request<TrainingJob>('post', `/admin/training-jobs/${id}/stop`)
    upsertTrainingJob(job)
    clearTrainingPoller(id)
    ElMessage.success('训练任务已终止')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '训练任务终止失败')
  }
}

async function loadTrainingHistory(id: number) {
  try {
    selectedTrainingJobId.value = id
    trainingHistory.value = await request<TrainingHistory>('get', `/admin/training-jobs/${id}/history`)
  } catch {
    trainingHistory.value = { taskId: id, history: {} }
  }
}

function pollTrainingJob(id: number) {
  if (trainingPollers.has(id)) return
  const timer = window.setInterval(async () => {
    try {
      const job = await request<TrainingJob>('get', `/admin/training-jobs/${id}/status`)
      upsertTrainingJob(job)
      if (selectedTrainingJobId.value === id) {
        trainingHistory.value = await request<TrainingHistory>('get', `/admin/training-jobs/${id}/history`)
      }
      if (isTrainingDone(job.trainStatus)) clearTrainingPoller(id)
    } catch {
      clearTrainingPoller(id)
    }
  }, 2000)
  trainingPollers.set(id, timer)
}

function clearTrainingPoller(id: number) {
  const timer = trainingPollers.get(id)
  if (timer) window.clearInterval(timer)
  trainingPollers.delete(id)
}

async function activateAdminModel(id: number) {
  try {
    const model = await request<ModelVersion>('post', `/admin/models/${id}/activate`)
    models.value = models.value.map((item) => ({
      ...item,
      active: item.diseaseType === model.diseaseType ? item.id === model.id : item.active
    }))
    ElMessage.success('模型版本已启用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型启用失败，请确认模型服务已启动')
  }
}

function prepareEvaluation(modelVersionId: number) {
  activeView.value = 'evaluations'
  evaluationForm.modelVersionId = modelVersionId
  evaluationForm.datasetId ||= validDatasets.value[0]?.id
}

async function createEvaluation() {
  if (!evaluationForm.modelVersionId || !evaluationForm.datasetId) {
    ElMessage.warning('请选择模型版本和评估数据集')
    return
  }
  loading.value = true
  try {
    const evaluation = await request<ModelEvaluation>('post', '/admin/model-evaluations', { ...evaluationForm })
    evaluations.value = [evaluation, ...evaluations.value.filter((item) => item.id !== evaluation.id)]
    ElMessage.success('模型评估完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型评估失败，请确认模型服务已启动')
  } finally {
    loading.value = false
  }
}

async function submitFeedback() {
  if (!feedbackForm.content.trim()) {
    ElMessage.warning('请填写反馈内容')
    return
  }
  loading.value = true
  try {
    const payload = cleanOptionalIds({ ...feedbackForm })
    const feedback = feedbackEditingId.value
      ? await request<ModelFeedback>('put', `/admin/model-feedback/${feedbackEditingId.value}`, payload)
      : await request<ModelFeedback>('post', '/admin/model-feedback', payload)
    feedbackList.value = [feedback, ...feedbackList.value.filter((item) => item.id !== feedback.id)]
    resetFeedbackForm()
    ElMessage.success('反馈已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '反馈保存失败')
  } finally {
    loading.value = false
  }
}

function editFeedback(row: ModelFeedback) {
  feedbackEditingId.value = row.id
  feedbackForm.modelVersionId = row.modelVersionId
  feedbackForm.evaluationId = row.evaluationId
  feedbackForm.problemType = row.problemType
  feedbackForm.priority = row.priority
  feedbackForm.status = row.status
  feedbackForm.content = row.content
}

async function deleteFeedback(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该反馈？', '删除反馈', { type: 'warning' })
    await request('delete', `/admin/model-feedback/${id}`)
    feedbackList.value = feedbackList.value.filter((item) => item.id !== id)
    ElMessage.success('反馈已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '反馈删除失败')
    }
  }
}

async function submitLlmProfile() {
  if (!llmProfileForm.displayName || !llmProfileForm.baseUrl || !llmProfileForm.modelName) {
    ElMessage.warning('请填写显示名称、Base URL 和模型名称')
    return
  }
  loading.value = true
  try {
    const payload = {
      ...llmProfileForm,
      apiKey: llmProfileForm.apiKey || undefined
    }
    const saved = llmProfileEditingId.value
      ? await request<LlmProfile>('put', `/admin/llm-profiles/${llmProfileEditingId.value}`, payload)
      : await request<LlmProfile>('post', '/admin/llm-profiles', payload)
    llmProfiles.value = [saved, ...llmProfiles.value.filter((item) => item.id !== saved.id)].map((item) => ({
      ...item,
      defaultProfile: saved.defaultProfile ? item.id === saved.id : item.defaultProfile
    }))
    resetLlmProfileForm()
    ElMessage.success('大模型配置已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '大模型配置保存失败')
  } finally {
    loading.value = false
  }
}

function editLlmProfile(row: LlmProfile) {
  llmProfileEditingId.value = row.id
  llmProfileForm.displayName = row.displayName
  llmProfileForm.provider = row.provider || 'openai-compatible'
  llmProfileForm.baseUrl = row.baseUrl
  llmProfileForm.modelName = row.modelName
  llmProfileForm.apiKey = ''
  llmProfileForm.reasoningSupported = Boolean(row.reasoningSupported)
  llmProfileForm.reasoningProtocol = row.reasoningProtocol || 'none'
  llmProfileForm.enabled = Boolean(row.enabled)
  llmProfileForm.defaultProfile = Boolean(row.defaultProfile)
}

async function disableLlmProfile(id: number) {
  try {
    await request('delete', `/admin/llm-profiles/${id}`)
    llmProfiles.value = llmProfiles.value.map((item) => (item.id === id ? { ...item, enabled: false } : item))
    ElMessage.success('大模型配置已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '大模型配置停用失败')
  }
}

function resetLlmProfileForm() {
  llmProfileEditingId.value = null
  llmProfileForm.displayName = '阿里云百炼 Qwen'
  llmProfileForm.provider = 'dashscope'
  llmProfileForm.baseUrl = ''
  llmProfileForm.modelName = 'qwen-plus'
  llmProfileForm.apiKey = ''
  llmProfileForm.reasoningSupported = true
  llmProfileForm.reasoningProtocol = 'bailian'
  llmProfileForm.enabled = true
  llmProfileForm.defaultProfile = false
}

function resetFeedbackForm() {
  feedbackEditingId.value = null
  feedbackForm.modelVersionId = undefined
  feedbackForm.evaluationId = undefined
  feedbackForm.problemType = '模型效果'
  feedbackForm.priority = '中'
  feedbackForm.status = '待处理'
  feedbackForm.content = ''
}

function upsertDataset(dataset: TrainingDataset) {
  datasets.value = [dataset, ...datasets.value.filter((item) => item.id !== dataset.id)]
}

function upsertTrainingJob(job: TrainingJob) {
  trainingJobs.value = [job, ...trainingJobs.value.filter((item) => item.id !== job.id)]
}

function cleanOptionalIds(payload: typeof feedbackForm) {
  return {
    ...payload,
    modelVersionId: payload.modelVersionId || undefined,
    evaluationId: payload.evaluationId || undefined
  }
}

async function generateReport(recordId: number) {
  pendingReportRecordId.value = recordId
  reportDialogVisible.value = true
  reportConversationId.value = null
  reportMessages.value = []
  selectedQaMessageIds.value = []
  includeReasoningInReport.value = false
  try {
    reportConversations.value = await request<ReportConversation[]>('get', '/conversations')
  } catch {
    reportConversations.value = []
  }
}

async function loadReportConversationMessages() {
  selectedQaMessageIds.value = []
  reportMessages.value = []
  if (!reportConversationId.value) return
  try {
    const detail = await request<{ conversation: ReportConversation; messages: ReportQaMessage[] }>('get', `/conversations/${reportConversationId.value}`)
    reportMessages.value = detail.messages || []
    selectedQaMessageIds.value = reportMessages.value.slice(-3).map((item) => item.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '问诊记录加载失败')
  }
}

async function submitReportGeneration(includeQa: boolean) {
  if (!pendingReportRecordId.value) return
  try {
    loading.value = true
    const payload = includeQa && reportConversationId.value
      ? {
          conversationId: reportConversationId.value,
          qaMessageIds: selectedQaMessageIds.value,
          includeReasoning: includeReasoningInReport.value
        }
      : undefined
    const report = await request<Report>('post', `/reports/generate/${pendingReportRecordId.value}`, payload)
    selectedReport.value = report
    await loadReports()
    activeView.value = 'reports'
    reportDialogVisible.value = false
    ElMessage.success('报告已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报告生成失败')
  } finally {
    loading.value = false
  }
}

async function download(reportId: number) {
  const response = await downloadReport(reportId)
  const blobUrl = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = `MedRisk-AI-report-${reportId}.pdf`
  link.click()
  URL.revokeObjectURL(blobUrl)
}

function riskText(label: RiskLabel | string) {
  return label === 'high' ? '高风险' : label === 'medium' ? '中风险' : '低风险'
}

function riskType(label: RiskLabel | string) {
  return label === 'high' ? 'danger' : label === 'medium' ? 'warning' : 'success'
}

function metric(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  const numberValue = Number(value)
  if (Number.isNaN(numberValue)) return String(value)
  return numberValue.toFixed(3).replace(/0+$/, '').replace(/\.$/, '')
}

function diseaseText(value: string) {
  return diseaseOptions.find((item) => item.value === value)?.label || value
}

function modelTypeText(value?: string) {
  const normalized = value || 'xgboost'
  return modelCapabilities.value.find((item) => item.modelType === normalized)?.label || normalized
}

function usesEstimatorCount(modelType: string) {
  return ['xgboost', 'random_forest', 'extra_trees', 'hist_gradient_boosting', 'lightgbm', 'catboost'].includes(modelType)
}

function usesTreeDepth(modelType: string) {
  return ['xgboost', 'random_forest', 'extra_trees', 'hist_gradient_boosting', 'lightgbm', 'catboost'].includes(modelType)
}

function usesLearningRate(modelType: string) {
  return ['xgboost', 'hist_gradient_boosting', 'lightgbm', 'catboost', 'ft_transformer'].includes(modelType)
}

function usesSampling(modelType: string) {
  return ['xgboost', 'lightgbm', 'catboost'].includes(modelType)
}

function usesRegularization(modelType: string) {
  return ['xgboost', 'hist_gradient_boosting', 'lightgbm', 'catboost'].includes(modelType)
}

function usesAdvancedSampleLimit(modelType: string) {
  return ['tabpfn', 'tabicl', 'ft_transformer'].includes(modelType)
}

function usesDevice(modelType: string) {
  return ['tabpfn', 'tabicl', 'ft_transformer'].includes(modelType)
}

function usesContextSize(modelType: string) {
  return ['tabicl', 'ft_transformer'].includes(modelType)
}

function usesSeed(modelType: string) {
  return ['random_forest', 'extra_trees', 'hist_gradient_boosting', 'lightgbm', 'catboost', 'tabicl', 'ft_transformer'].includes(modelType)
}

function datasetStatus(status: string) {
  return status === 'VALID' ? '已通过' : status === 'INVALID' ? '未通过' : '待校验'
}

function trainingStatusType(status: string) {
  if (status === '训练成功' || status === '评估完成') return 'success'
  if (status === '训练失败') return 'danger'
  if (status === '训练终止') return 'warning'
  return 'primary'
}

function isTrainingDone(status: string) {
  return ['训练成功', '训练失败', '训练终止', '评估完成'].includes(status)
}

function confusionText(value: unknown) {
  if (!Array.isArray(value)) return '-'
  return value.map((row) => (Array.isArray(row) ? row.join('/') : String(row))).join(' | ')
}

function roleText(role: string) {
  return role === 'ADMIN' ? '管理员' : role === 'DOCTOR' ? '医生' : '患者'
}

function userStatusText(status?: string) {
  return status === 'DISABLED' ? '禁用' : '启用'
}

function visibilityText(value?: string) {
  if (value === 'DOCTOR_ONLY') return '医生专用'
  if (value === 'ADMIN_ONLY') return '管理员'
  if (value === 'DRAFT') return '草稿'
  return '公开'
}

function percent(value: number) {
  return `${Math.round(value * 100)}%`
}

function formatScreenTime(value: Date) {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}

function pieOption(data: NameValue[], colors: string[]) {
  const chartData = data.length ? data : [{ name: '暂无数据', value: 0 }]
  return {
    color: colors,
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#cbd5e1' } },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '44%'],
        label: { color: '#e2e8f0' },
        data: chartData
      }
    ]
  }
}

function lineSeriesForScreen(name: string, data: number[], color: string) {
  return {
    name,
    type: 'line',
    smooth: true,
    showSymbol: false,
    data,
    lineStyle: { width: 3, color },
    itemStyle: { color },
    areaStyle: { color: `${color}22` }
  }
}

function barSeriesForScreen(name: string, data: number[], color: string) {
  return {
    name,
    type: 'bar',
    data,
    barWidth: 14,
    itemStyle: { color, borderRadius: [4, 4, 0, 0] }
  }
}
</script>
