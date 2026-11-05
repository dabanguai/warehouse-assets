const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { parse, compileScript } = require('@vue/compiler-sfc');
const ts = require('typescript');
const vue = require('vue');

function setup(file, props = {}, modules = {}) {
    const filename = resolve(__dirname, '../src', file);
    const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename });
    const script = compileScript(descriptor, { id: 'picker-test' });
    const compiled = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
    const mod = { exports: {} };
    const requireSource = name => {
        if (modules[name]) return modules[name];
        if (name === 'vue') return vue;
        if (name === '@dcloudio/uni-app') return { onLoad() {}, onShow() {}, onReady() {} };
        if (name.endsWith('/session')) return { markPageReady() {} };
        if (name.endsWith('/initial-outbounds')) {
            const source = readFileSync(resolve(__dirname, '../src/initial-outbounds.ts'), 'utf8');
            const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
            const loaded = { exports: {} };
            new Function('module', 'exports', code)(loaded, loaded.exports);
            return loaded.exports;
        }
        return {};
    };
    new Function('require', 'module', 'exports', compiled)(requireSource, mod, mod.exports);
    return mod.exports.default.setup(props, { expose() {} });
}

test('期初出库预览按剧组组别合单，其他出库关联同剧组仍独立', () => {
    const page = setup('pages/import/index.vue');
    page.preview.value = { rows: [
        { initial: { outbounds: [
            { name: '《无尽夏》A组', kind: 'CREW', quantity: 1 },
            { name: '《无尽夏》B组', kind: 'CREW', quantity: 1 },
            { name: '无尽夏（灯）', kind: 'OTHER', quantity: 1, relatedCrewId: 'crew' }
        ] } },
        { initial: { outbounds: [{ name: '《无尽夏》A组', kind: 'CREW', quantity: 2 }] } },
        { unmanaged: true, initial: { outbounds: [{ name: '不计库存', quantity: 1 }] } }
    ] };
    assert.deepEqual(page.openingPreview.value.map(d => [d.name, d.group, d.count]), [['无尽夏', 'A组', 2], ['无尽夏', 'B组', 1], ['无尽夏（灯）', '未分组', 1]]);
    const groupA = page.destinations.value.find(d => d.out.name === '《无尽夏》A组');
    page.setDestination(groupA, 'crewId', 'crew');
    page.setDestination(groupA, 'groupName', '小分队');
    assert.equal(page.preview.value.rows[1].initial.outbounds[0].crewId, 'crew');
    assert.equal(page.openingPreview.value.length, 3);
    assert.equal(page.openingPreview.value[0].group, '小分队');
});

test('空设备列表确认和失效索引不会改写录入行', () => {
    const props = vue.reactive({ row: { rawName: '45尺炮', createEquipment: true, quantity: 1 }, equipment: [], kind: 'PURCHASE' });
    const editor = setup('components/RowEditor.vue', props);
    const original = structuredClone(vue.toRaw(props.row));
    for (const i of [0, -1, 99, NaN]) editor.pickEquipment(i);
    assert.deepEqual(vue.toRaw(props.row), original);
    props.equipment.push({ id: 'crane', name: '天蝎45呎', unit: '套' });
    editor.pickEquipment(0);
    assert.equal(props.row.equipmentId, 'crane');
    assert.equal(props.row.rawName, '45尺炮');
    assert.equal(props.row.unit, '套');
    assert.equal(props.row.createEquipment, false);
});

test('归还空列表不清除来源，选中有效明细后同步编号和归属', () => {
    const props = vue.reactive({ row: { owner2Quantity: 2, partNo: '2*2', serials: ['058'] }, equipment: [], kind: 'RETURN', allocationOptions: [] });
    const editor = setup('components/RowEditor.vue', props);
    editor.selectAllocation(0);
    assert.equal(props.row.owner2Quantity, 2);
    props.allocationOptions.push({ id: 'allocation', equipment_id: 'crane', owner_id: 1, serial: '011' });
    editor.selectAllocation(0);
    assert.equal(props.row.allocationId, 'allocation');
    assert.equal(props.row.ownerId, 1);
    assert.deepEqual([...props.row.serials], ['011']);
    assert.equal('owner2Quantity' in props.row, false);
    assert.equal('partNo' in props.row, false);
});

test('录入新设备按大类筛选具体类别，切换大类清除旧类别', () => {
    const props = vue.reactive({ row: { categoryId: 'crane' }, equipment: [], kind: 'PURCHASE', categoryGroups: [{ id: 'mechanical', name: '机械器材' }, { id: 'light', name: '灯光器材' }], categories: [{ id: 'crane', group_id: 'mechanical' }, { id: 'led', group_id: 'light' }] });
    const editor = setup('components/RowEditor.vue', props);
    assert.deepEqual(editor.categoryOptions.value.map(c => c.id), ['crane']);
    editor.pickGroup(2);
    assert.equal(props.row.categoryId, undefined);
    assert.deepEqual(editor.categoryOptions.value.map(c => c.id), ['led']);
    editor.pickCategory(0);
    assert.equal(props.row.categoryId, 'led');
});

test('手工出库和批量导入的空剧组选择不报错，有效选择同步组别', () => {
    for (const [file, method, key] of [['editor', 'chooseCrew', 'payload'], ['import', 'pickCrew', 'crewId']]) {
        const page = setup(`pages/${file}/index.vue`);
        page[method](0);
        page.meta.value = { crews: [{ id: 'crew', groups: ['B组'] }] };
        page[method](0);
        assert.equal(key === 'payload' ? page.payload.crewId : page.crewId.value, 'crew');
        assert.equal(key === 'payload' ? page.payload.groupName : page.group.value, 'B组');
    }
});

test('供货页面空设备和剧组不破坏当前测算，有效换设备后清除旧测算', () => {
    const page = setup('pages/supply/index.vue');
    const row = { equipmentId: 'old', rawName: '旧设备' };
    page.result.value = { measuredAt: 'old' };
    page.pickEquipment(row, 0);
    page.pickCrew(0);
    assert.equal(row.equipmentId, 'old');
    assert.equal(page.result.value.measuredAt, 'old');
    page.meta.value = { equipment: [{ id: 'new', name: '新设备' }], crews: [{ id: 'crew', groups: ['A组'] }] };
    page.pickEquipment(row, 0);
    assert.deepEqual(row, { equipmentId: 'new', rawName: '新设备' });
    assert.equal(page.result.value, null);
    page.pickCrew(0);
    assert.equal(page.cid.value, 'crew');
});

test('管理中切换设备大类不会遗留其他大类的具体类别', () => {
    const page = setup('pages/catalog/index.vue');
    page.meta.value = { categoryGroups: [{ id: 'mechanical', name: '机械器材' }], categories: [{ id: 'crane', group_id: 'mechanical' }] };
    page.form.value = { categoryId: 'old', categoryGroupId: '' };
    page.pickCategory(0);
    assert.equal(page.form.value.categoryId, 'old');
    page.pickEquipmentGroup(1);
    assert.equal(page.form.value.categoryId, '');
    page.pickCategory(0);
    assert.equal(page.form.value.categoryId, 'crane');
});

test('库存品牌默认收起，搜索自动展开且可收起，类别筛选更新型号数', async () => {
    global.uni = {};
    const page = setup('pages/inventory/index.vue');
    page.items.value = [
        { id: 'one', name: '稳定器套装', brand: ' DJI ', model: 'RS 4 Pro', category_id: 'stabilizer', aliases: ['大疆稳定器'] },
        { id: 'two', name: '稳定器单机', brand: 'DJI', model: 'RS 4 Pro', category_id: 'stabilizer' },
        { id: 'three', name: '相机', brand: 'DJI', model: 'Osmo', category_id: 'camera' },
        { id: 'old', name: '旧脚架', brand: '  ', category_id: 'tripod' }
    ];
    await vue.nextTick();
    assert.deepEqual(page.brandGroups.value.map(b => [b.name, b.modelCount, b.items.length]), [['DJI', 2, 3], ['无品牌', 1, 1]]);
    assert.deepEqual([...page.expandedBrands.value], []);
    page.toggleBrand('DJI'); page.toggleBrand('');
    assert.deepEqual([...page.expandedBrands.value], ['DJI', '']);
    page.toggleBrand('DJI');
    assert.deepEqual([...page.expandedBrands.value], ['']);
    page.search.value = 'rs 4 pro';
    await vue.nextTick();
    assert.equal(page.brandGroups.value[0].modelCount, 1);
    assert.deepEqual([...page.expandedBrands.value], ['DJI']);
    page.toggleBrand('DJI');
    assert.deepEqual([...page.expandedBrands.value], []);
    page.search.value = '大疆';
    await vue.nextTick();
    assert.deepEqual(page.filtered.value.map(e => e.id), ['one']);
    page.search.value = '';
    await vue.nextTick();
    assert.deepEqual([...page.expandedBrands.value], []);
    page.search.value = 'dji';
    page.categoryId.value = 'camera';
    await vue.nextTick();
    assert.deepEqual(page.brandGroups.value[0].items.map(e => e.id), ['three']);
    page.search.value = '不存在';
    await vue.nextTick();
    assert.equal(page.brandGroups.value.length, 0);
    assert.deepEqual([...page.expandedBrands.value], []);
});

test('管理设备编辑保留品牌型号并提交，型号参与搜索', async () => {
    const calls = [];
    global.uni = { pageScrollTo() {}, showToast() {} };
    const page = setup('pages/catalog/index.vue', {}, { '../../api': {
        api: async (path, method, body) => {
            calls.push([path, method, body]);
            if (path === '/meta') return { categories: [] };
            return { role: 'ADMIN' };
        }
    } });
    page.mode.value = 'equipment';
    const equipment = { id: 'one', name: '稳定器', brand: 'DJI', model: 'RS 4 Pro', category_id: 'stabilizer', unit: '套' };
    page.meta.value = { categories: [{ id: 'stabilizer' }], equipment: [equipment] };
    page.search.value = 'rs 4';
    assert.equal(page.records.value.length, 1);
    await page.openForm(equipment);
    assert.equal(page.form.value.model, 'RS 4 Pro');
    await page.save();
    assert.equal(calls[0][0], '/equipment/one');
    assert.equal(calls[0][2].brand, 'DJI');
    assert.equal(calls[0][2].model, 'RS 4 Pro');
});

test('库存按两级分类筛选，未归类设备保留可查', () => {
    global.uni = {};
    const page = setup('pages/inventory/index.vue');
    page.meta.value = { categoryGroups: [{ id: 'mechanical', name: '机械器材' }], categories: [{ id: 'crane', group_id: 'mechanical' }, { id: 'old', group_id: null }] };
    page.items.value = [{ id: 'one', name: '天蝎45呎', group_id: 'mechanical', category_id: 'crane' }, { id: 'two', name: '旧设备', group_id: null, category_id: 'old' }];
    page.pickGroup(2);
    assert.deepEqual(page.filtered.value.map(e => e.id), ['one']);
    page.pickCategory(1);
    assert.equal(page.categoryId.value, 'crane');
    page.pickGroup(1);
    assert.equal(page.categoryId.value, '');
    assert.deepEqual(page.filtered.value.map(e => e.id), ['two']);
});

test('类别关联设备跳转清除旧条件，保留具体类别和未归类范围', () => {
    const page = setup('pages/catalog/index.vue');
    page.meta.value = { categories: [{ id: 'crane', group_id: 'mechanical', name: '摇臂' }], equipment: [
        { id: 'one', category_id: 'crane', group_id: 'mechanical', name: '天蝎', specification: '45尺' },
        { id: 'two', category_id: 'track', group_id: 'mechanical', name: '轨道' },
        { id: 'old', category_id: 'other', group_id: null, name: '旧设备' }
    ] };
    page.mode.value = 'categories';
    page.search.value = '旧搜索';
    page.showEquipment({ id: 'crane', group_id: 'mechanical' });
    assert.equal(page.mode.value, 'equipment');
    assert.equal(page.search.value, '');
    assert.deepEqual(page.records.value.map(r => r.id), ['one']);
    page.search.value = '45尺';
    assert.equal(page.records.value.length, 1);
    page.search.value = '轨道';
    assert.equal(page.records.value.length, 0);
    page.selectMode('categoryGroups');
    page.showEquipment({ id: 'mechanical' });
    assert.deepEqual(page.records.value.map(r => r.id), ['one', 'two']);
    page.selectMode('categories');
    page.showEquipment({ id: 'other', group_id: null });
    assert.deepEqual(page.records.value.map(r => r.id), ['old']);
    page.selectMode('categories');
    page.filterGroup.value = 'mechanical';
    page.search.value = '摇臂';
    assert.deepEqual(page.records.value.map(r => r.id), ['crane']);
});

test('库存状态汇总与仓库、资产归属及搜索筛选一致', () => {
    global.uni = {};
    const page = setup('pages/inventory/index.vue');
    page.items.value = [{ name: '轨道', breakdown: [
        { ownerId: 1, warehouseId: 'beijing', total: 10, inStock: 7, available: 4, outside: 2, reserved: 1, maintenance: 2, inTransit: 1 },
        { ownerId: 2, warehouseId: 'hengdian', total: 5, inStock: 5, available: 5, outside: 0, reserved: 0, maintenance: 0, inTransit: 0 }
    ] }];
    assert.equal(page.statusSummary.value.total, 15);
    page.wh.value = 1;
    assert.equal(page.statusSummary.value.total, 10);
    assert.equal(page.statusSummary.value.outside, 2);
    assert.equal(page.statusSummary.value.maintenance, 2);
    page.owner.value = 2;
    assert.equal(page.statusSummary.value.total, 0);
    page.wh.value = 2;
    assert.equal(page.statusSummary.value.inStock, 5);
    page.search.value = '不存在';
    assert.equal(page.statusSummary.value.total, 0);
});

test('期初待核对筛选保留相互抵消的逐行差额，并与分类搜索组合', () => {
    global.uni = {};
    const page = setup('pages/inventory/index.vue');
    page.items.value = [
        { id: 'difference', name: '摇臂', category_id: 'crane', initialReviews: [{ difference: 2, needsReview: true }, { difference: -2, needsReview: true }] },
        { id: 'pending', name: '轨道', category_id: 'track', initialReviews: [{ total: null, needsReview: true }] },
        { id: 'balanced', name: '云台', initialReviews: [{ difference: 0, needsReview: false }] },
        { id: 'ordinary', name: '脚架' }
    ];
    page.reviewFilter.value = 1;
    assert.deepEqual(page.filtered.value.map(e => e.id), ['difference', 'pending']);
    page.categoryId.value = 'crane';
    assert.deepEqual(page.filtered.value.map(e => e.id), ['difference']);
    page.search.value = '轨道';
    assert.equal(page.filtered.value.length, 0);
    page.categoryId.value = '';
    assert.deepEqual(page.filtered.value.map(e => e.id), ['pending']);
});

test('期初数量核对失败保留表单，提交版本和三项数量', async () => {
    const calls = [], messages = [];
    const page = setup('pages/equipment/index.vue', {}, { '../../api': {
        api: async (...args) => { calls.push(args); throw new Error('减少数量会影响已确认出库或预留，请先核对'); },
        errorMessage: error => messages.push(error.message)
    } });
    page.eid.value = 'equipment';
    page.reviewForm.value = { documentId: 'initial', rowIndex: 2, expectedVersion: 3, total: 5, beijing: 1, hengdian: 2, reason: '核对' };
    await page.saveReview();
    assert.deepEqual(calls, [['/equipment/equipment/initial-reviews/initial/2', 'PATCH', { expectedVersion: 3, total: 5, beijing: 1, hengdian: 2, reason: '核对' }]]);
    assert.equal(page.reviewForm.value.total, 5);
    assert.equal(page.reviewBusy.value, false);
    assert.deepEqual(messages, ['减少数量会影响已确认出库或预留，请先核对']);
});

test('先导入类别不提交数量，保留未完成的库存核对并刷新分类选项', async () => {
    const calls = [], messages = [];
    global.uni = { showToast: message => messages.push(message.title) };
    const page = setup('pages/import/index.vue', {}, { '../../api': {
        api: async (path, method, body) => {
            calls.push([path, method, body]);
            if (method === 'POST') return { groupName: '机械器材', categoryCount: 1 };
            return { categoryGroups: [{ id: 'group', name: '机械器材' }], categories: [{ id: 'category', name: '摇臂类', group_id: 'group' }] };
        }, errorMessage: error => { throw error; }
    } });
    page.categoryGroup.value = '机械器材';
    page.uploadInfo.value = { id: 'file' };
    page.preview.value = { rows: [{ category: '摇臂类', rawName: '45尺炮', quantity: null, note: '尚待核对' }] };
    await page.saveCategories();
    assert.deepEqual(calls[0], ['/imports/file/categories', 'POST', { groupName: '机械器材' }]);
    assert.equal(calls.length, 2);
    assert.equal(page.preview.value.rows[0].quantity, null);
    assert.equal(page.preview.value.rows[0].note, '尚待核对');
    assert.equal(page.preview.value.rows[0].categoryId, 'category');
    assert.equal(page.categoryBusy.value, false);
    assert.deepEqual(messages, ['已导入1个类别']);

    const props = vue.reactive({ row: { categoryGroup: '机械器材' }, categories: [], categoryGroups: [], equipment: [], kind: 'INITIAL' });
    const editor = setup('components/RowEditor.vue', props);
    props.categories = page.meta.value.categories;
    props.categoryGroups = page.meta.value.categoryGroups;
    props.row.categoryId = page.preview.value.rows[0].categoryId;
    assert.equal(editor.selectedGroup.value, 'group');
    assert.equal(editor.categoryOptions.value[0].id, 'category');
});

test('导入保存各阶段失败时提示错误，保留核对数据并允许重试', async () => {
    for (const failure of ['/imports/file/rows', '/documents', '/documents/draft/confirm']) {
        const messages = [], navigations = [];
        const page = setup('pages/import/index.vue', {}, { '../../api': {
            api: async path => {
                if (path === failure) throw new Error('请核对数量后重试');
                return { id: 'draft', version: 1 };
            },
            errorMessage: error => messages.push(error.message),
            go: path => navigations.push(path)
        } });
        page.kind.value = 'INITIAL';
        page.uploadInfo.value = { id: 'file' };
        page.preview.value = { rows: [{ rawName: '测试设备', quantity: 5, note: '待核对备注' }] };
        await page.save(true);
        assert.deepEqual(messages, ['请核对数量后重试']);
        assert.deepEqual(navigations, []);
        assert.equal(page.busy.value, false);
        assert.equal(page.preview.value.rows[0].note, '待核对备注');
        if (failure.endsWith('/confirm')) assert.equal(page.record.value.id, 'draft');
    }
});
