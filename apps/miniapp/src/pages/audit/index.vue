<template>
  
  <view class="page module-page function-page audit-page">
<FeatureHeader title="操作及更正记录" description="查看操作人、时间和修改内容" icon="audit" />
    <view v-for="a in records" :key="a.id" class="audit">
      <view class="title">{{actions[a.action]||a.action}}</view>
      <view class="muted small">{{a.username}} · {{new Date(a.created_at).toLocaleString('zh-CN')}}</view>
      <view v-if="a.reason" class="notice">原因：{{a.reason}}</view>
      <view v-if="a.before" class="card">
        <text class="label">更正前</text>
        <view class="muted" style="white-space:pre-wrap">{{summary(a.before)}}</view>
      </view>
      <view v-if="a.after" class="card">
        <text class="label">操作后</text>
        <view class="muted" style="white-space:pre-wrap">{{summary(a.after)}}</view>
      </view>
    </view>
    <view v-if="!records.length" class="empty">暂无操作记录</view>
  </view>
  
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import { markPageReady } from '../../session';
onReady(markPageReady);
import { ref } from 'vue';
import { onShow, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, ownerName, warehouseName, typeNames, statusNames } from '../../api';
const records = ref<any[]>([]), equipment = ref<any[]>([]);
const actions: Record<string, string> = { CONFIRM_INITIAL: '确认期初', CONFIRM_PURCHASE: '确认新购入库', CONFIRM_OUTBOUND: '确认出库', CONFIRM_RESERVATION: '确认预留', CONFIRM_RETURN: '确认归还', CONFIRM_TRANSFER: '调拨发出', TRANSFER_RECEIVE: '调拨接收', CORRECTION: '更正单据', ASSET_CORRECTION: '更正实物归属或编号', EXTEND: '延期', DISPATCH: '预留转出库', CANCEL: '取消草稿或预留', CREATE_EQUIPMENT: '新增设备', EDIT_EQUIPMENT: '编辑设备', SALE: '二手出售', SCRAP: '报废', ADJUST: '盘点调整' };
onShow(async () => {
    try {
        [records.value, equipment.value] = await Promise.all([api('/audit'), api('/equipment')]);
    }
    catch (e) {
        errorMessage(e);
    }
});
function summary(o: any) {
    if (o.allocations)
        return (o.number || '') + ' · ' + (statusNames[o.status] || '') + '\n' + o.allocations.map((a: any) => (equipment.value.find(e => e.id === a.equipment_id)?.name || '设备') + ' / ' + ownerName(a.owner_id) + ' / ' + warehouseName(a.warehouse_id) + ' / 数量 ' + a.quantity + ' / 编号 ' + (a.serial || '无')).join('\n');
    if (o.equipment_id)
        return (equipment.value.find(e => e.id === o.equipment_id)?.name || '设备') + ' / ' + ownerName(o.owner_id) + ' / ' + warehouseName(o.warehouse_id) + ' / 数量 ' + o.quantity + ' / 编号 ' + (o.serial || '无');
    return [o.name, o.specification, o.unit, typeNames[o.type], o.number].filter(Boolean).join(' · ');
}
</script>
