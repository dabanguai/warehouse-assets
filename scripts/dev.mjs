import {spawn} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const children=[
 spawn(process.execPath,[path.join(root,'scripts/api.mjs'),'dev'],{cwd:root,stdio:'inherit'}),
 spawn(process.execPath,[path.join(root,'node_modules/@dcloudio/vite-plugin-uni/bin/uni.js'),'--host','127.0.0.1'],{cwd:path.join(root,'apps/miniapp'),stdio:'inherit'})
];
let stopping=false;
function stop(code=0){if(stopping)return;stopping=true;for(const child of children){if(process.platform==='win32'&&child.pid)spawn('taskkill',['/pid',String(child.pid),'/t','/f'],{stdio:'ignore',windowsHide:true});else child.kill();}process.exitCode=code;}
children.forEach(child=>{child.on('error',e=>{console.error(e.message);stop(1)});child.on('exit',code=>{if(!stopping)stop(code||0)});});
process.on('SIGINT',()=>stop());process.on('SIGTERM',()=>stop());
console.log('本地开发界面：http://127.0.0.1:5173 ，按 Ctrl+C 停止。');
