<script setup lang="tsx">
import { reactive, ref, unref } from 'vue'
import { deleteRoleApi, getRoleListApi, saveRoleApi } from '@/api/role'
import type { RoleItem } from '@/api/role/types'
import { useTable } from '@/hooks/web/useTable'
import { useI18n } from '@/hooks/web/useI18n'
import { Table, TableColumn } from '@/components/Table'
import { ElMessage, ElTag } from 'element-plus'
import { hasPermi } from '@/components/Permission/src/utils'
import { Search } from '@/components/Search'
import { FormSchema } from '@/components/Form'
import { ContentWrap } from '@/components/ContentWrap'
import Write from './components/Write.vue'
import Detail from './components/Detail.vue'
import { Dialog } from '@/components/Dialog'
import { BaseButton } from '@/components/Button'

const { t } = useI18n()
const ids = ref<Array<string | number>>([])
const selectedRows = ref<RoleItem[]>([])

const { tableRegister, tableState, tableMethods } = useTable({
  fetchDataApi: async () => {
    const { currentPage, pageSize } = tableState
    const res = await getRoleListApi({
      pageNo: unref(currentPage),
      pageSize: unref(pageSize),
      ...unref(searchParams)
    })
    return {
      list: res.data.list || [],
      total: res.data.total
    }
  },
  fetchDelApi: async () => {
    const res = await deleteRoleApi(unref(ids))
    return !!res
  }
})

const { dataList, loading, total, currentPage, pageSize } = tableState
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
    field: 'roleName',
    label: t('role.roleName')
  },
  {
    field: 'roleCode',
    label: '角色编码'
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
    field: 'createTime',
    label: t('tableDemo.displayTime')
  },
  {
    field: 'remark',
    label: t('userDemo.remark')
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
    field: 'roleName',
    label: t('role.roleName'),
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
  }
])

const searchParams = ref({})
const setSearchParams = (data: any) => {
  searchParams.value = data
  currentPage.value = 1
  getList()
}

const dialogVisible = ref(false)
const dialogTitle = ref('')

const currentRow = ref()
const actionType = ref('')

const writeRef = ref<ComponentRef<typeof Write>>()

const saveLoading = ref(false)
const delLoading = ref(false)

const buildDefaultRole = (): RoleItem => {
  return {
    roleCode: '',
    roleName: '',
    status: 1,
    remark: '',
    menu: []
  }
}

const syncSelection = (rows: RoleItem[]) => {
  selectedRows.value = rows || []
}

const delData = async () => {
  ids.value = selectedRows.value.map((item) => item.id || '').filter(Boolean)
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

const action = (row: any, type: string) => {
  dialogTitle.value = t(type === 'edit' ? 'exampleDemo.edit' : 'exampleDemo.detail')
  actionType.value = type
  currentRow.value = {
    ...buildDefaultRole(),
    ...row,
    menu: row?.menu || []
  }
  dialogVisible.value = true
}

const AddAction = () => {
  dialogTitle.value = t('exampleDemo.add')
  currentRow.value = buildDefaultRole()
  dialogVisible.value = true
  actionType.value = 'create'
}

const save = async () => {
  const write = unref(writeRef)
  const formData = await write?.submit()
  if (formData) {
    saveLoading.value = true
    const res = await saveRoleApi(formData as RoleItem)
      .catch(() => {})
      .finally(() => {
        saveLoading.value = false
      })
    if (res) {
      dialogVisible.value = false
      selectedRows.value = []
      currentPage.value = 1
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
      :pagination="{
        total
      }"
      v-model:pageSize="pageSize"
      v-model:currentPage="currentPage"
      @register="tableRegister"
      @selection-change="syncSelection"
    />
  </ContentWrap>

  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <Write
      v-if="actionType !== 'detail'"
      :key="currentRow?.id || actionType || 'role-create'"
      ref="writeRef"
      :current-row="currentRow"
    />
    <Detail v-else :key="currentRow?.id || 'role-detail'" :current-row="currentRow" />

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
