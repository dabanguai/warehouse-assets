import { handleUnauthorized } from './session';
let base = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:3100/api';
// #ifdef MP-WEIXIN
base = import.meta.env.VITE_WEIXIN_API_BASE || base;
// #endif
// #ifdef H5
base = import.meta.env.VITE_API_BASE || '/api';
// #endif
export const warehouseName = (v: string) => ({ beijing: '北京', hengdian: '横店', unknown: '来源仓待核实' }[v] || v);
export const ownerName = (v: number) => v === 2 ? '2号库 · Boss' : '1号库 · 投资方';
export function sourceLabel(r: any) {
    if (r.unmanaged)
        return '不计库存';
    if (r.quantity === null || r.quantity === undefined || r.quantity === '')
        return (r.ownerId === 2 || r.partNo === '2' ? '2号库' : '1号库') + ' · 数量待核实';
    if (r.owner2Quantity === null)
        return '来源待核对';
    const q = Number(r.quantity);
    let two = 0;
    if (r.owner2Quantity !== undefined)
        two = Number(r.owner2Quantity);
    else if (r.ownerId)
        two = r.ownerId === 2 ? q : 0;
    else if (String(r.partNo || '').trim() === '2')
        two = q;
    else if (String(r.partNo || '').trim()) {
        const m = /^2\s*\*\s*(\d+)$/.exec(r.partNo);
        if (!m)
            return '来源待核对';
        two = Number(m[1]);
    }
    if (!Number.isFinite(q) || !Number.isFinite(two) || two < 0 || two > q)
        return '来源待核对';
    return two === 0 ? '1号库 ' + q : two === q ? '2号库 ' + q : '1号库 ' + (q - two) + ' / 2号库 ' + two;
}
export const typeNames: Record<string, string> = { INITIAL: '期初', PURCHASE: '新购入库', OUTBOUND: '出库', RESERVATION: '预留', RETURN: '归还', TRANSFER: '调拨', SALE: '出售', SCRAP: '报废', ADJUST: '盘点' };
export const statusNames: Record<string, string> = { DRAFT: '草稿', RESERVED: '已预留', OUT: '使用中', CONFIRMED: '已确认', CANCELLED: '已取消', CLOSED: '已归还' };
export const addDays = (s: string, n = 1) => new Date(Date.parse(s + 'T00:00:00Z') + n * 86400000).toISOString().slice(0, 10);
export function api<T = any>(path: string, method: any = 'GET', data?: any): Promise<T> {
    const requestToken = uni.getStorageSync('token') || '';
    return new Promise((resolve, reject) => uni.request({
        url: base + path, method, data,
        header: { Authorization: 'Bearer ' + requestToken },
        success: response => {
            if (response.statusCode >= 200 && response.statusCode < 300) {
                resolve(response.data as T);
                return;
            }
            const body = response.data as any;
            if (response.statusCode === 401 && path !== '/auth/login')
                handleUnauthorized(requestToken);
            const error: any = new Error(body?.message || '请求失败');
            error.code = body?.code;
            error.details = body?.details;
            reject(error);
        },
        fail: () => reject(new Error('连接失败，请检查网络或联系管理员'))
    }));
}
export const errorMessage = (e: unknown) => uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none', duration: 3500 });
export const go = (path: string) => uni.navigateTo({ url: '/pages/' + path });
export async function upload(path: string, filePath: string) {
    return new Promise<any>((resolve, reject) => uni.uploadFile({ url: base + path, filePath, name: 'file', header: { Authorization: 'Bearer ' + uni.getStorageSync('token') }, success: r => {
            let body: any;
            try {
                body = JSON.parse(r.data);
            }
            catch {
                reject(new Error('文件上传失败，请重新上传'));
                return;
            }
            r.statusCode >= 200 && r.statusCode < 300 ? resolve(body) : reject(new Error(body.message || '上传失败'));
        }, fail: () => reject(new Error('文件上传失败')) }));
}
export function chooseExcel(): Promise<string> {
    return new Promise((resolve, reject) => {
        // #ifdef H5
        uni.chooseFile({ count: 1, extension: ['xls', 'xlsx'], success: r => resolve(r.tempFilePaths[0]), fail: () => reject(new Error('未选择文件')) });
        // #endif
        // #ifdef MP-WEIXIN
        wx.chooseMessageFile({ count: 1, type: 'file', extension: ['xls', 'xlsx'], success: (r: any) => resolve(r.tempFiles[0].path), fail: () => reject(new Error('未选择文件')) });
        // #endif
    });
}
export async function exportFile(path: string, data: any, name: string) {
    const buffer = await new Promise<ArrayBuffer>((resolve, reject) => uni.request({ url: base + path, method: 'POST', data, responseType: 'arraybuffer', header: { Authorization: 'Bearer ' + uni.getStorageSync('token') }, success: r => r.statusCode === 200 ? resolve(r.data as ArrayBuffer) : reject(new Error('导出失败，请核对条件')), fail: reject }));
    // #ifdef H5
    const u = URL.createObjectURL(new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })), a = document.createElement('a');
    a.href = u;
    a.download = name + '.xlsx';
    a.click();
    setTimeout(() => URL.revokeObjectURL(u), 1000);
    // #endif
    // #ifdef MP-WEIXIN
    const pathName = wx.env.USER_DATA_PATH + '/' + name + '.xlsx';
    await new Promise<void>((resolve, reject) => wx.getFileSystemManager().writeFile({ filePath: pathName, data: buffer, success: () => resolve(), fail: reject }));
    uni.openDocument({ filePath: pathName, fileType: 'xlsx', showMenu: true });
    // #endif
}
export async function photoUrl(aid: string) { return new Promise<string>((resolve, reject) => uni.downloadFile({ url: base + '/attachments/' + aid, header: { Authorization: 'Bearer ' + uni.getStorageSync('token') }, success: r => r.statusCode === 200 ? resolve(r.tempFilePath) : reject(new Error('照片加载失败')), fail: reject })); }
export function confirm(text: string): Promise<boolean> { return new Promise(resolve => uni.showModal({ title: '确认操作', content: text, success: r => resolve(r.confirm) })); }
