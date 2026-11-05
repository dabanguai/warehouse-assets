<template>
  <view class="page module-page documents-page">
    <view class="module-hero module-enter">
      <view class="module-hero-title" role="heading" aria-level="1">单据管理</view>
      <view class="module-hero-note">入库、出库、归还与期初记录</view>
      <view class="document-metrics" aria-live="polite">
        <view><view class="document-metric-value">{{loading?'—':docs.length}}</view><view class="document-metric-label">全部单据</view></view>
        <view><view class="document-metric-value">{{loading?'—':outCount}}</view><view class="document-metric-label">使用中</view></view>
        <view><view class="document-metric-value">{{loading?'—':draftCount}}</view><view class="document-metric-label">待确认</view></view>
      </view>
    </view>
    <view v-if="role==='ADMIN'||role==='KEEPER'" class="document-toolbar module-enter">
      <navigator class="module-card document-action asset-tile" url="/pages/import/index"><view class="module-symbol"><AppIcon name="upload" /></view><view><view class="module-name">Excel 批量上传</view><view class="module-description">导入并核对设备明细</view></view></navigator>
      <button class="module-card document-action" role="button" tabindex="0" @click="newDocument" @keydown.enter="newDocument" @keydown.space.prevent="newDocument"><view class="module-symbol"><AppIcon name="edit" /></view><view><view class="module-name">手工录入</view><view class="module-description">登记出入库与归还</view></view></button>
    </view>
    <navigator v-if="role==='ADMIN'" class="module-card module-link initial-entry" url="/pages/import/index?kind=INITIAL"><view class="module-symbol"><AppIcon name="asset" /></view><view class="module-copy"><view class="module-name">总库期初导入</view><view class="module-description">建立类别、库存与在外记录</view></view><text class="module-arrow" aria-hidden="true">›</text></navigator>
    <view class="module-section"><text class="module-section-title" role="heading" aria-level="2">单据记录</text><text class="module-section-note" aria-live="polite">{{loading?'加载中…':filtered.length+' 条记录'}}</text></view>
    <view class="filter document-filter">
      <picker :range="types" range-key="name" aria-label="按单据类型筛选" @change="type=types[Number($event.detail.value)].id"><view class="pick">{{types.find(t=>t.id===type)?.name}}</view></picker>
      <picker :range="statuses" range-key="name" aria-label="按单据状态筛选" @change="status=statuses[Number($event.detail.value)].id"><view class="pick">{{statuses.find(t=>t.id===status)?.name}}</view></picker>
    </view>
    <view v-if="loading" class="document-empty" role="status" aria-live="polite">加载单据…</view>
    <template v-else>
      <navigator v-for="d in filtered" :key="d.id" class="module-card document-card" :url="'/pages/document/index?id='+d.id">
        <view class="document-card-top"><view class="document-type-icon"><AppIcon :name="documentIcon(d.type)" /></view><view class="document-number">{{d.number}}</view><text class="document-badge" :class="'status-'+d.status">{{statusNames[d.status]}}</text></view>
        <view class="document-recipient">{{d.crew_name||'库内业务'}}<text v-if="d.group_name!=='未分组'"> · {{d.group_name}}</text></view>
        <view v-if="d.related_crew_name" class="document-related">关联剧组：{{d.related_crew_name}}</view>
        <view class="document-card-footer"><text>{{d.type==='OUTBOUND'&&d.recipient_kind==='COMPANY'?'其他出库':typeNames[d.type]||d.type}} · {{d.business_date}}</text><text class="document-detail">查看单据 <text aria-hidden="true">›</text></text></view>
      </navigator>
      <view v-if="!filtered.length" class="document-empty"><view class="module-symbol"><AppIcon name="document" /></view><view class="module-name">暂无符合条件的单据</view><view class="module-description">可调整类型或状态查看其他记录</view></view>
    </template>
  </view>
</template>
<script setup lang="ts">
import { markPageReady } from '../../session';
onReady(markPageReady);
import { ref, computed } from 'vue';
import { onShow, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, go, typeNames, statusNames } from '../../api';
import AppIcon from '../../components/AppIcon.vue';
const docs = ref<any[]>([]), type = ref(''), status = ref(''), role = ref(''), loading = ref(true);
const types = [{ id: '', name: '全部类型' }, ...Object.entries(typeNames).map(([id, name]) => ({ id, name }))], statuses = [{ id: '', name: '全部状态' }, ...Object.entries(statusNames).map(([id, name]) => ({ id, name }))];
const filtered = computed(() => docs.value.filter(d => (!type.value || d.type === type.value) && (!status.value || d.status === status.value)));
const outCount = computed(() => docs.value.filter(d => d.status==='OUT').length);
const draftCount = computed(() => docs.value.filter(d => d.status==='DRAFT').length);
onShow(async () => {
    loading.value = true;
    try {
        const [records, user] = await Promise.all([api('/documents'), api('/auth/me')]);
        docs.value = records;
        role.value = user.role;
    }
    catch (e) { errorMessage(e); }
    finally { loading.value = false; }
});
function documentIcon(type: string) {
    return ({ PURCHASE:'upload', OUTBOUND:'equipment', RETURN:'inventory', RESERVATION:'calendar', INITIAL:'asset' } as Record<string,string>)[type] || 'document';
}
function newDocument() { uni.showActionSheet({ itemList: ['新购入库', '出库 / 增补', '设备归还', '租期预留'], success: r => go('editor/index?type=' + ['PURCHASE', 'OUTBOUND', 'RETURN', 'RESERVATION'][r.tapIndex]) }); }
</script>
<style scoped>
.document-metrics{display:flex;gap:12px;margin-top:22px;padding-top:18px;border-top:1px solid rgba(255,255,255,.18)}
.document-metrics>view{flex:1;min-width:0}
.document-metric-value{font-size:27px;font-weight:650;line-height:1.3;font-variant-numeric:tabular-nums}
.document-metric-label{font-size:11px;color:#d3e3f8;margin-top:6px}
.document-toolbar{display:flex;gap:12px}
.module-page .document-action{flex:1;min-width:0;padding:17px 13px;text-align:left;line-height:1.7}
.document-action .module-symbol{width:38px;height:38px;border-radius:12px;margin-bottom:11px}
.document-action .module-name{font-size:14px}
.document-action .module-description{font-size:11px}
.module-page .initial-entry{background:#e6eefb;border-color:#d2e1f5;margin-top:12px;min-height:72px;padding:13px 16px}
.initial-entry .module-symbol{background:#d9e7fb}
.initial-entry .module-name{font-size:13px}
.initial-entry .module-description{font-size:11px}
.document-filter{margin:0 0 14px}
.document-filter .pick{border-color:#d7e2f1;color:#3c5677;min-height:44px;background:#f8faff}
.module-page .document-card{padding:17px 16px 0;margin-bottom:12px;border-left:3px solid #b4ccec}
.document-card-top{display:flex;align-items:center;gap:10px}
.document-type-icon{display:flex;align-items:center;justify-content:center;width:32px;height:36px;flex-shrink:0}
.document-number{flex:1;min-width:0;overflow-wrap:anywhere;font-size:14px;font-weight:650;line-height:1.6;font-variant-numeric:tabular-nums}
.document-badge{font-size:10px;color:#41618d;background:#eaf0f8;border-radius:6px;padding:4px 7px;white-space:nowrap;flex-shrink:0}
.document-badge.status-OUT{color:#1b579e;background:#e0edff}
.document-badge.status-DRAFT,.document-badge.status-CANCELLED{color:#5f6c7c;background:#edf0f5}
.document-recipient{font-size:12px;color:#435e7d;margin-top:12px;line-height:1.8;overflow-wrap:anywhere}
.document-related{font-size:11px;color:#536e90;line-height:1.8;margin-top:3px;overflow-wrap:anywhere}
.document-card-footer{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;border-top:1px solid #e7edf6;margin-top:13px;padding:12px 0;font-size:11px;color:#536984;line-height:1.7}
.document-detail{color:#245db5;white-space:nowrap}
.document-empty{text-align:center;background:#eaf0f8;color:#536984;border:1px dashed #cddbeb;border-radius:18px;padding:30px 18px;line-height:1.8}
.document-empty .module-symbol{margin:0 auto 13px}
.document-empty .module-name{font-size:14px}
</style>
