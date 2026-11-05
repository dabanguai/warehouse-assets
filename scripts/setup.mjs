import fs from 'node:fs/promises';
import path from 'node:path';
import {randomBytes} from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const file=path.join(root,'.env');
try{await fs.access(file);const existing=await fs.readFile(file,'utf8');const defaults={MYSQL_URL:'jdbc:mysql://127.0.0.1:3306/warehouse?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai',MYSQL_USER:'warehouse',MYSQL_PASSWORD:''};const missing=Object.entries(defaults).filter(([key])=>!new RegExp('^'+key+'=','m').test(existing));if(missing.length)await fs.appendFile(file,'\n'+missing.map(([key,value])=>key+'='+value).join('\n')+'\n');console.log('.env 已保留，缺少的 MySQL 配置项已补齐；启动前填写连接信息。');}catch(error){
 if(error.code!=='ENOENT')throw error;
 const python=path.join(root,'.venv-excel',process.platform==='win32'?'Scripts/python.exe':'bin/python');
 let executable='python';try{await fs.access(python);executable=python.replaceAll('\\','/')}catch{}
 await fs.writeFile(file,`HOST=127.0.0.1\nPORT=3100\nDATA_DIR=../../data\nPYTHON=${executable}\nMYSQL_URL=jdbc:mysql://127.0.0.1:3306/warehouse?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai\nMYSQL_USER=warehouse\nMYSQL_PASSWORD=\nADMIN_USERNAME=admin\nADMIN_PASSWORD=${randomBytes(18).toString('base64url')}\nCORS_ORIGIN=http://127.0.0.1:5173\n`,{mode:0o600});
 console.log('已生成 .env。首次管理员账号和随机密码保存在本机配置中，未输出到日志。');
}
