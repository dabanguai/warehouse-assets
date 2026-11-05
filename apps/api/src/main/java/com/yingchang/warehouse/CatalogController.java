package com.yingchang.warehouse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.yingchang.warehouse.Data.*;
import static com.yingchang.warehouse.Auth.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final Store store;
    private final WarehouseService service;
    private final RequestSchemas schemas;
    public CatalogController(Store store, WarehouseService service, RequestSchemas schemas) { this.store = store; this.service = service; this.schemas = schemas; }
    @GetMapping("/health") public Data health() { store.one("SELECT 1 healthy"); return of("ok", true); }
    @GetMapping("/meta") public Data meta() {
        return of("today", service.today(), "owners", store.all("SELECT * FROM owners"), "warehouses", store.all("SELECT * FROM warehouses"), "categoryGroups", store.all("SELECT * FROM category_groups ORDER BY name"), "categories", store.all("SELECT c.*,g.name group_name FROM categories c LEFT JOIN category_groups g ON g.id=c.group_id ORDER BY c.name"), "crews", crews(), "equipment", equipment(), "matchingMode", "rules-and-manual");
    }
    @GetMapping("/equipment") public List<Data> equipment() {
        List<Data> equipment = store.all("SELECT e.*,c.name category_name,c.group_id,g.name group_name,c.active category_active,COALESCE(g.active,1) group_active FROM equipment e JOIN categories c ON c.id=e.category_id LEFT JOIN category_groups g ON g.id=c.group_id ORDER BY e.name");
        return equipment.stream().map(e -> e.with("aliases", store.all("SELECT name FROM aliases WHERE equipment_id=?", e.s("id")).stream().map(a -> a.s("name")).toList())).toList();
    }
    @PostMapping("/equipment") public Data createEquipment(@RequestBody Data b, HttpServletRequest req) { return saveEquipment(null, b, req); }
    @PatchMapping("/equipment/{id}") public Data editEquipment(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { return saveEquipment(id, b, req); }
    private Data saveEquipment(String recordId, Data b, HttpServletRequest req) {
        Data a = guard(req, "ADMIN"); schemas.validate("equipment", b);
        return store.tx(() -> {
            String eid = recordId == null ? id() : recordId;
            Data before = recordId == null ? null : store.required("equipment", eid);
            int active = Boolean.FALSE.equals(b.get("active")) ? 0 : 1;
            if (recordId == null) store.run("INSERT INTO equipment(id,name,english_name,category_id,brand,model,specification,unit,active) VALUES (?,?,?,?,?,?,?,?,?)", eid, b.s("name").trim(), b.s("englishName"), b.s("categoryId"), b.s("brand").trim(), b.s("model").trim(), b.s("specification"), b.s("unit"), active);
            else store.run("UPDATE equipment SET name=?,english_name=?,category_id=?,brand=?,model=?,specification=?,unit=?,active=? WHERE id=?", b.s("name").trim(), b.s("englishName"), b.s("categoryId"), b.s("brand").trim(), b.s("model").trim(), b.s("specification"), b.s("unit"), active, eid);
            store.run("DELETE FROM aliases WHERE equipment_id=?", eid);
            for (String name : new LinkedHashSet<>(b.strings("aliases").stream().map(String::trim).filter(v -> !v.isEmpty()).toList())) store.run("INSERT INTO aliases VALUES (?,?,?)", id(), name, eid);
            Data result = store.required("equipment", eid); store.audit(a, recordId == null ? "CREATE_EQUIPMENT" : "EDIT_EQUIPMENT", eid, before, result, ""); return result;
        });
    }
    @PostMapping("/category-groups") public Data createGroup(@RequestBody Data b, HttpServletRequest req) {
        guard(req, "ADMIN"); schemas.validate("categoryGroup", b);
        return store.tx(() -> { String gid = id(); store.run("INSERT INTO category_groups(id,name,active) VALUES (?,?,?)", gid, b.s("name").trim(), Boolean.FALSE.equals(b.get("active")) ? 0 : 1); return store.required("category_groups", gid); });
    }
    @PatchMapping("/category-groups/{id}") public Data editGroup(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) {
        guard(req, "ADMIN"); schemas.validate("categoryGroupEdit", b);
        return store.tx(() -> { store.required("category_groups", id); store.run("UPDATE category_groups SET name=?,active=? WHERE id=?", b.s("name").trim(), b.b("active") ? 1 : 0, id); return store.required("category_groups", id); });
    }
    @PostMapping("/categories") public Data createCategory(@RequestBody Data b, HttpServletRequest req) {
        guard(req, "ADMIN"); schemas.validate("category", b);
        return store.tx(() -> { String cid = id(); store.run("INSERT INTO categories(id,name,active,group_id) VALUES (?,?,?,?)", cid, b.s("name").trim(), Boolean.FALSE.equals(b.get("active")) ? 0 : 1, b.get("groupId")); return store.required("categories", cid); });
    }
    @PatchMapping("/categories/{id}") public Data editCategory(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) {
        guard(req, "ADMIN"); schemas.validate("categoryEdit", b);
        return store.tx(() -> { store.required("categories", id); store.run("UPDATE categories SET name=?,active=?,group_id=? WHERE id=?", b.s("name").trim(), b.b("active") ? 1 : 0, b.get("groupId"), id); return store.required("categories", id); });
    }
    @DeleteMapping("/equipment/{id}") public Data deleteEquipment(@PathVariable String id, HttpServletRequest req) { return deleteCatalog("equipment", id, guard(req, "ADMIN")); }
    @DeleteMapping("/categories/{id}") public Data deleteCategory(@PathVariable String id, HttpServletRequest req) { return deleteCatalog("categories", id, guard(req, "ADMIN")); }
    @DeleteMapping("/category-groups/{id}") public Data deleteGroup(@PathVariable String id, HttpServletRequest req) { return deleteCatalog("category_groups", id, guard(req, "ADMIN")); }
    private Data deleteCatalog(String table, String recordId, Data actor) {
        return store.tx(() -> {
            Data before = store.required(table, recordId);
            if (table.equals("category_groups") && store.one("SELECT id FROM categories WHERE group_id=? LIMIT 1", recordId) != null) throw new BusinessError("CATALOG_IN_USE", "大类下仍有具体类别，请先调整类别或停用大类", 409);
            if (table.equals("categories") && store.one("SELECT id FROM equipment WHERE category_id=? LIMIT 1", recordId) != null) throw new BusinessError("CATALOG_IN_USE", "类别下仍有设备，请先调整设备归类或停用类别", 409);
            if (table.equals("equipment") && store.one("SELECT id FROM assets WHERE equipment_id=? LIMIT 1", recordId) != null) throw new BusinessError("CATALOG_IN_USE", "设备已有资产或库存记录，请使用停用", 409);
            // 草稿和历史单据的 JSON 明细同样属于业务引用，不能只依赖关系表外键。
            String field = table.equals("equipment") ? "equipmentId" : table.equals("categories") ? "categoryId" : "categoryGroupId";
            for (Data doc : store.all("SELECT number,payload_json FROM documents")) {
                if (references(parse(doc.s("payload_json")), field, recordId)) throw new BusinessError("CATALOG_IN_USE", "资料已被单据“" + doc.s("number") + "”引用，请使用停用", 409);
            }
            if (table.equals("equipment")) store.run("DELETE FROM aliases WHERE equipment_id=?", recordId);
            store.run("DELETE FROM " + table + " WHERE id=?", recordId);
            store.audit(actor, "DELETE_" + table.toUpperCase(Locale.ROOT), recordId, before, null, "");
            return of("deleted", true);
        });
    }
    private boolean references(Object value, String field, String recordId) {
        if (value instanceof Map<?, ?> map) return recordId.equals(map.get(field)) || map.values().stream().anyMatch(v -> references(v, field, recordId));
        if (value instanceof List<?> list) return list.stream().anyMatch(v -> references(v, field, recordId));
        return false;
    }
    @GetMapping("/crews") public List<Data> crews() { return store.all("SELECT * FROM crews").stream().map(c -> c.with("groups", parseValue(c.s("groups_json")))).toList(); }
    @PostMapping("/crews") public Data createCrew(@RequestBody Data b, HttpServletRequest req) { return saveCrew(null, b, req); }
    @PatchMapping("/crews/{id}") public Data editCrew(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { return saveCrew(id, b, req); }
    private Data saveCrew(String recordId, Data b, HttpServletRequest req) {
        guard(req, "ADMIN"); schemas.validate("crew", b);
        return store.tx(() -> {
            String cid = recordId == null ? id() : recordId;
            String groups = json(b.s("kind").equals("COMPANY") ? List.of("未分组") : b.strings("groups"));
            if (recordId == null) store.run("INSERT INTO crews VALUES (?,?,?,?,?,?,?)", cid, b.s("name").trim(), b.s("kind"), b.s("contact"), b.s("phone"), b.s("status", "PREPARING"), groups);
            else { store.required("crews", cid); store.run("UPDATE crews SET name=?,kind=?,contact=?,phone=?,status=?,groups_json=? WHERE id=?", b.s("name").trim(), b.s("kind"), b.s("contact"), b.s("phone"), b.s("status", "PREPARING"), groups, cid); }
            return store.required("crews", cid);
        });
    }
    @GetMapping("/inventory") public List<Data> inventory(@RequestParam(defaultValue="") String equipmentId, @RequestParam(defaultValue="0") int ownerId, @RequestParam(defaultValue="") String warehouseId) { return service.inventory().list(equipmentId, ownerId, warehouseId); }
    @GetMapping("/initial-reviews") public List<Data> initialReviews(@RequestParam(defaultValue="") String equipmentId) { return service.initialReviews(equipmentId); }
    @PatchMapping("/equipment/{id}/initial-reviews/{documentId}/{rowIndex}") public Data reviewInitial(@PathVariable String id, @PathVariable String documentId, @PathVariable int rowIndex, @RequestBody Data b, HttpServletRequest req) {
        Data actor = guard(req, "ADMIN"); schemas.validate("initialReview", b);
        return service.reviewInitial(id, documentId, rowIndex, b, actor);
    }
    @GetMapping("/equipment/{id}/assets") public List<Data> assets(@PathVariable String id) {
        Inventory inv = service.inventory(); return inv.assets.stream().filter(a -> a.s("equipment_id").equals(id)).map(a -> a.with("inStock", inv.physical(a), "available", inv.free(a, service.today(), dayAfter(service.today())))).toList();
    }
    @GetMapping("/equipment/{id}/schedule") public Data schedule(@PathVariable String id) {
        Inventory inv = service.inventory(); return of("allocations", inv.allocations.stream().filter(a -> a.s("equipment_id").equals(id)).toList(), "conflicts", inv.conflicts().stream().filter(c -> c.obj("resource").s("equipment_id").equals(id)).toList());
    }
    @PostMapping("/availability/query") public Data availability(@RequestBody Data b) { schemas.validate("payload", b); return service.inventory().query(b); }
    @GetMapping("/conflicts") public List<Data> conflicts() { return service.inventory().conflicts(); }
    @GetMapping("/crews/{id}/sources") public List<Data> sources(@PathVariable String id) {
        store.required("crews", id); return store.all("SELECT a.*,e.name,e.unit,COALESCE(a.serial_snapshot,r.serial) serial,d.status,d.number,d.group_name,d.crew_id,d.related_crew_id FROM allocations a JOIN documents d ON d.id=a.document_id JOIN equipment e ON e.id=a.equipment_id JOIN assets r ON r.id=a.asset_id WHERE d.crew_id=? OR d.related_crew_id=? ORDER BY a.start_date DESC", id, id);
    }
    @PostMapping("/assets/{id}/actions") public Data assetAction(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data actor = guard(req, "ADMIN"); schemas.validate("assetAction", b); return service.assetAction(id, b.s("action"), b.n("quantity"), b.s("reason"), actor); }
    @PatchMapping("/assets/{id}") public Data editAsset(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data actor = guard(req, "ADMIN"); schemas.validate("asset", b); return service.correctAsset(id, b, actor); }
    @GetMapping("/audit") public List<Data> audit() { return store.all("SELECT a.*,u.username FROM audit a JOIN users u ON u.id=a.user_id ORDER BY a.created_at DESC LIMIT 300").stream().map(a -> a.with("before", a.has("before_json") ? parseValue(a.s("before_json")) : null, "after", a.has("after_json") ? parseValue(a.s("after_json")) : null)).toList(); }
}
