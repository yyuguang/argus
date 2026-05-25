<script setup lang="tsx">
import { ContentWrap } from '@/components/ContentWrap'
import { Dialog } from '@/components/Dialog'
import { Search } from '@/components/Search'
import { Table } from '@/components/Table'
import { BaseButton } from '@/components/Button'
import { hasPermi } from '@/components/Permission/src/utils'
import { useCrudSchemas, CrudSchema } from '@/hooks/web/useCrudSchemas'
import { useI18n } from '@/hooks/web/useI18n'
import { useTable } from '@/hooks/web/useTable'
import { getDepartmentApi } from '@/api/department'
import type { DepartmentItem } from '@/api/department/types'
import { getRoleListApi } from '@/api/role'
import {
  deleteUserApi,
  getUserPageApi,
  resetUserPasswordApi,
  saveUserApi,
  updateUserStatusApi
} from '@/api/user'
import type { UserItem } from '@/api/user/types'
import { reactive, ref, unref, watch, nextTick } from 'vue'
import { ElDivider, ElInput, ElMessage, ElMessageBox, ElTag, ElTree } from 'element-plus'
import Write from './components/Write.vue'
import Detail from './components/Detail.vue'

const { t } = useI18n()

const statusOptions = [
  {
    value: 0,
    label: t('userDemo.disable')
  },
  {
    value: 1,
    label: t('userDemo.enable')
  }
]

const roleOptionsApi = async () => {
  const res = await getRoleListApi()
  return res.data?.list?.map((item) => ({
    label: item.roleName,
    value: String(item.id)
  }))
}

const loadDepartmentTree = async () => {
  const res = await getDepartmentApi()
  return res.data.list
}

const renderStatusTag = (status?: number) => {
  return (
    <ElTag type={status === 1 ? 'success' : 'danger'}>
      {status === 1 ? t('userDemo.enable') : t('userDemo.disable')}
    </ElTag>
  )
}

const renderRoleTags = (row: UserItem) => {
  if (!row.roleNames?.length) {
    return '-'
  }
  return row.roleNames.map((roleName) => (
    <ElTag class="mr-4px mb-4px" size="small">
      {roleName}
    </ElTag>
  ))
}

const ids = ref<string[]>([])
const selectedRows = ref<UserItem[]>([])

const { tableRegister, tableState, tableMethods } = useTable({
  immediate: false,
  fetchDataApi: async () => {
    const { pageSize, currentPage } = tableState
    const res = await getUserPageApi({
      id: unref(currentNodeKey) || undefined,
      pageIndex: unref(currentPage),
      pageSize: unref(pageSize),
      ...unref(searchParams)
    })
    return {
      list: res.data.list || [],
      total: res.data.total || 0
    }
  },
  fetchDelApi: async () => {
    const res = await deleteUserApi(unref(ids))
    return !!res
  }
})
const { total, loading, dataList, pageSize, currentPage } = tableState
const { getList, delList } = tableMethods

const crudSchemas = reactive<CrudSchema[]>([
  {
    field: 'selection',
    search: {
      hidden: true
    },
    form: {
      hidden: true
    },
    detail: {
      hidden: true
    },
    table: {
      type: 'selection'
    }
  },
  {
    field: 'index',
    label: t('userDemo.index'),
    search: {
      hidden: true
    },
    form: {
      hidden: true
    },
    detail: {
      hidden: true
    },
    table: {
      type: 'index'
    }
  },
  {
    field: 'id',
    search: {
      hidden: true
    },
    table: {
      hidden: true
    },
    form: {
      hidden: true
    },
    detail: {
      hidden: true
    }
  },
  {
    field: 'username',
    label: t('userDemo.username'),
    search: {
      component: 'Input',
      componentProps: {
        maxlength: 64
      }
    },
    form: {
      component: 'Input',
      componentProps: {
        maxlength: 64,
        showWordLimit: true
      }
    }
  },
  {
    field: 'account',
    label: t('userDemo.account'),
    search: {
      component: 'Input',
      componentProps: {
        maxlength: 64
      }
    },
    form: {
      component: 'Input',
      componentProps: {
        maxlength: 64,
        showWordLimit: true
      }
    }
  },
  {
    field: 'password',
    label: t('userDemo.password'),
    search: {
      hidden: true
    },
    table: {
      hidden: true
    },
    detail: {
      hidden: true
    },
    form: {
      component: 'InputPassword',
      componentProps: {
        strength: true,
        placeholder: t('userDemo.passwordPlaceholder')
      }
    }
  },
  {
    field: 'department.id',
    label: t('userDemo.department'),
    search: {
      hidden: true
    },
    form: {
      component: 'TreeSelect',
      componentProps: {
        nodeKey: 'id',
        props: {
          label: 'departmentName',
          value: 'id',
          children: 'children'
        },
        highlightCurrent: true,
        expandOnClickNode: false,
        checkStrictly: true,
        checkOnClickNode: true,
        clearable: true
      },
      optionApi: loadDepartmentTree
    },
    table: {
      hidden: true
    },
    detail: {
      slots: {
        default: (data: UserItem) => <>{data.department?.departmentName || '-'}</>
      }
    }
  },
  {
    field: 'role',
    label: t('userDemo.role'),
    search: {
      hidden: true
    },
    form: {
      component: 'Select',
      value: [],
      componentProps: {
        multiple: true,
        collapseTags: true,
        maxCollapseTags: 1
      },
      optionApi: roleOptionsApi
    },
    table: {
      slots: {
        default: (data: any) => renderRoleTags(data.row as UserItem)
      }
    },
    detail: {
      slots: {
        default: (data: UserItem) => <>{data.roleNames?.length ? data.roleNames.join('、') : '-'}</>
      }
    }
  },
  {
    field: 'roleId',
    label: t('userDemo.role'),
    table: {
      hidden: true
    },
    form: {
      hidden: true
    },
    detail: {
      hidden: true
    },
    search: {
      component: 'Select',
      componentProps: {
        clearable: true
      },
      optionApi: roleOptionsApi
    }
  },
  {
    field: 'email',
    label: t('userDemo.email'),
    search: {
      hidden: true
    },
    form: {
      component: 'Input',
      componentProps: {
        maxlength: 128,
        showWordLimit: true
      }
    }
  },
  {
    field: 'phone',
    label: t('userDemo.phone'),
    search: {
      hidden: true
    },
    form: {
      component: 'Input',
      componentProps: {
        maxlength: 20
      }
    }
  },
  {
    field: 'status',
    label: t('userDemo.status'),
    search: {
      component: 'Select',
      componentProps: {
        clearable: true,
        options: statusOptions
      }
    },
    table: {
      slots: {
        default: (data: any) => renderStatusTag(data.row.status)
      }
    },
    form: {
      component: 'Select',
      value: 1,
      componentProps: {
        options: statusOptions
      }
    },
    detail: {
      slots: {
        default: (data: UserItem) => renderStatusTag(data.status)
      }
    }
  },
  {
    field: 'createTime',
    label: t('userDemo.createTime'),
    search: {
      hidden: true
    },
    form: {
      hidden: true
    }
  },
  {
    field: 'action',
    label: t('userDemo.action'),
    search: {
      hidden: true
    },
    form: {
      hidden: true
    },
    detail: {
      hidden: true
    },
    table: {
      width: 180,
      slots: {
        default: (data: any) => {
          const row = data.row as UserItem
          return (
            <div class="flex flex-wrap gap-8px">
              {hasPermi('update') ? (
                <BaseButton type="primary" onClick={() => action(row, 'edit')}>
                  {t('exampleDemo.edit')}
                </BaseButton>
              ) : null}
              {hasPermi('view') ? (
                <BaseButton type="success" onClick={() => action(row, 'detail')}>
                  {t('exampleDemo.detail')}
                </BaseButton>
              ) : null}
            </div>
          )
        }
      }
    }
  }
])

const { allSchemas } = useCrudSchemas(crudSchemas)

const searchParams = ref({})
const setSearchParams = (params: any) => {
  currentPage.value = 1
  searchParams.value = params
  getList()
}

const treeEl = ref<typeof ElTree>()
const currentNodeKey = ref('')
const currentDepartment = ref('')
const departmentList = ref<DepartmentItem[]>([])

// 默认选中树中的第一个可用部门，避免首屏查询没有部门上下文。
const findFirstDepartmentId = (list: DepartmentItem[]): string => {
  for (const item of list) {
    if (item?.id) {
      return String(item.id)
    }
    if (item.children?.length) {
      const childId = findFirstDepartmentId(item.children)
      if (childId) {
        return childId
      }
    }
  }
  return ''
}

const fetchDepartment = async () => {
  const res = await getDepartmentApi()
  departmentList.value = res.data.list
  currentNodeKey.value = findFirstDepartmentId(res.data.list)
  await nextTick()
  if (currentNodeKey.value) {
    unref(treeEl)?.setCurrentKey(currentNodeKey.value)
  }
  getList()
}
fetchDepartment()

watch(
  () => currentDepartment.value,
  (value) => {
    unref(treeEl)?.filter(value)
  }
)

const currentChange = (data: DepartmentItem) => {
  if (!data?.id) {
    return
  }
  currentNodeKey.value = String(data.id)
  currentPage.value = 1
  getList()
}

const filterNode = (value: string, data: DepartmentItem) => {
  if (!value) return true
  return data.departmentName.includes(value)
}

const dialogVisible = ref(false)
const dialogTitle = ref('')
const actionType = ref('')
const currentRow = ref<UserItem>()

const buildDefaultUser = (): UserItem => {
  return {
    username: '',
    account: '',
    email: '',
    phone: '',
    password: '',
    status: 1,
    role: [],
    department: currentNodeKey.value ? { id: currentNodeKey.value } : {}
  }
}

const addAction = () => {
  dialogTitle.value = t('exampleDemo.add')
  actionType.value = 'create'
  currentRow.value = buildDefaultUser()
  dialogVisible.value = true
}

const delLoading = ref(false)

const syncSelection = (rows: UserItem[]) => {
  selectedRows.value = rows || []
}

const selectedIds = () => {
  return selectedRows.value.map((item) => item.id || '').filter(Boolean)
}

const requireSelectedUsers = () => {
  const nextIds = selectedIds()
  if (!nextIds.length) {
    ElMessage.warning(t('common.delNoData'))
    return null
  }
  return nextIds
}

const requireSingleSelectedUser = () => {
  if (selectedRows.value.length !== 1) {
    ElMessage.warning(t('userDemo.selectSingleUser'))
    return null
  }
  return selectedRows.value[0]
}

const delData = async () => {
  ids.value = requireSelectedUsers() || []
  if (!ids.value.length) {
    return
  }
  delLoading.value = true
  await delList(unref(ids).length).finally(() => {
    delLoading.value = false
    selectedRows.value = []
  })
}

const action = (row: UserItem, type: string) => {
  dialogTitle.value = t(type === 'edit' ? 'exampleDemo.edit' : 'exampleDemo.detail')
  actionType.value = type
  currentRow.value = {
    ...buildDefaultUser(),
    ...row,
    role: row.role || [],
    department: row.department ? { ...row.department } : {}
  }
  dialogVisible.value = true
}

const writeRef = ref<ComponentRef<typeof Write>>()
const saveLoading = ref(false)

const updateStatus = async (nextStatus: number) => {
  const nextIds = requireSelectedUsers()
  if (!nextIds?.length) {
    return
  }
  const actionLabel = nextStatus === 1 ? t('userDemo.enable') : t('userDemo.disable')

  await ElMessageBox.confirm(
    t('userDemo.batchStatusConfirm', { action: actionLabel, count: nextIds.length }),
    t('common.reminder'),
    {
      type: 'warning'
    }
  )

  await updateUserStatusApi(
    nextIds,
    nextStatus,
    nextStatus === 0 ? `批量禁用账号: ${nextIds.join(',')}` : undefined
  )
  ElMessage.success(`${actionLabel}成功`)
  selectedRows.value = []
  getList()
}

const resetPassword = async () => {
  const row = requireSingleSelectedUser()
  if (!row) {
    return
  }
  const { value } = await ElMessageBox.prompt(
    t('userDemo.passwordResetPrompt'),
    t('userDemo.resetPassword'),
    {
      inputType: 'password',
      inputPlaceholder: t('userDemo.passwordPlaceholder'),
      inputValidator: (inputValue) => {
        if (!inputValue || inputValue.length < 8) {
          return t('userDemo.passwordRule')
        }
        return true
      }
    }
  )

  await resetUserPasswordApi(row.id || '', value)
  ElMessage.success(t('userDemo.passwordResetSuccess'))
  selectedRows.value = []
}

const save = async () => {
  const write = unref(writeRef)
  const formData = await write?.submit()
  if (!formData) {
    return
  }

  saveLoading.value = true
  try {
    const creating = !formData.id
    const res = await saveUserApi(formData)
    if (res) {
      if (creating) {
        currentPage.value = 1
      }
      dialogVisible.value = false
      selectedRows.value = []
      ElMessage.success(creating ? '用户创建成功' : '用户更新成功')
      getList()
    }
  } finally {
    saveLoading.value = false
  }
}
</script>

<template>
  <div class="flex w-100% h-100%">
    <ContentWrap class="w-250px">
      <div class="flex justify-center items-center">
        <div class="flex-1">{{ t('userDemo.departmentList') }}</div>
        <ElInput
          v-model="currentDepartment"
          class="flex-[2]"
          :placeholder="t('userDemo.searchDepartment')"
          clearable
        />
      </div>
      <ElDivider />
      <ElTree
        ref="treeEl"
        :data="departmentList"
        default-expand-all
        :expand-on-click-node="false"
        node-key="id"
        :current-node-key="currentNodeKey"
        :props="{
          label: 'departmentName'
        }"
        :filter-node-method="filterNode"
        @current-change="currentChange"
      >
        <template #default="{ data }">
          <div
            :title="data.departmentName"
            class="whitespace-nowrap overflow-ellipsis overflow-hidden"
          >
            {{ data.departmentName }}
          </div>
        </template>
      </ElTree>
    </ContentWrap>

    <ContentWrap class="flex-[3] ml-20px">
      <div class="mb-10px">
        <BaseButton v-hasPermi="'create'" type="primary" @click="addAction">
          {{ t('exampleDemo.add') }}
        </BaseButton>
        <BaseButton
          v-hasPermi="'disable'"
          type="success"
          :disabled="!selectedRows.length"
          @click="updateStatus(1)"
        >
          {{ t('userDemo.enable') }}
        </BaseButton>
        <BaseButton
          v-hasPermi="'disable'"
          type="warning"
          :disabled="!selectedRows.length"
          @click="updateStatus(0)"
        >
          {{ t('userDemo.disable') }}
        </BaseButton>
        <BaseButton
          v-hasPermi="'resetPassword'"
          type="warning"
          :disabled="selectedRows.length !== 1"
          @click="resetPassword"
        >
          {{ t('userDemo.resetPassword') }}
        </BaseButton>
        <BaseButton v-hasPermi="'delete'" :loading="delLoading" type="danger" @click="delData()">
          {{ t('exampleDemo.del') }}
        </BaseButton>
      </div>

      <Search
        :schema="allSchemas.searchSchema"
        @reset="setSearchParams"
        @search="setSearchParams"
      />

      <Table
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :columns="allSchemas.tableColumns"
        :data="dataList"
        :loading="loading"
        @register="tableRegister"
        @selection-change="syncSelection"
        :pagination="{
          total
        }"
      />
    </ContentWrap>

    <Dialog v-model="dialogVisible" :title="dialogTitle">
      <Write
        v-if="actionType !== 'detail'"
        ref="writeRef"
        :form-schema="allSchemas.formSchema"
        :current-row="currentRow"
      />

      <Detail
        v-if="actionType === 'detail'"
        :detail-schema="allSchemas.detailSchema"
        :current-row="currentRow"
      />

      <template #footer>
        <BaseButton
          v-if="actionType !== 'detail'"
          type="primary"
          :loading="saveLoading"
          @click="save"
        >
          {{ t('exampleDemo.save') }}
        </BaseButton>
        <BaseButton @click="dialogVisible = false">{{ t('dialogDemo.close') }}</BaseButton>
      </template>
    </Dialog>
  </div>
</template>
