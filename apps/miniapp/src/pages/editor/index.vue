<template>
  
  <view class="page module-page function-page editor-page">
<FeatureHeader :title="correction?'更正单据':typeNames[type]||'业务录入'" :description="correction?'更正后保留原记录和修改原因。':'草稿不影响库存，确认入账后生效。'" icon="edit" />
    <view class="card">
      <view class="entry-subheading">单据资料</view>
      <view class="field">
        <text class="label">单据编号</text>
        <input v-model="number" :disabled="!!record"  :aria-label="'单据编号'" name="number" autocomplete="off"/>
      </view>
      <view class="gap">
        <view class="field">
          <text class="label">业务日期</text>
          <picker mode="date" :value="businessDate" @change="businessDate=$event.detail.value" :aria-label="'业务日期'">
            <view class="pick">{{businessDate}}</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">默认仓库</text>
          <picker :range="['北京','横店']" @change="payload.warehouseId=['beijing','hengdian'][Number($event.detail.value)]" :aria-label="'默认仓库'">
            <view class="pick">{{payload.warehouseId==='hengdian'?'横店':'北京'}}</view>
          </picker>
        </view>
      </view>
      <template v-if="['OUTBOUND','RESERVATION'].includes(type)">
        <view class="field">
          <text class="label">剧组 / 外借公司</text>
          <picker :range="meta.crews||[]" :disabled="!meta.crews?.length" range-key="name" @change="chooseCrew(Number($event.detail.value))" :aria-label="'剧组 / 外借公司'">
            <view class="pick">{{meta.crews?.find((c:any)=>c.id===payload.crewId)?.name||(meta.crews?.length?'请选择剧组':'暂无剧组，请先在管理中新增')}}</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">组别</text>
          <picker :range="groups" @change="payload.groupName=groups[Number($event.detail.value)]" :aria-label="'组别'">
            <view class="pick">{{payload.groupName||'未分组'}}</view>
          </picker>
        </view>
      </template>
      <template v-if="['OUTBOUND','RESERVATION','RETURN'].includes(type)">
        <view v-if="type==='RESERVATION'" class="field">
          <text class="label">计划发货日</text>
          <picker mode="date" :value="payload.startDate" @change="payload.startDate=$event.detail.value" :aria-label="'计划发货日'">
            <view class="pick">{{payload.startDate}}</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">可再次发出日期</text>
          <picker mode="date" :value="payload.availableDate" @change="payload.availableDate=$event.detail.value" :aria-label="'可再次发出日期'">
            <view class="pick">{{payload.availableDate}}</view>
          </picker>
          <view class="muted small">归还当天不可再出库，默认次日可用。</view>
        </view>
      </template>
      <view v-if="correction" class="field">
        <text class="label">更正原因</text>
        <textarea v-model="reason" placeholder="填写修改原因"  :aria-label="'更正原因'" name="reason" autocomplete="off"/>
      </view>
    </view>
    <view class="section row">
      <text>设备明细</text>
      <button v-if="type!=='RETURN'" class="link" @click="addRow" role="button" tabindex="0" @keydown.enter="addRow" @keydown.space.prevent="addRow">＋ 添加</button>
    </view>
    <view v-for="(r,i) in payload.rows" :key="i">
      <RowEditor :row="r" :equipment="meta.equipment||[]" :categories="meta.categories||[]" :category-groups="meta.categoryGroups||[]" :kind="type" :allocation-options="allocationOptions" />
      <button v-if="payload.rows.length>1" class="link" @click="payload.rows.splice(i,1)" role="button" tabindex="0" @keydown.enter="payload.rows.splice(i,1)" @keydown.space.prevent="payload.rows.splice(i,1)">移除本条明细</button>
    </view>
    <view v-if="error" class="notice error">{{error}}</view>
    <view class="sticky">
      <view class="actions">
        <button v-if="!correction" :disabled="busy" @click="save(false)" role="button" tabindex="0" @keydown.enter="save(false)" @keydown.space.prevent="save(false)">保存草稿</button>
        <button class="primary" :loading="busy" :disabled="busy" @click="save(true)" role="button" tabindex="0" @keydown.enter="save(true)" @keydown.space.prevent="save(true)">{{correction?'确认更正':'确认入账'}}</button>
      </view>
    </view>
  </view>
  
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import { markPageReady } from '../../session';
onReady(markPageReady);
import { ref, reactive, computed } from 'vue';
import { onLoad, onReady } from '@dcloudio/uni-app';
import RowEditor from '../../components/RowEditor.vue';
import { api, go, errorMessage, typeNames, addDays, ownerName } from '../../api';
const meta = ref<any>({}), type = ref('PURCHASE'), number = ref(''), businessDate = ref(''), record = ref<any>(null), busy = ref(false), error = ref(''), correction = ref(false), reason = ref(''), allocationOptions = ref<any[]>([]);
const payload = reactive<any>({ rows: [], warehouseId: 'beijing', groupName: '未分组' });
const groups = computed(() => meta.value.crews?.find((c: any) => c.id === payload.crewId)?.groups || ['未分组']);
function chooseCrew(i: number) {
    const crew = meta.value.crews?.[i];
    if (!crew) return;
    payload.crewId = crew.id;
    payload.groupName = groups.value[0];
}
function addRow() { payload.rows.push({ rawName: '', quantity: 1, owner2Quantity: 0, warehouseId: payload.warehouseId, serials: [], note: '' }); }
onLoad(async (q: any) => {
    try {
        meta.value = await api('/meta');
        type.value = q?.type || 'PURCHASE';
        businessDate.value = meta.value.today;
        number.value = type.value + '-' + Date.now();
        payload.startDate = meta.value.today;
        payload.availableDate = addDays(meta.value.today, type.value === 'RETURN' ? 1 : 7);
        if (q?.crewId) {
            payload.crewId = q.crewId;
            payload.groupName = groups.value[0];
        }
        if (q?.id) {
            record.value = await api('/documents/' + q.id);
            type.value = record.value.type;
            number.value = record.value.number;
            businessDate.value = record.value.business_date;
            Object.assign(payload, record.value.payload);
            correction.value = q.correction === '1';
            if (correction.value)
                payload.rows = record.value.allocations.map((a: any) => ({ equipmentId: a.equipment_id, rawName: meta.value.equipment.find((e: any) => e.id === a.equipment_id)?.name, quantity: a.quantity, ownerId: a.owner_id, warehouseId: a.warehouse_id, serials: a.serial ? [a.serial] : [], note: '' }));
        }
        if (type.value === 'RETURN') {
            const docs = await api('/documents?status=OUT');
            const full = await Promise.all(docs.map((d: any) => api('/documents/' + d.id)));
            allocationOptions.value = full.flatMap(d => d.allocations.filter((a: any) => a.quantity > a.returned_quantity).map((a: any) => ({ id: a.id, label: d.number + ' / ' + ownerName(a.owner_id) + ' / ' + (meta.value.equipment.find((e: any) => e.id === a.equipment_id)?.name || '') + ' / 未还' + (a.quantity - a.returned_quantity), ...a })));
            if (q?.outbound) {
                const original = full.find(d => d.id === q.outbound);
                if (original) {
                    payload.rows = original.allocations.filter((a: any) => a.quantity > a.returned_quantity).map((a: any) => ({ equipmentId: a.equipment_id, rawName: meta.value.equipment.find((e: any) => e.id === a.equipment_id)?.name, quantity: a.quantity - a.returned_quantity, ownerId: a.owner_id, allocationId: a.id, serials: a.serial ? [a.serial] : [], warehouseId: 'beijing', note: '' }));
                }
            }
        }
        if (!payload.rows.length)
            addRow();
        if (q?.equipmentId)
            payload.rows[0].equipmentId = q.equipmentId;
    }
    catch (e) {
        errorMessage(e);
    }
});
function body() {
    const p: any = { rows: payload.rows, warehouseId: payload.warehouseId };
    if (['OUTBOUND', 'RESERVATION'].includes(type.value)) {
        p.crewId = payload.crewId;
        p.groupName = payload.groupName;
        p.startDate = type.value === 'OUTBOUND' ? businessDate.value : payload.startDate;
        p.availableDate = payload.availableDate;
    }
    if (type.value === 'RETURN')
        p.availableDate = payload.availableDate;
    if (payload.importId)
        p.importId = payload.importId;
    return p;
}
async function save(confirm: boolean) {
    busy.value = true;
    error.value = '';
    try {
        if (correction.value) {
            if (!reason.value.trim())
                throw Error('请填写更正原因');
            await api('/documents/' + record.value.id + '/corrections', 'POST', { expectedVersion: record.value.version, rows: payload.rows, reason: reason.value });
            go('document/index?id=' + record.value.id);
            return;
        }
        const p = body();
        record.value = record.value ? await api('/documents/' + record.value.id, 'PATCH', { expectedVersion: record.value.version, payload: p }) : await api('/documents', 'POST', { number: number.value, type: type.value, businessDate: businessDate.value, payload: p });
        if (confirm)
            record.value = await api('/documents/' + record.value.id + '/confirm', 'POST', { expectedVersion: record.value.version, idempotencyKey: 'confirm-' + record.value.id + '-' + record.value.version });
        go('document/index?id=' + record.value.id);
    }
    catch (e) {
        error.value = e instanceof Error ? e.message : '操作失败';
    }
    finally {
        busy.value = false;
    }
}
</script>
