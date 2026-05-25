<script setup lang="tsx">
import { reactive, ref, unref } from 'vue'
import { deleteMenuApi, getMenuListApi, saveMenuApi, updateMenuStatusApi } from '@/api/menu'
import type { MenuItem } from '@/api/menu/types'
import { useTable } from '@/hooks/web/useTable'
import { useI18n } from '@/hooks/web/useI18n'
import { Table, TableColumn } from '@/components/Table'
import { ElMessage, ElMessageBox, ElTag } from 'element-plus'
import { Icon } from '@/components/Icon'
import { hasPermi } from '@/components/Permission/src/utils'
import { Search } from '@/components/Search'
import { FormSchema } from '@/components/Form'
import { ContentWrap } from '@/components/ContentWrap'
import Write from './components/Write.vue'
import Detail from './components/Detail.vue'
import { Dialog } from '@/components/Dialog'
import { BaseButton } from '@/components/Button'
import { filter } from '@/utils/tree'

const { t } = useI18n()
const ids = ref<Array<string | number>>([])
const selectedRows = ref<MenuItem[]>([])
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

const { tableRegister, tableState, tableMethods } = useTable({
  fetchDataApi: async () => {
    const res = await getMenuListApi()
    const keyword = String(unref(searchParams)?.['meta.title'] || '').trim()
    const status = unref(searchParams)?.status
    const nextList =
      keyword || status !== undefined
        ? filter(res.data.list || [], (item: any) => {
            const titleMatched =
              !keyword || (item.meta?.title || item.title || '').includes(keyword)
            const statusMatched = status === undefined || status === null || item.status === status
            return titleMatched && statusMatched
          })
        : res.data.list || []
    return {
      list: nextList
    }
  },
  fetchDelApi: async () => {
    const res = await deleteMenuApi(unref(ids))
    return !!res
  }
})

const { dataList, loading } = tableState
const { getList, delList } = tableMethods

const tableColumns = reactive<TableColumn[]>([
  {
    field: 'selection',
    type: 'selection'
  },
  {
    field: 'index',
    label: t('userDemo.index'),
    type: 'index'
  },
  {
    field: 'meta.title',
    label: t('menu.menuName'),
    slots: {
      default: (data: any) => {
        const title = data.row.meta.title
        return <>{title}</>
      }
    }
  },
  {
    field: 'meta.icon',
    label: t('menu.icon'),
    slots: {
      default: (data: any) => {
        const icon = data.row.meta.icon
        if (icon) {
          return (
            <>
              <Icon icon={icon} />
            </>
          )
        } else {
          return null
        }
      }
    }
  },
  // {
  //   field: 'meta.permission',
  //   label: t('menu.permission'),
  //   slots: {
  //     default: (data: any) => {
  //       const permission = data.row.meta.permission
  //       return permission ? <>{permission.join(', ')}</> : null
  //     }
  //   }
  // },
  {
    field: 'component',
    label: t('menu.component'),
    slots: {
      default: (data: any) => {
        const component = data.row.component
        return <>{component === '#' ? '顶级目录' : component === '##' ? '子目录' : component}</>
      }
    }
  },
  {
    field: 'path',
    label: t('menu.path')
  },
  {
    field: 'status',
    label: t('menu.status'),
    slots: {
      default: (data: any) => {
        return (
          <>
            <ElTag type={data.row.status === 0 ? 'danger' : 'success'}>
              {data.row.status === 1 ? t('userDemo.enable') : t('userDemo.disable')}
            </ElTag>
          </>
        )
      }
    }
  },
  {
    field: 'action',
    label: t('userDemo.action'),
    width: 180,
    slots: {
      default: (data: any) => {
        const row = data.row
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
])

const searchSchema = reactive<FormSchema[]>([
  {
    field: 'meta.title',
    label: t('menu.menuName'),
    component: 'Input'
  },
  {
    field: 'status',
    label: t('menu.status'),
    component: 'Select',
    componentProps: {
      options: statusOptions
    }
  }
])

const searchParams = ref<Record<string, any>>({})
const setSearchParams = (data: any) => {
  searchParams.value = data
  getList()
}

const dialogVisible = ref(false)
const dialogTitle = ref('')

const currentRow = ref()
const actionType = ref('')

const writeRef = ref<ComponentRef<typeof Write>>()

const saveLoading = ref(false)
const delLoading = ref(false)
const statusLoading = ref(false)

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

const syncSelection = (rows: MenuItem[]) => {
  selectedRows.value = rows || []
}

const selectedIds = () => {
  return selectedRows.value.map((item) => item.id || '').filter(Boolean)
}

const delData = async () => {
  ids.value = selectedIds()
  if (!ids.value.length) {
    ElMessage.warning(t('common.delNoData'))
    return
  }
  delLoading.value = true
  await delList(unref(ids).length).finally(() => {
    delLoading.value = false
    selectedRows.value = []
  })
}

const updateStatus = async (nextStatus: number) => {
  const nextIds = selectedIds()
  if (!nextIds.length) {
    ElMessage.warning(t('common.delNoData'))
    return
  }

  const actionLabel = nextStatus === 1 ? t('userDemo.enable') : t('userDemo.disable')
  await ElMessageBox.confirm(
    `确认对选中的 ${nextIds.length} 个菜单执行${actionLabel}吗？`,
    t('common.reminder'),
    {
      type: 'warning'
    }
  )

  statusLoading.value = true
  await updateMenuStatusApi(nextIds, nextStatus)
    .then(() => {
      ElMessage.success(`${actionLabel}成功`)
      selectedRows.value = []
      getList()
    })
    .finally(() => {
      statusLoading.value = false
    })
}

const action = (row: any, type: string) => {
  dialogTitle.value = t(type === 'edit' ? 'exampleDemo.edit' : 'exampleDemo.detail')
  actionType.value = type
  currentRow.value = {
    ...buildDefaultMenu(),
    ...row,
    meta: {
      ...buildDefaultMenu().meta,
      ...(row?.meta || {})
    },
    permissionList: row?.permissionList || []
  }
  dialogVisible.value = true
}

const AddAction = () => {
  dialogTitle.value = t('exampleDemo.add')
  currentRow.value = buildDefaultMenu()
  dialogVisible.value = true
  actionType.value = 'create'
}

const save = async () => {
  const write = unref(writeRef)
  const formData = await write?.submit()
  if (formData) {
    saveLoading.value = true
    const res = await saveMenuApi(formData as MenuItem)
      .catch(() => {})
      .finally(() => {
        saveLoading.value = false
      })
    if (res) {
      dialogVisible.value = false
      selectedRows.value = []
      getList()
    }
  }
}
</script>

<template>
  <ContentWrap>
    <Search :schema="searchSchema" @reset="setSearchParams" @search="setSearchParams" />
    <div class="mb-10px">
      <BaseButton v-hasPermi="'create'" type="primary" @click="AddAction">
        {{ t('exampleDemo.add') }}
      </BaseButton>
      <BaseButton v-hasPermi="'update'" :loading="statusLoading" @click="updateStatus(1)">
        {{ t('userDemo.enable') }}
      </BaseButton>
      <BaseButton v-hasPermi="'update'" :loading="statusLoading" @click="updateStatus(0)">
        {{ t('userDemo.disable') }}
      </BaseButton>
      <BaseButton v-hasPermi="'delete'" :loading="delLoading" type="danger" @click="delData">
        {{ t('exampleDemo.del') }}
      </BaseButton>
    </div>
    <Table
      :columns="tableColumns"
      default-expand-all
      node-key="id"
      :data="dataList"
      :loading="loading"
      @register="tableRegister"
      @selection-change="syncSelection"
    />
  </ContentWrap>

  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <Write
      v-if="actionType !== 'detail'"
      :key="currentRow?.id || actionType || 'menu-create'"
      ref="writeRef"
      :current-row="currentRow"
    />

    <Detail
      v-if="actionType === 'detail'"
      :key="currentRow?.id || 'menu-detail'"
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
</template>
