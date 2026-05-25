<script setup lang="tsx">
import { PropType, computed, ref } from 'vue'
import { Descriptions, DescriptionsSchema } from '@/components/Descriptions'
import { ElTag } from 'element-plus'
import { getMenuListApi } from '@/api/menu'
import { eachTree } from '@/utils/tree'

const props = defineProps({
  currentRow: {
    type: Object as PropType<any>,
    default: () => undefined
  }
})

const renderStatusTag = (status?: number) => {
  return <ElTag type={status === 1 ? 'success' : 'danger'}>{status === 1 ? '启用' : '禁用'}</ElTag>
}

const treeData = ref<any[]>([])
const menuNameMap = ref<Record<string, string>>({})
const permissionLabelMap = ref<Record<string, Record<string, string>>>({})

const getMenuList = async () => {
  const res = await getMenuListApi()
  if (res) {
    treeData.value = res.data.list
    const nextMenuNameMap: Record<string, string> = {}
    const nextPermissionLabelMap: Record<string, Record<string, string>> = {}

    eachTree(treeData.value, (item) => {
      nextMenuNameMap[String(item.id)] = item.meta?.title || item.title || String(item.id)
      nextPermissionLabelMap[String(item.id)] = (item.permissionList || []).reduce(
        (acc: Record<string, string>, permission: any) => {
          acc[permission.value] = permission.label
          return acc
        },
        {}
      )
    })

    menuNameMap.value = nextMenuNameMap
    permissionLabelMap.value = nextPermissionLabelMap
  }
}
getMenuList()

const grantedMenus = computed(() => props.currentRow?.menu || [])

const detailSchema = ref<DescriptionsSchema[]>([
  {
    field: 'roleCode',
    label: '角色编码'
  },
  {
    field: 'roleName',
    label: '角色名称'
  },
  {
    field: 'status',
    label: '状态',
    slots: {
      default: (data: any) => {
        return renderStatusTag(data.status)
      }
    }
  },
  {
    field: 'remark',
    label: '备注',
    span: 24
  },
  {
    field: 'menu',
    label: '菜单分配',
    span: 24,
    slots: {
      default: () => {
        return (
          <div>
            {grantedMenus.value.length ? (
              grantedMenus.value.map((item: any) => {
                const permissionMap = permissionLabelMap.value[String(item.id)] || {}
                const permissions = item.meta?.permission || []
                return (
                  <div class="mb-8px">
                    <span class="mr-8px font-500">
                      {menuNameMap.value[String(item.id)] || item.id}
                    </span>
                    {permissions.length ? (
                      permissions.map((permission: string) => (
                        <ElTag class="mr-4px mb-4px" size="small">
                          {permissionMap[permission] || permission}
                        </ElTag>
                      ))
                    ) : (
                      <ElTag size="small">页面权限</ElTag>
                    )}
                  </div>
                )
              })
            ) : (
              <span>-</span>
            )}
          </div>
        )
      }
    }
  }
])
</script>

<template>
  <Descriptions :schema="detailSchema" :data="currentRow || {}" />
</template>
