import {spawn} from 'node:child_process';
import {existsSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const api=path.join(root,'apps/api');
if(existsSync(path.join(root,'.env'))) process.loadEnvFile(path.join(root,'.env'));
const [mode='dev',input]=process.argv.slice(2);
const goals={dev:['spring-boot:run'],build:['package','-DskipTests'],check:['test-compile'],test:['test']};
let child;
if(mode==='start' || mode==='import') {
  const args=['-jar',path.join(api,'target/warehouse-api-0.1.0.jar')];
  if(mode==='import') {
    if(!input) throw new Error('用法：npm run db:import -- 导出文件.json');
    args.push('--spring.main.web-application-type=none','--migration-file='+path.resolve(input));
  }
  child=spawn('java',args,{cwd:api,stdio:'inherit'});
} else {
  if(!goals[mode]) throw new Error('未知后端操作');
  child=spawn(process.platform==='win32'?'mvn.cmd':'mvn',goals[mode],{cwd:api,stdio:'inherit',shell:process.platform==='win32'});
}
child.on('error',error=>{console.error(error.message);process.exitCode=1;});
child.on('exit',code=>{process.exitCode=code??1;});
function stop(){
  if(process.platform==='win32' && child.pid) spawn('taskkill',['/pid',String(child.pid),'/t','/f'],{stdio:'ignore',windowsHide:true});
  else child.kill('SIGTERM');
}
process.on('SIGINT',stop);process.on('SIGTERM',stop);
