<script setup lang="ts">
import { PropType } from 'vue'
import type { UserItem } from '@/api/user/types'
import { Descriptions, DescriptionsSchema } from '@/components/Descriptions'

defineProps({
  currentRow: {
    type: Object as PropType<Nullable<UserItem>>,
    default: () => null
  },
  detailSchema: {
    type: Array as PropType<DescriptionsSchema[]>,
    default: () => []
  }
})
</script>

<template>
  <Descriptions :schema="detailSchema" :data="currentRow || {}" />
</template>
