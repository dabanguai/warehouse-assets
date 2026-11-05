import {DatabaseSync} from 'node:sqlite';
import {writeFileSync} from 'node:fs';
import {resolve} from 'node:path';

const [input, output] = process.argv.slice(2);
if (!input || !output) throw new Error('用法：node scripts/export-sqlite.mjs 原数据库.sqlite 导出文件.json');
const db = new DatabaseSync(resolve(input), {readOnly:true});
try {
  db.exec('BEGIN');
  const version=db.prepare('PRAGMA user_version').get().user_version;
  if (![1,2].includes(version)) throw new Error('仅支持原后端结构版本1或2');
  const tables={};
  for (const table of ['users','owners','warehouses','category_groups','categories','equipment','aliases','crews','assets','documents','allocations','holds','returns','transfers','imports','templates','attachments','audit']) {
    tables[table]=table==='category_groups' && version===1 ? [] : db.prepare(`SELECT * FROM ${table}`).all();
  }
  if(version===1) tables.categories=tables.categories.map(row=>({...row,group_id:null}));
  // 会话重新登录；确认记录保留，避免重试请求再次入账。
  tables.idempotency=db.prepare('SELECT * FROM idempotency').all();
  writeFileSync(resolve(output),JSON.stringify({format:'warehouse-sqlite-v1',exportedAt:new Date().toISOString(),tables},null,2),{flag:'wx',mode:0o600});
  db.exec('COMMIT');
  console.log('导出完成。迁移文件包含账号及业务数据，请妥善保管；原数据库未修改。');
} finally {db.close();}
