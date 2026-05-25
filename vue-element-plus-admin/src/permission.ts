import router from './router'
import { useAppStoreWithOut } from '@/store/modules/app'
import type { RouteRecordRaw } from 'vue-router'
import { useTitle } from '@/hooks/web/useTitle'
import { useNProgress } from '@/hooks/web/useNProgress'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { usePageLoading } from '@/hooks/web/usePageLoading'
import { NO_REDIRECT_WHITE_LIST } from '@/constants'
import { useUserStoreWithOut } from '@/store/modules/user'
import { pathResolve } from '@/utils/routerHelper'

const { start, done } = useNProgress()

const { loadStart, loadDone } = usePageLoading()

const permissionManagedPrefixes = [
  '/authorization',
  '/code-review',
  '/error-governance',
  '/application-governance',
  '/monitor-center',
  '/rule-governance'
]

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

router.beforeEach(async (to, from, next) => {
  start()
  loadStart()
  const permissionStore = usePermissionStoreWithOut()
  const appStore = useAppStoreWithOut()
  const userStore = useUserStoreWithOut()
  const hasLoginState = userStore.getUserInfo || userStore.getToken
  if (hasLoginState) {
    if (!userStore.getUserInfo) {
      const currentUser = await userStore.fetchCurrentUser().catch(() => undefined)
      if (!currentUser) {
        userStore.reset(false)
        next(`/login?redirect=${to.path}`)
        return
      }
    }
    if (to.path === '/login') {
      next({ path: '/' })
    } else {
      const correctedDynamicRouterFlags =
        !appStore.getDynamicRouter || !appStore.getServerDynamicRouter
      if (correctedDynamicRouterFlags) {
        appStore.setDynamicRouter(true)
        appStore.setServerDynamicRouter(true)
        permissionStore.resetRoutes()
      }

      const requiresPermissionMeta = permissionManagedPrefixes.some((prefix) =>
        to.path.startsWith(prefix)
      )
      const currentRoutePermissions = (to.meta.permission || []) as string[]
      const cachedRoutePermissions = findPermissionByPath(permissionStore.getRouters, to.path)
      const needsPermissionRefresh =
        requiresPermissionMeta &&
        currentRoutePermissions.length === 0 &&
        cachedRoutePermissions.length === 0

      if (
        permissionStore.getIsAddRouters &&
        !correctedDynamicRouterFlags &&
        !needsPermissionRefresh
      ) {
        next()
        return
      }

      let roleRouters = userStore.getRoleRouters || []
      if (appStore.getDynamicRouter && appStore.getServerDynamicRouter) {
        roleRouters = await userStore.fetchRoleRouters().catch(() => [])
      }

      // 是否使用动态路由
      if (appStore.getDynamicRouter) {
        appStore.serverDynamicRouter
          ? await permissionStore.generateRoutes('server', roleRouters as AppCustomRouteRecordRaw[])
          : await permissionStore.generateRoutes('frontEnd', roleRouters as string[])
      } else {
        await permissionStore.generateRoutes('static')
      }

      permissionStore.getAddRouters.forEach((route) => {
        router.addRoute(route as unknown as RouteRecordRaw) // 动态添加可访问路由表
      })
      const redirectPath = from.query.redirect || to.path
      const redirect = decodeURIComponent(redirectPath as string)
      const nextData = to.path === redirect ? { ...to, replace: true } : { path: redirect }
      permissionStore.setIsAddRouters(true)
      next(nextData)
    }
  } else {
    if (NO_REDIRECT_WHITE_LIST.indexOf(to.path) !== -1) {
      next()
    } else {
      next(`/login?redirect=${to.path}`) // 否则全部重定向到登录页
    }
  }
})

router.afterEach((to) => {
  useTitle(to?.meta?.title as string)
  done() // 结束Progress
  loadDone()
})
