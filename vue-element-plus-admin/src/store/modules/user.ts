import { defineStore } from 'pinia'
import { store } from '../index'
import { UserLoginType, UserType } from '@/api/login/types'
import { ElMessageBox } from 'element-plus'
import { useI18n } from '@/hooks/web/useI18n'
import { getAdminRoleApi, getCurrentUserApi, loginOutApi } from '@/api/login'
import { useTagsViewStore } from './tagsView'
import { usePermissionStore } from './permission'
import router from '@/router'

interface UserState {
  userInfo?: UserType
  tokenKey: string
  token: string
  roleRouters?: string[] | AppCustomRouteRecordRaw[]
  rememberMe: boolean
  loginInfo?: UserLoginType
}

export const useUserStore = defineStore('user', {
  state: (): UserState => {
    return {
      userInfo: undefined,
      tokenKey: 'Authorization',
      token: '',
      roleRouters: undefined,
      // 记住我
      rememberMe: true,
      loginInfo: undefined
    }
  },
  getters: {
    getTokenKey(): string {
      return this.tokenKey
    },
    getToken(): string {
      return this.token
    },
    getUserInfo(): UserType | undefined {
      return this.userInfo
    },
    getRoleRouters(): string[] | AppCustomRouteRecordRaw[] | undefined {
      return this.roleRouters
    },
    getRememberMe(): boolean {
      return this.rememberMe
    },
    getLoginInfo(): UserLoginType | undefined {
      return this.loginInfo
    }
  },
  actions: {
    setTokenKey(tokenKey: string) {
      this.tokenKey = tokenKey
    },
    setToken(token: string) {
      this.token = token
    },
    setUserInfo(userInfo?: UserType) {
      this.userInfo = userInfo
    },
    setRoleRouters(roleRouters: string[] | AppCustomRouteRecordRaw[]) {
      this.roleRouters = roleRouters
    },
    async fetchCurrentUser() {
      const res = await getCurrentUserApi()
      if (res) {
        this.setUserInfo({
          ...this.userInfo,
          ...res.data
        })
      }
      return this.userInfo
    },
    async fetchRoleRouters() {
      const res = await getAdminRoleApi()
      const routers = res?.data || []
      this.setRoleRouters(routers)
      return routers
    },
    logoutConfirm() {
      const { t } = useI18n()
      ElMessageBox.confirm(t('common.loginOutMessage'), t('common.reminder'), {
        confirmButtonText: t('common.ok'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      })
        .then(async () => {
          const res = await loginOutApi().catch(() => {})
          if (res) {
            this.reset()
          }
        })
        .catch(() => {})
    },
    reset(redirect = true) {
      const tagsViewStore = useTagsViewStore()
      const permissionStore = usePermissionStore()
      tagsViewStore.delAllViews()
      permissionStore.resetRoutes()
      this.setTokenKey('Authorization')
      this.setToken('')
      this.setUserInfo(undefined)
      this.setRoleRouters([])
      if (redirect) {
        router.replace('/login')
      }
    },
    logout() {
      this.reset()
    },
    setRememberMe(rememberMe: boolean) {
      this.rememberMe = rememberMe
    },
    setLoginInfo(loginInfo: UserLoginType | undefined) {
      this.loginInfo = loginInfo
    }
  },
  persist: true
})

export const useUserStoreWithOut = () => {
  return useUserStore(store)
}
