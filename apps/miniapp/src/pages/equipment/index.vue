<template>
  
  <view class="page module-page function-page equipment-page">
    <FeatureHeader :title="item?.model?.trim()?[item.brand,item.model].filter(Boolean).join(' '):item?.name||'设备详情'" icon="equipment" tone="strong">
      <view v-if="item?.model?.trim()" class="muted">{{item.name}}</view>
      <view class="english">{{item?.english_name}}</view>
      <view v-if="item" class="header-tags">
        <text v-if="item?.group_name" class="badge">{{item.group_name}} / {{item.category_name}}</text><view v-else class="pending-info"><AppIcon name="notice" /><text>待归类 / {{item?.category_name}}</text></view>
        <text v-if="item?.specification?.trim()" class="badge">{{item.specification}}</text><view v-else class="pending-info"><AppIcon name="notice" /><text>规格待补充</text></view>
      </view>
      <view class="metric">
        <view>
          <view class="value">{{item?.total||0}}</view>
          <text class="label">现有资产</text>
        </view>
        <view>
          <view class="value">{{item?.available||0}}</view>
          <text class="label">当前可供</text>
        </view>
        <view>
          <view class="value">{{item?.outside||0}}</view>
          <text class="label">在外数量</text>
        </view>
      </view>
    </FeatureHeader>
    <view v-if="initialReviews.length" class="section">期初数量核对</view>
    <view v-for="r in initialReviews" :key="r.documentId+'-'+r.rowIndex" class="card">
      <view class="row">
        <text class="title">{{ownerName(r.ownerId)}}</text>
        <button v-if="role==='ADMIN'" class="small-button secondary" :disabled="reviewBusy" @click="reviewForm={...r,reason:''}" role="button" tabindex="0" @keydown.enter="reviewForm={...r,reason:''}" @keydown.space.prevent="reviewForm={...r,reason:''}">编辑数量</button>
      </view>
      <view class="muted small">{{r.number}}{{r.sourceRow?' · 第'+r.sourceRow+'行':''}}</view>
      <view class="meta">
        <text>资产总数 {{r.total??'待核实'}}</text><text>北京 {{r.beijing}}</text><text>横店 {{r.hengdian}}</text><text>期初在外 {{r.outside}}</text>
      </view>
      <view class="notice" :class="r.needsReview?'warn':''">{{r.total===null?'资产总数待核实':'期初差额：'+r.difference}}。期初差额不计入可供库存。</view>
      <view v-if="reviewForm?.documentId===r.documentId&&reviewForm?.rowIndex===r.rowIndex">
        <view class="gap">
          <view v-for="f in reviewFields" :key="f.key" class="field">
            <text class="label">{{f.label}}</text>
            <input v-model.number="reviewForm[f.key]" type="digit" :aria-label="f.label"  name="reviewForm-f-key-" autocomplete="off"/>
          </view>
        </view>
        <view class="field">
          <text class="label">核对原因</text>
          <textarea v-model="reviewForm.reason" aria-label="核对原因" placeholder="填写数量核对依据"  name="reviewForm-reason" autocomplete="off"/>
        </view>
        <view class="muted">请填写期初时点数量，分仓改动会同步调整库存。在外数量保留原记录，归还请使用设备归还。</view>
        <view class="actions">
          <button :disabled="reviewBusy" @click="reviewForm=null" role="button" tabindex="0" @keydown.enter="reviewForm=null" @keydown.space.prevent="reviewForm=null">取消</button>
          <button class="primary" :disabled="reviewBusy" :loading="reviewBusy" @click="saveReview" role="button" tabindex="0" @keydown.enter="saveReview" @keydown.space.prevent="saveReview">保存核对</button>
        </view>
      </view>
    </view>
    <button v-if="role==='ADMIN'" class="secondary full" @click="go('catalog/index?section=equipment&edit='+eid)" role="button" tabindex="0" @keydown.enter="go('catalog/index?section=equipment&edit='+eid)" @keydown.space.prevent="go('catalog/index?section=equipment&edit='+eid)">编辑设备资料</button>
    <view class="section">分仓与资产归属</view>
    <view class="card">
      <view v-for="owner in [1,2]" :key="owner" class="list-row">
        <view class="title">{{ownerName(owner)}}</view>
        <view class="meta">
          <text>北京可供 {{qty(owner,'beijing')}}</text>
          <text>横店可供 {{qty(owner,'hengdian')}}</text>
        </view>
      </view>
      <view class="muted small">已预留、在外、在途或待检查的设备，同一档期不能重复安排。</view>
    </view>
    <view class="section">剧组去向与排期</view>
    <view v-if="schedule.conflicts?.length" class="notice error">{{schedule.conflicts.length}} 笔后续预留有冲突，请核对设备档期。</view>
    <navigator v-for="a in schedule.allocations||[]" :key="a.id" class="card" :url="'/pages/'+('document/index?id='+a.document_id)">
      <view class="row">
        <text class="title">{{meta.crews?.find((c:any)=>c.id===a.crew_id)?.name}} · {{a.group_name}}</text>
        <text class="badge" :class="a.status==='OUT'?'state-active':''">{{statusNames[a.status]}}</text>
      </view>
      <view v-if="a.related_crew_id" class="muted">关联剧组：{{meta.crews?.find((c:any)=>c.id===a.related_crew_id)?.name}}</view>
      <view class="meta">
        <text>{{ownerName(a.owner_id)}}</text>
        <text>{{a.quantity-a.returned_quantity}}{{item?.unit}}</text>
      </view>
      <view class="muted">{{a.start_date}} 开始 · <text v-if="a.available_date">再次可供 {{a.available_date}}</text><view v-else class="pending-info"><AppIcon name="notice" /><text>归期待定，暂不计入可供数量</text></view></view>
    </navigator>
    <view v-if="!schedule.allocations?.length" class="card muted">暂无在外或预留安排</view>
    <view class="section">设备明细</view>
    <view v-for="a in assets.filter(a=>a.quantity>0)" :key="a.id" class="card">
      <view class="row">
        <text class="title">{{a.serial?'编号 '+a.serial:'无编号 · 按数量管理'}}</text>
        <button v-if="role!=='VIEWER'" class="link" @click="assetAction(a)" role="button" tabindex="0" @keydown.enter="assetAction(a)" @keydown.space.prevent="assetAction(a)">管理</button>
      </view>
      <view class="muted">{{ownerName(a.owner_id)}} / {{warehouseName(a.warehouse_id)}}</view>
      <view class="meta">
        <text>总量 {{a.quantity}}</text>
        <text>在库 {{a.inStock}}</text>
        <text>可供 {{a.available}}</text>
      </view>
    </view>
    <view class="actions">
      <button @click="go('editor/index?type=PURCHASE&equipmentId='+eid)" role="button" tabindex="0" @keydown.enter="go('editor/index?type=PURCHASE&equipmentId='+eid)" @keydown.space.prevent="go('editor/index?type=PURCHASE&equipmentId='+eid)">新购入库</button>
      <button class="primary" @click="go('supply/index?equipmentId='+eid)" role="button" tabindex="0" @keydown.enter="go('supply/index?equipmentId='+eid)" @keydown.space.prevent="go('supply/index?equipmentId='+eid)">查未来租期</button>
    </view>
  </view>
  
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import AppIcon from '../../components/AppIcon.vue';
import { markPageReady } from '../../session';
onReady(markPageReady);
import { ref } from 'vue';
import { onLoad, onShow, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, go, ownerName, warehouseName, statusNames } from '../../api';
const eid = ref(''), item = ref<any>(), assets = ref<any[]>([]), schedule = ref<any>({}), meta = ref<any>({}), role = ref('');
const initialReviews = ref<any[]>([]), reviewForm = ref<any>(null), reviewBusy = ref(false);
const reviewFields = [{key:'total',label:'资产总数'},{key:'beijing',label:'北京在库'},{key:'hengdian',label:'横店在库'}];
onLoad((q: any) => { eid.value = q.id; });
onShow(load);
async function load() {
    if (!eid.value)
        return;
    try {
        [meta.value, item.value, assets.value, schedule.value, initialReviews.value] = await Promise.all([api('/meta'), api('/inventory?equipmentId=' + eid.value).then(r => r[0]), api('/equipment/' + eid.value + '/assets'), api('/equipment/' + eid.value + '/schedule'), api('/initial-reviews?equipmentId=' + eid.value)]);
        role.value = (await api('/auth/me')).role;
    }
    catch (e) {
        errorMessage(e);
    }
}
async function saveReview() {
    reviewBusy.value = true;
    try {
        const r = reviewForm.value;
        await api('/equipment/' + eid.value + '/initial-reviews/' + r.documentId + '/' + r.rowIndex, 'PATCH', {expectedVersion:r.expectedVersion,total:r.total,beijing:r.beijing,hengdian:r.hengdian,reason:r.reason});
        reviewForm.value = null;
        await load();
        uni.showToast({title:'已保存核对',icon:'success'});
    } catch (e) { errorMessage(e); }
    finally { reviewBusy.value = false; }
}
function qty(owner: number, wh: string) { return item.value?.breakdown.find((x: any) => x.ownerId === owner && x.warehouseId === wh)?.available || 0; }
const ask = (title: string, value = '') => new Promise<string | null>(r => uni.showModal({ title, editable: true, content: value, success: e => r(e.confirm ? (e.content || '') : null) }));
const pick = (items: string[]) => new Promise<number | null>(r => uni.showActionSheet({ itemList: items, success: e => r(e.tapIndex), fail: () => r(null) }));
async function assetAction(a: any) {
    try {
        const action = await pick(['更正编号 / 资产归属', '二手出售', '报废', '盘点调整', '调拨']);
        if (action === null)
            return;
        if (action === 0) {
            const serial = await ask('实物编号（可为空）', a.serial || '');
            if (serial === null)
                return;
            const owner = await pick(['1号库 · 投资方', '2号库 · Boss']);
            if (owner === null)
                return;
            const reason = await ask('更正原因');
            if (!reason)
                return;
            await api('/assets/' + a.id, 'PATCH', { serial, ownerId: owner + 1, reason });
        }
        else if (action === 4) {
            const target = await pick(['调往北京', '调往横店']);
            if (target === null)
                return;
            const quantity = await ask('调拨数量', '1');
            if (quantity === null)
                return;
            const d = await api('/documents', 'POST', { number: 'TRANSFER-' + Date.now(), type: 'TRANSFER', businessDate: meta.value.today, payload: { rows: [{ equipmentId: eid.value, assetId: a.id, quantity: Number(quantity) }], targetWarehouseId: ['beijing', 'hengdian'][target] } });
            await api('/documents/' + d.id + '/confirm', 'POST', { expectedVersion: d.version, idempotencyKey: 'transfer-' + d.id });
            go('document/index?id=' + d.id);
        }
        else {
            const quantity = await ask(action === 3 ? '实盘在库数量' : '处置数量', String(action === 3 ? a.inStock : 1));
            if (quantity === null)
                return;
            const reason = await ask(action === 1 ? '买方及出售说明' : '原因');
            if (!reason)
                return;
            await api('/assets/' + a.id + '/actions', 'POST', { action: action === 1 ? 'SALE' : action === 2 ? 'SCRAP' : 'ADJUST', quantity: Number(quantity), reason });
        }
        await load();
        uni.showToast({ title: '已保存', icon: 'success' });
    }
    catch (e) {
        errorMessage(e);
    }
}
</script>
