import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '../auth/session'
import DashboardPage from '../views/DashboardPage.vue'
import LoginPage from '../views/LoginPage.vue'
import ReviewTaskPage from '../views/ReviewTaskPage.vue'
import ScmConfigPage from '../views/ScmConfigPage.vue'
import PersonalQualityPage from '../views/PersonalQualityPage.vue'
import ErrorDiagnosisPage from '../views/ErrorDiagnosisPage.vue'
import DataMonitorPage from '../views/DataMonitorPage.vue'
import WebhookGuidePage from '../views/WebhookGuidePage.vue'
import SettingsPage from '../views/SettingsPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginPage,
      meta: {
        title: '登录',
        public: true,
      },
    },
    {
      path: '/scm-config',
      redirect: '/integration/scm-config',
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: DashboardPage,
      meta: {
        title: '平台总览',
        section: '总览',
      },
    },
    {
      path: '/integration/scm-config',
      name: 'scm-config',
      component: ScmConfigPage,
      meta: {
        title: '仓库配置',
        section: '代码接入',
      },
    },
    {
      path: '/integration/review-tasks',
      name: 'review-tasks',
      component: ReviewTaskPage,
      meta: {
        title: '评审任务中心',
        section: '代码接入',
      },
    },
    {
      path: '/quality/personal',
      name: 'personal-quality',
      component: PersonalQualityPage,
      meta: {
        title: '个人代码质量',
        section: '质量治理',
      },
    },
    {
      path: '/quality/errors',
      name: 'error-diagnosis',
      component: ErrorDiagnosisPage,
      meta: {
        title: '应用服务错误',
        section: '质量治理',
      },
    },
    {
      path: '/quality/data-monitor',
      name: 'data-monitor',
      component: DataMonitorPage,
      meta: {
        title: '数据监控',
        section: '质量治理',
      },
    },
    {
      path: '/integration/webhook-guide',
      name: 'webhook-guide',
      component: WebhookGuidePage,
      meta: {
        title: 'Webhook 接入指引',
        section: '代码接入',
      },
    },
    {
      path: '/settings/general',
      name: 'settings-general',
      component: SettingsPage,
      meta: {
        title: '系统设置',
        section: '系统设置',
      },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) {
    if (to.path === '/login' && isAuthenticated()) {
      return '/dashboard'
    }
    return true
  }

  if (!isAuthenticated()) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  return true
})

export default router
