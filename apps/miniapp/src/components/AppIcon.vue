<template>
  <image class="app-icon" :src="'/static/icons/'+name+'.svg'" mode="aspectFit" aria-hidden="true" alt="" />
</template>
<script setup lang="ts">
defineProps<{ name: string }>();
</script>
<style scoped>
.app-icon{display:block;width:24px;height:24px;flex-shrink:0}
</style>
