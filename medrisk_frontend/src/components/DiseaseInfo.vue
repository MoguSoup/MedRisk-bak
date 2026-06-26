<template>
  <section class="view-stack">
    <section v-if="canSubmit" class="panel">
      <div class="panel-title">
        <h3>{{ editingId ? '编辑疾病' : isAdmin ? '新增疾病' : '提交疾病草稿' }}</h3>
        <el-button v-if="editingId" @click="resetForm">取消编辑</el-button>
      </div>
      <el-form label-position="top" class="admin-form-grid">
        <el-form-item label="疾病编号">
          <el-input v-model="form.diseaseCode" placeholder="ICD-10 编号" />
        </el-form-item>
        <el-form-item label="疾病名称">
          <el-input v-model="form.diseaseName" />
        </el-form-item>
        <el-form-item label="英文名称">
          <el-input v-model="form.diseaseNameEn" />
        </el-form-item>
        <el-form-item label="就诊科室">
          <el-input v-model="form.department" />
        </el-form-item>
        <el-form-item label="疾病类别">
          <el-input v-model="form.diseaseCategory" />
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="form.severityLevel">
            <el-option label="轻度" value="轻度" />
            <el-option label="中度" value="中度" />
            <el-option label="重度" value="重度" />
            <el-option label="严重" value="严重" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin" label="可见性">
          <el-select v-model="form.visibility">
            <el-option label="公开" value="PUBLIC" />
            <el-option label="医生专用" value="DOCTOR_ONLY" />
            <el-option label="管理员" value="ADMIN_ONLY" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否传染">
          <el-switch v-model="form.infectious" />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload :auto-upload="false" :limit="1" :on-change="handleImage" :on-remove="clearImage" accept="image/*">
            <el-button :icon="Upload">选择图片</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="症状" class="wide-form-item">
          <el-input v-model="form.symptoms" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="治疗方案" class="wide-form-item">
          <el-input v-model="form.treatmentPlan" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="详细描述" class="wide-form-item">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="操作">
          <el-button type="primary" :loading="loading" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>疾病信息</h3>
        <div class="table-actions">
          <el-input v-model="keyword" clearable placeholder="疾病名称或编号" @keyup.enter="loadDiseases" />
          <el-button :icon="Refresh" @click="loadDiseases">查询</el-button>
        </div>
      </div>
      <el-table :data="diseases" empty-text="暂无疾病信息" @row-click="selected = $event">
        <el-table-column prop="diseaseCode" label="编号" width="120" />
        <el-table-column prop="diseaseName" label="疾病" width="140" />
        <el-table-column prop="department" label="科室" width="120" />
        <el-table-column label="可见性" width="110">
          <template #default="{ row }"><el-tag>{{ row.visibilityLabel || visibilityText(row.visibility) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="sourceName" label="来源" min-width="150" show-overflow-tooltip />
        <el-table-column prop="severityLevel" label="严重程度" width="110" />
        <el-table-column prop="symptoms" label="症状" min-width="240" show-overflow-tooltip />
        <el-table-column v-if="isAdmin" label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="edit(row)">编辑</el-button>
            <el-button text type="danger" @click.stop="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="selected" class="panel disease-detail">
      <div>
        <img v-if="selected.imageUrl" :src="selected.imageUrl" alt="疾病图片" />
      </div>
      <div>
        <div class="panel-title">
          <h3>{{ selected.diseaseName }}</h3>
          <div class="table-actions">
            <el-tag>{{ selected.department || '未设置科室' }}</el-tag>
            <el-tag type="info">{{ selected.visibilityLabel || visibilityText(selected.visibility) }}</el-tag>
          </div>
        </div>
        <p v-if="selected.sourceName" class="muted-line">
          来源：<a v-if="selected.sourceUrl" :href="selected.sourceUrl" target="_blank" rel="noreferrer">{{ selected.sourceName }}</a>
          <span v-else>{{ selected.sourceName }}</span>
          <span v-if="selected.sourceLicense"> · {{ selected.sourceLicense }}</span>
        </p>
        <p><strong>症状：</strong>{{ selected.symptoms || '-' }}</p>
        <p><strong>危险因素：</strong>{{ selected.riskFactors || '-' }}</p>
        <p><strong>预防措施：</strong>{{ selected.preventionMeasures || '-' }}</p>
        <p><strong>治疗方案：</strong>{{ selected.treatmentPlan || '-' }}</p>
        <p class="muted-line">{{ selected.description || '暂无详细描述' }}</p>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Upload } from '@element-plus/icons-vue'
import { postForm, putForm, request } from '../api/client'

const props = defineProps<{ role: string }>()
const isAdmin = props.role === 'ADMIN'
const isDoctor = props.role === 'DOCTOR'
const canSubmit = isAdmin || isDoctor

type Disease = Record<string, any> & { id: number; diseaseCode: string; diseaseName: string }

const diseases = ref<Disease[]>([])
const selected = ref<Disease | null>(null)
const keyword = ref('')
const editingId = ref<number | null>(null)
const image = ref<File | null>(null)
const loading = ref(false)
const form = reactive<Record<string, any>>({
  diseaseCode: '',
  diseaseName: '',
  diseaseNameEn: '',
  diseaseCategory: '',
  department: '',
  severityLevel: '中度',
  infectious: false,
  symptoms: '',
  riskFactors: '',
  preventionMeasures: '',
  treatmentPlan: '',
  description: '',
  visibility: 'PUBLIC'
})

onMounted(loadDiseases)

async function loadDiseases() {
  const query = keyword.value ? `?keyword=${encodeURIComponent(keyword.value)}` : ''
  diseases.value = await request<Disease[]>('get', `/diseases${query}`)
  selected.value ||= diseases.value[0] || null
}

function handleImage(file: UploadFile) {
  image.value = file.raw || null
}

function clearImage() {
  image.value = null
}

function toFormData() {
  const data = new FormData()
  Object.entries(form).forEach(([key, value]) => data.append(key, String(value ?? '')))
  if (image.value) data.append('image', image.value)
  return data
}

async function save() {
  if (!form.diseaseCode || !form.diseaseName) {
    ElMessage.warning('请填写疾病编号和名称')
    return
  }
  loading.value = true
  try {
    if (editingId.value) {
      await putForm(`/admin/diseases/${editingId.value}`, toFormData())
    } else {
      await postForm(isAdmin ? '/admin/diseases' : '/doctor/diseases', toFormData())
    }
    resetForm()
    await loadDiseases()
    ElMessage.success('疾病信息已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    loading.value = false
  }
}

function edit(row: Disease) {
  editingId.value = row.id
  Object.keys(form).forEach((key) => {
    form[key] = row[key] ?? (key === 'infectious' ? false : '')
  })
}

function resetForm() {
  editingId.value = null
  image.value = null
  Object.assign(form, {
    diseaseCode: '',
    diseaseName: '',
    diseaseNameEn: '',
    diseaseCategory: '',
    department: '',
    severityLevel: '中度',
    infectious: false,
    symptoms: '',
    riskFactors: '',
    preventionMeasures: '',
    treatmentPlan: '',
    description: '',
    visibility: 'PUBLIC'
  })
}

async function remove(id: number) {
  await ElMessageBox.confirm('确认删除该疾病？关联病历会一起删除。', '删除疾病', { type: 'warning' })
  await request('delete', `/admin/diseases/${id}`)
  diseases.value = diseases.value.filter((item) => item.id !== id)
  selected.value = diseases.value[0] || null
  ElMessage.success('疾病已删除')
}

function visibilityText(value?: string) {
  if (value === 'DOCTOR_ONLY') return '医生专用'
  if (value === 'ADMIN_ONLY') return '管理员'
  if (value === 'DRAFT') return '草稿'
  return '公开'
}
</script>
