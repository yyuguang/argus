import type { BasePageParams, TableListResponse } from '@/api/common/page'

export interface RuleDocumentPageParams extends BasePageParams {
  category?: string
  scope?: string
  scmConfigId?: number | string
  status?: string
  parseStatus?: string
  vectorStatus?: string
  keyword?: string
}

export interface RuleDocumentItem {
  id?: string | number
  documentCode?: string
  documentName?: string
  category?: string
  scope?: string
  scmConfigId?: number | string
  scmProjectName?: string
  status?: string
  parseStatus?: string
  vectorStatus?: string
  chunkCount?: number
  latestErrorMessage?: string | null
  updateTime?: string
}

export type RuleDocumentListResponse = TableListResponse<RuleDocumentItem>
export type RuleDocumentPageResponse = RuleDocumentListResponse

export interface RuleDocumentDetailItem extends RuleDocumentItem {
  sourceType?: string
  fileName?: string
  fileExt?: string
  summaryText?: string
  versionNo?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
}

export interface RuleDocumentChunkPreviewItem {
  id?: string | number
  chunkNo?: number
  title?: string
  contentText?: string
  tokenEstimate?: number
  status?: string
}

export interface RuleDocumentPreviewItem {
  plainText?: string
  chunks: RuleDocumentChunkPreviewItem[]
  parseStatus?: string
  vectorStatus?: string
  latestErrorMessage?: string | null
}

export interface RuleDocumentImportPayload {
  category: string
  scope: string
  scmConfigId?: number | string
  documentName: string
  remark?: string
  activeAfterImport?: boolean
}

export interface RuleDocumentActionPayload {
  operator?: string
  comment?: string
}

export interface RuleStandardsMigrationPayload {
  activeAfterImport?: boolean
}

export interface RuleStandardsMigrationResult {
  importedCount?: number
  skippedCount?: number
  failedCount?: number
  importedDocumentCodes?: string[]
  failedFiles?: string[]
}

export interface RuleSeverityDefinitionItem {
  deduction: number | null
  label: string
  examples: string[]
}

export interface RuleScoringDimensions {
  compliance: number | null
  correctness: number | null
  dataIntegrity: number | null
  performance: number | null
  maintainability: number | null
}

export interface RuleBlockingRules {
  criticalDirectBlock: boolean
  majorBlockThreshold: number | null
  suggestionOnlyBlockEnabled: boolean
}

export interface RuleScoringProfile {
  blockThreshold: number | null
  blockingRules: RuleBlockingRules
  dimensions: RuleScoringDimensions
  severityDefinitions: Record<string, RuleSeverityDefinitionItem>
}

export interface RuleScopeProfile {
  standardCategories: string[]
}

export interface RuleProfileItem {
  scmConfigId?: number | string
  scoringProfile: RuleScoringProfile
  ruleProfile: RuleScopeProfile
}

export interface PromptTemplateCategorySummaryItem {
  categoryCode: string
  categoryName: string
  templateCount: number
  configuredCount: number
  missingCount: number
}

export interface PromptCatalogTemplateItem {
  templateCode: string
  templateName: string
  templateScene: string
  supportScmOverride: boolean
  sortNo: number
  status: string
  description?: string
}

export interface PromptCatalogCategoryItem {
  category: string
  categoryName: string
  description?: string
  templateCount: number
  templates: PromptCatalogTemplateItem[]
}

export interface PromptTemplateQueryParams {
  category?: string
  keyword?: string
}

export interface PromptTemplateSchemeItem {
  templateCode: string
  templateName: string
  category: string
  templateScene: string
  supportScmOverride: boolean
  description?: string
  currentScope: 'GLOBAL' | 'SCM'
  currentScmConfigId?: number
  contentText?: string
  remark?: string
  status?: string
  effectiveScope?: 'GLOBAL' | 'SCM'
  effectiveScmConfigId?: number
  hasScmOverride: boolean
  updateBy?: string
  updateTime?: string
}

export interface PromptTemplateSavePayload {
  templateCode?: string
  scope?: 'GLOBAL' | 'SCM'
  scmConfigId?: number
  contentText: string
  remark?: string
  status?: 'ACTIVE' | 'DISABLED'
}

export interface PromptTemplateListItem {
  templateCode: string
  templateName: string
  categoryCode: string
  categoryName: string
  slotKey: string
  scope: 'GLOBAL' | 'SCM'
  scmConfigId?: number | string
  configured: boolean
  effectiveSource: 'SCM' | 'GLOBAL' | 'SYSTEM'
  fallbackSource?: 'GLOBAL' | 'SYSTEM'
}

export interface ScoringPolicyDimensionItem {
  code: string
  name: string
  weight: number | null
  description?: string
}

export interface ScoringSeverityMatrixItem {
  severityCode: string
  severityName: string
  deduction: number | null
  examples: string[]
}
