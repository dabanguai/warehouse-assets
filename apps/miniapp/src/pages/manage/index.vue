<template>
  <view class="page module-page">
    <view class="module-hero module-enter">
      <view class="module-hero-title" role="heading" aria-level="1">管理</view>
      <view class="module-hero-note">设备资产、业务单据与剧组协作</view>
      <view class="account-line">
        <view class="account-avatar"><AppIcon name="user" /></view>
        <view class="account-copy"><view>{{user.username||'账号'}}</view><view class="account-role">{{roles[user.role]}}</view></view>
        <button class="account-logout" role="button" tabindex="0" :disabled="loggingOut" @click="logout" @keydown.enter="logout" @keydown.space.prevent="logout">退出登录</button>
      </view>
    </view>
    <view class="module-section"><text class="module-section-title" role="heading" aria-level="2">业务管理</text><text class="module-section-note">资料与业务分开管理</text></view>
    <view class="module-grid module-enter">
      <navigator class="module-card module-tile asset-tile" url="/pages/asset-manage/index">
        <view class="tile-top"><view class="module-symbol"><AppIcon name="asset" /></view><text class="module-arrow" aria-hidden="true">›</text></view>
        <view class="module-name">资产管理</view><view class="module-description">设备、分类与库存</view>
      </navigator>
      <navigator class="module-card module-tile document-tile" url="/pages/documents/index" open-type="switchTab">
        <view class="tile-top"><view class="module-symbol"><AppIcon name="document" /></view><text class="module-arrow" aria-hidden="true">›</text></view>
        <view class="module-name">单据管理</view><view class="module-description">入库、出库与归还</view>
      </navigator>
    </view>
    <view class="module-section"><text class="module-section-title" role="heading" aria-level="2">协作与设置</text></view>
    <view class="module-list module-enter">
      <navigator class="module-card module-link" url="/pages/crews/index" open-type="switchTab">
        <view class="module-symbol"><AppIcon name="crew" /></view><view class="module-copy"><view class="module-name">剧组跟进</view><view class="module-description">使用设备、归还与排期</view></view><text class="module-arrow" aria-hidden="true">›</text>
      </navigator>
      <navigator v-if="user.role==='ADMIN'" class="module-card module-link" url="/pages/catalog/index?section=users">
        <view class="module-symbol"><AppIcon name="users" /></view><view class="module-copy"><view class="module-name">人员管理</view><view class="module-description">系统账号与角色权限</view></view><text class="module-arrow" aria-hidden="true">›</text>
      </navigator>
      <view class="module-card module-link module-disabled" aria-disabled="true">
        <view class="module-symbol"><AppIcon name="logistics" /></view><view class="module-copy"><view class="module-name">物流管理</view><view class="module-description">运输与交接</view></view><text class="module-tag">待开放</text>
      </view>
    </view>
    <navigator class="module-card module-link module-audit" url="/pages/audit/index">
      <view class="module-symbol"><AppIcon name="audit" /></view><view class="module-copy"><view class="module-name">操作及更正记录</view></view><text class="module-arrow" aria-hidden="true">›</text>
    </navigator>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onReady } from '@dcloudio/uni-app';
import { markPageReady, redirectToLogin } from '../../session';
import { api, errorMessage } from '../../api';
import AppIcon from '../../components/AppIcon.vue';
const user = ref<any>({}), loggingOut = ref(false);
const roles: Record<string,string> = { ADMIN: '管理员', KEEPER: '仓管', VIEWER: '查询人员' };
onReady(markPageReady);
onShow(async () => {
    try { user.value = await api('/auth/me'); }
    catch (e) { errorMessage(e); }
});
async function logout() {
    loggingOut.value = true;
    try { await api('/auth/logout', 'POST'); }
    finally {
        uni.removeStorageSync('token');
        uni.removeStorageSync('user');
        redirectToLogin();
        loggingOut.value = false;
    }
}
</script>
