<template>
  <view class="page module-page crews-page">
    <view class="module-hero module-enter">
      <view class="module-hero-title" role="heading" aria-level="1">剧组</view><view class="module-hero-note">在用设备、归还与排期跟进</view>
      <view class="crew-metrics" aria-live="polite"><view><view class="crew-metric-value number">{{crews.length}}</view><view class="crew-metric-label">全部对象</view></view><view><view class="crew-metric-value number">{{crews.filter(c=>c.kind==='CREW'&&c.status==='ACTIVE').length}}</view><view class="crew-metric-label">使用中剧组</view></view><view><view class="crew-metric-value number">{{crews.filter(c=>c.kind==='COMPANY').length}}</view><view class="crew-metric-label">外借公司</view></view></view>
    </view>
    <navigator v-if="role==='ADMIN'" class="module-card module-link crew-create module-enter" url="/pages/catalog/index?section=crews&create=1"><view class="module-symbol"><AppIcon name="crew" /></view><view class="module-copy"><view class="module-name">新增剧组 / 公司</view><view class="module-description">维护组别与联系人资料</view></view><text class="module-arrow" aria-hidden="true">＋</text></navigator>
    <view class="crew-filters module-enter"><view class="crew-search"><AppIcon name="search" /><input v-model="search" aria-label="搜索剧组或外借公司" name="crew-search" autocomplete="off" placeholder="搜索剧组或外借公司…" /></view><picker :range="['全部状态','筹备中','使用中','已结束']" aria-label="按剧组状态筛选" @change="status=Number($event.detail.value)"><view class="pick">{{['全部状态','筹备中','使用中','已结束'][status]}}</view></picker></view>
    <view class="module-section"><text class="module-section-title" role="heading" aria-level="2">剧组与公司</text><text class="module-section-note" aria-live="polite">{{filtered.length}} 条记录</text></view>
    <navigator v-for="c in filtered" :key="c.id" class="module-card crew-card" :url="'/pages/crew/index?id='+c.id">
      <view class="crew-card-body"><view class="crew-card-heading"><view class="crew-symbol" :class="c.kind==='COMPANY'?'company-symbol':''"><AppIcon :name="c.kind==='COMPANY'?'asset':'crew'" /></view><view class="crew-name">{{c.name}}</view><text class="crew-badge" :class="c.kind==='COMPANY'?'crew-company':'crew-'+c.status">{{c.kind==='COMPANY'?'外借公司':names[c.status]}}</text></view><view v-if="c.groups.length" class="crew-groups"><text v-for="(g,i) in c.groups" :key="i" class="crew-group">{{g}}</text></view><view class="crew-card-footer"><view v-if="c.contact?.trim()" class="crew-contact"><AppIcon name="user" /><text>{{c.contact}}</text></view><view v-else class="crew-contact crew-contact-pending"><AppIcon name="notice" /><text>联系人待补充</text></view><text class="crew-detail">设备与来源 <text aria-hidden="true">›</text></text></view></view>
    </navigator>
    <view v-if="!filtered.length" class="crew-empty"><view class="empty-symbol"><AppIcon name="crew" /></view><view class="module-name">暂无符合条件的剧组</view><view class="module-description">可调整搜索词或状态查看其他记录</view><navigator v-if="role==='ADMIN'" class="crew-empty-create" url="/pages/catalog/index?section=crews&create=1">添加剧组或公司 <text aria-hidden="true">›</text></navigator></view>
  </view>
</template>
<script setup lang="ts">
import AppIcon from '../../components/AppIcon.vue';
import { markPageReady } from '../../session';
onReady(markPageReady);
const platform = uni;
import { ref, computed } from 'vue';
import { onShow, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, go } from '../../api';
const role = ref('');
const crews = ref<any[]>([]), search = ref(''), status = ref(0);
const names: Record<string, string> = { PREPARING: '筹备中', ACTIVE: '使用中', FINISHED: '已结束' };
const filtered = computed(() => crews.value.filter(c => c.name.includes(search.value) && (!status.value || c.status === ['', 'PREPARING', 'ACTIVE', 'FINISHED'][status.value])));
onShow(async () => {
    try {
        const [list,user]=await Promise.all([api('/crews'),api('/auth/me')]);
        crews.value=list; role.value=user.role;
    }
    catch (e) {
        errorMessage(e);
    }
});
</script>
<style scoped>
.crew-metrics{display:flex;gap:12px;border-top:1px solid rgba(255,255,255,.18);padding-top:17px;margin-top:22px}
.crew-metrics>view{flex:1;min-width:0}
.crew-metric-value{font-size:26px;font-weight:650;line-height:1.4;overflow-wrap:anywhere}
.crew-metric-label{font-size:11px;line-height:1.8;color:#d3e3f8;margin-top:3px}
.crews-page .crew-create{background:linear-gradient(130deg,#e5effd,#f3f7fe);border-color:#d3e2f6;margin-bottom:16px;min-height:76px;padding:14px 16px}
.crew-create .module-symbol{background:#d8e7fc}
.crew-create .module-name{font-size:14px}
.crew-create .module-description{font-size:11px}
.crew-filters{padding:15px;background:#fff;border:1px solid #dce6f3;border-radius:18px;box-shadow:0 4px 14px rgba(30,61,105,.035)}
.crew-search{display:flex;align-items:center;gap:9px;border:1px solid #dbe5f2;background:#f4f7fc;border-radius:11px;padding-left:11px;min-width:0;margin-bottom:10px}
.crew-search .app-icon{width:19px;height:19px}
.crew-search input{flex:1;min-width:0;width:0;border:0;background:transparent;min-height:44px;font-size:13px;padding:11px 10px 11px 0}
.crew-search:focus-within{outline:2px solid #3675ce;outline-offset:2px}
.crews-page .pick{min-height:44px;background:#f8faff;border-color:#dbe5f2;color:#3c5677;font-size:12px}
.crews-page .crew-card{margin-bottom:12px;padding:0;border-left:3px solid #b4ccec}
.crew-card-body{padding:17px 16px 0}
.crew-card-heading{display:flex;align-items:center;gap:11px}
.crew-symbol{display:flex;align-items:center;justify-content:center;width:40px;height:40px;border-radius:13px;background:#e7f0fe;flex-shrink:0}
.company-symbol{background:#e9edf7}
.crew-name{flex:1;min-width:0;overflow-wrap:anywhere;font-size:16px;font-weight:650;line-height:1.7}
.crew-badge{display:block;flex-shrink:0;font-size:10px;line-height:1.7;padding:4px 7px;border-radius:7px;white-space:nowrap;background:#eaf0f8;color:#41618d}
.crew-ACTIVE{background:#e0edff;color:#1b579e}
.crew-FINISHED{background:#edf0f5;color:#5f6c7c}
.crew-company{background:#e9edf6;color:#4e6381}
.crew-groups{display:flex;flex-wrap:wrap;gap:6px;margin-top:13px}
.crew-group{font-size:11px;line-height:1.8;color:#486487;background:#f0f5fc;border:1px solid #e1eaf6;padding:3px 9px;border-radius:7px;max-width:100%;overflow-wrap:anywhere}
.crew-card-footer{display:flex;align-items:center;justify-content:space-between;gap:10px;border-top:1px solid #e7edf6;padding:12px 0;margin-top:14px;font-size:11px;color:#536984}
.crew-contact{display:flex;align-items:center;gap:6px;flex:1;min-width:0;line-height:1.8}
.crew-contact .app-icon{width:16px;height:16px}
.crew-contact-pending{flex:0 1 auto;background:#fff4df;color:#8a5400;border:1px solid #efd6aa;border-radius:7px;padding:4px 7px}
.crew-contact>text{min-width:0;overflow-wrap:anywhere}
.crew-detail{white-space:nowrap;color:#245db5;flex-shrink:0}
.crew-empty{text-align:center;background:#eaf0f8;border:1px dashed #cddbeb;border-radius:18px;padding:28px 18px;line-height:1.8}
.crew-empty .module-name{font-size:14px}
.empty-symbol{display:flex;align-items:center;justify-content:center;width:44px;height:44px;border-radius:14px;background:#dfeafa;margin:0 auto 12px}
.crew-empty-create{font-size:12px;color:#245db5;min-height:44px;padding:11px 0;margin-top:8px}
/* #ifdef H5 */
.crew-empty-create :deep(a:focus-visible){outline:2px solid #3675ce;outline-offset:2px;border-radius:7px}
/* #endif */
</style>
