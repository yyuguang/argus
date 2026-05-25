<script setup lang="tsx">
import { ContentWrap } from '@/components/ContentWrap'
import { Search } from '@/components/Search'
import { Dialog } from '@/components/Dialog'
import { useI18n } from '@/hooks/web/useI18n'
import { ElMessage, ElTag } from 'element-plus'
import { Table } from '@/components/Table'
import {
  getDepartmentApi,
  getDepartmentTableApi,
  saveDepartmentApi,
  deleteDepartmentApi
} from '@/api/department'
import type { DepartmentItem } from '@/api/department/types'
import { useTable } from '@/hooks/web/useTable'
import { ref, unref, reactive } from 'vue'
import Write from './components/Write.vue'
import Detail from './components/Detail.vue'
import { CrudSchema, useCrudSchemas } from '@/hooks/web/useCrudSchemas'
import { BaseButton } from '@/components/Button'
import { hasPermi } from '@/components/Permission/src/utils'

const ids = ref<string[]>([])
const selectedRows = ref<DepartmentItem[]>([])

const { tableRegister, tableState, tableMethods } = useTable({
  fetchDataApi: async () => {
    const { currentPage, pageSize } = tableState
    const res = await getDepartmentTableApi({
      pageIndex: unref(currentPage),
      pageSize: unref(pageSize),
      ...unref(searchParams)
    })
    return {
      list: res.data.list,
      total: res.data.total
    }
  },
  fetchDelApi: async () => {
    const res = await deleteDepartmentApi(unref(ids))
    return !!res
  }
})
const { loading, dataList, total, currentPage, pageSize } = tableState
const { getList, delList } = tableMethods

const searchParams = ref({})
const setSearchParams = (params: any) => {
  searchParams.value = params
  currentPage.value = 1
  getList()
}

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

const loadDepartmentTree = async () => {
  const res = await getDepartmentApi()
  return res.data.list
}

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
    label: t('tableDemo.index'),
    type: 'index',
    search: {
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
    field: 'parentId',
    label: '上级部门',
    search: {
      hidden: true
    },
    table: {
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
    detail: {
      hidden: true
    }
  },
  {
    field: 'departmentName',
    label: t('userDemo.departmentName'),
    search: {
      component: 'Input',
      componentProps: {
        maxlength: 64
      }
    },
    table: {
      slots: {
        default: (data: any) => {
          return <>{data.row.departmentName}</>
        }
      }
    },
    form: {
      component: 'Input',
      componentProps: {
        maxlength: 64,
        showWordLimit: true
      }
    },
    detail: {
      slots: {
        default: (data: DepartmentItem) => <>{data.departmentName}</>
      }
    }
  },
  {
    field: 'status',
    label: t('userDemo.status'),
    search: {
      component: 'Select',
      componentProps: {
        options: statusOptions
      }
    },
    table: {
      slots: {
        default: (data: any) => {
          const status = data.row.status
          return (
            <>
              <ElTag type={status === 0 ? 'danger' : 'success'}>
                {status === 1 ? t('userDemo.enable') : t('userDemo.disable')}
              </ElTag>
            </>
          )
        }
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
        default: (data: any) => {
          return (
            <>
              <ElTag type={data.status === 0 ? 'danger' : 'success'}>
                {data.status === 1 ? t('userDemo.enable') : t('userDemo.disable')}
              </ElTag>
            </>
          )
        }
      }
    }
  },
  {
    field: 'sortOrder',
    label: '排序',
    search: {
      hidden: true
    },
    table: {
      width: '100px'
    },
    form: {
      component: 'InputNumber',
      value: 0,
      componentProps: {
        min: 0,
        precision: 0,
        controlsPosition: 'right'
      }
    }
  },
  {
    field: 'createTime',
    label: t('tableDemo.displayTime'),
    search: {
      hidden: true
    },
    form: {
      hidden: true
    }
  },
  {
    field: 'remark',
    label: t('userDemo.remark'),
    search: {
      hidden: true
    },
    form: {
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 5
      },
      colProps: {
        span: 24
      }
    },
    detail: {
      slots: {
        default: (data: any) => {
          return <>{data.remark}</>
        }
      }
    }
  },
  {
    field: 'action',
    width: '180px',
    label: t('tableDemo.action'),
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
      slots: {
        default: (data: any) => {
          return (
            <div class="flex flex-wrap gap-8px">
              {hasPermi('update') ? (
                <BaseButton type="primary" onClick={() => action(data.row, 'edit')}>
                  {t('exampleDemo.edit')}
                </BaseButton>
              ) : null}
              {hasPermi('view') ? (
                <BaseButton type="success" onClick={() => action(data.row, 'detail')}>
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

// @ts-ignore
const { allSchemas } = useCrudSchemas(crudSchemas)

const dialogVisible = ref(false)
const dialogTitle = ref('')

const currentRow = ref<DepartmentItem | null>(null)
const actionType = ref('')

const AddAction = () => {
  dialogTitle.value = t('exampleDemo.add')
  currentRow.value = null
  dialogVisible.value = true
  actionType.value = ''
}

const delLoading = ref(false)

const syncSelection = (rows: DepartmentItem[]) => {
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

const action = (row: DepartmentItem, type: string) => {
  dialogTitle.value = t(type === 'edit' ? 'exampleDemo.edit' : 'exampleDemo.detail')
  actionType.value = type
  currentRow.value = row
  dialogVisible.value = true
}

const writeRef = ref<ComponentRef<typeof Write>>()

const saveLoading = ref(false)

const save = async () => {
  const write = unref(writeRef)
  const formData = await write?.submit()
  if (formData) {
    saveLoading.value = true
    const res = await saveDepartmentApi(formData)
      .catch(() => {})
      .finally(() => {
        saveLoading.value = false
      })
    if (res) {
      dialogVisible.value = false
      currentPage.value = 1
      getList()
    }
  }
}
</script>

<template>
  <ContentWrap>
    <Search :schema="allSchemas.searchSchema" @search="setSearchParams" @reset="setSearchParams" />

    <div class="mb-10px">
      <BaseButton v-if="hasPermi('create')" type="primary" @click="AddAction">
        {{ t('exampleDemo.add') }}
      </BaseButton>
      <BaseButton v-if="hasPermi('delete')" :loading="delLoading" type="danger" @click="delData">
        {{ t('exampleDemo.del') }}
      </BaseButton>
    </div>

    <Table
      v-model:pageSize="pageSize"
      v-model:currentPage="currentPage"
      :columns="allSchemas.tableColumns"
      :data="dataList"
      :loading="loading"
      :pagination="{
        total: total
      }"
      @register="tableRegister"
      @selection-change="syncSelection"
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
</template>
