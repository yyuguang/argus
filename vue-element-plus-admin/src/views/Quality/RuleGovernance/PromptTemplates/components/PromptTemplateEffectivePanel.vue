<script setup lang="ts">
defineProps<{
  supported: boolean
  configured: boolean
  effectiveSourceLabel: string
  scmConfigSelected: boolean
}>()
</script>

<template>
  <div class="rounded-6px border border-solid border-[var(--el-border-color-light)] p-12px">
    <div class="mb-8px font-600">生效关系</div>
    <div class="flex flex-col gap-6px text-13px leading-22px">
      <div>
        <span class="font-600">当前仓库覆盖：</span>
        <span>{{ configured ? '已配置' : '未配置' }}</span>
      </div>
      <div>
        <span class="font-600">当前实际生效：</span>
        <span>{{ effectiveSourceLabel }}</span>
      </div>
      <div class="text-12px color-[var(--el-text-color-secondary)]">
        <template v-if="supported && scmConfigSelected">
          当前页先维护仓库级覆盖。若未配置，会继续走系统兜底；全局模板资产链路后续继续补齐。
        </template>
        <template v-else-if="supported">
          请先选择仓库，才能判断当前仓库是否存在 Prompt 覆盖配置。
        </template>
        <template v-else>
          该槽位已纳入 Prompt 盘点清单，但当前前后端资产化链路尚未接通，先以“待接入”显示。
        </template>
      </div>
    </div>
  </div>
</template>
