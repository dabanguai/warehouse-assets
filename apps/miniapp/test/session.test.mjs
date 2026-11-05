import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

async function fixture(){
 let page={route:'pages/inventory/index'};
 const storage=new Map([['token','old-token'],['user',{role:'ADMIN'}]]),redirects=[],tabs=[],toasts=[];
 globalThis.getCurrentPages=()=>[page];
 globalThis.uni={getStorageSync:k=>storage.get(k),removeStorageSync:k=>storage.delete(k),reLaunch:o=>redirects.push(o),switchTab:o=>tabs.push(o),showToast:o=>toasts.push(o)};
 const s=await import(new URL('../src/session.ts?test='+crypto.randomUUID(),import.meta.url));
 return{s,storage,redirects,tabs,toasts,setPage:route=>{page={route}}};
}
test('冷启动使用登录入口，App不在onLaunch中导航',()=>{
 const config=JSON.parse(fs.readFileSync(new URL('../src/pages.json',import.meta.url),'utf8'));
 assert.equal(config.pages[0].path,'pages/login/index');
 assert.ok(!fs.readFileSync(new URL('../src/App.vue',import.meta.url),'utf8').includes('reLaunch'));
});
test('首次页面尚未就绪时401只排队，不导航',async()=>{
 const f=await fixture();f.s.handleUnauthorized('old-token');assert.equal(f.redirects.length,0);assert.equal(f.storage.has('token'),false);
 f.s.markPageReady();assert.equal(f.redirects.length,1);
});
test('并发401仅进行一次登录跳转，迟到响应不再次导航',async()=>{
 const f=await fixture();f.s.markPageReady();f.s.handleUnauthorized('old-token');f.s.handleUnauthorized('old-token');f.s.handleUnauthorized('');assert.equal(f.redirects.length,1);
 f.setPage('pages/login/index');f.redirects[0].complete();f.s.handleUnauthorized('');assert.equal(f.redirects.length,1);
});
test('旧请求401不能删除新登录的token',async()=>{
 const f=await fixture();f.storage.set('token','new-token');f.s.markPageReady();f.s.handleUnauthorized('old-token');assert.equal(f.storage.get('token'),'new-token');assert.equal(f.redirects.length,0);
});
test('已在登录页不会再次reLaunch',async()=>{
 const f=await fixture();f.setPage('pages/login/index');f.s.handleUnauthorized('old-token');f.s.markPageReady();assert.equal(f.redirects.length,0);
});
test('登录后使用switchTab打开库存，并等待导航完成再处理失效',async()=>{
 const f=await fixture();f.setPage('pages/login/index');f.s.markPageReady();const opened=f.s.openInventory();assert.equal(f.tabs.length,1);assert.equal(f.redirects.length,0);
 f.setPage('pages/inventory/index');f.s.markPageReady();f.s.handleUnauthorized('old-token');assert.equal(f.redirects.length,0);
 f.tabs[0].success();f.tabs[0].complete();await opened;assert.equal(f.redirects.length,1);
});
test('导航失败不会自动重试，后续人工操作可重新导航',async()=>{
 const f=await fixture();f.s.markPageReady();f.s.redirectToLogin();f.redirects[0].fail();f.redirects[0].complete();assert.equal(f.redirects.length,1);assert.equal(f.toasts.length,1);
 f.s.redirectToLogin();assert.equal(f.redirects.length,2);
});
test('退出再登录，新页面实例仍需等onReady',async()=>{
 const f=await fixture();f.s.markPageReady();f.setPage('pages/equipment/index');f.s.redirectToLogin();assert.equal(f.redirects.length,0);f.s.markPageReady();assert.equal(f.redirects.length,1);
});
