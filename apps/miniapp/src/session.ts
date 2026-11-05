const loginRoute = 'pages/login/index';
const inventoryRoute = 'pages/inventory/index';
const readyPages = new WeakSet<object>();
let navigating = false;
let loginPending = false;

function currentPage() {
    const stack = getCurrentPages();
    return stack[stack.length - 1];
}

// 路由只在当前页面已就绪、且没有其他登录导航时执行。
function flushLogin() {
    if (!loginPending || navigating) return;
    const page = currentPage();
    if (!page) return;
    if (page.route === loginRoute) {
        loginPending = false;
        return;
    }
    if (!readyPages.has(page)) return;
    loginPending = false;
    navigating = true;
    uni.reLaunch({
        url: '/' + loginRoute,
        fail: () => {
            loginPending = false;
            uni.showToast({ title: '登录页打开失败，请关闭页面后重试', icon: 'none' });
        },
        complete: () => {
            navigating = false;
            flushLogin();
        }
    });
}

export function markPageReady() {
    const page = currentPage();
    if (page) readyPages.add(page);
    flushLogin();
}

export function redirectToLogin() {
    loginPending = true;
    flushLogin();
}

export function handleUnauthorized(requestToken: string) {
    // 旧请求的401不能清除重新登录后取得的新会话。
    if ((uni.getStorageSync('token') || '') !== requestToken) return;
    uni.removeStorageSync('token');
    uni.removeStorageSync('user');
    redirectToLogin();
}

export function openInventory(): Promise<void> {
    if (currentPage()?.route === inventoryRoute) return Promise.resolve();
    if (navigating) return Promise.reject(new Error('页面正在切换，请稍后操作'));
    navigating = true;
    return new Promise((resolve, reject) => uni.switchTab({
        url: '/' + inventoryRoute,
        success: () => resolve(),
        fail: error => reject(new Error(error.errMsg || '无法打开库存页')),
        complete: () => {
            navigating = false;
            flushLogin();
        }
    }));
}
