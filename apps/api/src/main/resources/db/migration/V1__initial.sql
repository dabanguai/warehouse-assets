# CREATE DATABASE warehouse CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

-- 设备仓管初始结构与基础字典数据。
-- 日期以 YYYY-MM-DD 字符串保存；表按业务依赖顺序定义。

-- 库存事务锁：业务事务通过固定记录的行锁串行执行。
CREATE TABLE warehouse_lock (
    id INT PRIMARY KEY -- 固定锁记录标识
) ENGINE=InnoDB;

-- 固定库存事务锁记录。
INSERT INTO warehouse_lock VALUES
    (1);

-- 用户账号与角色。
CREATE TABLE users (
    id VARCHAR(100) PRIMARY KEY, -- 用户标识
    username VARCHAR(80) NOT NULL UNIQUE, -- 登录账号
    password_hash VARCHAR(200) NOT NULL, -- 密码哈希
    role VARCHAR(20) NOT NULL, -- 角色：ADMIN 管理员、KEEPER 仓管员、VIEWER 查看者
    active INT NOT NULL DEFAULT 1, -- 启用状态：1 启用，0 停用
    CHECK (role IN ('ADMIN', 'KEEPER', 'VIEWER')),
    CHECK (active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 登录会话：保存令牌哈希，通过用户关联检查账号状态。
CREATE TABLE sessions (
    token_hash VARCHAR(64) PRIMARY KEY, -- 会话令牌哈希
    user_id VARCHAR(100) NOT NULL, -- 登录用户标识
    expires_at VARCHAR(32) NOT NULL, -- 会话过期时间
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 资产归属方字典。
CREATE TABLE owners (
    id INT PRIMARY KEY, -- 归属方标识
    name VARCHAR(100) NOT NULL -- 归属方名称
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 初始资产归属方。
INSERT INTO owners VALUES
    (1, '投资方'),
    (2, '大哥');

-- 仓库字典：来源未核实的历史库存使用 unknown。
CREATE TABLE warehouses (
    id VARCHAR(100) PRIMARY KEY, -- 仓库标识
    name VARCHAR(100) NOT NULL UNIQUE -- 仓库名称
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 初始仓库及来源待核实的占位仓库。
INSERT INTO warehouses VALUES
    ('beijing', '北京'),
    ('hengdian', '横店'),
    ('unknown', '来源仓待核实');

-- 设备分类分组。
CREATE TABLE category_groups (
    id VARCHAR(100) PRIMARY KEY, -- 分类分组标识
    name VARCHAR(100) NOT NULL UNIQUE, -- 分类分组名称
    active INT NOT NULL DEFAULT 1 -- 启用状态：1 启用，0 停用
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 设备分类：可关联分类分组，分类名称全局唯一。
CREATE TABLE categories (
    id VARCHAR(100) PRIMARY KEY, -- 分类标识
    name VARCHAR(500) NOT NULL UNIQUE, -- 分类名称
    active INT NOT NULL DEFAULT 1, -- 启用状态：1 启用，0 停用
    group_id VARCHAR(100), -- 所属分类分组标识，可为空
    FOREIGN KEY (group_id) REFERENCES category_groups (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 设备目录：维护设备名称、规格和计量单位，不记录库存数量。
CREATE TABLE equipment (
    id VARCHAR(100) PRIMARY KEY, -- 设备标识
    name VARCHAR(500) NOT NULL UNIQUE, -- 设备名称
    english_name VARCHAR(500) NOT NULL DEFAULT '', -- 设备英文名称
    category_id VARCHAR(100) NOT NULL, -- 所属分类标识
    brand VARCHAR(500) NOT NULL DEFAULT '', -- 品牌
    specification VARCHAR(500) NOT NULL DEFAULT '', -- 规格型号
    unit VARCHAR(20) NOT NULL DEFAULT '件', -- 计量单位
    active INT NOT NULL DEFAULT 1, -- 启用状态：1 启用，0 停用
    FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 设备别名：将不同名称关联到统一设备目录。
CREATE TABLE aliases (
    id VARCHAR(100) PRIMARY KEY, -- 别名标识
    name VARCHAR(200) NOT NULL UNIQUE, -- 别名名称
    equipment_id VARCHAR(100) NOT NULL, -- 对应设备标识
    FOREIGN KEY (equipment_id) REFERENCES equipment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 剧组与公司档案。
CREATE TABLE crews (
    id VARCHAR(100) PRIMARY KEY, -- 档案标识
    name VARCHAR(300) NOT NULL UNIQUE, -- 剧组或公司名称
    kind VARCHAR(20) NOT NULL, -- 档案类型：CREW 剧组、COMPANY 公司
    contact VARCHAR(500) NOT NULL DEFAULT '', -- 联系人
    phone VARCHAR(500) NOT NULL DEFAULT '', -- 联系电话
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARING', -- 业务状态，默认 PREPARING（筹备中）
    groups_json LONGTEXT NOT NULL, -- 组别列表，JSON 数组
    CHECK (kind IN ('CREW', 'COMPANY')),
    CHECK (JSON_VALID(groups_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 库存资产：按设备、归属方和仓库记录单件实物或数量批次。
CREATE TABLE assets (
    id VARCHAR(100) PRIMARY KEY, -- 资产标识
    equipment_id VARCHAR(100) NOT NULL, -- 设备标识
    owner_id INT NOT NULL, -- 资产归属方标识
    warehouse_id VARCHAR(100) NOT NULL, -- 所属仓库标识
    serial VARCHAR(100), -- 单件编号；为空时按数量批次管理
    quantity DOUBLE NOT NULL, -- 资产账面数量，可用数量由业务占用计算
    created_at VARCHAR(32) NOT NULL, -- 创建时间
    -- 同一设备的非空单件编号唯一；数量批次允许编号为空。
    UNIQUE (equipment_id, serial),
    CHECK (quantity >= 0),
    -- 有单件编号的资产数量不得超过 1。
    CHECK (serial IS NULL OR quantity <= 1),
    FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    FOREIGN KEY (owner_id) REFERENCES owners (id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 业务单据：保存单据主体、业务数据及并发修改版本。
CREATE TABLE documents (
    id VARCHAR(100) PRIMARY KEY, -- 单据标识
    number VARCHAR(100) NOT NULL UNIQUE, -- 单据编号
    type VARCHAR(30) NOT NULL, -- 业务类型
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- 单据状态，默认 DRAFT（草稿）
    version INT NOT NULL DEFAULT 1, -- 版本号，用于校验并发修改
    business_date VARCHAR(10) NOT NULL, -- 业务日期，格式 YYYY-MM-DD
    crew_id VARCHAR(100), -- 关联剧组或公司标识，可为空
    group_name VARCHAR(100) NOT NULL DEFAULT '未分组', -- 剧组或公司的组别名称
    payload_json LONGTEXT NOT NULL, -- 单据业务数据，JSON 对象
    created_by VARCHAR(100) NOT NULL, -- 创建用户标识
    created_at VARCHAR(32) NOT NULL, -- 创建时间
    updated_at VARCHAR(32) NOT NULL, -- 最后更新时间
    CHECK (JSON_VALID(payload_json)),
    FOREIGN KEY (crew_id) REFERENCES crews (id),
    FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 设备分配：记录预留或出库的资产占用及累计归还数量。
CREATE TABLE allocations (
    id VARCHAR(100) PRIMARY KEY, -- 分配记录标识
    document_id VARCHAR(100) NOT NULL, -- 所属业务单据标识
    equipment_id VARCHAR(100) NOT NULL, -- 设备标识
    asset_id VARCHAR(100) NOT NULL, -- 分配的资产标识
    owner_id INT NOT NULL, -- 分配时的资产归属方标识
    warehouse_id VARCHAR(100) NOT NULL, -- 分配时的仓库标识
    quantity DOUBLE NOT NULL, -- 分配数量
    returned_quantity DOUBLE NOT NULL DEFAULT 0, -- 累计归还数量
    start_date VARCHAR(10) NOT NULL, -- 占用开始日期，格式 YYYY-MM-DD
    available_date VARCHAR(10), -- 可供日期，占用不含当日；为空表示归期待确认，逾期未还的占用由业务层延续
    serial_snapshot VARCHAR(100), -- 分配时的单件编号快照
    CHECK (quantity > 0),
    -- 累计归还数量不得为负，也不得超过原分配数量。
    CHECK (returned_quantity >= 0 AND returned_quantity <= quantity),
    -- 已确定的可供日期必须晚于占用开始日期。
    CHECK (available_date IS NULL OR available_date > start_date),
    FOREIGN KEY (document_id) REFERENCES documents (id),
    FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    FOREIGN KEY (asset_id) REFERENCES assets (id),
    FOREIGN KEY (owner_id) REFERENCES owners (id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    -- 按资产和占用日期查询分配记录。
    INDEX idx_allocations_asset (asset_id, start_date, available_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 库存占用：记录归还检查等暂不可供使用的数量。
CREATE TABLE holds (
    id VARCHAR(100) PRIMARY KEY, -- 占用记录标识
    asset_id VARCHAR(100) NOT NULL, -- 占用的资产标识
    quantity DOUBLE NOT NULL, -- 占用数量
    start_date VARCHAR(10) NOT NULL, -- 占用开始日期，格式 YYYY-MM-DD
    available_date VARCHAR(10), -- 恢复可供日期，占用不含当日；为空表示持续占用
    reason VARCHAR(500) NOT NULL, -- 占用原因
    document_id VARCHAR(100), -- 关联业务单据标识，可为空
    CHECK (quantity > 0),
    FOREIGN KEY (asset_id) REFERENCES assets (id),
    FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 归还明细：按原分配记录登记归还数量与接收仓库。
CREATE TABLE returns (
    id VARCHAR(100) PRIMARY KEY, -- 归还记录标识
    document_id VARCHAR(100) NOT NULL, -- 归还业务单据标识
    allocation_id VARCHAR(100) NOT NULL, -- 原分配记录标识
    quantity DOUBLE NOT NULL, -- 归还数量
    warehouse_id VARCHAR(100) NOT NULL, -- 归还接收仓库标识
    return_date VARCHAR(10) NOT NULL, -- 实际归还日期，格式 YYYY-MM-DD
    available_date VARCHAR(10), -- 归还后可供日期，格式 YYYY-MM-DD，可为空
    CHECK (quantity > 0),
    FOREIGN KEY (document_id) REFERENCES documents (id),
    FOREIGN KEY (allocation_id) REFERENCES allocations (id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 仓库调拨：在途数量占用来源资产，接收后转入目标仓库。
CREATE TABLE transfers (
    id VARCHAR(100) PRIMARY KEY, -- 调拨明细标识
    document_id VARCHAR(100) NOT NULL, -- 调拨业务单据标识
    asset_id VARCHAR(100) NOT NULL, -- 来源资产标识
    quantity DOUBLE NOT NULL, -- 调拨数量
    target_warehouse_id VARCHAR(100) NOT NULL, -- 目标仓库标识
    status VARCHAR(30) NOT NULL DEFAULT 'IN_TRANSIT', -- 调拨状态：IN_TRANSIT 在途、RECEIVED 已接收
    CHECK (quantity > 0),
    FOREIGN KEY (document_id) REFERENCES documents (id),
    FOREIGN KEY (asset_id) REFERENCES assets (id),
    FOREIGN KEY (target_warehouse_id) REFERENCES warehouses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 导入文件与预览记录。
CREATE TABLE imports (
    id VARCHAR(100) PRIMARY KEY, -- 导入记录标识
    filename VARCHAR(500) NOT NULL, -- 原始文件名
    stored_name VARCHAR(200) NOT NULL, -- 文件存储名称
    file_hash VARCHAR(64) NOT NULL, -- 文件内容哈希
    kind VARCHAR(30) NOT NULL, -- 导入类型
    sheets_json LONGTEXT NOT NULL, -- 工作表信息，JSON 数据
    preview_json LONGTEXT, -- 导入预览，JSON 数据，可为空
    created_by VARCHAR(100) NOT NULL, -- 上传用户标识
    created_at VARCHAR(32) NOT NULL, -- 上传时间
    CHECK (JSON_VALID(sheets_json)),
    CHECK (preview_json IS NULL OR JSON_VALID(preview_json)),
    FOREIGN KEY (created_by) REFERENCES users (id),
    -- 按文件哈希与导入类型查找导入记录。
    INDEX idx_imports_hash (file_hash, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 导入映射模板。
CREATE TABLE templates (
    id VARCHAR(100) PRIMARY KEY, -- 模板标识
    name VARCHAR(100) NOT NULL UNIQUE, -- 模板名称
    kind VARCHAR(500) NOT NULL, -- 适用导入类型
    mapping_json LONGTEXT NOT NULL, -- 字段映射配置，JSON 数据
    CHECK (JSON_VALID(mapping_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 单据附件：可关联整张单据或指定明细行。
CREATE TABLE attachments (
    id VARCHAR(100) PRIMARY KEY, -- 附件标识
    document_id VARCHAR(100) NOT NULL, -- 所属单据标识
    row_index INT, -- 关联明细行索引，可为空
    filename VARCHAR(500) NOT NULL, -- 原始文件名
    stored_name VARCHAR(200) NOT NULL, -- 文件存储名称
    mime_type VARCHAR(100) NOT NULL, -- 文件 MIME 类型
    size INT NOT NULL, -- 文件大小，单位字节
    created_by VARCHAR(100) NOT NULL, -- 上传用户标识
    created_at VARCHAR(32) NOT NULL, -- 上传时间
    FOREIGN KEY (document_id) REFERENCES documents (id),
    FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 操作审计：保存操作对象、前后数据及业务原因。
CREATE TABLE audit (
    id VARCHAR(100) PRIMARY KEY, -- 审计记录标识
    user_id VARCHAR(100) NOT NULL, -- 操作用户标识
    action VARCHAR(100) NOT NULL, -- 操作类型
    entity_id VARCHAR(100) NOT NULL, -- 操作对象标识
    before_json LONGTEXT, -- 操作前数据快照，可为空
    after_json LONGTEXT, -- 操作后数据快照，可为空
    reason TEXT NOT NULL, -- 操作原因
    created_at VARCHAR(32) NOT NULL, -- 操作时间
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 操作幂等记录：同一用户的同一幂等键复用结果，指纹用于识别请求冲突。
CREATE TABLE idempotency (
    `key` VARCHAR(100) NOT NULL, -- 请求幂等键
    user_id VARCHAR(100) NOT NULL, -- 操作用户标识，与幂等键共同组成主键
    fingerprint VARCHAR(64) NOT NULL, -- 请求指纹
    result_json LONGTEXT NOT NULL, -- 操作结果，JSON 数据
    PRIMARY KEY (`key`, user_id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    CHECK (JSON_VALID(result_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
