package com.yingchang.warehouse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.*;
import static com.yingchang.warehouse.Data.*;

@Component
public class Bootstrap implements ApplicationRunner {
    private final Store store;
    private final ConfigurableApplicationContext context;
    private final String username, password;
    static final List<String> TABLES = List.of("users", "owners", "warehouses", "category_groups", "categories", "equipment", "aliases", "crews", "assets", "documents", "allocations", "holds", "returns", "transfers", "imports", "templates", "attachments", "audit", "idempotency");
    public Bootstrap(Store store, ConfigurableApplicationContext context, @Value("${warehouse.admin-username}") String username, @Value("${warehouse.admin-password}") String password) { this.store = store; this.context = context; this.username = username; this.password = password; }
    @Override public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("migration-file")) {
            String path = args.getOptionValues("migration-file").get(0);
            importData(parse(Files.readString(Path.of(path))));
            System.out.println("SQLite迁移完成，请核对各表数量及文件目录后启动服务。");
            context.close(); return;
        }
        store.tx(() -> {
            if (store.one("SELECT id FROM users LIMIT 1") != null) return null;
            if (password.length() < 12) throw new IllegalStateException("首次启动请在.env中设置至少12位ADMIN_PASSWORD");
            store.run("INSERT INTO users(id,username,password_hash,role) VALUES (?,?,?,?)", id(), username, Auth.passwordHash(password), "ADMIN");
            return null;
        });
    }
    void importData(Data exported) {
        if (!exported.s("format").equals("warehouse-sqlite-v1")) throw new IllegalArgumentException("迁移文件格式无效");
        Data tables = exported.obj("tables");
        if (!tables.keySet().equals(new HashSet<>(TABLES)) || tables.rows("users").isEmpty()) throw new IllegalArgumentException("迁移文件缺少完整业务表或账号");
        store.tx(() -> {
            // 只允许导入空业务库；基础归属和仓库使用原库值，失败整批回滚。
            for (String table : TABLES) if (!Set.of("owners", "warehouses").contains(table) && store.one("SELECT 1 found FROM `" + table + "` LIMIT 1") != null) throw new IllegalStateException("目标数据库必须为空：" + table);
            store.run("DELETE FROM warehouses"); store.run("DELETE FROM owners");
            for (String table : TABLES) {
                Set<String> columns = new HashSet<>();
                store.all("SHOW COLUMNS FROM `" + table + "`").forEach(c -> columns.add(c.s("Field")));
                for (Data row : tables.rows(table)) {
                    if (row.isEmpty() || !columns.containsAll(row.keySet())) throw new IllegalArgumentException("迁移字段无效：" + table);
                    String names = String.join(",", row.keySet().stream().map(c -> "`" + c + "`").toList());
                    String placeholders = String.join(",", Collections.nCopies(row.size(), "?"));
                    store.run("INSERT INTO `" + table + "` (" + names + ") VALUES (" + placeholders + ")", row.values().toArray());
                }
                int count = store.one("SELECT COUNT(*) total FROM `" + table + "`").i("total");
                if (count != tables.rows(table).size()) throw new IllegalStateException("迁移数量不一致：" + table);
            }
            return null;
        });
    }
}
