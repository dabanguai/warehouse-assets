<template>
  <view class="page module-page inventory-page">
    <view class="module-hero inventory-dashboard module-enter">
      <view class="inventory-heading">
        <view class="inventory-heading-copy"><view class="module-hero-title" role="heading" aria-level="1">库存</view><view class="module-hero-note">北京 / 横店</view></view>
        <navigator class="inventory-intake" url="/pages/editor/index?type=PURCHASE">＋ 入库</navigator>
      </view>
      <view class="availability-row">
        <view><view class="availability-label">当前可提供</view><view class="availability-number number">{{filtered.filter(e=>count(e,'available')>0).length}} <text class="dashboard-unit">种设备</text></view></view>
        <navigator class="dashboard-link" url="/pages/supply/index"><view class="schedule-link"><view class="schedule-symbol"><AppIcon name="calendar" /></view><text>查未来档期 <text aria-hidden="true">›</text></text></view></navigator>
      </view>
      <view class="status-grid" aria-live="polite">
        <view v-for="s in statuses" :key="s.key" class="status-cell"><view class="status-value number">{{statusSummary[s.key]}}</view><text class="status-label">{{s.label}}</text></view>
      </view>
      <view class="dashboard-note">按当前筛选汇总数量，单位依设备。在库含预留及检查；维修 / 检查统计归还检查记录。</view>
    </view>
    <view class="inventory-actions module-enter">
      <navigator class="module-card inventory-action" url="/pages/import/index"><view class="quick-action-content"><view class="quick-action-symbol"><AppIcon name="upload" /></view><text>Excel 上传</text></view></navigator>
      <navigator class="module-card inventory-action" url="/pages/editor/index?type=OUTBOUND"><view class="quick-action-content"><view class="quick-action-symbol"><AppIcon name="edit" /></view><text>手工出库</text></view></navigator>
      <navigator class="module-card inventory-action" url="/pages/supply/index"><view class="quick-action-content"><view class="quick-action-symbol"><AppIcon name="calendar" /></view><text>租期可供</text></view></navigator>
    </view>
    <view class="inventory-filters module-enter">
      <view class="filter-heading"><text class="module-section-title" role="heading" aria-level="2">筛选设备</text><text class="filter-note">按仓库、归属与类别查询</text></view>
      <view class="inventory-search"><AppIcon name="search" /><input v-model="search" aria-label="搜索品牌、型号、设备或别名" name="inventory-search" autocomplete="off" placeholder="搜索品牌、型号、设备或别名…" /></view>
      <view class="filter">
        <picker :range="['全部仓库','北京','横店']" aria-label="按仓库筛选" @change="wh=Number($event.detail.value)"><view class="pick">{{['全部仓库','北京','横店'][wh]}}</view></picker>
        <picker :range="['两方资产','1号库 · 投资方','2号库 · Boss']" aria-label="按资产归属筛选" @change="owner=Number($event.detail.value)"><view class="pick">{{['两方资产','1号库 · 投资方','2号库 · Boss'][owner]}}</view></picker>
      </view>
      <view class="filter">
        <picker :range="groupOptions" range-key="name" aria-label="按设备大类筛选" @change="pickGroup(Number($event.detail.value))"><view class="pick">{{groupOptions.find(g=>g.id===groupId)?.name}}</view></picker>
        <picker :range="categoryOptions" range-key="name" aria-label="按具体类别筛选" @change="pickCategory(Number($event.detail.value))"><view class="pick">{{categoryOptions.find(c=>c.id===categoryId)?.name||'全部具体类别'}}</view></picker>
      </view>
      <view class="filter review-filter"><picker :range="['全部核对状态','存在差额 / 总数待核实']" aria-label="按核对状态筛选" @change="reviewFilter=Number($event.detail.value)"><view class="pick">{{['全部核对状态','存在差额 / 总数待核实'][reviewFilter]}}</view></picker></view>
    </view>
    <view class="module-section"><text class="module-section-title" role="heading" aria-level="2">设备清单</text><text class="module-section-note" aria-live="polite">{{brandGroups.length}} 个品牌 · {{filtered.length}} 个品种</text></view>
    <view v-if="!items.length" class="inventory-empty"><view class="empty-symbol"><AppIcon name="inventory" /></view><view class="module-name">暂无设备和库存</view><view class="module-description">可上传总库建立期初，或添加设备资料</view><view class="empty-links"><navigator url="/pages/import/index?kind=INITIAL">上传总库，建立期初</navigator><navigator url="/pages/manage/index" open-type="switchTab">到管理页添加设备</navigator></view></view>
    <template v-else>
      <view v-for="brand in brandGroups" :key="brand.key" class="module-card brand-card">
        <button class="brand-toggle" role="button" tabindex="0" :aria-expanded="expandedBrands.includes(brand.key)" :aria-label="brand.name+'，'+brand.modelCount+'个型号'" @click="toggleBrand(brand.key)" @keydown.enter="toggleBrand(brand.key)" @keydown.space.prevent="toggleBrand(brand.key)">
          <view class="brand-heading"><view class="brand-symbol"><AppIcon name="equipment" /></view><text class="brand-name">{{brand.name}}</text></view><view class="brand-count"><text class="number">{{brand.modelCount}} 个型号</text><text class="brand-chevron" :class="expandedBrands.includes(brand.key)?'brand-chevron-open':''" aria-hidden="true">›</text></view>
        </button>
        <view v-if="expandedBrands.includes(brand.key)" class="brand-equipment module-enter">
          <navigator v-for="e in brand.items" :key="e.id" class="equipment-row" :url="'/pages/equipment/index?id='+encodeURIComponent(e.id)">
            <view class="equipment-body">
              <view class="row"><view class="equipment-content"><view class="title">{{e.model?.trim()||e.name}}</view><view class="equipment-category">{{e.model?.trim()?e.name+' · ':''}}{{e.category_name}}</view><view v-if="e.english_name||e.specification" class="equipment-specification">{{[e.english_name,e.specification].filter(Boolean).join(' · ')}}</view><view v-if="e.initialReviews?.some((r:any)=>r.needsReview)" class="badge orange">期初数量待核对</view></view><text class="equipment-arrow" aria-hidden="true">›</text></view>
              <view class="equipment-stats"><view><text class="equipment-quantity number">{{count(e,'available','beijing')}}</text><text class="equipment-stat-label">北京可供</text></view><view><text class="equipment-quantity number">{{count(e,'available','hengdian')}}</text><text class="equipment-stat-label">横店可供</text></view><view><text class="equipment-quantity number">{{count(e,'outside')}}</text><text class="equipment-stat-label">项目占用</text></view></view>
              <view class="equipment-footer"><text>数量单位：{{e.unit}}</text><text>查看设备 <text aria-hidden="true">›</text></text></view>
            </view>
          </navigator>
        </view>
      </view>
      <view v-if="!filtered.length" class="inventory-empty"><view class="empty-symbol"><AppIcon name="search" /></view><view class="module-name">暂无符合条件的设备</view><view class="module-description">可调整搜索词、类别或核对状态</view></view>
    </template>
  </view>
</template>
<script setup lang="ts">
import AppIcon from '../../components/AppIcon.vue';
import { markPageReady } from '../../session';
onReady(markPageReady);
const platform = uni;
import { ref, computed, watch } from 'vue';
import { onShow, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, go } from '../../api';
const items = ref<any[]>([]), search = ref(''), owner = ref(0), wh = ref(0);
const reviewFilter = ref(0);
const statuses = [{ key: 'total', label: '总数' }, { key: 'inStock', label: '在库' }, { key: 'outside', label: '项目占用' }, { key: 'reserved', label: '当前预留' }, { key: 'maintenance', label: '维修 / 检查' }, { key: 'inTransit', label: '调拨在途' }];
const statusSummary = computed(() => Object.fromEntries(statuses.map(s => [s.key, filtered.value.reduce((n, e) => n + count(e, s.key), 0)])));
const meta = ref<any>({}), groupId = ref<string | null>(''), categoryId = ref('');
const groupOptions = computed<any[]>(() => [{ id: '', name: '全部大类' }, { id: null, name: '待归类' }, ...(meta.value.categoryGroups || [])]);
const categoryOptions = computed<any[]>(() => [{ id: '', name: '全部具体类别' }, ...(meta.value.categories || []).filter((c: any) => groupId.value === '' || (c.group_id || null) === groupId.value)]);
const filtered = computed(() => items.value.filter(e => (!reviewFilter.value || e.initialReviews?.some((r:any)=>r.needsReview)) && (groupId.value === '' || (e.group_id || null) === groupId.value) && (!categoryId.value || e.category_id === categoryId.value) && [e.brand, e.model, e.name, e.english_name, e.specification, ...(e.aliases || [])].join(' ').toLowerCase().includes(search.value.trim().toLowerCase())));
const expandedBrands = ref<string[]>([]);
const brandGroups = computed(() => {
    const groups = new Map<string, any[]>();
    for (const item of filtered.value) {
        const key = (item.brand || '').trim();
        if (!groups.has(key)) groups.set(key, []);
        groups.get(key)!.push(item);
    }
    return Array.from(groups, ([key, rows]) => ({ key, name: key || '无品牌', items: rows, modelCount: new Set(rows.map(e => e.model?.trim() || e.name)).size }));
});
// 搜索直接展示命中的设备；清空搜索后恢复品牌收起状态。
watch([brandGroups, () => search.value.trim()], () => {
    expandedBrands.value = search.value.trim() ? brandGroups.value.map(b => b.key) : [];
});
function toggleBrand(key: string) {
    expandedBrands.value = expandedBrands.value.includes(key) ? expandedBrands.value.filter(v => v !== key) : [...expandedBrands.value, key];
}
function pickGroup(i: number) {
    const group = groupOptions.value[i];
    if (!group) return;
    groupId.value = group.id;
    categoryId.value = '';
}
function pickCategory(i: number) {
    const category = categoryOptions.value[i];
    if (!category) return;
    categoryId.value = category.id;
}
function count(e: any, key: string, warehouse = '') { return e.breakdown.filter((r: any) => (!owner.value || r.ownerId === owner.value) && (!warehouse || r.warehouseId === warehouse) && (!wh.value || r.warehouseId === ['', 'beijing', 'hengdian'][wh.value])).reduce((n: number, r: any) => n + r[key], 0); }
onShow(async () => {
    try {
        const [inventory, data, reviews] = await Promise.all([api('/inventory'), api('/meta'), api('/initial-reviews')]);
        meta.value = data;
        items.value = inventory.map((v: any) => ({ ...v, aliases: data.equipment.find((e: any) => e.id === v.id)?.aliases || [], initialReviews: reviews.filter((r:any)=>r.equipmentId===v.id) }));
    }
    catch (e) {
        errorMessage(e);
    }
});
</script>
<style scoped>
.inventory-page .inventory-dashboard{padding:22px 20px 18px;margin-bottom:16px}
.inventory-heading{display:flex;align-items:center;justify-content:space-between;gap:10px}
.inventory-heading-copy{display:flex;align-items:baseline;gap:11px;flex-wrap:wrap;min-width:0}
.inventory-dashboard .module-hero-note{margin-top:0;font-size:11px}
.inventory-intake{box-sizing:border-box;color:#fff;background:rgba(255,255,255,.12);border:1px solid rgba(255,255,255,.2);border-radius:11px;padding:11px 12px;min-height:44px;font-size:12px;flex-shrink:0}
.availability-row{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px}
.availability-label{font-size:12px;color:#d3e3f8;line-height:1.6}
.availability-number{font-size:34px;font-weight:650;line-height:1.4;margin-top:3px}
.dashboard-unit{font-size:12px;font-weight:400}
.dashboard-link{font-size:11px;color:#fff;flex-shrink:0;min-height:44px}
.schedule-link{display:flex;align-items:center;gap:7px;min-height:44px}
.schedule-symbol{display:flex;align-items:center;justify-content:center;width:30px;height:30px;border-radius:10px;background:#e7f0ff}
.schedule-symbol .app-icon{width:19px;height:19px}
.status-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:14px}
.status-cell{background:rgba(255,255,255,.075);border:1px solid rgba(255,255,255,.1);border-radius:11px;padding:8px 9px}
.status-value{font-size:20px;font-weight:600;line-height:1.4;overflow-wrap:anywhere}
.status-label{display:block;font-size:10px;color:#dae8fa;line-height:1.8;margin-top:2px}
.dashboard-note{font-size:10px;line-height:1.8;color:#d3e3f8;margin-top:12px}
.inventory-actions{display:flex;gap:9px;margin-bottom:18px}
.inventory-page .inventory-action{flex:1;min-width:0;padding:13px 5px 12px;text-align:center;background:linear-gradient(160deg,#fff,#f0f5fd)}
.quick-action-content{display:flex;flex-direction:column;align-items:center;gap:8px;font-size:12px;color:#284f80;line-height:1.7}
.quick-action-symbol{display:flex;align-items:center;justify-content:center;width:34px;height:34px;border-radius:11px;background:#e6effd}
.quick-action-symbol .app-icon{width:21px;height:21px}
.inventory-filters{padding:16px;background:#fff;border:1px solid #dce6f3;border-radius:18px;box-shadow:0 4px 14px rgba(30,61,105,.035)}
.filter-heading{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:12px}
.filter-heading .module-section-title{font-size:13px}
.filter-note{font-size:10px;color:#536984;text-align:right;line-height:1.7}
.inventory-search{display:flex;align-items:center;gap:8px;background:#f4f7fc;border:1px solid #dbe5f2;border-radius:11px;padding-left:10px;min-width:0}
.inventory-search .app-icon{width:18px;height:18px}
.inventory-search input{flex:1;min-width:0;width:0;border:0;background:transparent;font-size:12px;min-height:44px;padding:11px 10px 11px 0}
.inventory-search:focus-within{outline:2px solid #3675ce;outline-offset:2px}
.inventory-page .filter{gap:8px;margin:8px 0}
.inventory-page .review-filter{margin-bottom:0}
.inventory-page .pick{min-height:44px;background:#f8faff;border-color:#dbe5f2;color:#3c5677;font-size:11px;padding:12px 9px;overflow-wrap:anywhere}
.inventory-page .brand-card{padding:0 15px;margin-bottom:12px;overflow:hidden}
.brand-toggle{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%;min-height:70px;padding:14px 0;background:transparent;color:#183657;border-radius:0;text-align:left;line-height:1.7}
.brand-heading{display:flex;align-items:center;gap:11px;min-width:0;flex:1}
.brand-symbol{display:flex;align-items:center;justify-content:center;width:36px;height:36px;border-radius:11px;background:#e8f0fc;flex-shrink:0}
.brand-symbol .app-icon{width:22px;height:22px}
.brand-name{min-width:0;overflow-wrap:anywhere;font-size:15px;font-weight:650}
.brand-count{display:flex;align-items:center;gap:10px;color:#536984;font-size:11px;flex-shrink:0}
.brand-chevron{display:block;font-size:22px;line-height:1;transition:transform 160ms ease}
.brand-chevron-open{transform:rotate(90deg)}
.brand-equipment{border-top:1px solid #e4ebf5}
.equipment-row{display:block;border-bottom:1px solid #e4ebf5;color:#183657;line-height:1.7;touch-action:manipulation;transition:opacity 160ms ease}
.equipment-row:last-child{border-bottom:0}
.equipment-body{padding:16px 0}
.equipment-content{flex:1;min-width:0;overflow-wrap:anywhere}
.equipment-content .title{font-size:14px}
.equipment-category{font-size:11px;color:#526b89;line-height:1.8;margin-top:3px}
.equipment-specification{font-size:10px;color:#586e88;line-height:1.8;margin-top:3px}
.equipment-arrow{font-size:22px;color:#6c86a9;flex-shrink:0}
.equipment-stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:12px}
.equipment-stats>view{padding:9px 8px;background:#edf3fc;border-radius:9px}
.equipment-quantity{display:block;font-size:18px;font-weight:600;color:#245b9b;overflow-wrap:anywhere}
.equipment-stat-label{display:block;font-size:10px;color:#536984;margin-top:2px}
.equipment-footer{display:flex;justify-content:space-between;gap:8px;margin-top:10px;font-size:10px;color:#536984}
.equipment-footer>text:last-child{color:#245db5}
.brand-toggle:active,.equipment-row:active{background:#f3f7fd}
.brand-toggle:focus-visible,.equipment-row:focus-visible{outline:2px solid #3675ce;outline-offset:-2px}
.inventory-intake:focus-visible,.dashboard-link:focus-visible{outline:2px solid #fff;outline-offset:2px}
.inventory-empty{padding:27px 18px;text-align:center;background:#eaf0f8;border:1px dashed #cddbeb;border-radius:18px;line-height:1.8}
.inventory-empty .module-name{font-size:14px}
.empty-symbol{display:flex;align-items:center;justify-content:center;width:44px;height:44px;border-radius:14px;background:#dfeafa;margin:0 auto 12px}
.empty-links{display:flex;flex-wrap:wrap;justify-content:center;gap:10px;margin-top:12px;color:#245db5;font-size:12px}
.empty-links>navigator{min-height:44px;padding:10px 0}
/* #ifdef H5 */
.equipment-row :deep(a:focus-visible),.empty-links :deep(a:focus-visible){outline:2px solid #3675ce;outline-offset:2px;border-radius:7px}
.inventory-intake :deep(a:focus-visible),.dashboard-link :deep(a:focus-visible){outline:2px solid #fff;outline-offset:2px;border-radius:7px}
/* #endif */
@media (prefers-reduced-motion:reduce){.brand-chevron,.equipment-row{transition:none}}
</style>
