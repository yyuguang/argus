<template>
  <div class="monitor-stats">
    <article v-for="item in cards" :key="item.key" class="monitor-stat" :class="item.tone">
      <span>{{ item.label }}</span>
      <strong>{{ item.value }}</strong>
      <small>{{ item.hint }}</small>
    </article>
  </div>
</template>

<script setup lang="ts">
import type { MonitorStatCard } from '@/api/dataMonitor/types'

defineProps<{
  cards: MonitorStatCard[]
}>()
</script>

<style scoped>
.monitor-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.monitor-stat {
  padding: 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.monitor-stat span,
.monitor-stat small {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.monitor-stat strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 28px;
  line-height: 1;
}

.monitor-stat.ok {
  border-top: 3px solid #22c55e;
}

.monitor-stat.warn {
  border-top: 3px solid #f59e0b;
}

.monitor-stat.danger {
  border-top: 3px solid #ef4444;
}

@media (max-width: 1100px) {
  .monitor-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
