<template>
  <div class="monitor-stats">
    <article v-for="item in cards" :key="item.key" class="monitor-stat" :class="item.tone">
      <span>{{ item.label }}</span>
      <strong>{{ item.value }}</strong>
      <small>{{ item.hint }}</small>
    </article>
  </div>
</template>

<script setup>
defineProps({
  cards: {
    type: Array,
    default: () => [],
  },
})
</script>

<style scoped>
.monitor-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.monitor-stat {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.monitor-stat span,
.monitor-stat small {
  display: block;
  color: var(--muted);
  font-size: 13px;
}

.monitor-stat strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 30px;
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

