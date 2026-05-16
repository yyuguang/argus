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
        description: '查看评审状态、评分与执行进展',
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
    title: '质量治理',
    items: [
      {
        path: '/quality/personal',
        label: '个人代码质量',
        description: '查看提交者画像、趋势与高频问题',
        icon: '◈',
      },
      {
        path: '/quality/errors',
        label: '错误诊断',
        description: '查看应用服务错误、AI 分析与处理闭环',
        icon: '!',
      },
      {
        path: '/quality/error-type-rules',
        label: '类型规则',
        description: '维护异常类型识别规则',
        icon: '≡',
      },
      {
        path: '/quality/data-monitor',
        label: '数据监控',
        description: '查看慢 SQL、连接池与接口日志质量',
        icon: '▥',
      },
      {
        path: '/quality/data-monitor/configs',
        label: '监控配置',
        description: '维护应用数据源、slow log 与日志表',
        icon: '◇',
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
