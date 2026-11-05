<template>
  <view class="page module-page">
    <view class="module-hero module-soft-hero module-enter">
      <view class="hero-heading-row"><view class="module-symbol"><AppIcon name="asset" /></view><view><view class="module-hero-title" role="heading" aria-level="1">资产管理</view><view class="module-hero-note">设备资料、库存与租期可供</view></view></view>
    </view>
    <template v-if="role==='ADMIN'">
      <view class="module-section"><text class="module-section-title" role="heading" aria-level="2">基础资料</text><text class="module-section-note">维护器材与分类</text></view>
      <view class="module-list module-enter">
        <navigator class="module-card module-link" url="/pages/catalog/index?section=equipment"><view class="module-symbol"><AppIcon name="equipment" /></view><view class="module-copy"><view class="module-name">设备资料</view><view class="module-description">新增、编辑、删除与停用</view></view><text class="module-arrow" aria-hidden="true">›</text></navigator>
        <navigator class="module-card module-link" url="/pages/catalog/index?section=categories"><view class="module-symbol"><AppIcon name="category" /></view><view class="module-copy"><view class="module-name">分类管理</view><view class="module-description">设备大类与具体类别</view></view><text class="module-arrow" aria-hidden="true">›</text></navigator>
      </view>
    </template>
    <view class="module-section"><text class="module-section-title" role="heading" aria-level="2">库存与档期</text></view>
    <view class="module-grid module-enter">
      <navigator class="module-card module-tile asset-tile" url="/pages/inventory/index" open-type="switchTab"><view class="tile-top"><view class="module-symbol"><AppIcon name="inventory" /></view><text class="module-arrow" aria-hidden="true">›</text></view><view class="module-name">库存查询</view><view class="module-description">数量、来源与在外设备</view></navigator>
      <navigator class="module-card module-tile document-tile" url="/pages/supply/index"><view class="tile-top"><view class="module-symbol"><AppIcon name="calendar" /></view><text class="module-arrow" aria-hidden="true">›</text></view><view class="module-name">租期可供</view><view class="module-description">查询完整租期可供数量</view></navigator>
    </view>
    <view class="module-section"><text class="module-section-title" role="heading" aria-level="2">仓库与资产归属</text></view>
    <view class="warehouse-panel">
      <view class="warehouse-line"><text class="warehouse-label">存放仓库</text>北京 / 横店</view>
      <view class="warehouse-line"><text class="warehouse-label">资产归属</text>1号库 · 投资方　/　2号库 · Boss</view>
      <view class="warehouse-note">期初在外设备未登记发货仓的，记为“来源仓待核实”。</view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onReady } from '@dcloudio/uni-app';
import { markPageReady } from '../../session';
import { api, errorMessage } from '../../api';
import AppIcon from '../../components/AppIcon.vue';
const role = ref('');
onReady(markPageReady);
onShow(async () => {
    try { role.value = (await api('/auth/me')).role; }
    catch (e) { errorMessage(e); }
});
</script>
