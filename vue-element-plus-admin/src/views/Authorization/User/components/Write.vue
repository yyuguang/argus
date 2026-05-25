<script setup lang="ts">
import { Form, FormSchema } from '@/components/Form'
import { useForm } from '@/hooks/web/useForm'
import { PropType, reactive, watch } from 'vue'
import type { FormItemRule } from 'element-plus'
import { useValidator } from '@/hooks/web/useValidator'
import type { UserItem } from '@/api/user/types'

const { required, email, phone, maxlength } = useValidator()

const props = defineProps({
  currentRow: {
    type: Object as PropType<Nullable<UserItem>>,
    default: () => null
  },
  formSchema: {
    type: Array as PropType<FormSchema[]>,
    default: () => []
  }
})

const passwordRule: FormItemRule = {
  validator: (_, value, callback) => {
    const password = value || ''
    const creating = !props.currentRow?.id

    if (creating && !password) {
      callback(new Error('初始密码不能为空'))
      return
    }
    if (password && password.length < 8) {
      callback(new Error('密码长度至少 8 位'))
      return
    }
    callback()
  }
}

const rules = reactive({
  username: [required(), maxlength(64)],
  account: [required(), maxlength(64)],
  password: [passwordRule],
  email: [email(), maxlength(128)],
  phone: [phone()],
  'department.id': [required('请选择所属部门')]
})

const { formRegister, formMethods } = useForm()
const { setValues, getFormData, getElFormExpose } = formMethods

const submit = async () => {
  const elForm = await getElFormExpose()
  const valid = await elForm?.validate().catch(() => false)
  if (valid) {
    const formData = await getFormData()
    return formData
  }
}

watch(
  () => props.currentRow,
  (currentRow) => {
    setValues({
      username: '',
      account: '',
      password: '',
      email: '',
      phone: '',
      status: 1,
      role: [],
      department: {}
    })
    if (!currentRow) {
      return
    }
    setValues({
      ...currentRow,
      password: '',
      role: currentRow.role || [],
      department: currentRow.department || {}
    })
  },
  {
    deep: true,
    immediate: true
  }
)

defineExpose({
  submit
})
</script>

<template>
  <Form :rules="rules" @register="formRegister" :schema="formSchema" />
</template>
