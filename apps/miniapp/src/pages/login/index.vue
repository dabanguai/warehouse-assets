<template>
  
  <view class="page module-page function-page login-page" style="padding-top:28px">
    <FeatureHeader title="映场" description="器材仓库管理" icon="inventory" tone="strong"><view class="login-locations">北京 / 横店 · 投资方 / Boss资产</view></FeatureHeader>
    <view class="section" role="heading" aria-level="2">登录设备仓管</view>
    <view class="card">
      <view class="field">
        <text class="label">账号</text>
        <input v-model="username" name="username" aria-label="账号" autocomplete="username" placeholder="请输入账号…" />
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input v-model="password" password name="password" aria-label="密码" autocomplete="current-password" placeholder="请输入密码…" @confirm="login" />
      </view>
      <button class="primary full" :loading="busy" :disabled="busy || !ready" @click="login" role="button" tabindex="0" @keydown.enter="login" @keydown.space.prevent="login">登录</button>
    </view>
    <view class="login-help"><AppIcon name="user" /><text>账号请联系管理员开通。</text></view>
  </view>
  
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import AppIcon from '../../components/AppIcon.vue';
import { markPageReady, openInventory } from '../../session';
onReady(markPageReady);
import { onReady } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { api, errorMessage } from '../../api';
const username = ref('admin'), password = ref(''), busy = ref(false);
const ready = ref(false);
onReady(() => {
    ready.value = true;
    if (uni.getStorageSync('token')) {
        busy.value = true;
        openInventory().catch(errorMessage).finally(() => { busy.value = false; });
    }
});
async function login() {
    if (busy.value || !ready.value) return;
    if (!username.value || !password.value)
        return errorMessage(new Error('请填写账号和密码'));
    busy.value = true;
    try {
        const r = await api('/auth/login', 'POST', { username: username.value, password: password.value });
        uni.setStorageSync('token', r.token);
        uni.setStorageSync('user', r.user);
        password.value = '';
        await openInventory();
    }
    catch (e) {
        errorMessage(e);
    }
    finally {
        busy.value = false;
    }
}
</script>
