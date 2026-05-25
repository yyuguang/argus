export const scoringCategoryOptions = [
  {
    label: '编码规范',
    value: 'CODING',
    description: '命名、注释、日志、分层、异常处理等团队编码要求'
  },
  {
    label: '接口规范',
    value: 'API',
    description: '入参校验、返回结构、幂等、兼容性和接口边界约束'
  },
  {
    label: '数据库规范',
    value: 'DATABASE',
    description: '索引、事务、SQL 风险、数据一致性与变更规范'
  },
  {
    label: '安全规范',
    value: 'SECURITY',
    description: '鉴权、越权、防注入、敏感信息和凭据保护'
  },
  {
    label: '自定义规范',
    value: 'CUSTOM',
    description: '团队当前阶段特别关注的专项治理规则'
  }
] as const

export const scoringDimensionDefinitions = [
  {
    key: 'compliance',
    name: '规范合规',
    description: '风格、命名、日志、注释、分层是否符合团队约束',
    focus: '规范遗漏、命名不清、日志上下文不足、目录职责混乱'
  },
  {
    key: 'correctness',
    name: '逻辑正确',
    description: '空指针、边界、异常、分支流程和调用时序是否正确',
    focus: '空指针风险、异常吞掉、边界分支缺失、流程判断错误'
  },
  {
    key: 'dataIntegrity',
    name: '数据完整',
    description: '事务、一致性、主键约束、更新链路和安全校验是否可靠',
    focus: '事务缺失、数据覆盖、重复写入、主数据依赖未校验'
  },
  {
    key: 'performance',
    name: '性能风险',
    description: 'SQL、远程调用、循环、资源使用是否存在明显成本问题',
    focus: 'N+1 查询、无索引扫描、重复 RPC、过大对象创建'
  },
  {
    key: 'maintainability',
    name: '可维护性',
    description: '复杂度、重复代码、模块清晰度和后续扩展成本',
    focus: '方法过长、重复逻辑、硬编码过多、语义不清'
  }
] as const

export const scoringPreviewScenarios = [
  {
    code: 'CRITICAL_PLUS_MINOR',
    name: '阻塞缺陷 + 一般问题',
    description: '适合观察高风险问题出现时，当前阈值是否还会放行',
    severities: ['CRITICAL', 'MINOR']
  },
  {
    code: 'DOUBLE_MAJOR',
    name: '两个高风险问题',
    description: '适合判断主链路逻辑问题累积后会不会直接阻断',
    severities: ['MAJOR', 'MAJOR']
  },
  {
    code: 'MULTI_MINOR',
    name: '多个一般问题',
    description: '适合衡量规范性和可维护性问题的累计惩罚力度',
    severities: ['MINOR', 'MINOR', 'MINOR', 'SUGGESTION']
  },
  {
    code: 'CRITICAL_PLUS_MAJOR',
    name: '阻塞缺陷 + 高风险问题',
    description: '适合检查最严苛场景下的最终分数与阻断表现',
    severities: ['CRITICAL', 'MAJOR']
  }
] as const

export const futureBlockingRuleItems = [
  '出现 CRITICAL 是否直接阻塞',
  '同次评审 MAJOR 数量达到多少阻塞',
  '是否允许仅因建议项触发阻塞'
] as const
