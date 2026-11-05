<template>
  
  
  
  <view class="card row-editor-panel" :class="row.unmanaged?'':'issue'" style="border-color:#e4eaf3">
    <view class="field">
      <text class="label">原始品名</text>
      <input v-model="row.rawName" placeholder="设备名称"  :aria-label="'原始品名'" name="row-rawName" autocomplete="off"/>
    </view>
    <label class="check">
      <checkbox :checked="!!row.unmanaged" @click="row.unmanaged=!row.unmanaged" />随单记录，不计库存</label>
    <template v-if="!row.unmanaged">
      <view class="field">
        <text class="label">对应设备</text>
        <picker :range="selectableEquipment" :disabled="!selectableEquipment.length" range-key="name" @change="pickEquipment(Number(inputValue($event)))" :aria-label="'对应设备'">
          <view class="pick">{{equipment.find(e=>e.id===row.equipmentId)?.name||(selectableEquipment.length?'选择已有设备':'暂无设备，可勾选下方新增')}}</view>
        </picker>
      </view>
      <label v-if="!row.equipmentId" class="check">
        <checkbox :checked="!!row.createEquipment" @click="row.createEquipment=!row.createEquipment" />新增设备（仅管理员）</label>
      <template v-if="row.createEquipment&&!row.equipmentId">
        <view v-if="kind==='INITIAL'&&row.categoryGroup&&!row.categoryId" class="field">
          <text class="label">总库分类</text>
          <view class="muted">{{row.categoryGroup}} / {{row.category||'其他'}}</view>
        </view>
        <view class="gap">
          <template v-if="kind!=='INITIAL'||!row.categoryGroup||row.categoryId">
          <view class="field">
            <text class="label">设备大类</text>
            <picker :range="groupOptions" range-key="name" @change="pickGroup(Number(inputValue($event)))" :aria-label="'设备大类'">
              <view class="pick">{{groupOptions.find(g=>g.id===selectedGroup)?.name}}</view>
            </picker>
          </view>
          <view class="field">
            <text class="label">具体类别</text>
            <picker :range="categoryOptions" :disabled="!categoryOptions.length" range-key="name" @change="pickCategory(Number(inputValue($event)))" :aria-label="'具体类别'">
              <view class="pick">{{categoryOptions.find(c=>c.id===row.categoryId)?.name||(categoryOptions.length?'请选择具体类别':'请先在管理中新增具体类别')}}</view>
            </picker>
          </view>
          </template>
          <view class="field">
            <text class="label">单位</text>
            <input v-model="row.unit" placeholder="套 / 节 / 米"  :aria-label="'单位'" name="row-unit" autocomplete="off"/>
          </view>
        </view>
        <view v-if="!row.categoryId" class="muted small">未选类别时，归入“{{row.category||'其他'}}”类别。</view>
      </template>
    </template>
    <view class="gap">
      <view class="field">
        <text class="label">{{kind==='INITIAL'?'现有资产数量':'数量'}}</text>
        <input type="digit" :value="kind==='INITIAL'?row.initial?.total??'':row.quantity??''" @input="setQuantity(inputValue($event))"  :aria-label="(kind==='INITIAL'?'现有资产数量':'数量')" name="detail" autocomplete="off"/>
      </view>
      <view v-if="!row.unmanaged&&kind!=='INITIAL'&&kind!=='RETURN'" class="field">
        <text class="label">2号库提供数量</text>
        <input type="digit" :value="two" @input="setTwo(inputValue($event))"  :aria-label="'2号库提供数量'" name="detail" autocomplete="off"/>
      </view>
      <view v-if="kind==='INITIAL'" class="field">
        <text class="label">资产归属</text>
        <picker :range="['1号库 · 投资方','2号库 · Boss']" @change="row.ownerId=Number(inputValue($event))+1" :aria-label="'资产归属'">
          <view class="pick">{{row.ownerId===2?'2号库 · Boss':'1号库 · 投资方'}}</view>
        </picker>
      </view>
    </view>
    <template v-if="!row.unmanaged">
      <template v-if="kind==='INITIAL'&&row.initial">
        <view class="gap">
          <view class="field">
            <text class="label">北京在库</text>
            <input type="digit" :value="row.initial.beijing??''" @input="row.initial.beijing=readNumber(inputValue($event))"  :aria-label="'北京在库'" name="detail" autocomplete="off"/>
          </view>
          <view class="field">
            <text class="label">横店在库</text>
            <input type="digit" :value="row.initial.hengdian??''" @input="row.initial.hengdian=readNumber(inputValue($event))"  :aria-label="'横店在库'" name="detail" autocomplete="off"/>
          </view>
        </view>
        <view v-for="(o,i) in row.initial.outbounds" :key="i" class="card">
          <text class="label">在外去向 {{i+1}}</text>
          <input v-model="o.name" placeholder="《剧组名》A组 或公司名"  :aria-label="'《剧组名》A组 或公司名'" name="o-name" autocomplete="off"/>
          <view class="gap" style="margin-top:8px">
            <input v-model.number="o.quantity" type="digit" placeholder="数量"  :aria-label="'数量'" name="o-quantity" autocomplete="off"/>
            <button class="small-button danger" @click="row.initial.outbounds.splice(i,1)" role="button" tabindex="0" @keydown.enter="row.initial.outbounds.splice(i,1)" @keydown.space.prevent="row.initial.outbounds.splice(i,1)">移除</button>
          </view>
        </view>
        <button class="link" @click="row.initial.outbounds.push({name:'',quantity:0,kind:'CREW'})" role="button" tabindex="0" @keydown.enter="row.initial.outbounds.push({name:'',quantity:0,kind:'CREW'})" @keydown.space.prevent="row.initial.outbounds.push({name:'',quantity:0,kind:'CREW'})">＋ 添加在外去向</button>
        <view class="notice" :class="difference?'warn':''">数量差额：{{difference}}。差额不拦截入账，可在设备清单中继续核对。</view>
      </template>
      <template v-else>
        <view class="field">
          <text class="label">{{kind==='RETURN'?'接收仓':'供货 / 接收仓'}}</text>
          <picker :range="['北京','横店']" @change="row.warehouseId=['beijing','hengdian'][Number(inputValue($event))]" :aria-label="(kind==='RETURN'?'接收仓':'供货 / 接收仓')">
            <view class="pick">{{row.warehouseId==='hengdian'?'横店':'北京'}}</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">实物编号（逗号分隔，保留前导零）</text>
          <input :value="(row.serials||[]).join('，')" @input="row.serials=inputValue($event).split(/[\s,，;；]+/).filter(Boolean)"  :aria-label="'实物编号（逗号分隔，保留前导零）'" name="detail" autocomplete="off"/>
        </view>
        <view v-if="kind==='RETURN'" class="field">
          <text class="label">关联原出库明细</text>
          <picker :range="allocationOptions" :disabled="!allocationOptions.length" range-key="label" @change="selectAllocation(Number(inputValue($event)))" :aria-label="'关联原出库明细'">
            <view class="pick">{{allocationOptions.find(a=>a.id===row.allocationId)?.label||(allocationOptions.length?'请选择原出库及资产来源':'暂无未归还明细，请先核对出库记录')}}</view>
          </picker>
        </view>
      </template>
    </template>
    <view class="field">
      <text class="label">备注（配件不计库存）</text>
      <textarea v-model="row.note" placeholder="填写配件及清点情况"  :aria-label="'备注（配件不计库存）'" name="row-note" autocomplete="off"/>
    </view>
  </view>
  
  
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
const props = defineProps<{
    row: any;
    equipment: any[];
    kind: string;
    allocationOptions?: any[];
    categories?: any[];
    categoryGroups?: any[];
}>();
const row = computed(() => props.row);
const selectableEquipment = computed(() => props.equipment.filter(e => props.kind==='RETURN' || e.active!==0 && e.category_active!==0 && e.group_active!==0));
const allocationOptions = computed(() => props.allocationOptions || []);
const groupOptions = computed<any[]>(() => [{ id: '', name: '待归类' }, ...(props.categoryGroups || []).filter(g=>g.active!==0)]);
const manualGroup = ref('');
const selectedGroup = computed(() => {
    const category = props.categories?.find(c => c.id === row.value.categoryId);
    return category ? (category.group_id || '') : manualGroup.value;
});
const categoryOptions = computed(() => (props.categories || []).filter(c => c.active!==0 && (c.group_id || '') === selectedGroup.value));
function pickGroup(i: number) {
    const group = groupOptions.value[i];
    if (!group) return;
    manualGroup.value = group.id;
    delete row.value.categoryId;
}
function pickCategory(i: number) {
    const category = categoryOptions.value[i];
    if (!category) return;
    row.value.categoryId = category.id;
}
const inputValue = (e: unknown) => (e as {
    detail: {
        value: string;
    };
}).detail.value;
const readNumber = (v: string) => v.trim() === '' ? null : Number(v);
function setQuantity(value: string) {
    const quantity = readNumber(value), initial = row.value.initial;
    if (props.kind === 'INITIAL' && initial) {
        initial.total = quantity;
        row.value.quantity = quantity ?? Number(initial.beijing || 0) + Number(initial.hengdian || 0) + initial.outbounds.reduce((n: number, r: any) => n + Number(r.quantity || 0), 0);
    } else row.value.quantity = quantity;
}
const two = computed(() => {
    const r = row.value;
    if (r.owner2Quantity !== undefined)
        return r.owner2Quantity;
    if (r.ownerId)
        return r.ownerId === 2 ? r.quantity : 0;
    if (r.partNo === '2')
        return r.quantity;
    const m = /^2\*(\d+)$/.exec(String(r.partNo || '').replace(/\s/g, ''));
    return m ? Number(m[1]) : 0;
});
function setTwo(v: string) { row.value.owner2Quantity = readNumber(v); delete row.value.ownerId; delete row.value.partNo; }
function pickEquipment(i: number) {
    const equipment = selectableEquipment.value[i];
    if (!equipment) return;
    row.value.equipmentId = equipment.id;
    row.value.createEquipment = false;
    if (!row.value.rawName)
        row.value.rawName = equipment.name;
    row.value.unit = equipment.unit;
}
function selectAllocation(i: number) {
    const option = allocationOptions.value[i];
    if (!option) return;
    row.value.allocationId = option.id;
    row.value.equipmentId = option.equipment_id;
    row.value.ownerId = option.owner_id;
    row.value.serials = option.serial ? [option.serial] : [];
    delete row.value.owner2Quantity;
    delete row.value.partNo;
}
const difference = computed(() => {
    const r = row.value, v = r.initial;
    if (!v)
        return 0;
    if ([v.total, v.beijing, v.hengdian].some(x => x === null || x === ''))
        return '待核实';
    return r.quantity - v.beijing - v.hengdian - v.outbounds.reduce((n: number, x: any) => n + Number(x.quantity || 0), 0);
});
</script>
