package com.yingchang.warehouse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import static com.yingchang.warehouse.Data.*;

@Repository
public class Store {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private static final Set<String> TABLES = Set.of("equipment", "category_groups", "categories", "crews", "assets", "documents", "imports", "allocations", "transfers", "attachments");
    public Store(JdbcTemplate jdbc, PlatformTransactionManager manager) { this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager); }
    public List<Data> all(String sql, Object... args) { return jdbc.queryForList(sql, args).stream().map(Data::new).toList(); }
    public Data one(String sql, Object... args) { List<Data> rows = all(sql, args); return rows.isEmpty() ? null : rows.get(0); }
    public int run(String sql, Object... args) { return jdbc.update(sql, args); }
    public <T> T tx(Supplier<T> work) {
        return transactions.execute(status -> {
            // 库存计算跨资产、占用及检查期；统一写锁延续原整单串行确认语义，跨进程同样生效。
            one("SELECT id FROM warehouse_lock WHERE id=1 FOR UPDATE");
            return work.get();
        });
    }
    public Data required(String table, String recordId) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Invalid internal table");
        Data row = one("SELECT * FROM " + table + " WHERE id=?", recordId);
        if (row == null) throw new BusinessError("NOT_FOUND", "记录不存在", 404);
        return row;
    }
    public void audit(Data actor, String action, String entityId, Object before, Object after, String reason) {
        run("INSERT INTO audit VALUES (?,?,?,?,?,?,?,?)", id(), actor.s("id"), action, entityId, before == null ? null : json(before), after == null ? null : json(after), reason, now());
    }
}
