<template>
  
  <view class="page module-page function-page supply-page">
<FeatureHeader title="租期可供" description="查询指定租期内的可供数量" icon="calendar" />
    <view class="card">
      <view class="entry-subheading">租期与来源</view>
      <view class="gap">
        <view class="field">
          <text class="label">计划发货日</text>
          <picker mode="date" :value="start" @change="start=$event.detail.value;result=null" :aria-label="'计划发货日'">
            <view class="pick">{{start}}</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">预计归还日</text>
          <picker mode="date" :value="returned" @change="returned=$event.detail.value;result=null" :aria-label="'预计归还日'">
            <view class="pick">{{returned}}</view>
          </picker>
        </view>
      </view>
      <view class="gap">
        <view class="field">
          <text class="label">默认资产来源</text>
          <picker :range="['两方资产','仅1号库','仅2号库']" @change="owner=Number($event.detail.value);result=null" :aria-label="'默认资产来源'">
            <view class="pick">{{['两方资产','仅1号库','仅2号库'][owner]}}</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">默认发货仓</text>
          <picker :range="['北京','横店']" @change="wh=['beijing','hengdian'][Number($event.detail.value)];result=null" :aria-label="'默认发货仓'">
            <view class="pick">{{warehouseName(wh)}}</view>
          </picker>
        </view>
      </view>
      <view class="field">
        <text class="label">剧组 / 公司</text>
        <picker :range="meta.crews||[]" :disabled="!meta.crews?.length" range-key="name" @change="pickCrew(Number($event.detail.value))" :aria-label="'剧组 / 公司'">
          <view class="pick">{{meta.crews?.find((c:any)=>c.id===cid)?.name||(meta.crews?.length?'确认预留前请选择':'暂无剧组，请先在管理中新增')}}</view>
        </picker>
      </view>
      <picker aria-label="选择剧组组别" :range="groups" @change="group=groups[Number($event.detail.value)]">
        <view class="pick">{{group}}</view>
      </picker>
      <view class="muted small" style="margin-top:10px">归还当日仍占用，{{returned?addDays(returned):''}}起再次可供。</view>
    </view>
    <view class="actions">
      <button @click="go('import/index?kind=RESERVATION')" role="button" tabindex="0" @keydown.enter="go('import/index?kind=RESERVATION')" @keydown.space.prevent="go('import/index?kind=RESERVATION')">上传需求 Excel</button>
      <button @click="addRow" role="button" tabindex="0" @keydown.enter="addRow" @keydown.space.prevent="addRow">＋ 添加设备</button>
    </view>
    <view class="section">需求明细</view>
    <view v-for="(r,i) in rows" :key="i" class="card">
      <view class="field">
        <picker aria-label="选择需求设备" :range="meta.equipment||[]" :disabled="!meta.equipment?.length" range-key="name" @change="pickEquipment(r,Number($event.detail.value))">
          <view class="pick">{{meta.equipment?.find((e:any)=>e.id===r.equipmentId)?.name||(meta.equipment?.length?'选择设备':'暂无设备，请先到管理页添加')}}</view>
        </picker>
      </view>
      <view class="gap">
        <input v-model.number="r.quantity" type="digit" placeholder="数量" @input="result=null"  :aria-label="'数量'" name="r-quantity" autocomplete="off"/>
        <picker :range="['默认仓库','北京','横店']" @change="r.warehouseId=['','beijing','hengdian'][Number($event.detail.value)];result=null" :aria-label="'需求仓库'">
          <view class="pick">{{r.warehouseId?warehouseName(r.warehouseId):'默认仓库'}}</view>
        </picker>
      </view>
      <button class="link" @click="rows.splice(i,1);result=null" role="button" tabindex="0" @keydown.enter="rows.splice(i,1);result=null" @keydown.space.prevent="rows.splice(i,1);result=null">移除此需求</button>
    </view>
    <button class="primary full" :loading="busy" :disabled="busy||!rows.length" @click="query" role="button" tabindex="0" @keydown.enter="query" @keydown.space.prevent="query">查询可供数量</button>
    <template v-if="result">
      <view class="notice" :class="result.issue?'warn':''">{{result.issue?.message||'所选租期内数量充足'}}<view class="small">查询时间 {{result.measuredAt}}</view>
      </view>
      <view v-for="(r,i) in result.rows" :key="i" class="card">
        <view class="title">{{r.row.rawName||name(r.row.equipmentId)}}</view>
        <view v-for="l in r.locations" :key="l.ownerId+l.warehouseId" class="list-row">
          <view class="row">
            <text class="muted">{{ownerName(l.ownerId)}} / {{warehouseName(l.warehouseId)}}</text>
            <text>{{l.quantity}} {{unit(r.row.equipmentId)}}</text>
          </view>
          <view v-if="l.dependencies.length" class="notice warn">需前一剧组按时归还</view>
        </view>
      </view>
      <view v-if="result.allocations.length" class="card">
        <view class="title">建议分配</view>
        <view v-for="(a,i) in result.allocations" :key="i" class="list-row">
          <view>{{name(a.equipment_id)}} × {{a.quantity}}</view>
          <view class="muted">{{ownerName(a.owner_id)}} / {{warehouseName(a.warehouse_id)}}</view>
        </view>
      </view>
      <view class="actions">
        <button @click="download" role="button" tabindex="0" @keydown.enter="download" @keydown.space.prevent="download">导出反馈 Excel</button>
        <button class="primary" :disabled="!!result.issue||busy" @click="reserve" role="button" tabindex="0" @keydown.enter="reserve" @keydown.space.prevent="reserve">确认预留</button>
      </view>
    </template>
  </view>
  
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import { markPageReady } from '../../session';
onReady(markPageReady);
import { ref, computed } from 'vue';
import { onLoad, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, go, addDays, ownerName, warehouseName, exportFile } from '../../api';
const meta = ref<any>({}), rows = ref<any[]>([]), start = ref(''), returned = ref(''), owner = ref(0), wh = ref('beijing'), cid = ref(''), group = ref('未分组'), busy = ref(false), result = ref<any>();
const groups = computed(() => meta.value.crews?.find((c: any) => c.id === cid.value)?.groups || ['未分组']);
const name = (id: string) => meta.value.equipment?.find((e: any) => e.id === id)?.name, unit = (id: string) => meta.value.equipment?.find((e: any) => e.id === id)?.unit;
onLoad(async (q: any) => {
    try {
        meta.value = await api('/meta');
        start.value = meta.value.today;
        returned.value = addDays(start.value, 6);
        addRow();
        if (q?.equipmentId)
            rows.value[0].equipmentId = q.equipmentId;
    }
    catch (e) {
        errorMessage(e);
    }
});
function addRow() { rows.value.push({ equipmentId: '', rawName: '', quantity: 1 }); }
function pickCrew(i: number) {
    const crew = meta.value.crews?.[i];
    if (!crew) return;
    cid.value = crew.id;
    group.value = groups.value[0];
}
function pickEquipment(row: any, i: number) {
    const equipment = meta.value.equipment?.[i];
    if (!equipment) return;
    row.equipmentId = equipment.id;
    row.rawName = equipment.name;
    result.value = null;
}
function payload() { return { rows: rows.value.map(r => ({ ...r, ...(owner.value ? { ownerId: owner.value } : {}), ...(r.warehouseId ? {} : { warehouseId: wh.value }) })), warehouseId: wh.value, startDate: start.value, availableDate: addDays(returned.value), ...(cid.value ? { crewId: cid.value, groupName: group.value } : {}) }; }
async function query() {
    busy.value = true;
    try {
        result.value = await api('/availability/query', 'POST', payload());
    }
    catch (e) {
        errorMessage(e);
    }
    finally {
        busy.value = false;
    }
}
async function reserve() {
    busy.value = true;
    try {
        if (!cid.value)
            throw Error('确认预留前请选择剧组或公司');
        const d = await api('/documents', 'POST', { number: 'RES-' + Date.now(), type: 'RESERVATION', businessDate: meta.value.today, payload: payload() });
        await api('/documents/' + d.id + '/confirm', 'POST', { expectedVersion: d.version, idempotencyKey: 'reserve-' + d.id });
        go('document/index?id=' + d.id);
    }
    catch (e) {
        errorMessage(e);
    }
    finally {
        busy.value = false;
    }
}
async function download() {
    try {
        await exportFile('/exports/availability', payload(), '租期供货反馈');
    }
    catch (e) {
        errorMessage(e);
    }
}
</script>
