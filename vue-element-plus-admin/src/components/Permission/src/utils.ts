import { useI18n } from '@/hooks/web/useI18n'
import router from '@/router'
import { pathResolve } from '@/utils/routerHelper'
import { usePermissionStoreWithOut } from '@/store/modules/permission'

/**
 * 递归查找当前路由在权限路由树中的按钮权限集合。
 * 服务端动态路由会带上 meta.permission，但当前实际命中的可能仍是静态路由记录，
 * 因此需要回退到 permission store 中按完整路径再次解析，避免管理员页面按钮被误隐藏。
 */
const findPermissionByPath = (
  routes: AppRouteRecordRaw[],
  currentPath: string,
  parentPath = ''
): string[] => {
  for (const route of routes || []) {
    const fullPath = pathResolve(parentPath, route.path || '')
    if (fullPath === currentPath) {
      return (route.meta?.permission || []) as string[]
    }
    if (route.children?.length) {
      const childPermissions = findPermissionByPath(route.children, currentPath, fullPath)
      if (childPermissions.length) {
        return childPermissions
      }
    }
  }
  return []
}

/**
 * 优先读取当前路由记录上的权限定义；若当前命中的是静态路由，则从权限路由树中补查。
 */
export const resolveCurrentRoutePermissions = (): string[] => {
  const currentRoute = router.currentRoute.value
  const routePermissions = (currentRoute.meta.permission || []) as string[]
  if (routePermissions.length) {
    return routePermissions
  }

  const permissionStore = usePermissionStoreWithOut()
  return findPermissionByPath(permissionStore.getRouters, currentRoute.path)
}

/**
 * 判断当前页面是否具备指定按钮权限。
 */
export const hasPermi = (value: string) => {
  const { t } = useI18n()
  if (!value) {
    throw new Error(t('permission.hasPermission'))
  }
  return resolveCurrentRoutePermissions().includes(value)
}
