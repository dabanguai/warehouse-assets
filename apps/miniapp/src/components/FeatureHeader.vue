<template>
  <view class="module-hero feature-header module-enter" :class="tone==='soft'?'module-soft-hero':''">
    <view class="hero-heading-row">
      <view class="module-symbol"><AppIcon :name="icon" /></view>
      <view class="feature-heading-copy"><view class="module-hero-title" role="heading" aria-level="1">{{title}}</view><view v-if="description" class="module-hero-note">{{description}}</view></view>
    </view>
    <slot />
  </view>
</template>
<script setup lang="ts">
import AppIcon from './AppIcon.vue';
withDefaults(defineProps<{ title: string; description?: string; icon: string; tone?: 'soft' | 'strong' }>(), { tone:'soft', description:'' });
</script>
<style scoped>
.feature-heading-copy{flex:1;min-width:0;overflow-wrap:anywhere}
.feature-header .module-hero-title{font-size:25px;line-height:1.5;overflow-wrap:anywhere}
.feature-header .hero-heading-row{align-items:flex-start}
.feature-header .module-symbol{background:#e5effd;width:46px;height:46px;border-radius:15px}
.feature-header .module-hero-note{overflow-wrap:anywhere;line-height:1.85}
</style>
