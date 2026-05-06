export const menuGroups = [
  {
    title: '总览',
    items: [
      {
        path: '/dashboard',
        label: '平台总览',
        description: '查看接入情况与治理状态',
        icon: '▣',
      },
    ],
  },
  {
    title: '代码接入',
    items: [
      {
        path: '/integration/scm-config',
        label: '仓库配置',
        description: '管理 GitLab、GitHub、Gitee 仓库接入',
        icon: '◆',
      },
      {
        path: '/integration/review-tasks',
        label: '评审任务',
        description: '查看评审状态、评分与失败原因',
        icon: '▤',
      },
      {
        path: '/integration/webhook-guide',
        label: 'Webhook 指引',
        description: '查看各平台回调地址与配置说明',
        icon: '↗',
      },
    ],
  },
  {
    title: '系统设置',
    items: [
      {
        path: '/settings/general',
        label: '基础设置',
        description: '预留通知、策略与权限配置入口',
        icon: '○',
      },
    ],
  },
]
