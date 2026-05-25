<script setup lang="ts">
import { computed } from 'vue'
import { Dialog } from '@/components/Dialog'
import type { CodeIndexScanTask } from '@/api/codeIndex/types'

type ScanTaskTagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

const props = defineProps<{
  modelValue: boolean
  task?: CodeIndexScanTask
  polling?: boolean
  pollFailureCount?: number
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const statusMeta = computed<{
  label: string
  type: ScanTaskTagType
}>(() => {
  const status = props.task?.taskStatus || 'PENDING'
  if (status === 'SUCCESS' || status === 'REUSED') {
    return { label: status === 'REUSED' ? '已复用' : '成功', type: 'success' }
  }
  if (status === 'FAILED' || status === 'CANCELED') {
    return { label: status === 'CANCELED' ? '已取消' : '失败', type: 'danger' }
  }
  if (status === 'RUNNING') {
    return { label: '执行中', type: 'warning' }
  }
  return { label: '等待中', type: 'info' }
})

const stageText = computed(() => {
  const stage = props.task?.scanStage || 'WAITING'
  const stageMap: Record<string, string> = {
    WAITING: '等待执行',
    SCM_READING: '读取 SCM 文件',
    MODULE_SCANNING: '扫描 Maven 模块',
    SOURCE_ROOT_DISCOVERING: '发现源码根',
    JAVA_PARSING: '解析 Java 文件',
    INDEX_AGGREGATING: '聚合索引',
    INDEX_PERSISTING: '持久化索引',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return stageMap[stage] || stage
})

const progressPercent = computed(() => {
  const raw = Number(props.task?.progressPercent ?? 0)
  if (!Number.isFinite(raw)) {
    return 0
  }
  return Math.max(0, Math.min(100, Math.floor(raw)))
})

const progressStatus = computed(() => {
  if (props.task?.taskStatus === 'SUCCESS' || props.task?.taskStatus === 'REUSED') {
    return 'success'
  }
  if (props.task?.taskStatus === 'FAILED' || props.task?.taskStatus === 'CANCELED') {
    return 'exception'
  }
  return undefined
})

const taskTitle = computed(
  () => props.task?.taskNo || (props.task?.taskId ? `#${props.task.taskId}` : '-')
)

const formatNumber = (value?: number) => {
  if (value === undefined || value === null) {
    return '-'
  }
  return String(value)
}

const formatDate = (value?: string) => {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <Dialog
    v-model="visible"
    title="源码索引扫描进度"
    width="620px"
    max-height="calc(100vh - 180px)"
    :fullscreen="false"
  >
    <div class="scan-task-dialog">
      <div class="scan-task-dialog__header">
        <div>
          <div class="scan-task-dialog__title">{{ taskTitle }}</div>
          <div class="scan-task-dialog__stage">{{ stageText }}</div>
        </div>
        <ElTag :type="statusMeta.type" effect="light">{{ statusMeta.label }}</ElTag>
      </div>

      <ElProgress
        :percentage="progressPercent"
        :status="progressStatus"
        :stroke-width="10"
        :text-inside="false"
      />

      <div class="scan-task-dialog__stats">
        <div>
          <span>已解析 / 总 Java 文件</span>
          <strong
            >{{ formatNumber(task?.parsedFileCount) }} /
            {{ formatNumber(task?.totalJavaFileCount) }}</strong
          >
        </div>
        <div>
          <span>失败文件</span>
          <strong>{{ formatNumber(task?.failedFileCount) }}</strong>
        </div>
        <div>
          <span>Class 数</span>
          <strong>{{ formatNumber(task?.classCount) }}</strong>
        </div>
        <div>
          <span>Package 数</span>
          <strong>{{ formatNumber(task?.packageCount) }}</strong>
        </div>
      </div>

      <ElDescriptions :column="2" border size="small" class="scan-task-dialog__descriptions">
        <ElDescriptionsItem label="当前阶段">{{ task?.scanStage || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="已读取文件">{{
          formatNumber(task?.loadedFileCount)
        }}</ElDescriptionsItem>
        <ElDescriptionsItem label="告警数">{{
          formatNumber(task?.warningCount)
        }}</ElDescriptionsItem>
        <ElDescriptionsItem label="结果索引">{{
          task?.resultIndexId || task?.reusedIndexId || '-'
        }}</ElDescriptionsItem>
        <ElDescriptionsItem label="开始时间">{{ formatDate(task?.startedAt) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="最近更新">{{
          formatDate(task?.lastHeartbeatAt)
        }}</ElDescriptionsItem>
        <ElDescriptionsItem label="完成时间">{{ formatDate(task?.finishedAt) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="轮询失败">{{ pollFailureCount || 0 }}</ElDescriptionsItem>
      </ElDescriptions>

      <ElAlert
        v-if="task?.taskStatus === 'FAILED'"
        type="error"
        show-icon
        :closable="false"
        :title="task.latestErrorMessage || task.message || '源码索引扫描失败'"
      />
      <ElAlert
        v-else-if="polling"
        type="info"
        show-icon
        :closable="false"
        title="正在刷新任务进度"
      />
      <ElAlert
        v-else-if="task?.taskStatus === 'SUCCESS' || task?.taskStatus === 'REUSED'"
        type="success"
        show-icon
        :closable="false"
        :title="task?.message || '源码索引任务已完成'"
      />
    </div>
  </Dialog>
</template>

<style scoped lang="less">
.scan-task-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scan-task-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.scan-task-dialog__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.scan-task-dialog__stage {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.scan-task-dialog__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;

  div {
    min-width: 0;
    padding: 10px 12px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    background: var(--el-fill-color-lighter);
  }

  span {
    display: block;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  strong {
    display: block;
    margin-top: 6px;
    font-size: 15px;
    color: var(--el-text-color-primary);
    overflow-wrap: anywhere;
  }
}

.scan-task-dialog__descriptions {
  width: 100%;
}

@media (max-width: 768px) {
  .scan-task-dialog__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
