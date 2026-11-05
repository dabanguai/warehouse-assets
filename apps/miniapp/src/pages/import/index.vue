<template>
  
  <view class="page module-page function-page import-page" :class="{ 'with-save-bar': stage===3 }">
<FeatureHeader title="Excel 批量处理" description="支持 .xls、.xlsx，上传后核对设备与来源" icon="upload" />
    <view class="import-steps" aria-live="polite"><view v-for="(label,i) in ['上传文件','选择工作表','逐行核对']" :key="i" class="import-step" :class="stage===i+1?'current':stage>i+1?'complete':''" :aria-current="stage===i+1?'step':undefined"><text class="import-step-number">{{stage>i+1?'✓':i+1}}</text><text>{{label}}</text></view></view>
    <view v-if="stage===1" class="card"><view class="upload-callout"><view class="module-symbol"><AppIcon name="upload" /></view><view><view class="title">上传业务文件</view><view class="muted small">选择文件后核对工作表和设备明细</view></view></view>
      <view class="field">
        <text class="label">业务类型</text>
        <picker :range="kinds" range-key="label" @change="kind=kinds[Number($event.detail.value)].value" :aria-label="'业务类型'">
          <view class="pick">{{typeNames[kind]}}</view>
        </picker>
      </view>
      <view class="notice">新购登记新增资产，归还关联原出库单。期初已包含的历史出库单，请勿重复导入。</view>
      <button class="primary full" :loading="busy" :disabled="busy" @click="uploadExcel" role="button" tabindex="0" @keydown.enter="uploadExcel" @keydown.space.prevent="uploadExcel">选择 .xls / .xlsx 文件</button>
    </view>
    <view v-if="stage===2" class="card">
      <view class="title">{{uploadInfo.filename}}</view>
      <view v-if="uploadInfo.duplicates?.length" class="notice warn">此文件已上传过，请核对原记录是否已入账。</view>
      <view class="field" style="margin-top:15px">
        <text class="label">工作表</text>
        <picker :range="uploadInfo.sheets" range-key="name" @change="sheet=uploadInfo.sheets[Number($event.detail.value)].name" :aria-label="'工作表'">
          <view class="pick">{{sheet}}</view>
        </picker>
      </view>
      <view v-if="kind!=='INITIAL'">
        <view class="field">
          <text class="label">导入模板</text>
          <picker :range="templateOptions" range-key="name" @change="useTemplate(Number($event.detail.value))" :aria-label="'导入模板'">
            <view class="pick">{{selectedTemplate||'自动识别 / 手动映射'}}</view>
          </picker>
        </view>
        <view class="muted">默认按现有出库单读取，格式不同请调整下方列号。</view>
        <view class="gap">
          <view v-for="f in mappingFields" :key="f.key" class="field" style="min-width:29%">
            <text class="label">{{f.label}}</text>
            <input v-model="mapping[f.key]"  :aria-label="(f.label)" name="mapping-f-key-" autocomplete="off"/>
          </view>
        </view>
        <view class="gap">
          <view class="field">
            <text class="label">首条明细行（留空自动）</text>
            <input v-model="startRow" type="number"  :aria-label="'首条明细行（留空自动）'" name="startRow" autocomplete="off"/>
          </view>
          <view class="field">
            <text class="label">末行（留空至表尾）</text>
            <input v-model="endRow" type="number"  :aria-label="'末行（留空至表尾）'" name="endRow" autocomplete="off"/>
          </view>
        </view>
        <input v-model="templateName" placeholder="模板名称（管理员可选填）"  :aria-label="'模板名称（管理员可选填）'" name="templateName" autocomplete="off"/>
      </view>
      <button class="primary full" :loading="busy" :disabled="busy" @click="parse" role="button" tabindex="0" @keydown.enter="parse" @keydown.space.prevent="parse">解析并核对</button>
    </view>
    <template v-if="stage===3">
      <view class="notice">{{uploadInfo.filename}} · {{preview.sheet}} · {{preview.rows.length}} 条明细</view>
      <view v-if="kind==='INITIAL'" class="notice">数量差额不拦截入账。库存按北京、横店及在外数量建立，可在设备清单筛选“存在差额 / 总数待核实”后编辑核对。</view>
      <view v-if="preview.context?.startDate" class="notice warn">原表出库日 {{preview.context.startDate}}，归还日 {{preview.context.returnDate||'未填写'}}。请核对业务日期，期初已含的出库单勿重复入账。</view>
      <view v-if="kind==='INITIAL'" class="card">
        <view class="title">总库分类</view>
        <view class="field" style="margin-top:12px">
          <text class="label">设备大类</text>
          <input v-model="categoryGroup" placeholder="填写设备大类，如机械器材"  :aria-label="'设备大类'" name="categoryGroup" autocomplete="off"/>
        </view>
        <view class="muted">{{categoryNames.length}} 个具体类别：{{categoryNames.join('、')}}</view>
        <view class="muted small" style="margin-top:8px">可先导入类别，库存数量稍后核对。确认期初入账时也会一并建立类别。</view>
        <button class="secondary full" :loading="categoryBusy" :disabled="busy||categoryBusy||!categoryGroup.trim()||!categoryNames.length" @click="saveCategories" role="button" tabindex="0" @keydown.enter="saveCategories" @keydown.space.prevent="saveCategories">导入类别</button>
      </view>
      <view v-if="kind==='INITIAL'" class="card">
        <view class="title">出库去向核对</view>
        <view class="muted small">同一剧组同一组别合为一张单；其他出库按对象独立建单，可关联剧组。</view>
        <view v-for="d in destinations" :key="d.key" class="destination">
          <view class="title">{{d.out.name}}</view>
          <view class="field"><text class="label">出库分类</text>
            <picker :range="['剧组出库','其他出库']" @change="setDestination(d,'kind',Number($event.detail.value)?'OTHER':'CREW')" :aria-label="'出库分类'"><view class="pick">{{outboundTarget(d.out).kind==='OTHER'?'其他出库':'剧组出库'}}</view></picker>
          </view>
          <view class="field"><text class="label">出库对象</text>
            <picker :range="recipientOptions(d.out)" range-key="name" @change="pickRecipient(d,Number($event.detail.value))" :aria-label="'出库对象'"><view class="pick">{{recipientOptions(d.out).find((c:any)=>c.id===d.out.crewId)?.name||'按原表名称建立'}}</view></picker>
          </view>
          <view v-if="outboundTarget(d.out).kind==='CREW'" class="field"><text class="label">组别</text><input :value="outboundTarget(d.out).group" @input="setDestinationGroup(d,$event)"  :aria-label="'组别'" name="detail" autocomplete="off"/></view>
          <view v-else class="field"><text class="label">关联剧组（可选）</text>
            <picker :range="relatedOptions" range-key="name" @change="pickRelated(d,Number($event.detail.value))" :aria-label="'关联剧组（可选）'"><view class="pick">{{relatedOptions.find((c:any)=>c.id===d.out.relatedCrewId)?.name||'未关联'}}</view></picker>
          </view>
        </view>
        <view class="section">预计生成 {{openingPreview.length}} 张出库单</view>
        <view v-for="(d,i) in openingPreview" :key="i" class="list-row"><view>{{destinationName(d)}} · {{d.group}}</view><view class="muted small">{{d.count}} 条设备明细{{d.relatedCrewId?' · 关联 '+meta.crews?.find((c:any)=>c.id===d.relatedCrewId)?.name:''}}</view></view>
      </view>
      <view class="card">
        <view class="field">
          <text class="label">业务单号</text>
          <input v-model="number"  :aria-label="'业务单号'" name="number" autocomplete="off"/>
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
            <picker :range="['北京','横店']" @change="wh=['beijing','hengdian'][Number($event.detail.value)]" :aria-label="'默认仓库'">
              <view class="pick">{{wh==='beijing'?'北京':'横店'}}</view>
            </picker>
          </view>
        </view>
        <template v-if="['OUTBOUND','RESERVATION'].includes(kind)">
          <view class="field">
            <text class="label">剧组 / 公司</text>
            <picker :range="meta.crews||[]" :disabled="!meta.crews?.length" range-key="name" @change="pickCrew(Number($event.detail.value))" :aria-label="'剧组 / 公司'">
              <view class="pick">{{meta.crews?.find((c:any)=>c.id===crewId)?.name||(meta.crews?.length?'请选择':'暂无剧组，请先在管理中新增')}}</view>
            </picker>
          </view>
          <view class="field">
            <text class="label">组别</text>
            <picker :range="groups" @change="group=groups[Number($event.detail.value)]" :aria-label="'组别'">
              <view class="pick">{{group}}</view>
            </picker>
          </view>
        </template>
        <view v-if="['OUTBOUND','RETURN','RESERVATION'].includes(kind)" class="field">
          <text class="label">可再次发出日期</text>
          <picker mode="date" :value="availableDate" @change="availableDate=$event.detail.value" :aria-label="'可再次发出日期'">
            <view class="pick">{{availableDate}}</view>
          </picker>
        </view>
      </view>
      <view class="filter">
        <picker aria-label="筛选导入明细" :range="['全部明细','待匹配','不计库存']" @change="filter=Number($event.detail.value)">
          <view class="pick">{{['全部明细','待匹配','不计库存'][filter]}}</view>
        </picker>
      </view>
      <template v-for="(r,i) in preview.rows" :key="i">
        <view v-if="visible(r)" class="card" :class="!r.equipmentId&&!r.createEquipment&&!r.unmanaged?'issue':''">
          <view class="row">
            <text class="muted small">{{r.sheet}} · 第{{r.sourceRow}}行</text>
            <text class="badge" :class="r.unmanaged?'gray':r.equipmentId?'green':'orange'">{{r.unmanaged?'不计库存':r.equipmentId?'已匹配':r.createEquipment?'待新增':'待匹配'}}</text>
          </view>
          <view class="title" style="margin-top:9px">{{r.rawName}}</view>
          <view v-if="kind==='INITIAL'" class="muted small">{{categoryGroup||r.categoryGroup||'未填写大类'}} / {{r.category}}</view>
          <view class="count-line" :class="(kind==='INITIAL'?r.initial?.total:r.quantity)==null?'pending-line':''">数量 {{(kind==='INITIAL'?r.initial?.total:r.quantity)??'待核实'}} {{r.unit}} · {{sourceLabel(r)}}</view>
          <view v-if="r.issues?.length" class="notice warn">{{r.issues.join('；')}}</view>
          <view v-if="r.note" class="muted" style="white-space:pre-wrap">{{r.note}}</view>
          <view class="row">
            <button class="link" @click="expanded=expanded===i?-1:i" role="button" tabindex="0" @keydown.enter="expanded=expanded===i?-1:i" @keydown.space.prevent="expanded=expanded===i?-1:i">{{expanded===i?'收起':'编辑核对'}}</button>
            <button class="link" @click="r.unmanaged=!r.unmanaged" role="button" tabindex="0" @keydown.enter="r.unmanaged=!r.unmanaged" @keydown.space.prevent="r.unmanaged=!r.unmanaged">{{r.unmanaged?'恢复库存管理':'不计库存'}}</button>
          </view>
          <view v-if="!r.equipmentId&&!r.unmanaged" class="gap">
            <button v-for="c in r.candidates||[]" :key="c.id" class="small-button secondary" @click="r.equipmentId=c.id;r.createEquipment=false" role="button" tabindex="0" @keydown.enter="r.equipmentId=c.id;r.createEquipment=false" @keydown.space.prevent="r.equipmentId=c.id;r.createEquipment=false">{{c.name}}</button>
          </view>
          <RowEditor v-if="expanded===i" :row="r" :equipment="meta.equipment||[]" :categories="meta.categories||[]" :category-groups="meta.categoryGroups||[]" :kind="kind" :allocation-options="allocationOptions" />
        </view>
      </template>
      <view class="save-bar">
        <label v-if="kind==='INITIAL'" class="check">
          <checkbox :checked="ack" @click="ack=!ack" />已核对期初日期、资产归属和数量</label>
        <view class="actions">
          <button :disabled="busy||categoryBusy" @click="save(false)" role="button" tabindex="0" @keydown.enter="save(false)" @keydown.space.prevent="save(false)">保存草稿</button>
          <button class="primary" :disabled="busy||categoryBusy||(kind==='INITIAL'&&!ack)" :loading="busy" @click="save(true)" role="button" tabindex="0" @keydown.enter="save(true)" @keydown.space.prevent="save(true)">确认入账</button>
        </view>
      </view>
    </template>
  </view>
  
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import AppIcon from '../../components/AppIcon.vue';
import {outboundTarget,openingDocuments} from '../../initial-outbounds';
import { markPageReady } from '../../session';
onReady(markPageReady);
import { ref, reactive, computed } from 'vue';
import { onLoad, onReady } from '@dcloudio/uni-app';
import RowEditor from '../../components/RowEditor.vue';
import { api, upload, chooseExcel, errorMessage, typeNames, addDays, go, ownerName, sourceLabel } from '../../api';
const kinds = [{ value: 'INITIAL', label: '期初建账' }, { value: 'PURCHASE', label: '新购入库' }, { value: 'OUTBOUND', label: '日常出库' }, { value: 'RETURN', label: '设备归还' }, { value: 'RESERVATION', label: '剧组需求预留' }];
const stage = ref(1), kind = ref('OUTBOUND'), busy = ref(false), meta = ref<any>({}), uploadInfo = ref<any>({}), preview = ref<any>({ rows: [] }), sheet = ref(''), number = ref(''), businessDate = ref(''), availableDate = ref(''), wh = ref('beijing'), crewId = ref(''), group = ref('未分组'), filter = ref(0), expanded = ref(-1), ack = ref(false), record = ref<any>(null), allocationOptions = ref<any[]>([]), templateName = ref('');
const destinations = computed(()=>{
    const map=new Map<string,any>();
    for(const row of preview.value.rows){
        if(row.unmanaged)continue;
        for(const out of row.initial?.outbounds||[]){
            if(!(out.quantity>0))continue;
            const key=outboundTarget(out).kind+':'+out.name.trim();
            const entry=map.get(key)||{key,out,items:[]};entry.items.push(out);map.set(key,entry);
        }
    }
    return [...map.values()];
});
const openingPreview=computed(()=>openingDocuments(preview.value.rows,meta.value.crews||[]));
const relatedOptions=computed<any[]>(()=>[{id:'',name:'未关联'},...(meta.value.crews||[]).filter((c:any)=>c.kind==='CREW')]);
function recipientOptions(out:any){return [{id:'',name:'按原表名称建立'},...(meta.value.crews||[]).filter((c:any)=>c.kind===(outboundTarget(out).kind==='CREW'?'CREW':'COMPANY'))];}
function setDestination(d:any,field:string,value:any){
    for(const out of d.items){out[field]=value;if(field==='kind'){delete out.crewId;delete out.relatedCrewId;delete out.groupName;}}
}
function setDestinationGroup(d:any,event:any){setDestination(d,'groupName',event.detail.value);}
function pickRecipient(d:any,i:number){const selected=recipientOptions(d.out)[i];if(selected)setDestination(d,'crewId',selected.id);}
function pickRelated(d:any,i:number){const selected=relatedOptions.value[i];if(selected)setDestination(d,'relatedCrewId',selected.id);}
function destinationName(d:any){return meta.value.crews?.find((c:any)=>c.id===d.crewId)?.name||d.name;}
const mapping = reactive<Record<string, string>>({ name: 'B', quantity: 'C', unit: 'D', serials: 'E', note: 'F', partNo: 'A' });
const mappingFields = [{ key: 'name', label: '品名列' }, { key: 'quantity', label: '数量列' }, { key: 'unit', label: '单位列' }, { key: 'serials', label: '编号列' }, { key: 'note', label: '备注列' }, { key: 'partNo', label: '货号列' }];
const templates = ref<any[]>([]), selectedTemplate = ref(''), startRow = ref(''), endRow = ref('');
const categoryGroup = ref(''), categoryBusy = ref(false);
const categoryNames = computed<string[]>(() => [...new Set<string>(preview.value.rows.map((r: any) => r.category?.trim()).filter(Boolean))]);
const templateOptions = computed(() => [{ name: '自动识别 / 手动映射', mapping: { name: 'B', quantity: 'C', unit: 'D', serials: 'E', note: 'F', partNo: 'A' } }, ...templates.value.filter(t => t.kind === kind.value)]);
function useTemplate(i: number) { const t = templateOptions.value[i]; selectedTemplate.value = t.name; Object.assign(mapping, t.mapping); }
const groups = computed(() => meta.value.crews?.find((c: any) => c.id === crewId.value)?.groups || ['未分组']);
onLoad(async (q: any) => {
    try {
        meta.value = await api('/meta');
        templates.value = await api('/templates');
        kind.value = q?.kind || 'OUTBOUND';
        businessDate.value = meta.value.today;
        availableDate.value = addDays(meta.value.today, 7);
        number.value = 'IMPORT-' + Date.now();
        if (q?.id) {
            uploadInfo.value = await api('/imports/' + q.id);
            kind.value = uploadInfo.value.kind;
            if (uploadInfo.value.preview) {
                preview.value = uploadInfo.value.preview;
                stage.value = 3;
            }
            else {
                sheet.value = uploadInfo.value.sheets[0].name;
                stage.value = 2;
            }
        }
        if (q?.documentId) {
            record.value = await api('/documents/' + q.documentId);
            const p = record.value.payload;
            kind.value = record.value.type;
            number.value = record.value.number;
            businessDate.value = record.value.business_date;
            wh.value = p.warehouseId || 'beijing';
            crewId.value = p.crewId || '';
            group.value = p.groupName || '未分组';
            availableDate.value = p.availableDate || availableDate.value;
            uploadInfo.value = await api('/imports/' + p.importId);
            preview.value = { ...uploadInfo.value.preview, rows: p.rows };
            stage.value = 3;
        }
        categoryGroup.value = preview.value.rows.find((r: any) => r.categoryGroup)?.categoryGroup || preview.value.context?.categoryGroup || '';
        await loadReturns();
    }
    catch (e) {
        errorMessage(e);
    }
});
function pickCrew(i: number) {
    const crew = meta.value.crews?.[i];
    if (!crew) return;
    crewId.value = crew.id;
    group.value = groups.value[0];
}
function visible(r: any) { return filter.value === 0 || filter.value === 1 && !r.equipmentId && !r.unmanaged || filter.value === 2 && r.unmanaged; }
async function loadReturns() {
    if (kind.value !== 'RETURN')
        return;
    const docs = await api('/documents?status=OUT'), full = await Promise.all(docs.map((d: any) => api('/documents/' + d.id)));
    allocationOptions.value = full.flatMap(d => d.allocations.filter((a: any) => a.quantity > a.returned_quantity).map((a: any) => ({ ...a, id: a.id, label: d.number + ' / ' + ownerName(a.owner_id) + ' / ' + (meta.value.equipment.find((e: any) => e.id === a.equipment_id)?.name || '') + ' / 未还' + (a.quantity - a.returned_quantity) })));
}
async function uploadExcel() {
    busy.value = true;
    try {
        const file = await chooseExcel();
        uploadInfo.value = await upload('/imports?kind=' + kind.value, file);
        sheet.value = uploadInfo.value.sheets[0].name;
        stage.value = 2;
        await loadReturns();
    }
    catch (e) {
        errorMessage(e);
    }
    finally {
        busy.value = false;
    }
}
async function parse() {
    busy.value = true;
    try {
        if (templateName.value)
            await api('/templates', 'POST', { name: templateName.value, kind: kind.value, mapping });
        preview.value = await api('/imports/' + uploadInfo.value.id + '/parse', 'POST', { sheet: sheet.value, ...(kind.value === 'INITIAL' ? {} : { mapping, ...(startRow.value ? { startRow: Number(startRow.value) } : {}), ...(endRow.value ? { endRow: Number(endRow.value) } : {}) }) });
        categoryGroup.value = preview.value.context?.categoryGroup || '';
        if (!preview.value.rows.length)
            throw Error('未识别到设备明细，请检查工作表、列映射和行范围');
        stage.value = 3;
    }
    catch (e) {
        errorMessage(e);
    }
    finally {
        busy.value = false;
    }
}
async function saveCategories() {
    categoryBusy.value = true;
    try {
        const result = await api('/imports/' + uploadInfo.value.id + '/categories', 'POST', { groupName: categoryGroup.value.trim() });
        meta.value = await api('/meta');
        const group = meta.value.categoryGroups.find((g: any) => g.name === result.groupName);
        for (const row of preview.value.rows) {
            row.categoryGroup = result.groupName;
            if (!row.categoryId)
                row.categoryId = meta.value.categories.find((c: any) => c.name === row.category?.trim() && c.group_id === group.id)?.id;
        }
        uni.showToast({ title: '已导入' + result.categoryCount + '个类别', icon: 'success' });
    }
    catch (e) { errorMessage(e); }
    finally { categoryBusy.value = false; }
}
async function save(confirm: boolean) {
    busy.value = true;
    try {
        const rows = preview.value.rows.map((r: any) => ({ ...r, ...(kind.value === 'INITIAL' ? { categoryGroup: categoryGroup.value.trim() || r.categoryGroup } : {}), quantity: r.unmanaged ? (r.quantity ?? 0) : r.quantity }));
        await api('/imports/' + uploadInfo.value.id + '/rows', 'PATCH', { rows });
        const p: any = { rows, importId: uploadInfo.value.id, warehouseId: wh.value };
        if (['OUTBOUND', 'RESERVATION'].includes(kind.value)) {
            p.crewId = crewId.value;
            p.groupName = group.value;
            p.startDate = businessDate.value;
            p.availableDate = availableDate.value;
        }
        if (kind.value === 'RETURN')
            p.availableDate = availableDate.value;
        record.value = record.value ? await api('/documents/' + record.value.id, 'PATCH', { expectedVersion: record.value.version, payload: p }) : await api('/documents', 'POST', { number: number.value, type: kind.value, businessDate: businessDate.value, payload: p });
        if (confirm)
            record.value = await api('/documents/' + record.value.id + '/confirm', 'POST', { expectedVersion: record.value.version, idempotencyKey: 'import-' + record.value.id + '-' + record.value.version });
        go('document/index?id=' + record.value.id);
    }
    catch (e) {
        errorMessage(e);
    }
    finally {
        busy.value = false;
    }
}
</script>
<style scoped>
.save-bar{position:fixed;left:0;right:0;bottom:0;max-width:780px;margin:0 auto;padding:12px 18px calc(12px + env(safe-area-inset-bottom));background:#f5f7fa;border-top:1px solid #e2e8f1;z-index:3}
.save-bar .check{margin:0 0 10px}
.save-bar .actions{margin-top:0}
</style>
