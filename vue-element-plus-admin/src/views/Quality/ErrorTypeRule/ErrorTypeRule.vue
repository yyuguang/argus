<script setup lang="tsx">
import { ContentWrap } from '@/components/ContentWrap'
import { Dialog } from '@/components/Dialog'
import { Search } from '@/components/Search'
import { Table, TableColumn } from '@/components/Table'
import { BaseButton } from '@/components/Button'
import { hasPermi } from '@/components/Permission/src/utils'
import { FormSchema } from '@/components/Form'
import {
  buildEmptyErrorTypeRuleForm,
  deleteErrorTypeRuleApi,
  getErrorTypeOptionsApi,
  getErrorTypeRuleListApi,
  saveErrorTypeRuleApi,
  toErrorTypeRuleForm
} from '@/api/error'
import type {
  ErrorTypeOptionItem,
  ErrorTypeRuleFormModel,
  ErrorTypeRuleItem,
  ErrorTypeRuleQueryParams
} from '@/api/error/types'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElCol,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElSwitch,
  ElTag
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'

const matchFieldOptions = [
  { label: '异常类', value: 'EXCEPTION_CLASS' },
  { label: '错误消息', value: 'MESSAGE' },
  { label: '异常栈', value: 'STACK_TRACE' },
  { label: '业务类名', value: 'CLASS_NAME' },
  { label: '任意文本', value: 'ANY' },
  { label: 'HTTP 状态码', value: 'HTTP_STATUS' }
]

const matchModeOptions = [
  { label: '精确匹配', value: 'EXACT' },
  { label: '包含', value: 'CONTAINS' },
  { label: '正则', value: 'REGEX' },
  { label: '区间', value: 'RANGE' }
]

const statusOptions = [
  { label: '启用', value: true },
  { label: '停用', value: false }
]

const searchSchema = reactive<FormSchema[]>([
  {
    field: 'errorType',
    label: '错误类型',
    component: 'Select',
    componentProps: {
      clearable: true,
      filterable: true,
      options: []
    }
  },
  {
    field: 'enabled',
    label: '状态',
    component: 'Select',
    componentProps: {
      clearable: true,
      options: statusOptions
    }
  },
  {
    field: 'keyword',
    label: '关键字',
    component: 'Input',
    componentProps: {
      placeholder: '规则名称 / 表达式 / 备注',
      maxlength: 100
    }
  }
])

const loading = ref(false)
const saveLoading = ref(false)
const errorMessage = ref('')
const searchParams = ref<ErrorTypeRuleQueryParams>({})
const rules = ref<ErrorTypeRuleItem[]>([])
const errorTypeOptions = ref<ErrorTypeOptionItem[]>([])

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ErrorTypeRuleFormModel>(buildEmptyErrorTypeRuleForm())

const dialogTitle = computed(() => (form.id ? '编辑错误类型规则' : '新增错误类型规则'))

const availableModeOptions = computed(() => {
  if (form.matchField === 'HTTP_STATUS') {
    return matchModeOptions.filter((item) => ['EXACT', 'RANGE'].includes(item.value))
  }
  return matchModeOptions.filter((item) => item.value !== 'RANGE')
})

const patternPlaceholder = computed(() => {
  if (form.matchField === 'HTTP_STATUS' && form.matchMode === 'RANGE') return '例如：400-499'
  if (form.matchField === 'HTTP_STATUS') return '例如：502'
  if (form.matchField === 'EXCEPTION_CLASS') return '例如：NoResourceFoundException'
  if (form.matchMode === 'REGEX') return '例如：(SQLException|MysqlDataTruncation)'
  return '请输入匹配文本'
})

const rulesConfig = reactive<FormRules<ErrorTypeRuleFormModel>>({
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  errorType: [{ required: true, message: '错误类型不能为空', trigger: 'change' }],
  matchField: [{ required: true, message: '匹配字段不能为空', trigger: 'change' }],
  matchMode: [{ required: true, message: '匹配模式不能为空', trigger: 'change' }],
  pattern: [{ required: true, message: '匹配表达式不能为空', trigger: 'blur' }]
})

const fieldLabelMap = new Map(matchFieldOptions.map((item) => [item.value, item.label]))
const modeLabelMap = new Map(matchModeOptions.map((item) => [item.value, item.label]))

const typeLabel = (value?: string) => {
  return errorTypeOptions.value.find((item) => item.value === value)?.label || '-'
}

const fieldLabel = (value?: string) => fieldLabelMap.get(String(value || '')) || value || '-'
const modeLabel = (value?: string) => modeLabelMap.get(String(value || '')) || value || '-'

const updateTypeSearchOptions = () => {
  const options = errorTypeOptions.value.map((item) => ({
    label: `${item.value} · ${item.label}`,
    value: item.value
  }))
  const schema = searchSchema.find((item) => item.field === 'errorType')
  if (schema) {
    schema.componentProps = {
      ...(schema.componentProps || {}),
      options
    }
  }
}

const loadTypeOptions = async () => {
  const res = await getErrorTypeOptionsApi()
  errorTypeOptions.value = res.data || []
  updateTypeSearchOptions()
}

const loadRules = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getErrorTypeRuleListApi(searchParams.value)
    rules.value = res.data || []
  } catch (error: any) {
    errorMessage.value = error?.message || '加载错误类型规则失败'
  } finally {
    loading.value = false
  }
}

const reloadAll = async () => {
  await Promise.all([loadTypeOptions(), loadRules()])
}

const setSearchParams = (params: Record<string, any>) => {
  searchParams.value = {
    errorType: params.errorType || undefined,
    enabled: typeof params.enabled === 'boolean' ? params.enabled : undefined,
    keyword: params.keyword || undefined
  }
  loadRules()
}

const handleFieldChange = () => {
  if (form.matchField === 'HTTP_STATUS' && !['EXACT', 'RANGE'].includes(form.matchMode)) {
    form.matchMode = 'EXACT'
  }
  if (form.matchField !== 'HTTP_STATUS' && form.matchMode === 'RANGE') {
    form.matchMode = 'EXACT'
  }
}

const openCreate = () => {
  Object.assign(form, buildEmptyErrorTypeRuleForm())
  formVisible.value = true
}

const openEdit = (row: ErrorTypeRuleItem) => {
  Object.assign(form, toErrorTypeRuleForm(row))
  formVisible.value = true
}

const saveRule = async () => {
  const formEl = formRef.value
  if (!formEl) return

  await formEl.validate()
  if (form.matchField === 'HTTP_STATUS' && form.matchMode === 'RANGE') {
    if (!/^\d{3}\s*-\s*\d{3}$/.test(String(form.pattern || '').trim())) {
      ElMessage.error('HTTP 状态码区间格式应为 400-499')
      return
    }
  }
  if (form.matchMode === 'REGEX') {
    try {
      new RegExp(String(form.pattern || '').trim())
    } catch {
      ElMessage.error('正则表达式不合法')
      return
    }
  }

  saveLoading.value = true
  try {
    await saveErrorTypeRuleApi(form)
    ElMessage.success(form.id ? '错误类型规则已更新' : '错误类型规则已创建')
    formVisible.value = false
    await loadRules()
  } catch (error: any) {
    ElMessage.error(error?.message || '保存错误类型规则失败')
  } finally {
    saveLoading.value = false
  }
}

const removeRule = async (row: ErrorTypeRuleItem) => {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleName || row.id}」吗？`, '删除错误类型规则', {
    type: 'warning'
  })
  await deleteErrorTypeRuleApi(row.id!)
  ElMessage.success('错误类型规则已删除')
  await loadRules()
}

const tableColumns = reactive<TableColumn[]>([
  {
    field: 'priority',
    label: '优先级',
    width: 90
  },
  {
    field: 'ruleName',
    label: '规则',
    minWidth: 230,
    slots: {
      default: ({ row }: { row: ErrorTypeRuleItem }) => (
        <div>
          <div class="font-600">{row.ruleName || '-'}</div>
          <div class="text-12px color-[var(--el-text-color-secondary)]">
            {row.remark || '无备注'}
          </div>
        </div>
      )
    }
  },
  {
    field: 'errorType',
    label: '错误类型',
    minWidth: 220,
    slots: {
      default: ({ row }: { row: ErrorTypeRuleItem }) => (
        <div class="flex items-center gap-8px">
          <ElTag effect="light">{row.errorType || '-'}</ElTag>
          <span class="text-12px color-[var(--el-text-color-secondary)]">
            {typeLabel(row.errorType)}
          </span>
        </div>
      )
    }
  },
  {
    field: 'matchField',
    label: '匹配字段',
    width: 150,
    slots: {
      default: ({ row }: { row: ErrorTypeRuleItem }) => <>{fieldLabel(row.matchField)}</>
    }
  },
  {
    field: 'matchMode',
    label: '匹配模式',
    width: 120,
    slots: {
      default: ({ row }: { row: ErrorTypeRuleItem }) => <>{modeLabel(row.matchMode)}</>
    }
  },
  {
    field: 'pattern',
    label: '表达式',
    minWidth: 280,
    slots: {
      default: ({ row }: { row: ErrorTypeRuleItem }) => (
        <code class="rule-pattern">{row.pattern || '-'}</code>
      )
    }
  },
  {
    field: 'enabled',
    label: '状态',
    width: 110,
    slots: {
      default: ({ row }: { row: ErrorTypeRuleItem }) => (
        <ElTag type={row.enabled === false ? 'info' : 'success'} effect="light">
          {row.enabled === false ? '停用' : '启用'}
        </ElTag>
      )
    }
  },
  {
    field: 'builtin',
    label: '来源',
    width: 110,
    slots: {
      default: ({ row }: { row: ErrorTypeRuleItem }) => (
        <ElTag type={row.builtin ? 'warning' : 'info'} effect="plain">
          {row.builtin ? '内置' : '自定义'}
        </ElTag>
      )
    }
  },
  {
    field: 'action',
    label: '操作',
    minWidth: 170,
    fixed: 'right',
    slots: {
      default: ({ row }: { row: ErrorTypeRuleItem }) => (
        <div class="flex flex-wrap gap-8px">
          {hasPermi('update') ? (
            <BaseButton type="primary" icon={Edit} onClick={() => openEdit(row)}>
              编辑
            </BaseButton>
          ) : null}
          {hasPermi('delete') ? (
            <BaseButton type="danger" icon={Delete} onClick={() => removeRule(row)}>
              删除
            </BaseButton>
          ) : null}
        </div>
      )
    }
  }
])

onMounted(() => {
  reloadAll()
})
</script>

<template>
  <ContentWrap>
    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />

    <div class="summary-grid">
      <div class="summary-card">
        <div class="summary-title">规则总数</div>
        <div class="summary-value">{{ rules.length }}</div>
        <div class="summary-hint">当前已维护识别规则</div>
      </div>
      <div class="summary-card">
        <div class="summary-title">启用规则</div>
        <div class="summary-value">{{ rules.filter((item) => item.enabled !== false).length }}</div>
        <div class="summary-hint">参与线上识别</div>
      </div>
      <div class="summary-card">
        <div class="summary-title">内置规则</div>
        <div class="summary-value">{{ rules.filter((item) => item.builtin).length }}</div>
        <div class="summary-hint">初始化默认规则</div>
      </div>
      <div class="summary-card">
        <div class="summary-title">自定义规则</div>
        <div class="summary-value">{{ rules.filter((item) => !item.builtin).length }}</div>
        <div class="summary-hint">人工扩展能力</div>
      </div>
    </div>

    <div class="mb-10px flex gap-10px">
      <BaseButton v-if="hasPermi('create')" type="primary" :icon="Plus" @click="openCreate">
        新增规则
      </BaseButton>
      <BaseButton :icon="Refresh" :loading="loading" @click="reloadAll">刷新</BaseButton>
    </div>

    <ElAlert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="mb-12px"
    />

    <Table :columns="tableColumns" :data="rules" :loading="loading" :pagination="undefined" />
  </ContentWrap>

  <Dialog v-model="formVisible" :title="dialogTitle" width="760px">
    <ElForm ref="formRef" :model="form" :rules="rulesConfig" label-width="110px">
      <ElRow :gutter="16">
        <ElCol :span="12">
          <ElFormItem label="规则名称" prop="ruleName">
            <ElInput v-model="form.ruleName" maxlength="100" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="错误类型" prop="errorType">
            <ElSelect v-model="form.errorType" filterable style="width: 100%">
              <ElOption
                v-for="item in errorTypeOptions"
                :key="item.value"
                :label="`${item.value} · ${item.label}`"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
      </ElRow>

      <ElRow :gutter="16">
        <ElCol :span="12">
          <ElFormItem label="匹配字段" prop="matchField">
            <ElSelect v-model="form.matchField" style="width: 100%" @change="handleFieldChange">
              <ElOption
                v-for="item in matchFieldOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="匹配模式" prop="matchMode">
            <ElSelect v-model="form.matchMode" style="width: 100%">
              <ElOption
                v-for="item in availableModeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>
      </ElRow>

      <ElFormItem label="匹配表达式" prop="pattern">
        <ElInput
          v-model="form.pattern"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          :placeholder="patternPlaceholder"
        />
      </ElFormItem>

      <ElRow :gutter="16">
        <ElCol :span="8">
          <ElFormItem label="优先级">
            <ElInputNumber
              v-model="form.priority"
              :min="1"
              :max="9999"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="状态">
            <ElSwitch v-model="form.enabled" active-text="启用" inactive-text="停用" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="内置规则">
            <ElSwitch v-model="form.builtin" active-text="是" inactive-text="否" />
          </ElFormItem>
        </ElCol>
      </ElRow>

      <ElFormItem label="备注">
        <ElInput v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <BaseButton @click="formVisible = false">取消</BaseButton>
      <BaseButton type="primary" :loading="saveLoading" @click="saveRule">保存</BaseButton>
    </template>
  </Dialog>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.summary-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}

.summary-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rule-pattern {
  display: inline-block;
  max-width: 100%;
  padding: 4px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  white-space: normal;
  word-break: break-all;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
