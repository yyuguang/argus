<script setup lang="tsx">
import { Form, FormSchema } from '@/components/Form'
import { useForm } from '@/hooks/web/useForm'
import { PropType, reactive, watch, ref, unref, nextTick } from 'vue'
import { useValidator } from '@/hooks/web/useValidator'
import { useI18n } from '@/hooks/web/useI18n'
import { ElTree, ElCheckboxGroup, ElCheckbox } from 'element-plus'
import { getMenuListApi } from '@/api/menu'
import { eachTree } from '@/utils/tree'
import { findIndex } from '@/utils'
import type { RoleItem } from '@/api/role/types'
import { cloneDeep } from 'lodash-es'

const { t } = useI18n()

const { required } = useValidator()

const props = defineProps({
  currentRow: {
    type: Object as PropType<any>,
    default: () => null
  }
})

const treeRef = ref<typeof ElTree>()

const formSchema = ref<FormSchema[]>([
  {
    field: 'roleCode',
    label: '角色编码',
    component: 'Input',
    componentProps: {
      maxlength: 64,
      showWordLimit: true
    }
  },
  {
    field: 'roleName',
    label: t('role.roleName'),
    component: 'Input',
    componentProps: {
      maxlength: 64,
      showWordLimit: true
    }
  },
  {
    field: 'status',
    label: t('menu.status'),
    component: 'Select',
    value: 1,
    componentProps: {
      options: [
        {
          label: t('userDemo.disable'),
          value: 0
        },
        {
          label: t('userDemo.enable'),
          value: 1
        }
      ]
    }
  },
  {
    field: 'menu',
    label: t('role.menu'),
    colProps: {
      span: 24
    },
    formItemProps: {
      slots: {
        default: () => {
          return (
            <>
              <div class="flex w-full">
                <div class="flex-1">
                  <ElTree
                    ref={treeRef}
                    show-checkbox
                    node-key="id"
                    highlight-current
                    check-strictly
                    expand-on-click-node={false}
                    data={treeData.value}
                    onNode-click={nodeClick}
                  >
                    {{
                      default: (data) => {
                        return <span>{data.data.meta?.title || data.data.title}</span>
                      }
                    }}
                  </ElTree>
                </div>
                <div class="flex-1">
                  {unref(currentTreeData) && unref(currentTreeData)?.permissionList ? (
                    <ElCheckboxGroup v-model={unref(currentTreeData).meta.permission}>
                      {unref(currentTreeData)?.permissionList.map((v: any) => {
                        return <ElCheckbox label={v.value}>{v.label}</ElCheckbox>
                      })}
                    </ElCheckboxGroup>
                  ) : null}
                </div>
              </div>
            </>
          )
        }
      }
    }
  },
  {
    field: 'remark',
    label: t('userDemo.remark'),
    component: 'Input',
    componentProps: {
      type: 'textarea',
      rows: 4,
      maxlength: 255,
      showWordLimit: true
    },
    colProps: {
      span: 24
    }
  }
])

const currentTreeData = ref()
const nodeClick = (treeData: any) => {
  currentTreeData.value = treeData
}

const rules = reactive({
  roleCode: [required()],
  roleName: [required()],
  status: [required()]
})

const { formRegister, formMethods } = useForm()
const { setValues, getFormData, getElFormExpose } = formMethods

const treeData = ref<any[]>([])
const buildDefaultRole = (): RoleItem => {
  return {
    roleCode: '',
    roleName: '',
    status: 1,
    remark: '',
    menu: []
  }
}

const syncMenuGrantSelection = async (currentRow?: RoleItem | null) => {
  currentTreeData.value = undefined
  eachTree(treeData.value, (item) => {
    item.meta = {
      ...(item.meta || {}),
      permission: []
    }
  })

  await nextTick()
  unref(treeRef)?.setCheckedKeys([], false)

  if (!currentRow?.menu?.length) {
    return
  }

  const checked: any[] = []
  eachTree(currentRow.menu, (item) => {
    checked.push({
      id: item.id,
      permission: item.meta?.permission || []
    })
  })

  eachTree(treeData.value, (item) => {
    const index = findIndex(checked, (grant) => grant.id === item.id)
    if (index > -1) {
      item.meta = {
        ...(item.meta || {}),
        permission: checked[index].permission
      }
    }
  })

  checked.forEach((item) => {
    unref(treeRef)?.setChecked(item.id, true, false)
  })
}

const getMenuList = async (currentRow?: RoleItem | null) => {
  const res = await getMenuListApi()
  if (res) {
    treeData.value = cloneDeep(res.data.list || [])
    await syncMenuGrantSelection(currentRow)
  }
}

const submit = async () => {
  const elForm = await getElFormExpose()
  const valid = await elForm?.validate().catch((err) => {
    console.log(err)
  })
  if (valid) {
    const formData = await getFormData()
    const checkedKeys = (unref(treeRef)?.getCheckedKeys() || []).map((item) => String(item))
    const checkedKeySet = new Set(checkedKeys)
    const menu: NonNullable<RoleItem['menu']> = []

    eachTree(unref(treeData), (item) => {
      if (!checkedKeySet.has(String(item.id))) {
        return
      }
      menu.push({
        id: String(item.id),
        meta: {
          permission: item.meta?.permission || []
        }
      })
    })

    formData.menu = menu
    return formData
  }
}

watch(
  () => props.currentRow,
  async (currentRow) => {
    const nextRow = currentRow
      ? { ...buildDefaultRole(), ...cloneDeep(currentRow) }
      : buildDefaultRole()
    await setValues(nextRow as Record<string, any>)
    await getMenuList(nextRow)
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
