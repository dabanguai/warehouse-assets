-- 型号独立于规格维护，历史设备保留原名称作为展示回退。
ALTER TABLE equipment ADD COLUMN model VARCHAR(500) NOT NULL DEFAULT '' COMMENT '设备型号';
