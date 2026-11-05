-- Excel 导入测试数据重置（MySQL）。仅在测试库执行，执行前备份。
-- 保留账号、登录会话、仓库、资产归属方、库存事务锁及 Flyway 迁移历史。
-- 清理期间停止后端服务，避免并发写入；在 SQL 客户端选中目标数据库。
-- 本脚本不提交事务。执行后在同一连接核对结果，再单独执行 COMMIT；
-- 若报错或数据库选择有误，在同一连接执行 ROLLBACK。
-- 数据库记录清理不删除磁盘上的 Excel 和附件文件。

SELECT DATABASE() AS current_database;

START TRANSACTION;

-- 按外键依赖顺序清理业务明细及单据。
DELETE FROM `returns`;
DELETE FROM `holds`;
DELETE FROM `transfers`;
DELETE FROM `attachments`;
DELETE FROM `allocations`;
DELETE FROM `documents`;

-- 清理库存、设备目录及剧组/公司档案，供期初导入重新建立。
DELETE FROM `assets`;
DELETE FROM `aliases`;
DELETE FROM `equipment`;
DELETE FROM `categories`;
DELETE FROM `category_groups`;
DELETE FROM `crews`;

-- 清理上传记录、解析预览、映射模板及历史操作结果。
DELETE FROM `imports`;
DELETE FROM `templates`;
DELETE FROM `idempotency`;
DELETE FROM `audit`;

-- 下列 remaining_rows 应全部为 0。
SELECT 'returns' AS table_name, COUNT(*) AS remaining_rows FROM `returns`
UNION ALL SELECT 'holds', COUNT(*) FROM `holds`
UNION ALL SELECT 'transfers', COUNT(*) FROM `transfers`
UNION ALL SELECT 'attachments', COUNT(*) FROM `attachments`
UNION ALL SELECT 'allocations', COUNT(*) FROM `allocations`
UNION ALL SELECT 'documents', COUNT(*) FROM `documents`
UNION ALL SELECT 'assets', COUNT(*) FROM `assets`
UNION ALL SELECT 'aliases', COUNT(*) FROM `aliases`
UNION ALL SELECT 'equipment', COUNT(*) FROM `equipment`
UNION ALL SELECT 'categories', COUNT(*) FROM `categories`
UNION ALL SELECT 'category_groups', COUNT(*) FROM `category_groups`
UNION ALL SELECT 'crews', COUNT(*) FROM `crews`
UNION ALL SELECT 'imports', COUNT(*) FROM `imports`
UNION ALL SELECT 'templates', COUNT(*) FROM `templates`
UNION ALL SELECT 'idempotency', COUNT(*) FROM `idempotency`
UNION ALL SELECT 'audit', COUNT(*) FROM `audit`;

-- 核对无误后，在同一连接单独执行：COMMIT;
COMMIT
-- 放弃清理时，在同一连接单独执行：ROLLBACK;
