<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Dialog } from '@/components/Dialog'
import { importRuleDocumentApi } from '@/api/rule'
import type { RuleDocumentDetailItem } from '@/api/rule/types'
import type { ScmConfigItem } from '@/api/scm/types'
import {
  ElButton,
  ElCheckbox,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElUpload
} from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  scmOptions: ScmConfigItem[]
  presetScmConfigId?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', value: RuleDocumentDetailItem): void
}>()

const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const selectedFile = ref<File | null>(null)

const form = reactive({
  documentName: '',
  category: 'CODING',
  scope: props.presetScmConfigId ? 'SCM' : 'GLOBAL',
  scmConfigId: props.presetScmConfigId || '',
  activeAfterImport: true,
  remark: ''
})

const categoryOptions = [
  { label: '编码规范', value: 'CODING' },
  { label: '接口规范', value: 'API' },
  { label: '数据库规范', value: 'DATABASE' },
  { label: '安全规范', value: 'SECURITY' },
  { label: '自定义规范', value: 'CUSTOM' }
]

const scopeOptions = [
  { label: '全局', value: 'GLOBAL' },
  { label: '仓库级', value: 'SCM' }
]

const rules = reactive<FormRules>({
  documentName: [{ required: true, message: '文档名称不能为空', trigger: 'blur' }],
  category: [{ required: true, message: '请选择规范分类', trigger: 'change' }],
  scope: [{ required: true, message: '请选择作用域', trigger: 'change' }],
  scmConfigId: [
    {
      validator: (_rule, value, callback) => {
        if (form.scope === 'SCM' && !value) {
          callback(new Error('仓库级规范必须选择适用仓库'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ]
})

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const resetForm = () => {
  form.documentName = ''
  form.category = 'CODING'
  form.scope = props.presetScmConfigId ? 'SCM' : 'GLOBAL'
  form.scmConfigId = props.presetScmConfigId || ''
  form.activeAfterImport = true
  form.remark = ''
  selectedFile.value = null
  formRef.value?.clearValidate()
}

const handleFileChange = (file: UploadFile) => {
  selectedFile.value = (file.raw as File) || null
  if (!form.documentName && file.name) {
    form.documentName = file.name.replace(/\.[^.]+$/, '')
  }
}

const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  if (!selectedFile.value) {
    ElMessage.error('请选择需要导入的规范文档')
    return
  }
  submitLoading.value = true
  try {
    const res = await importRuleDocumentApi(selectedFile.value, {
      documentName: form.documentName,
      category: form.category,
      scope: form.scope,
      scmConfigId: form.scope === 'SCM' ? form.scmConfigId : undefined,
      activeAfterImport: form.activeAfterImport,
      remark: form.remark
    })
    ElMessage.success(res.message || '规则文档导入成功')
    emit('success', res.data)
    visible.value = false
  } finally {
    submitLoading.value = false
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      resetForm()
    }
  }
)
</script>

<template>
  <Dialog v-model="visible" title="导入规范文档" width="720px">
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top">
      <ElFormItem label="文档名称" prop="documentName">
        <ElInput
          v-model.trim="form.documentName"
          maxlength="100"
          placeholder="例如：订单主数据校验规范"
        />
      </ElFormItem>
      <div class="grid grid-cols-1 gap-12px md:grid-cols-2">
        <ElFormItem label="规范分类" prop="category">
          <ElSelect v-model="form.category" class="w-100%">
            <ElOption
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="作用域" prop="scope">
          <ElSelect v-model="form.scope" class="w-100%">
            <ElOption
              v-for="item in scopeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </div>
      <ElFormItem v-if="form.scope === 'SCM'" label="适用仓库" prop="scmConfigId">
        <ElSelect v-model="form.scmConfigId" class="w-100%" filterable placeholder="选择 SCM 仓库">
          <ElOption
            v-for="item in scmOptions"
            :key="item.id"
            :label="
              item.repoOwner && item.repoName
                ? `${item.repoOwner}/${item.repoName}`
                : item.projectName || String(item.id)
            "
            :value="String(item.id)"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="导入说明">
        <ElInput
          v-model.trim="form.remark"
          type="textarea"
          :rows="3"
          maxlength="300"
          placeholder="可选，记录文档来源、适用范围或本次导入目的"
        />
      </ElFormItem>
      <ElFormItem label="导入文件">
        <ElUpload
          class="w-100%"
          drag
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :show-file-list="true"
        >
          <div class="text-center">
            <div class="mb-6px">点击或拖拽上传文件</div>
            <div class="text-12px color-[var(--el-text-color-secondary)]">
              支持 .md / .txt / .docx / .xlsx / .xls / .pptx
            </div>
          </div>
        </ElUpload>
      </ElFormItem>
      <ElFormItem>
        <ElCheckbox v-model="form.activeAfterImport">导入完成后立即启用</ElCheckbox>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="submitLoading" @click="submit">确认导入</ElButton>
    </template>
  </Dialog>
</template>
