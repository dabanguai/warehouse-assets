<template>
  
  <view class="page module-page function-page document-page" v-if="record">
<FeatureHeader :title="typeNames[record.type]||'单据详情'" :description="record.number+' · 版本 '+record.version" icon="document" tone="strong" />
    <view class="card">
      <view class="row">
        <text class="title">{{meta.crews?.find((c:any)=>c.id===record.crew_id)?.name||'库内业务'}} {{record.group_name!=='未分组'?record.group_name:''}}</text>
        <text class="badge">{{statusNames[record.status]}}</text>
      </view>
      <view v-if="record.related_crew_id" class="muted">关联剧组：{{meta.crews?.find((c:any)=>c.id===record.related_crew_id)?.name}}</view>
      <view v-if="role==='ADMIN'&&record.type==='OUTBOUND'&&meta.crews?.find((c:any)=>c.id===record.crew_id)?.kind==='COMPANY'" class="field">
        <text class="label">关联剧组（保留独立出库单）</text>
        <picker :range="relatedOptions" range-key="name" :disabled="busy" @change="relateCrew(Number($event.detail.value))" :aria-label="'关联剧组（保留独立出库单）'"><view class="pick">{{relatedOptions.find((c:any)=>c.id===record.related_crew_id)?.name||'未关联'}}</view></picker>
      </view>
      <view class="muted">业务日期 {{record.business_date}}</view>
      <view v-if="record.payload.availableDate" class="muted">可再次发出 {{record.payload.availableDate}}</view>
    </view>
    <view class="section">设备与来源</view>
    <view v-for="a in record.allocations" :key="a.id" class="card">
      <view class="title">{{name(a.equipment_id)}}</view>
      <view class="muted">{{ownerName(a.owner_id)}} / {{warehouseName(a.warehouse_id)}} / {{a.serial?'编号 '+a.serial:'按数量管理'}}</view>
      <view class="meta">
        <text>数量 {{a.quantity}}</text>
        <text>已还 {{a.returned_quantity}}</text>
        <text>未还 {{a.quantity-a.returned_quantity}}</text>
      </view>
      <view class="muted small">{{a.start_date}} — <text v-if="a.available_date">再次可供 {{a.available_date}}</text><view v-else class="pending-info"><AppIcon name="notice" /><text>归期待确认</text></view></view>
    </view>
    <view v-if="!record.allocations.length" v-for="(r,i) in record.payload.rows" :key="i" class="card">
      <view class="title">{{name(r.equipmentId)||r.rawName}}</view>
      <view class="muted">{{r.quantity}} {{r.unit||''}} · {{sourceLabel(r)}}</view>
      <view v-if="r.initial" class="muted">北京 {{r.initial.beijing}} · 横店 {{r.initial.hengdian}}</view>
      <view class="muted" style="white-space:pre-wrap">{{r.note}}</view>
    </view>
    <view v-for="(r,i) in record.payload.rows.filter((r:any)=>r.note||r.unmanaged)" :key="'note'+i" class="card">
      <text class="badge gray">{{r.unmanaged?'随单小件':'备注'}} · 不计库存</text>
      <view class="title">{{r.rawName}}</view>
      <view class="muted" style="white-space:pre-wrap">{{r.note}}</view>
    </view>
    <view class="actions" v-if="record.status==='DRAFT'">
      <button @click="edit" role="button" tabindex="0" @keydown.enter="edit" @keydown.space.prevent="edit">继续编辑</button>
      <button class="primary" :disabled="busy" @click="action('confirm')" role="button" tabindex="0" @keydown.enter="action('confirm')" @keydown.space.prevent="action('confirm')">确认入账</button>
    </view>
    <view class="actions" v-if="record.status==='RESERVED'">
      <button class="primary" :disabled="busy" @click="action('dispatch')" role="button" tabindex="0" @keydown.enter="action('dispatch')" @keydown.space.prevent="action('dispatch')">确认发货</button>
      <button @click="action('cancel')" role="button" tabindex="0" @keydown.enter="action('cancel')" @keydown.space.prevent="action('cancel')">取消预留</button>
    </view>
    <view class="actions" v-if="record.status==='OUT'">
      <button class="primary" @click="go('editor/index?type=RETURN&outbound='+record.id)" role="button" tabindex="0" @keydown.enter="go('editor/index?type=RETURN&outbound='+record.id)" @keydown.space.prevent="go('editor/index?type=RETURN&outbound='+record.id)">登记归还</button>
      <button @click="extend" role="button" tabindex="0" @keydown.enter="extend" @keydown.space.prevent="extend">延期</button>
    </view>
    <button v-if="['OUT','RESERVED'].includes(record.status)&&role==='ADMIN'" class="secondary full" @click="go('editor/index?id='+record.id+'&correction=1')" role="button" tabindex="0" @keydown.enter="go('editor/index?id='+record.id+'&correction=1')" @keydown.space.prevent="go('editor/index?id='+record.id+'&correction=1')">更正编号 / 来源 / 数量</button>
    <button v-if="record.type==='TRANSFER'&&record.status==='CONFIRMED'" class="primary full" @click="action('receive')" role="button" tabindex="0" @keydown.enter="action('receive')" @keydown.space.prevent="action('receive')">确认调拨接收</button>
    <button v-if="record.status==='DRAFT'" class="danger full" @click="action('cancel')" role="button" tabindex="0" @keydown.enter="action('cancel')" @keydown.space.prevent="action('cancel')">取消草稿</button>
    <view class="section row">
      <text>交接照片</text>
      <button class="link" @click="addPhoto" role="button" tabindex="0" @keydown.enter="addPhoto" @keydown.space.prevent="addPhoto">＋ 添加</button>
    </view>
    <view class="card">
      <image v-for="p in photos" :key="p.id" :src="p.url" mode="aspectFill" class="photo" alt="交接照片" @click="platform.previewImage({urls:photos.map(p=>p.url),current:p.url})" />
        <view v-if="!photos.length" class="muted">添加交接照片，便于归还时清点。</view>
      </view>
      <button class="full" @click="go('audit/index')" role="button" tabindex="0" @keydown.enter="go('audit/index')" @keydown.space.prevent="go('audit/index')">查看操作及更正记录</button>
      <view v-if="error" class="notice error">{{error}}</view>
    </view>
    
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import AppIcon from '../../components/AppIcon.vue';
import { markPageReady } from '../../session';
onReady(markPageReady);
const platform = uni;
import { ref,computed } from 'vue';
import { onLoad, onShow, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, go, typeNames, statusNames, ownerName, warehouseName, upload, photoUrl, confirm, sourceLabel } from '../../api';
const did = ref(''), record = ref<any>(), meta = ref<any>({}), role = ref(''), photos = ref<any[]>([]), busy = ref(false), error = ref('');
onLoad((q: any) => { did.value = q.id; });
onShow(load);
const relatedOptions=computed<any[]>(()=>[{id:'',name:'未关联'},...(meta.value.crews||[]).filter((c:any)=>c.kind==='CREW')]);
async function relateCrew(i:number){
    const crew=relatedOptions.value[i]; if(!crew)return;
    busy.value=true;
    try{await api('/documents/'+did.value+'/related-crew','PATCH',{expectedVersion:record.value.version,relatedCrewId:crew.id});await load();}
    catch(e){errorMessage(e);}finally{busy.value=false;}
}
const name = (eid: string) => meta.value.equipment?.find((e: any) => e.id === eid)?.name;
async function load() {
    if (!did.value)
        return;
    try {
        [record.value, meta.value] = await Promise.all([api('/documents/' + did.value), api('/meta')]);
        role.value = (await api('/auth/me')).role;
        const ps = await api('/documents/' + did.value + '/attachments');
        photos.value = await Promise.all(ps.map(async (p: any) => ({ ...p, url: await photoUrl(p.id) })));
    }
    catch (e) {
        errorMessage(e);
    }
}
function edit() { go(record.value.payload.importId ? 'import/index?documentId=' + did.value : 'editor/index?id=' + did.value); }
async function action(action: string) {
    if (!await confirm(action === 'confirm' ? '确认入账？库存和排期将按此单更新。' : '确认执行此操作？'))
        return;
    busy.value = true;
    error.value = '';
    try {
        await api('/documents/' + did.value + '/' + action, 'POST', { expectedVersion: record.value.version, ...(action === 'confirm' ? { idempotencyKey: 'doc-' + did.value + '-' + record.value.version } : {}) });
        await load();
    }
    catch (e) {
        error.value = e instanceof Error ? e.message : '操作失败';
    }
    finally {
        busy.value = false;
    }
}
async function extend() {
    uni.showModal({ title: '修改可再次发出日期', editable: true, placeholderText: 'YYYY-MM-DD', success: async (r) => {
            if (!r.confirm || !r.content)
                return;
            try {
                const result = await api('/documents/' + did.value + '/extend', 'POST', { expectedVersion: record.value.version, availableDate: r.content, reason: '剧组延期' });
                await load();
                if (result.conflicts.length)
                    uni.showModal({ title: '发现排期冲突', content: result.conflicts.length + '笔后续预留有冲突，请核对设备档期。', showCancel: false });
            }
            catch (e) {
                errorMessage(e);
            }
        } });
}
async function addPhoto() {
    try {
        const chosen = await new Promise<string>((resolve, reject) => uni.chooseImage({ count: 1, success: r => resolve(r.tempFilePaths[0]), fail: reject }));
        await upload('/documents/' + did.value + '/attachments', chosen);
        await load();
    }
    catch (e) {
        errorMessage(e);
    }
}
</script>
