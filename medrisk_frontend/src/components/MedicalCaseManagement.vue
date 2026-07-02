<template>
  <section class="view-stack">
    <section v-if="canSubmit" class="panel">
      <div class="panel-title">
        <h3>{{ editingId ? '编辑病历' : isAdmin ? '新增病历' : '提交病历草稿' }}</h3>
        <el-button v-if="editingId" @click="resetForm">取消编辑</el-button>
      </div>
      <el-form label-position="top" class="admin-form-grid">
        <el-form-item label="关联疾病">
          <el-select v-model="form.diseaseId" placeholder="请选择关联疾病">
            <el-option v-for="item in diseases" :key="item.id" :label="item.diseaseName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="病历标题">
          <el-input v-model="form.caseTitle" placeholder="如：高血压合并糖尿病随访案例" />
        </el-form-item>
        <el-form-item label="医院">
          <el-input v-model="form.hospital" placeholder="如：湖北工业大学附属教学医院" />
        </el-form-item>
        <el-form-item label="就诊日期">
          <el-date-picker v-model="form.visitDate" value-format="YYYY-MM-DD" type="date" placeholder="请选择就诊日期" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="form.patientAge" :min="0" :max="120" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.patientGender" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="form.severityLevel" placeholder="请选择严重程度">
            <el-option label="轻度" value="轻度" />
            <el-option label="中度" value="中度" />
            <el-option label="重度" value="重度" />
            <el-option label="严重" value="严重" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin" label="可见性">
          <el-select v-model="form.visibility" placeholder="请选择可见范围">
            <el-option label="公开" value="PUBLIC" />
            <el-option label="医生专用" value="DOCTOR_ONLY" />
            <el-option label="管理员" value="ADMIN_ONLY" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item label="病历图片">
          <el-upload :auto-upload="false" multiple :on-change="handleImages" :on-remove="handleImages" accept="image/*">
            <el-button :icon="Upload">选择图片</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="主诉" class="wide-form-item">
          <el-input v-model="form.chiefComplaint" type="textarea" :rows="2" placeholder="填写患者主要不适、持续时间和诱因" />
        </el-form-item>
        <el-form-item label="诊断结果" class="wide-form-item">
          <el-input v-model="form.diagnosis" type="textarea" :rows="2" placeholder="填写诊断结论、检查依据或鉴别诊断" />
        </el-form-item>
        <el-form-item label="治疗方案" class="wide-form-item">
          <el-input v-model="form.treatmentGiven" type="textarea" :rows="2" placeholder="填写治疗措施、用药方案和健康建议" />
        </el-form-item>
        <el-form-item label="随访记录" class="wide-form-item">
          <el-input v-model="form.followupNotes" type="textarea" :rows="2" placeholder="填写复诊计划、疗效变化和风险提醒" />
        </el-form-item>
        <el-form-item label="操作">
          <el-button type="primary" :loading="loading" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-title">
        <h3>病历管理</h3>
        <div class="table-actions">
          <el-select v-model="diseaseFilter" clearable placeholder="按疾病筛选">
            <el-option v-for="item in diseases" :key="item.id" :label="item.diseaseName" :value="item.id" />
          </el-select>
          <el-input v-model="keyword" clearable placeholder="标题、医院、诊断" @keyup.enter="loadCases" />
          <el-button :icon="Refresh" @click="loadCases">查询</el-button>
        </div>
      </div>
      <el-table :data="cases" empty-text="暂无病历案例" @row-click="selected = $event">
        <el-table-column prop="caseTitle" label="标题" min-width="200" />
        <el-table-column prop="diseaseName" label="疾病" width="120" />
        <el-table-column label="可见性" width="110">
          <template #default="{ row }"><el-tag>{{ row.visibilityLabel || visibilityText(row.visibility) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><el-tag :type="row.syntheticCase ? 'success' : 'info'">{{ row.syntheticCase ? '合成' : '录入' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="hospital" label="医院" min-width="170" />
        <el-table-column prop="patientAge" label="年龄" width="80" />
        <el-table-column prop="severityLevel" label="程度" width="90" />
        <el-table-column v-if="isAdmin" label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="edit(row)">编辑</el-button>
            <el-button text type="danger" @click.stop="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="selected" class="panel case-detail">
      <div class="panel-title">
        <h3>{{ selected.caseTitle }}</h3>
        <div class="table-actions">
          <el-tag>{{ selected.diseaseName }}</el-tag>
          <el-tag type="info">{{ selected.visibilityLabel || visibilityText(selected.visibility) }}</el-tag>
          <el-tag v-if="selected.syntheticCase" type="success">合成病历</el-tag>
        </div>
      </div>
      <p v-if="selected.sourceName" class="muted-line">
        来源：<a v-if="selected.sourceUrl" :href="selected.sourceUrl" target="_blank" rel="noreferrer">{{ selected.sourceName }}</a>
        <span v-else>{{ selected.sourceName }}</span>
        <span v-if="selected.sourceLicense"> · {{ selected.sourceLicense }}</span>
      </p>
      <div class="case-image-row">
        <img v-for="image in selected.images || []" :key="image.url" :src="image.url" alt="病历图片" />
      </div>
      <div class="case-text-grid">
        <p><strong>主诉：</strong>{{ selected.chiefComplaint || '-' }}</p>
        <p><strong>诊断：</strong>{{ selected.diagnosis || '-' }}</p>
        <p><strong>治疗：</strong>{{ selected.treatmentGiven || '-' }}</p>
        <p><strong>随访：</strong>{{ selected.followupNotes || '-' }}</p>
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

type Disease = { id: number; diseaseName: string }
type MedicalCase = Record<string, any> & { id: number; diseaseId: number; caseTitle: string }

const diseases = ref<Disease[]>([])
const cases = ref<MedicalCase[]>([])
const selected = ref<MedicalCase | null>(null)
const keyword = ref('')
const diseaseFilter = ref<number | null>(null)
const editingId = ref<number | null>(null)
const images = ref<File[]>([])
const loading = ref(false)
const form = reactive<Record<string, any>>({
  diseaseId: undefined,
  caseTitle: '',
  visitDate: '',
  hospital: '',
  patientAge: 60,
  patientGender: '男',
  severityLevel: '中度',
  chiefComplaint: '',
  diagnosis: '',
  treatmentGiven: '',
  followupNotes: '',
  visibility: 'PUBLIC'
})

onMounted(async () => {
  diseases.value = await request<Disease[]>('get', '/diseases')
  form.diseaseId = diseases.value[0]?.id
  await loadCases()
})

async function loadCases() {
  const params = new URLSearchParams()
  if (keyword.value) params.set('keyword', keyword.value)
  if (diseaseFilter.value) params.set('diseaseId', String(diseaseFilter.value))
  cases.value = await request<MedicalCase[]>('get', `/medical-cases?${params.toString()}`)
  selected.value ||= cases.value[0] || null
}

function handleImages(_: UploadFile, fileList: UploadFile[]) {
  images.value = fileList.map((item) => item.raw).filter(Boolean) as File[]
}

function toFormData() {
  const data = new FormData()
  Object.entries(form).forEach(([key, value]) => {
    if (value !== undefined && value !== null) data.append(key, String(value))
  })
  images.value.forEach((image) => data.append('images', image))
  return data
}

async function save() {
  if (!form.diseaseId || !form.caseTitle) {
    ElMessage.warning('请选择疾病并填写病历标题')
    return
  }
  loading.value = true
  try {
    if (editingId.value) {
      await putForm(`/admin/medical-cases/${editingId.value}`, toFormData())
    } else {
      await postForm(isAdmin ? '/admin/medical-cases' : '/doctor/medical-cases', toFormData())
    }
    resetForm()
    await loadCases()
    ElMessage.success('病历已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    loading.value = false
  }
}

function edit(row: MedicalCase) {
  editingId.value = row.id
  Object.keys(form).forEach((key) => {
    form[key] = row[key] ?? form[key]
  })
}

function resetForm() {
  editingId.value = null
  images.value = []
  Object.assign(form, {
    diseaseId: diseases.value[0]?.id,
    caseTitle: '',
    visitDate: '',
    hospital: '',
    patientAge: 60,
    patientGender: '男',
    severityLevel: '中度',
    chiefComplaint: '',
    diagnosis: '',
    treatmentGiven: '',
    followupNotes: '',
    visibility: 'PUBLIC'
  })
}

async function remove(id: number) {
  await ElMessageBox.confirm('确认删除该病历？', '删除病历', { type: 'warning' })
  await request('delete', `/admin/medical-cases/${id}`)
  cases.value = cases.value.filter((item) => item.id !== id)
  selected.value = cases.value[0] || null
  ElMessage.success('病历已删除')
}

function visibilityText(value?: string) {
  if (value === 'DOCTOR_ONLY') return '医生专用'
  if (value === 'ADMIN_ONLY') return '管理员'
  if (value === 'DRAFT') return '草稿'
  return '公开'
}
</script>
