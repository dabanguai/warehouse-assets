<template>
  
  <view class="page module-page function-page catalog-page">
<FeatureHeader :title="pageTitle" :description="section==='users'?'系统账号与角色':section==='crews'?'剧组与外借公司资料':'基础资料维护'" :icon="section==='users'?'users':section==='crews'?'crew':section==='equipment'?'equipment':'category'" />
    <view v-if="user.role!=='ADMIN'" class="notice">基础资料和人员由管理员维护。</view>
    <template v-if="user.role==='ADMIN'">
      <view v-if="section==='categories'" class="notice">基础资料可手动维护，也可从总库导入。已有业务记录的资料可停用。</view>
      <view v-if="visibleTabs.length>1" class="tabs" style="gap:14px;flex-wrap:wrap">
        <button v-for="t in visibleTabs" :key="t.id" :class="mode===t.id?'active':''" @click="selectMode(t.id)" role="button" tabindex="0" @keydown.enter="selectMode(t.id)" @keydown.space.prevent="selectMode(t.id)">{{t.label}}</button>
      </view>
      <view class="manage-toolbar">
        <input v-if="mode!=='categoryGroups'" v-model="search" :aria-label="'搜索'+modeNames[mode]" name="manage-search" :placeholder="'搜索'+modeNames[mode]+'…'"  autocomplete="off"/>
        <button class="primary" @click="openForm()" role="button" tabindex="0" @keydown.enter="openForm()" @keydown.space.prevent="openForm()">＋ 新增{{modeNames[mode]}}</button>
      </view>
      <view v-if="mode==='categories'||mode==='equipment'" class="filter">
        <picker :range="filterGroups" range-key="name" aria-label="按设备大类筛选" @change="filterGroup=filterGroups[Number($event.detail.value)].id;filterCategory=''">
          <view class="pick">{{filterGroups.find(g=>g.id===filterGroup)?.name}}</view>
        </picker>
        <picker v-if="mode==='equipment'" :range="filterCategories" range-key="name" aria-label="按具体类别筛选" @change="filterCategory=filterCategories[Number($event.detail.value)].id">
          <view class="pick">{{filterCategories.find(c=>c.id===filterCategory)?.name}}</view>
        </picker>
      </view>
      <view v-for="r in records" :key="r.id" class="card" style="margin-top:12px">
        <view class="row catalog-record-row">
          <view class="record-content">
            <button v-if="mode==='categories'||mode==='categoryGroups'" class="category-link title" :aria-label="'查看'+r.name+'关联设备'" @click="showEquipment(r)" role="button" tabindex="0" @keydown.enter="showEquipment(r)" @keydown.space.prevent="showEquipment(r)">{{r.name}} <text class="link">查看设备 ›</text></button>
            <text v-else class="title">{{mode==='equipment'&&r.model?.trim()?[r.brand,r.model].filter(Boolean).join(' '):r.name||r.username}}</text>
            <view v-if="mode==='equipment'&&r.model?.trim()" class="muted">{{r.name}}</view>
            <view class="muted" :class="['equipment','categories'].includes(mode)&&!r.group_name?'pending-line':''">{{mode==='equipment'?(r.group_name||'待归类')+' / '+r.category_name+' · '+r.unit+(r.active===0?' · 已停用':''):mode==='categories'?(r.group_name||'待归类')+' · '+(r.active===0?'已停用':'启用'):mode==='categoryGroups'?meta.categories.filter((c:any)=>c.group_id===r.id).length+'个具体类别 · '+(r.active===0?'已停用':'启用'):mode==='crews'?r.groups.join(' / '):roles[r.role]}}</view>
          </view>
          <button v-if="mode==='equipment'" class="edit-button" @click="go('equipment/index?id='+r.id)" role="button" tabindex="0" @keydown.enter="go('equipment/index?id='+r.id)" @keydown.space.prevent="go('equipment/index?id='+r.id)">详情</button>
          <button v-if="mode!=='users'" class="edit-button" @click="openForm(r)" role="button" tabindex="0" @keydown.enter="openForm(r)" @keydown.space.prevent="openForm(r)">编辑</button>
          <button v-if="['equipment','categories','categoryGroups'].includes(mode)" class="edit-button danger" :disabled="busy" @click="remove(r)" role="button" tabindex="0" @keydown.enter="remove(r)" @keydown.space.prevent="remove(r)">删除</button>
        </view>
        <view v-if="mode==='equipment'&&r.aliases.length" class="muted">别名：{{r.aliases.join(' / ')}}</view>
      </view>
      <view v-if="!records.length" class="empty">{{search||filterGroup!==''||filterCategory?'暂无符合条件的'+modeNames[mode]:'暂无'+modeNames[mode]+'，点击上方新增'}}</view>
      <view v-if="form" class="card" id="edit-form">
        <view class="section" style="margin-top:0">{{form.id?'编辑':'新增'}}{{modeNames[mode]}}</view>
        <view class="field">
          <text class="label">{{mode==='users'?'账号':'名称'}}</text>
          <input v-model="form.name"  :aria-label="(mode==='users'?'账号':'名称')" name="form-name" autocomplete="off"/>
        </view>
        <view v-if="mode==='categories'" class="field">
          <text class="label">所属设备大类</text>
          <picker :range="groupOptions" range-key="name" @change="pickCategoryGroup(Number($event.detail.value))" :aria-label="'所属设备大类'">
            <view class="pick">{{groupOptions.find(g=>g.id===form.groupId)?.name}}</view>
          </picker>
          <view v-if="!meta.categoryGroups?.length" class="muted small">请先添加设备大类，也可暂存为“待归类”。</view>
        </view>
        <template v-if="mode==='equipment'">
          <view class="field">
            <text class="label">设备大类</text>
            <picker :range="groupOptions" range-key="name" @change="pickEquipmentGroup(Number($event.detail.value))" :aria-label="'设备大类'">
              <view class="pick">{{groupOptions.find(g=>g.id===form.categoryGroupId)?.name}}</view>
            </picker>
          </view>
          <view class="field">
            <text class="label">具体类别</text>
            <picker :range="equipmentCategories" :disabled="!equipmentCategories.length" range-key="name" @change="pickCategory(Number($event.detail.value))" :aria-label="'具体类别'">
              <view class="pick">{{equipmentCategories.find(c=>c.id===form.categoryId)?.name||(equipmentCategories.length?'请选择具体类别':'此大类下暂无类别，请先添加')}}</view>
            </picker>
          </view>
          <view class="field">
            <text class="label">英文名称</text>
            <input v-model="form.englishName"  :aria-label="'英文名称'" name="form-englishName" autocomplete="off"/>
          </view>
          <view class="gap">
            <view class="field">
              <text class="label">品牌</text>
              <input v-model="form.brand" aria-label="品牌" placeholder="例如 DJI…"  name="form-brand" autocomplete="off"/>
            </view>
            <view class="field">
              <text class="label">型号</text>
              <input v-model="form.model" aria-label="型号" placeholder="例如 RS 4 Pro…"  name="form-model" autocomplete="off"/>
            </view>
          </view>
          <view class="field">
            <text class="label">单位</text>
            <input v-model="form.unit" aria-label="单位"  name="form-unit" autocomplete="off"/>
          </view>
          <view class="field">
            <text class="label">规格</text>
            <input v-model="form.specification"  :aria-label="'规格'" name="form-specification" autocomplete="off"/>
          </view>
          <view class="field">
            <text class="label">别名（逗号分隔）</text>
            <input v-model="form.aliasText"  :aria-label="'别名（逗号分隔）'" name="form-aliasText" autocomplete="off"/>
          </view>
          <view class="notice">添加设备后，还需办理入库登记数量。</view>
        </template>
        <template v-if="mode==='crews'">
          <view class="field">
            <text class="label">对象类型</text>
            <picker :range="['剧组','外借公司']" @change="form.kind=Number($event.detail.value)?'COMPANY':'CREW'" :aria-label="'对象类型'">
              <view class="pick">{{form.kind==='COMPANY'?'外借公司':'剧组'}}</view>
            </picker>
          </view>
          <view class="field">
            <text class="label">组别（逗号分隔）</text>
            <input v-model="form.groupText"  :aria-label="'组别（逗号分隔）'" name="form-groupText" autocomplete="off"/>
          </view>
          <view class="gap">
            <view class="field">
              <text class="label">联系人</text>
              <input v-model="form.contact"  :aria-label="'联系人'" name="form-contact" autocomplete="off"/>
            </view>
            <view class="field">
              <text class="label">联系电话</text>
              <input v-model="form.phone"  :aria-label="'联系电话'" name="form-phone" autocomplete="off"/>
            </view>
          </view>
          <picker :range="['筹备中','使用中','已结束']" @change="form.status=['PREPARING','ACTIVE','FINISHED'][Number($event.detail.value)]" :aria-label="'剧组状态'">
            <view class="pick">{{crewStatuses[form.status]}}</view>
          </picker>
        </template>
        <template v-if="mode==='users'">
          <view class="field">
            <text class="label">密码（至少12位）</text>
            <input v-model="form.password" password  :aria-label="'密码（至少12位）'" name="form-password" autocomplete="off"/>
          </view>
          <picker :range="['管理员','仓管','查询人员']" @change="form.role=['ADMIN','KEEPER','VIEWER'][Number($event.detail.value)]" :aria-label="'账号角色'">
            <view class="pick">{{roles[form.role]}}</view>
          </picker>
        </template>
        <label v-if="['equipment','categories','categoryGroups'].includes(mode)" class="check">
          <checkbox :checked="form.active" @click="form.active=!form.active" />启用</label>
        <view class="actions">
          <button @click="form=null" role="button" tabindex="0" @keydown.enter="form=null" @keydown.space.prevent="form=null">取消</button>
          <button class="primary" :loading="busy" :disabled="busy" @click="save" role="button" tabindex="0" @keydown.enter="save" @keydown.space.prevent="save">保存</button>
        </view>
        <view v-if="error" class="notice error">{{error}}</view>
      </view>
    </template>

  </view>
  
</template>
<script setup lang="ts">
import FeatureHeader from '../../components/FeatureHeader.vue';
import { markPageReady, redirectToLogin } from '../../session';
onReady(markPageReady);
import { ref, computed, nextTick } from 'vue';
import { onLoad, onShow, onReady } from '@dcloudio/uni-app';
import { api, errorMessage, go, confirm } from '../../api';
const user = ref<any>({}), meta = ref<any>({}), users = ref<any[]>([]), mode = ref('categoryGroups'), form = ref<any>(), busy = ref(false), error = ref('');
const roles: Record<string, string> = { ADMIN: '管理员', KEEPER: '仓管', VIEWER: '查询人员' }, crewStatuses: Record<string, string> = { PREPARING: '筹备中', ACTIVE: '使用中', FINISHED: '已结束' }, modeNames: Record<string, string> = { categoryGroups: '设备大类', categories: '具体类别', equipment: '设备', crews: '剧组 / 公司', users: '账号' };
const tabs = [{ id: 'categoryGroups', label: '设备大类' }, { id: 'categories', label: '具体类别' }, { id: 'equipment', label: '设备' }, { id: 'crews', label: '剧组' }, { id: 'users', label: '人员' }];
const section = ref('categories'), requestedEdit = ref(''), requestedNew = ref(false);
const pageTitle = computed(() => ({equipment:'设备资料',categories:'分类管理',users:'人员管理',crews:'剧组资料'}[section.value] || '基础资料'));
const visibleTabs = computed(() => tabs.filter(t => section.value==='categories' ? ['categoryGroups','categories','equipment'].includes(t.id) : t.id===section.value));
onLoad((q: any) => {
    section.value = ['equipment','categories','users','crews'].includes(q?.section) ? q.section : 'categories';
    mode.value = section.value==='categories' ? 'categoryGroups' : section.value;
    requestedEdit.value = q?.edit || '';
    requestedNew.value = q?.create==='1';
});
const groupOptions = computed<any[]>(() => [{ id: '', name: '待归类' }, ...(meta.value.categoryGroups || [])]);
const equipmentCategories = computed<any[]>(() => (meta.value.categories || []).filter((c: any) => (c.group_id || '') === form.value?.categoryGroupId));
const search = ref(''), filterGroup = ref<string | null>(''), filterCategory = ref('');
const filterGroups = computed<any[]>(() => [{ id: '', name: '全部设备大类' }, { id: null, name: '待归类' }, ...(meta.value.categoryGroups || [])]);
const filterCategories = computed<any[]>(() => [{ id: '', name: '全部具体类别' }, ...(meta.value.categories || []).filter((c: any) => filterGroup.value === '' || (c.group_id || null) === filterGroup.value)]);
const records = computed(() => {
    const rows = mode.value === 'users' ? users.value : meta.value[mode.value] || [];
    return rows.filter((r: any) => {
        if (['categories', 'equipment'].includes(mode.value) && filterGroup.value !== '' && (r.group_id || null) !== filterGroup.value) return false;
        if (mode.value === 'equipment' && filterCategory.value && r.category_id !== filterCategory.value) return false;
        return [r.name, r.username, r.english_name, r.specification, r.brand, r.model, ...(r.aliases || [])].join(' ').toLowerCase().includes(search.value.trim().toLowerCase());
    });
});
function selectMode(value: string) {
    mode.value = value;
    form.value = null;
    search.value = '';
    filterGroup.value = '';
    filterCategory.value = '';
}
function showEquipment(record: any) {
    const isCategory = mode.value === 'categories';
    selectMode('equipment');
    filterGroup.value = isCategory ? (record.group_id || null) : record.id;
    filterCategory.value = isCategory ? record.id : '';
}
function pickCategory(i: number) {
    const category = equipmentCategories.value[i];
    if (!category) return;
    form.value.categoryId = category.id;
}
function pickCategoryGroup(i: number) {
    const group = groupOptions.value[i];
    if (!group) return;
    form.value.groupId = group.id;
}
function pickEquipmentGroup(i: number) {
    const group = groupOptions.value[i];
    if (!group) return;
    form.value.categoryGroupId = group.id;
    form.value.categoryId = '';
}
onShow(load);
async function load() {
    try {
        [meta.value, user.value] = await Promise.all([api('/meta'), api('/auth/me')]);
        if (user.value.role === 'ADMIN')
            users.value = section.value==='users' ? await api('/users') : [];
        if (user.value.role==='ADMIN' && (requestedEdit.value || requestedNew.value)) {
            const record = requestedEdit.value ? (meta.value[mode.value] || []).find((r: any) => r.id===requestedEdit.value) : undefined;
            if (requestedEdit.value && !record) throw Error('资料不存在，请返回列表重新选择');
            requestedEdit.value = ''; requestedNew.value = false;
            await openForm(record);
        }
    }
    catch (e) {
        errorMessage(e);
    }
}
async function openForm(r?: any) {
    error.value = '';
    const category = meta.value.categories?.find((c: any) => c.id === r?.category_id) || meta.value.categories?.[0];
    form.value = { id: r?.id, name: r?.name || r?.username || '', englishName: r?.english_name || '', categoryId: r?.category_id || category?.id || '', categoryGroupId: category?.group_id || '', groupId: r ? (r.group_id || '') : (meta.value.categoryGroups?.[0]?.id || ''), brand: r?.brand || '', model: r?.model || '', specification: r?.specification || '', unit: r?.unit || '套', aliasText: r?.aliases?.join('，') || '', active: r?.active !== 0, kind: r?.kind || 'CREW', groupText: r?.groups?.join('，') || 'A组，B组', contact: r?.contact || '', phone: r?.phone || '', status: r?.status || 'PREPARING', role: 'VIEWER', password: '' };
    await nextTick();
    uni.pageScrollTo({ selector: '#edit-form', duration: 200 });
}
async function save() {
    busy.value = true;
    error.value = '';
    try {
        const f = form.value;
        let body: any;
        if (mode.value === 'equipment' && !f.categoryId)
            throw Error('请先新增并选择具体类别');
        if (mode.value === 'equipment')
            body = { name: f.name, englishName: f.englishName, categoryId: f.categoryId, brand: f.brand, model: f.model, specification: f.specification, unit: f.unit, aliases: f.aliasText.split(/[,，]/).map((v: string) => v.trim()).filter(Boolean), active: f.active };
        else if (mode.value === 'categories')
            body = { name: f.name, active: f.active, groupId: f.groupId || null };
        else if (mode.value === 'categoryGroups')
            body = { name: f.name, active: f.active };
        else if (mode.value === 'crews')
            body = { name: f.name, kind: f.kind, contact: f.contact, phone: f.phone, status: f.status, groups: f.groupText.split(/[,，]/).map((v: string) => v.trim()).filter(Boolean) };
        else
            body = { username: f.name, password: f.password, role: f.role };
        await api('/' + (mode.value === 'categoryGroups' ? 'category-groups' : mode.value) + (f.id ? '/' + f.id : ''), f.id ? 'PATCH' : 'POST', body);
        form.value = null;
        await load();
        uni.showToast({ title: '已保存', icon: 'success' });
    }
    catch (e) {
        error.value = e instanceof Error ? e.message : '保存失败';
    }
    finally {
        busy.value = false;
    }
}
async function remove(record: any) {
    if (!await confirm('删除“'+record.name+'”？已有业务引用的资料无法删除，可使用停用。')) return;
    busy.value = true;
    try {
        const resource = mode.value==='categoryGroups' ? 'category-groups' : mode.value;
        await api('/'+resource+'/'+record.id, 'DELETE');
        if (form.value?.id===record.id) form.value = null;
        await load();
        uni.showToast({title:'已删除',icon:'success'});
    } catch (e) { errorMessage(e); }
    finally { busy.value = false; }
}

</script>
<style scoped>
.manage-toolbar{display:flex;align-items:center;justify-content:space-between;gap:10px}
.manage-toolbar input{flex:1;min-width:0}
.manage-toolbar button{flex-shrink:0;padding:12px}
.record-content{min-width:0;flex:1;overflow-wrap:anywhere}
.category-link{padding:0;background:transparent;text-align:left;color:#17243a;white-space:normal;line-height:1.7}
.category-link .link{padding:0;font-size:11px;font-weight:400;white-space:nowrap}
.edit-button{padding:10px 0 10px 12px;flex-shrink:0;font-size:12px}
button:focus-visible{outline:2px solid #1768ea;outline-offset:3px}
</style>
