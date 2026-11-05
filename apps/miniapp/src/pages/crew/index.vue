<template>
  
  <view class="page module-page function-page crew-page">
    <FeatureHeader :title="crew?.name||'剧组详情'" :description="crew?.kind==='COMPANY'?'外借公司':'剧组'" icon="crew" tone="strong">
      <view v-if="crew" class="header-contact"><text v-if="crew.contact?.trim()">{{crew.contact}}</text><view v-else class="pending-info"><AppIcon name="notice" /><text>联系人待补充</text></view></view>
    </FeatureHeader>
    <button v-if="role==='ADMIN'" class="secondary full" @click="go('catalog/index?section=crews&edit='+cid)" role="button" tabindex="0" @keydown.enter="go('catalog/index?section=crews&edit='+cid)" @keydown.space.prevent="go('catalog/index?section=crews&edit='+cid)">编辑剧组资料</button>
    <view class="actions">
      <button class="primary" @click="go('editor/index?type=OUTBOUND&crewId='+cid)" role="button" tabindex="0" @keydown.enter="go('editor/index?type=OUTBOUND&crewId='+cid)" @keydown.space.prevent="go('editor/index?type=OUTBOUND&crewId='+cid)">增补出库</button>
      <button @click="go('import/index?kind=RETURN')" role="button" tabindex="0" @keydown.enter="go('import/index?kind=RETURN')" @keydown.space.prevent="go('import/index?kind=RETURN')">批量归还</button>
    </view>
    <view class="tabs" role="tablist" aria-label="剧组详情内容">
      <button v-for="t in tabs" :key="t.id" role="tab" tabindex="0" :aria-selected="tab===t.id" :class="tab===t.id?'active':''" @click="tab=t.id" @keydown.enter="tab=t.id" @keydown.space.prevent="tab=t.id">{{t.label}}</button>
    </view>
    <picker aria-label="按剧组组别筛选" :range="['全部组别',...(crew?.groups||[])]" @change="group=Number($event.detail.value)">
      <view class="pick">{{group?crew.groups[group-1]:'全部组别'}}</view>
    </picker>
    <template v-if="tab==='sources'">
      <view class="notice">按设备查看累计出库、已还、未还和预留数量。</view>
      <view v-for="e in sourceSummary" :key="e.eid" class="card">
        <view class="title">{{e.name}}</view>
        <view v-for="o in [1,2]" :key="o" class="list-row">
          <view class="muted">{{ownerName(o)}}</view>
          <view class="meta">
            <text>累计出库 {{e.out[o]}}{{e.unit}}</text>
            <text>已还 {{e.returned[o]}}</text>
            <text>未还 {{e.out[o]-e.returned[o]}}</text>
            <text>预留 {{e.reserved[o]}}</text>
          </view>
        </view>
      </view>
      <button class="secondary full" @click="exportSource" role="button" tabindex="0" @keydown.enter="exportSource" @keydown.space.prevent="exportSource">导出来源 Excel</button>
    </template>
    <template v-else-if="tab==='devices'">
      <navigator v-for="e in deviceSummary" :key="e.id" class="card" :url="'/pages/'+('equipment/index?id='+e.id)">
        <view class="title">{{e.name}}</view><view class="meta"><text>在用 {{e.quantity}} {{e.unit}}</text></view>
      </navigator>
      <view v-if="!deviceSummary.length" class="empty">暂无在用设备</view>
    </template>
    <template v-else>
      <navigator v-for="d in filteredDocs" :key="d.id" class="card" :url="'/pages/'+('document/index?id='+d.id)">
        <view class="row">
          <text class="title">{{d.number}}</text>
          <text class="badge" :class="d.status==='OUT'?'state-active':''">{{statusNames[d.status]}}</text>
        </view>
        <view v-if="d.related_crew_id===cid" class="muted">其他出库：{{d.crew_name}}</view>
        <view class="muted">{{typeNames[d.type]}} · {{d.group_name}} · {{d.business_date}}</view>
        <view v-if="d.payload.availableDate" class="muted">再次可供 {{d.payload.availableDate}}</view>
      </navigator>
      <view v-if="!filteredDocs.length" class="empty">暂无记录</view>
    </template>
  </view>
  
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import AppIcon from '../../components/AppIcon.vue';
import { markPageReady } from '../../session';
onReady(markPageReady);
import { ref, computed } from 'vue';
import { onLoad, onShow, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, go, ownerName, statusNames, typeNames, exportFile } from '../../api';
const role = ref('');
const cid = ref(''), crew = ref<any>(), documents = ref<any[]>([]), sources = ref<any[]>([]), tab = ref('devices'), group = ref(0);
const tabs = [{ id: 'devices', label: '使用设备' }, { id: 'sources', label: '资产来源' }, { id: 'schedule', label: '排期' }, { id: 'docs', label: '单据' }];
onLoad((q: any) => { cid.value = q.id; });
onShow(async () => {
    if (!cid.value)
        return;
    try {
        const [cs, ds, ss, user] = await Promise.all([api('/crews'), api('/documents?crewId=' + cid.value), api('/crews/' + cid.value + '/sources'), api('/auth/me')]);
        role.value=user.role;
        crew.value = cs.find((c: any) => c.id === cid.value);
        documents.value = ds;
        sources.value = ss;
    }
    catch (e) {
        errorMessage(e);
    }
});
const filteredDocs = computed(() => documents.value.filter(d => (!group.value || d.group_name === crew.value.groups[group.value - 1]) && (tab.value === 'docs' || tab.value === 'devices' && d.status === 'OUT' || tab.value === 'schedule' && ['OUT', 'RESERVED'].includes(d.status))));
const deviceSummary=computed(()=>{
    const summary:Record<string,any>={};
    for(const a of sources.value){
        if(a.status!=='OUT'||group.value&&a.group_name!==crew.value.groups[group.value-1])continue;
        const remaining=a.quantity-a.returned_quantity;if(remaining<=0)continue;
        const e=summary[a.equipment_id]??={id:a.equipment_id,name:a.name,unit:a.unit,quantity:0};e.quantity+=remaining;
    }
    return Object.values(summary);
});
const sourceSummary = computed(() => {
    const grouped: Record<string, any> = {};
    for (const a of sources.value) {
        if (group.value && a.group_name !== crew.value.groups[group.value - 1] || ['DRAFT', 'CANCELLED'].includes(a.status))
            continue;
        const e = grouped[a.equipment_id] ??= { eid: a.equipment_id, name: a.name, unit: a.unit, out: { 1: 0, 2: 0 }, returned: { 1: 0, 2: 0 }, reserved: { 1: 0, 2: 0 } };
        if (a.status === 'RESERVED')
            e.reserved[a.owner_id] += a.quantity;
        else {
            e.out[a.owner_id] += a.quantity;
            e.returned[a.owner_id] += a.returned_quantity;
        }
    }
    return Object.values(grouped);
});
async function exportSource() {
    try {
        await exportFile('/exports/crew/' + cid.value, {}, crew.value.name + '设备来源');
    }
    catch (e) {
        errorMessage(e);
    }
}
</script>
