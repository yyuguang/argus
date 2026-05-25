<script setup lang="tsx">
import { Form, FormSchema } from '@/components/Form'
import { useForm } from '@/hooks/web/useForm'
import { PropType, reactive, watch, ref, unref } from 'vue'
import { useValidator } from '@/hooks/web/useValidator'
import { useI18n } from '@/hooks/web/useI18n'
import { getMenuListApi } from '@/api/menu'
import {
  ElButton,
  ElInput,
  ElMessage,
  ElPopconfirm,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import AddButtonPermission from './AddButtonPermission.vue'
import { BaseButton } from '@/components/Button'
import { cloneDeep } from 'lodash-es'
import type { MenuItem, MenuPermissionItem } from '@/api/menu/types'

const { t } = useI18n()

const { required } = useValidator()

const props = defineProps({
  currentRow: {
    type: Object as PropType<any>,
    default: () => null
  }
})

const buildDefaultMenu = (): MenuItem => {
  return {
    type: 0,
    parentId: null,
    path: '',
    component: '#',
    name: '',
    status: 1,
    sortOrder: 0,
    meta: {
      title: '',
      icon: '',
      activeMenu: '',
      hidden: false,
      alwaysShow: false,
      noCache: false,
      breadcrumb: true,
      affix: false,
      noTagsView: false,
      canTo: false
    },
    permissionList: []
  }
}

const normalisePermissionItem = (item: any): MenuPermissionItem => {
  return {
    id: item.id,
    label: item.label,
    value: item.value,
    permissionCode: item.permissionCode,
    status: item.status ?? 1
  }
}

const handleClose = async (tag: any) => {
  const formData = await getFormData()
  // 删除对应的权限
  setValues({
    permissionList: formData?.permissionList?.filter((v: any) => v.value !== tag.value) || []
  })
}

const handleEdit = async (row: any) => {
  // 深拷贝当前行数据到编辑行
  permissionEditingRow.value = { ...row }
}

const handleSave = async () => {
  const formData = await getFormData()
  const index = formData?.permissionList?.findIndex((x) => x.id === permissionEditingRow.value.id)
  if (index !== -1) {
    formData.permissionList[index] = normalisePermissionItem(permissionEditingRow.value)
    await setValues({
      permissionList: [...formData.permissionList]
    })
    permissionEditingRow.value = null // 重置编辑状态
  }
}

const showDrawer = ref(false)
// 存储正在编辑的行的数据
const permissionEditingRow = ref<any>(null)

const formSchema = reactive<FormSchema[]>([
  {
    field: 'type',
    label: '菜单类型',
    component: 'RadioButton',
    value: 0,
    colProps: {
      span: 24
    },
    componentProps: {
      options: [
        {
          label: '目录',
          value: 0
        },
        {
          label: '菜单',
          value: 1
        }
      ],
      on: {
        change: async (val: number) => {
          const formData = await getFormData()
          if (val === 1) {
            setSchema([
              {
                field: 'component',
                path: 'componentProps.disabled',
                value: false
              }
            ])
            setValues({
              component: unref(cacheComponent)
            })
          } else {
            setSchema([
              {
                field: 'component',
                path: 'componentProps.disabled',
                value: true
              }
            ])

            if (formData.parentId === void 0) {
              setValues({
                component: '#'
              })
            } else {
              setValues({
                component: '##'
              })
            }
          }
        }
      }
    }
  },
  {
    field: 'parentId',
    label: '父级菜单',
    component: 'TreeSelect',
    componentProps: {
      nodeKey: 'id',
      props: {
        label: 'title',
        value: 'id',
        children: 'children'
      },
      highlightCurrent: true,
      expandOnClickNode: false,
      checkStrictly: true,
      checkOnClickNode: true,
      clearable: true,
      on: {
        change: async (val: number) => {
          const formData = await getFormData()
          if (val && formData.type === 0) {
            setValues({
              component: '##'
            })
          } else if (!val && formData.type === 0) {
            setValues({
              component: '#'
            })
          } else if (formData.type === 1) {
            setValues({
              component: unref(cacheComponent) ?? ''
            })
          }
        }
      }
    },
    optionApi: async () => {
      const res = await getMenuListApi()
      return res.data.list || []
    }
  },
  {
    field: 'meta.title',
    label: t('menu.menuName'),
    component: 'Input'
  },
  {
    field: 'component',
    label: '组件',
    component: 'Input',
    value: '#',
    componentProps: {
      disabled: true,
      placeholder: '#为顶级目录，##为子目录',
      on: {
        change: (val: string) => {
          cacheComponent.value = val
        }
      }
    }
  },
  {
    field: 'sortOrder',
    label: '排序',
    component: 'InputNumber',
    value: 0,
    componentProps: {
      min: 0,
      precision: 0,
      controlsPosition: 'right'
    }
  },
  {
    field: 'name',
    label: t('menu.name'),
    component: 'Input'
  },
  {
    field: 'meta.icon',
    label: t('menu.icon'),
    component: 'Input'
  },
  {
    field: 'path',
    label: t('menu.path'),
    component: 'Input'
  },
  {
    field: 'meta.activeMenu',
    label: t('menu.activeMenu'),
    component: 'Input'
  },
  {
    field: 'status',
    label: t('menu.status'),
    component: 'Select',
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
    field: 'permissionList',
    label: t('menu.permission'),
    component: 'CheckboxGroup',
    colProps: {
      span: 24
    },
    formItemProps: {
      slots: {
        default: (data: any) => (
          <>
            <BaseButton
              class="m-t-5px"
              type="primary"
              size="small"
              onClick={() => (showDrawer.value = true)}
            >
              添加权限
            </BaseButton>
            <ElTable data={data?.permissionList}>
              <ElTableColumn type="index" prop="id" />
              <ElTableColumn
                prop="value"
                label="Value"
                v-slots={{
                  default: ({ row }: any) =>
                    permissionEditingRow.value && permissionEditingRow.value.id === row.id ? (
                      <ElInput v-model={permissionEditingRow.value.value} size="small" />
                    ) : (
                      <span>{row.value}</span>
                    )
                }}
              />
              <ElTableColumn
                prop="label"
                label="Label"
                v-slots={{
                  default: ({ row }: any) =>
                    permissionEditingRow.value && permissionEditingRow.value.id === row.id ? (
                      <ElInput v-model={permissionEditingRow.value.label} size="small" />
                    ) : (
                      <ElTag class="mr-1" key={row.value}>
                        {row.label}
                      </ElTag>
                    )
                }}
              />
              <ElTableColumn
                prop="permissionCode"
                label="Permission Code"
                min-width="220"
                v-slots={{
                  default: ({ row }: any) =>
                    permissionEditingRow.value && permissionEditingRow.value.id === row.id ? (
                      <ElInput v-model={permissionEditingRow.value.permissionCode} size="small" />
                    ) : (
                      <span>{row.permissionCode}</span>
                    )
                }}
              />
              <ElTableColumn
                label="Operations"
                width="180"
                v-slots={{
                  default: ({ row }: any) =>
                    permissionEditingRow.value && permissionEditingRow.value.id === row.id ? (
                      <ElButton size="small" type="primary" onClick={handleSave}>
                        确定
                      </ElButton>
                    ) : (
                      <>
                        <ElButton size="small" type="primary" onClick={() => handleEdit(row)}>
                          编辑
                        </ElButton>
                        <ElPopconfirm
                          title="Are you sure to delete this?"
                          onConfirm={() => handleClose(row)}
                        >
                          {{
                            reference: () => (
                              <ElButton size="small" type="danger">
                                删除
                              </ElButton>
                            )
                          }}
                        </ElPopconfirm>
                      </>
                    )
                }}
              />
            </ElTable>
          </>
        )
      }
    }
  },
  {
    field: 'meta.hidden',
    label: t('menu.hidden'),
    component: 'Switch'
  },
  {
    field: 'meta.alwaysShow',
    label: t('menu.alwaysShow'),
    component: 'Switch'
  },
  {
    field: 'meta.noCache',
    label: t('menu.noCache'),
    component: 'Switch'
  },
  {
    field: 'meta.breadcrumb',
    label: t('menu.breadcrumb'),
    component: 'Switch'
  },
  {
    field: 'meta.affix',
    label: t('menu.affix'),
    component: 'Switch'
  },
  {
    field: 'meta.noTagsView',
    label: t('menu.noTagsView'),
    component: 'Switch'
  },
  {
    field: 'meta.canTo',
    label: t('menu.canTo'),
    component: 'Switch'
  }
])

const rules = reactive({
  name: [required()],
  component: [required()],
  path: [required()],
  'meta.title': [required()],
  status: [required()]
})

const { formRegister, formMethods } = useForm()
const { setValues, getFormData, getElFormExpose, setSchema } = formMethods

const submit = async () => {
  const elForm = await getElFormExpose()
  const valid = await elForm?.validate().catch((err) => {
    console.log(err)
  })
  if (valid) {
    const formData = await getFormData()
    return formData
  }
}

const cacheComponent = ref('')

watch(
  () => props.currentRow,
  async (value) => {
    permissionEditingRow.value = null
    if (!value) {
      await setValues(buildDefaultMenu() as Record<string, any>)
      return
    }
    const currentRow = {
      ...buildDefaultMenu(),
      ...cloneDeep(value),
      meta: {
        ...buildDefaultMenu().meta,
        ...(value.meta || {})
      },
      permissionList: (value.permissionList || []).map((item: any) => normalisePermissionItem(item))
    }
    cacheComponent.value = currentRow.type === 1 ? currentRow.component : ''
    if (currentRow.type === 0) {
      setSchema([
        {
          field: 'component',
          path: 'componentProps.disabled',
          value: true
        }
      ])
    } else {
      setSchema([
        {
          field: 'component',
          path: 'componentProps.disabled',
          value: false
        }
      ])
    }
    await setValues(currentRow)
  },
  {
    deep: true,
    immediate: true
  }
)

defineExpose({
  submit
})

const confirm = async (data: any) => {
  const formData = await getFormData()
  const duplicated = (formData?.permissionList || []).some(
    (item: any) => item.value === data.value || item.permissionCode === data.permissionCode
  )
  if (duplicated) {
    ElMessage.warning('按钮动作或权限编码已存在')
    return
  }
  setValues({
    permissionList: [...(formData?.permissionList || []), normalisePermissionItem(data)]
  })
}
</script>

<template>
  <Form :rules="rules" @register="formRegister" :schema="formSchema" />
  <AddButtonPermission v-model="showDrawer" @confirm="confirm" />
</template>
