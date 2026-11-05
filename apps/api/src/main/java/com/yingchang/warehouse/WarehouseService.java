package com.yingchang.warehouse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import static com.yingchang.warehouse.Data.*;

@Service
public class WarehouseService {
    final Store store;
    private final Clock clock;
    public WarehouseService(Store store, Clock clock) { this.store = store; this.clock = clock; }
    public String today() { return LocalDate.now(clock).toString(); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Inventory inventory() { return new Inventory(store, today()); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Data document(String recordId) {
        Data d = store.required("documents", recordId);
        return d.with("payload", parse(d.s("payload_json")), "allocations", store.all("SELECT a.*,COALESCE(a.serial_snapshot,r.serial) serial FROM allocations a JOIN assets r ON r.id=a.asset_id WHERE a.document_id=?", recordId), "returns", store.all("SELECT * FROM returns WHERE document_id=?", recordId));
    }
    public Data create(Data input, Data actor) {
        return store.tx(() -> {
            String did = id(); Data p = input.obj("payload");
            validateCatalogReferences(p);
            store.run("INSERT INTO documents(id,number,type,status,business_date,crew_id,group_name,payload_json,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", did, input.s("number"), input.s("type"), "DRAFT", input.s("businessDate"), p.get("crewId"), p.s("groupName", "未分组"), json(p), actor.s("id"), now(), now());
            return document(did);
        });
    }
    public Data update(String recordId, int version, Data payload) {
        return store.tx(() -> {
            Data d = store.required("documents", recordId); checkVersion(d, version);
            if (!d.s("status").equals("DRAFT")) throw new BusinessError("DOCUMENT_CONFIRMED", "已确认单据请使用更正功能");
            validateCatalogReferences(payload);
            if (!d.s("crew_id").equals(payload.s("crewId"))) store.run("UPDATE documents SET related_crew_id=NULL WHERE id=?", recordId);
            store.run("UPDATE documents SET payload_json=?,crew_id=?,group_name=?,version=version+1,updated_at=? WHERE id=?", json(payload), payload.get("crewId"), payload.s("groupName", "未分组"), now(), recordId);
            return document(recordId);
        });
    }
    private void validateCatalogReferences(Data payload) {
        for (Data row : payload.rows("rows")) {
            if (!row.s("equipmentId").isEmpty()) store.required("equipment", row.s("equipmentId"));
            if (!row.s("categoryId").isEmpty()) store.required("categories", row.s("categoryId"));
        }
    }
    void checkVersion(Data d, int version) { if (d.i("version") != version) throw new BusinessError("VERSION_CONFLICT", "记录已被更新，请刷新后核对"); }
    void audit(Data actor, String action, String did, Object before, String reason) { store.audit(actor, action, did, before, document(did), reason); }
    String newAsset(String eid, int owner, String wh, double quantity, String serial) {
        if (quantity == 0) return null;
        String aid = id();
        store.run("INSERT INTO assets VALUES (?,?,?,?,?,?,?)", aid, eid, owner, wh, serial == null || serial.isEmpty() ? null : serial, quantity, now());
        return aid;
    }
    void saveAllocation(String did, Data a) {
        store.run("INSERT INTO allocations VALUES (?,?,?,?,?,?,?,?,?,?,?)", id(), did, a.get("equipment_id"), a.get("asset_id"), a.get("owner_id"), a.get("warehouse_id"), a.get("quantity"), a.get("returned_quantity"), a.get("start_date"), a.get("available_date"), store.required("assets", a.s("asset_id")).get("serial"));
    }
    String resolveEquipment(Data row) {
        if (!row.s("equipmentId").isEmpty()) return Inventory.activeEquipment(store, row.s("equipmentId")).s("id");
        if (!row.b("createEquipment") || row.s("rawName").isBlank()) throw new BusinessError("MATCH_REQUIRED", "请选择已有设备，或勾选新增设备", 422);
        String name = row.s("rawName").trim();
        Data existing = store.one("SELECT * FROM equipment WHERE name=?", name);
        if (existing != null) return Inventory.activeEquipment(store, existing.s("id")).s("id");
        String cat = row.s("category").trim(); if (cat.isEmpty()) cat = "其他";
        Data category = row.s("categoryId").isEmpty() ? store.one("SELECT * FROM categories WHERE name=?", cat) : store.required("categories", row.s("categoryId"));
        if (category == null) { category = of("id", id()); store.run("INSERT INTO categories(id,name) VALUES (?,?)", category.s("id"), cat); }
        String eid = id();
        store.run("INSERT INTO equipment(id,name,category_id,unit) VALUES (?,?,?,?)", eid, name, category.s("id"), row.s("unit", "件").trim());
        row.put("equipmentId", eid);
        return Inventory.activeEquipment(store, eid).s("id");
    }
    void intake(Data payload) {
        for (Data row : payload.rows("rows")) {
            if (row.b("unmanaged")) continue;
            String eid = resolveEquipment(row);
            List<Data> parts = inventory().sourceParts(row, true);
            if (row.n("quantity") <= 0) throw new BusinessError("QUANTITY_INVALID", "入库数量须大于零", 422);
            List<String> serials = row.strings("serials");
            if (!serials.isEmpty() && (serials.size() != row.n("quantity") || new HashSet<>(serials).size() != serials.size())) throw new BusinessError("SERIAL_COUNT", "编号数量不一致或重复", 422);
            if (!serials.isEmpty() && parts.stream().filter(p -> p.n("quantity") > 0).count() > 1) throw new BusinessError("SOURCE_SERIAL_REVIEW", "新设备混合来源且有编号，请按资产方拆行确认每个编号归属", 422);
            int n = 0;
            for (Data part : parts) {
                if (part.n("quantity") == 0) continue;
                String wh = row.s("warehouseId", payload.s("warehouseId"));
                if (wh.isEmpty() || wh.equals("unknown")) throw new BusinessError("WAREHOUSE_REQUIRED", "请选择接收仓", 422);
                if (!serials.isEmpty()) for (int k = 0; k < part.n("quantity"); k++) newAsset(eid, part.i("owner"), wh, 1, serials.get(n++));
                else newAsset(eid, part.i("owner"), wh, part.n("quantity"), null);
            }
        }
    }
    // 调用方事务内建立总库分类；已指定的类别及已有归类不自动覆盖。
    public Data initializeCategories(List<Data> rows) {
        int groupsCreated = 0, categoriesCreated = 0, categoriesAssigned = 0;
        Set<String> categories = new HashSet<>();
        for (Data row : rows) {
            if (row.b("unmanaged") || row.s("category").isBlank() || !row.s("categoryId").isEmpty()) continue;
            String name = row.s("category").trim(), groupName = row.s("categoryGroup").trim();
            if (groupName.isEmpty()) throw new BusinessError("CATEGORY_GROUP_REQUIRED", "请填写总库的设备大类", 422);
            Data group = store.one("SELECT * FROM category_groups WHERE name=?", groupName);
            if (group == null) { group = of("id", id()); store.run("INSERT INTO category_groups(id,name) VALUES (?,?)", group.s("id"), groupName); groupsCreated++; }
            Data category = store.one("SELECT * FROM categories WHERE name=?", name);
            if (category == null) {
                category = of("id", id(), "group_id", group.get("id"));
                store.run("INSERT INTO categories(id,name,group_id) VALUES (?,?,?)", category.s("id"), name, group.s("id")); categoriesCreated++;
            } else if (category.s("group_id").isEmpty()) {
                store.run("UPDATE categories SET group_id=? WHERE id=?", group.s("id"), category.s("id")); categoriesAssigned++;
            } else if (!category.s("group_id").equals(group.s("id"))) throw new BusinessError("CATEGORY_GROUP_CONFLICT", "“" + name + "”已归入其他大类，请先到管理页核对", 409, of("categoryId", category.get("id"), "groupId", category.get("group_id"), "sourceGroup", groupName));
            row.put("categoryId", category.get("id")); categories.add(category.s("id"));
        }
        return of("groupsCreated", groupsCreated, "categoriesCreated", categoriesCreated, "categoriesAssigned", categoriesAssigned, "categoryCount", categories.size());
    }
    private int initialOwner(Data row) { return row.i("ownerId") != 0 ? row.i("ownerId") : ownerTwo(row.s("partNo"), row.n("quantity")) > 0 ? 2 : 1; }
    public List<Data> initialReviews(String equipmentId) {
        List<Data> result = new ArrayList<>();
        for (Data document : store.all("SELECT id,number,version,payload_json FROM documents WHERE type='INITIAL' AND status='CONFIRMED'")) {
            List<Data> rows = parse(document.s("payload_json")).rows("rows");
            for (int i = 0; i < rows.size(); i++) {
                Data row = rows.get(i), initial = row.obj("initial");
                if (row.b("unmanaged") || !equipmentId.isEmpty() && !row.s("equipmentId").equals(equipmentId)) continue;
                double outside = initial.rows("outbounds").stream().mapToDouble(r -> r.n("quantity")).sum();
                Double total = initial.has("total") ? row.n("quantity") : null;
                double distributed = initial.n("beijing") + initial.n("hengdian") + outside;
                result.add(of("documentId", document.get("id"), "number", document.get("number"), "expectedVersion", document.get("version"), "rowIndex", i, "equipmentId", row.get("equipmentId"), "ownerId", initialOwner(row), "sourceRow", row.get("sourceRow"), "total", total, "beijing", initial.get("beijing"), "hengdian", initial.get("hengdian"), "outside", outside, "difference", total == null ? null : total - distributed, "needsReview", total == null || Math.abs(total - distributed) > 0.000001));
            }
        }
        return result;
    }
    public Data reviewInitial(String equipmentId, String documentId, int rowIndex, Data input, Data actor) {
        return store.tx(() -> {
            Data before = document(documentId); checkVersion(before, input.i("expectedVersion"));
            if (!before.s("type").equals("INITIAL") || !before.s("status").equals("CONFIRMED")) throw new BusinessError("STATE_INVALID", "仅已入账期初记录可核对", 422);
            Data payload = before.obj("payload"); List<Data> rows = payload.rows("rows");
            if (rowIndex < 0 || rowIndex >= rows.size()) throw new BusinessError("NOT_FOUND", "期初明细不存在", 404);
            Data row = rows.get(rowIndex), initial = row.obj("initial");
            if (row.b("unmanaged") || !row.s("equipmentId").equals(equipmentId)) throw new BusinessError("INITIAL_EQUIPMENT", "期初明细与设备不匹配", 422);
            Data snapshot = copy(row);
            for (String warehouse : List.of("beijing", "hengdian")) {
                double delta = input.n(warehouse) - initial.n(warehouse);
                if (Math.abs(delta) <= 0.000001) continue;
                String assetId = initial.s(warehouse + "AssetId");
                if (assetId.isEmpty()) {
                    if (initial.n(warehouse) > 0) throw new BusinessError("INITIAL_ASSET_REVIEW", "原期初资产未关联，请先核对设备明细", 422);
                    initial.put(warehouse + "AssetId", newAsset(equipmentId, initialOwner(row), warehouse, delta, null));
                } else {
                    Data asset = store.required("assets", assetId);
                    if (!asset.s("equipment_id").equals(equipmentId) || asset.i("owner_id") != initialOwner(row) || !asset.s("warehouse_id").equals(warehouse) || !asset.s("serial").isEmpty()) throw new BusinessError("INITIAL_ASSET_REVIEW", "期初资产编号或归属已变更，请先核对设备明细", 422);
                    Inventory inv = inventory();
                    if (delta < 0 && (inv.physical(asset) < -delta || inv.free(asset, today(), "9999-12-31") < -delta)) throw new BusinessError("ASSET_IN_USE", "减少数量会影响已确认出库或预留，请先核对", 422);
                    store.run("UPDATE assets SET quantity=quantity+? WHERE id=?", delta, assetId);
                }
                initial.put(warehouse, input.get(warehouse));
            }
            row.put("quantity", input.get("total")); initial.put("total", input.get("total"));
            store.run("UPDATE documents SET payload_json=?,version=version+1,updated_at=? WHERE id=?", json(payload), now(), documentId);
            store.audit(actor, "INITIAL_REVIEW", documentId, snapshot, row, input.s("reason"));
            return of("ok", true);
        });
    }
    void initial(String did, Data payload, Data actor) {
        List<Data> rows = payload.rows("rows"); initializeCategories(rows);
        for (Data r : rows) {
            if (r.b("unmanaged")) continue;
            String eid = resolveEquipment(r); int owner = initialOwner(r);
            if (r.has("categoryId") && !store.required("equipment", eid).s("category_id").equals(r.s("categoryId"))) throw new BusinessError("EQUIPMENT_CATEGORY_CONFLICT", "“" + r.s("rawName", "设备") + "”已有其他类别，请先到管理页核对", 409);
            Data v = r.obj("initial");
            if (!r.has("initial") || r.n("quantity") < 0 || !v.has("beijing") || !v.has("hengdian") || v.n("beijing") < 0 || v.n("hengdian") < 0 || v.rows("outbounds").stream().anyMatch(x -> x.n("quantity") < 0)) throw new BusinessError("INITIAL_REVIEW", "请填写资产总数、北京、横店及在外数量；没有的填0", 422);
            if (store.one("SELECT id FROM assets WHERE equipment_id=? AND owner_id=? LIMIT 1", eid, owner) != null) throw new BusinessError("INITIAL_EXISTS", "该设备及资产方已有记录，不能重复初始化");
        }
        Map<List<String>, Data> opening = new LinkedHashMap<>();
        String businessDate = store.required("documents", did).s("business_date");
        for (Data r : rows) {
            if (r.b("unmanaged")) continue;
            String eid = r.s("equipmentId");
            if (eid.isEmpty()) eid = store.one("SELECT id FROM equipment WHERE name=?", r.s("rawName")).s("id");
            int owner = initialOwner(r); Data v = r.obj("initial");
            // 差额保留在期初快照中；库存仅按实际分仓和在外数量建立。
            r.put("equipmentId", eid);
            v.with("beijingAssetId", newAsset(eid, owner, "beijing", v.n("beijing"), null), "hengdianAssetId", newAsset(eid, owner, "hengdian", v.n("hengdian"), null));
            for (Data out : v.rows("outbounds")) {
                if (out.n("quantity") <= 0) continue;
                String raw = out.s("name").trim();
                if (raw.isEmpty()) throw new BusinessError("CREW_REQUIRED", "在外记录必须填写剧组或借用方", 422);
                var match = Pattern.compile("^《(.+?)》\\s*(.*)$").matcher(raw); boolean matched = match.matches();
                boolean other = out.s("kind", matched ? "CREW" : "OTHER").equals("OTHER");
                String name = other ? raw : matched ? match.group(1) : raw;
                String group = other ? "未分组" : out.s("groupName", matched ? match.group(2) : "未分组").trim();
                if (group.isEmpty()) group = "未分组";
                Data crew = out.s("crewId").isEmpty() ? store.one("SELECT * FROM crews WHERE name=?", name) : store.required("crews", out.s("crewId"));
                if (crew == null) { crew = of("id", id()); store.run("INSERT INTO crews(id,name,kind,status,groups_json) VALUES (?,?,?,?,?)", crew.s("id"), name, other ? "COMPANY" : "CREW", "ACTIVE", json(List.of(group))); }
                else {
                    if (!crew.s("kind").equals(other ? "COMPANY" : "CREW")) throw new BusinessError("DESTINATION_KIND", "出库对象类型不一致，请核对去向：" + raw, 422);
                    List<String> groups = new ArrayList<>(crewGroups(crew));
                    if (!groups.contains(group)) { groups.add(group); store.run("UPDATE crews SET groups_json=? WHERE id=?", json(groups), crew.s("id")); }
                }
                String related = other ? out.s("relatedCrewId") : "";
                if (!related.isEmpty()) requireRelatedCrew(related);
                List<String> key = List.of(crew.s("id"), group, related);
                Data doc = opening.get(key);
                if (doc == null) {
                    String oid = id();
                    Data p = of("rows", new ArrayList<Data>(), "openingDocumentId", did, "outboundKind", other ? "OTHER" : "CREW", "recipientName", crew.s("name", name), "note", "期初在外，原发货仓待核实");
                    store.run("INSERT INTO documents(id,number,type,status,business_date,crew_id,related_crew_id,group_name,payload_json,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", oid, "OPEN-" + oid.substring(0, 12), "OUTBOUND", "OUT", businessDate, crew.s("id"), related.isEmpty() ? null : related, group, json(p), actor.s("id"), now(), now());
                    doc = of("id", oid, "payload", p); opening.put(key, doc);
                }
                String oid = doc.s("id"), aid = newAsset(eid, owner, "unknown", out.n("quantity"), null);
                List<Data> detail = doc.obj("payload").rows("rows");
                detail.add(of("equipmentId", eid, "rawName", r.s("rawName"), "quantity", out.get("quantity"), "ownerId", owner, "unit", r.s("unit"), "sourceRow", r.get("sourceRow"), "sheet", r.get("sheet")));
                doc.obj("payload").put("rows", detail);
                saveAllocation(oid, of("asset_id", aid, "equipment_id", eid, "owner_id", owner, "warehouse_id", "unknown", "quantity", out.get("quantity"), "returned_quantity", 0, "start_date", businessDate, "available_date", out.get("availableDate")));
            }
        }
        for (Data doc : opening.values()) store.run("UPDATE documents SET payload_json=? WHERE id=?", json(doc.obj("payload")), doc.s("id"));
    }
    @SuppressWarnings("unchecked") List<String> crewGroups(Data crew) { return (List<String>) parseValue(crew.s("groups_json")); }
    void requireRelatedCrew(String crewId) {
        if (!store.required("crews", crewId).s("kind").equals("CREW")) throw new BusinessError("RELATED_CREW_INVALID", "关联对象须为剧组", 422);
    }
    public Data relateCrew(String recordId, Data input, Data actor) {
        return store.tx(() -> {
            Data before = document(recordId); checkVersion(before, input.i("expectedVersion"));
            Data recipient = before.s("crew_id").isEmpty() ? null : store.required("crews", before.s("crew_id"));
            if (!before.s("type").equals("OUTBOUND") || recipient == null || !recipient.s("kind").equals("COMPANY")) throw new BusinessError("OUTBOUND_REQUIRED", "仅其他出库单可关联剧组", 422);
            String related = input.s("relatedCrewId");
            if (!related.isEmpty()) requireRelatedCrew(related);
            store.run("UPDATE documents SET related_crew_id=?,version=version+1,updated_at=? WHERE id=?", related.isEmpty() ? null : related, now(), recordId);
            audit(actor, "RELATE_CREW", recordId, before, "关联剧组"); return document(recordId);
        });
    }
    public Data confirm(String recordId, int version, String key, Data actor) {
        return store.tx(() -> {
            String fp = hash(json(of("operation", "confirm", "recordId", recordId, "version", version)));
            Data previous = store.one("SELECT * FROM idempotency WHERE `key`=? AND user_id=?", key, actor.s("id"));
            if (previous != null) {
                if (!previous.s("fingerprint").equals(fp)) throw new BusinessError("IDEMPOTENCY_CONFLICT", "该确认标识已用于其他操作");
                return parse(previous.s("result_json"));
            }
            Data before = document(recordId), d = store.required("documents", recordId); checkVersion(d, version);
            if (!d.s("status").equals("DRAFT")) throw new BusinessError("ALREADY_CONFIRMED", "单据已确认，不能重复入账");
            Data p = parse(d.s("payload_json"));
            if (p.rows("rows").isEmpty()) throw new BusinessError("EMPTY_DOCUMENT", "请添加明细", 422);
            if (!actor.s("role").equals("ADMIN") && p.rows("rows").stream().anyMatch(r -> r.b("createEquipment") && r.s("equipmentId").isEmpty())) throw new BusinessError("FORBIDDEN", "新建设备资料需要管理员权限", 403);
            if (p.has("importId")) {
                Data upload = store.required("imports", p.s("importId"));
                if (!upload.s("kind").equals(d.s("type"))) throw new BusinessError("IMPORT_KIND", "上传业务与单据类型不一致", 422);
                if (store.one("SELECT d.id FROM documents d JOIN imports i ON i.id=JSON_UNQUOTE(JSON_EXTRACT(d.payload_json,'$.importId')) WHERE d.id<>? AND d.status NOT IN ('DRAFT','CANCELLED') AND i.file_hash=? AND i.kind=?", recordId, upload.get("file_hash"), upload.get("kind")) != null) throw new BusinessError("IMPORT_DUPLICATE", "该上传批次已入账，请使用更正");
            }
            if (d.s("business_date").compareTo(today()) > 0 && !d.s("type").equals("RESERVATION")) throw new BusinessError("FUTURE_DOCUMENT", "尚未到业务日期，请先登记预留，实际出入库当天再入账", 422);
            if (!d.s("type").equals("INITIAL") && d.s("business_date").compareTo(today()) < 0) throw new BusinessError("HISTORICAL_REVIEW", "暂不支持历史出库补录，请核对期初记录", 422);
            String status = "CONFIRMED";
            switch (d.s("type")) {
                case "PURCHASE" -> intake(p);
                case "INITIAL" -> { if (!actor.s("role").equals("ADMIN")) throw new BusinessError("FORBIDDEN", "期初确认需要管理员", 403); initial(recordId, p, actor); }
                case "OUTBOUND", "RESERVATION" -> {
                    if (d.s("crew_id").isEmpty()) throw new BusinessError("CREW_REQUIRED", "请选择剧组或外借公司", 422);
                    Data c = store.required("crews", d.s("crew_id"));
                    if (!((List<?>) parseValue(c.s("groups_json"))).contains(d.s("group_name"))) throw new BusinessError("GROUP_INVALID", "组别不属于所选剧组", 422);
                    p.put("startDate", p.s("startDate", d.s("business_date")));
                    if (d.s("type").equals("OUTBOUND") && !p.s("startDate").equals(d.s("business_date"))) throw new BusinessError("OUTBOUND_DATE", "实际出库日须与业务日期一致", 422);
                    List<Data> plans = inventory().plan(p, "", d.s("type").equals("OUTBOUND"));
                    for (Data a : plans) saveAllocation(recordId, a);
                    status = d.s("type").equals("RESERVATION") ? "RESERVED" : plans.isEmpty() ? "CONFIRMED" : "OUT";
                }
                case "RETURN" -> {
                    returnItems(recordId, p, d.s("business_date"));
                    List<Data> related = store.all("SELECT DISTINCT d.crew_id,d.group_name FROM returns r JOIN allocations a ON a.id=r.allocation_id JOIN documents d ON d.id=a.document_id WHERE r.document_id=?", recordId);
                    if (related.size() == 1) store.run("UPDATE documents SET crew_id=?,group_name=? WHERE id=?", related.get(0).get("crew_id"), related.get(0).get("group_name"), recordId);
                }
                case "TRANSFER" -> transfer(recordId, p);
                default -> throw new BusinessError("TYPE_INVALID", "不支持的单据类型", 400);
            }
            store.run("UPDATE documents SET status=?,payload_json=?,version=version+1,updated_at=? WHERE id=?", status, json(p), now(), recordId);
            audit(actor, "CONFIRM_" + d.s("type"), recordId, before, "");
            Data result = document(recordId);
            store.run("INSERT INTO idempotency VALUES (?,?,?,?)", key, actor.s("id"), fp, json(result));
            return result;
        });
    }
    void returnItems(String did, Data p, String date) {
        String available = p.s("availableDate");
        if (available.isEmpty() || available.compareTo(dayAfter(date)) < 0) throw new BusinessError("RETURN_DATE", "归还设备最早次日可再次发出", 422);
        for (Data r : p.rows("rows")) {
            if (r.b("unmanaged")) continue;
            if (r.s("allocationId").isEmpty()) throw new BusinessError("RETURN_REFERENCE", "请选择对应的原出库明细", 422);
            Data a = store.required("allocations", r.s("allocationId")), doc = store.required("documents", a.s("document_id")), resource = store.required("assets", a.s("asset_id"));
            if (!doc.s("status").equals("OUT") || r.n("quantity") <= 0 || r.n("quantity") > a.n("quantity") - a.n("returned_quantity")) throw new BusinessError("RETURN_EXCESS", "归还数量超过原单未归还数量");
            if (r.has("equipmentId") && !r.s("equipmentId").equals(a.s("equipment_id"))) throw new BusinessError("RETURN_EQUIPMENT", "归还设备与原单不一致", 422);
            if (r.has("partNo") || r.has("ownerId") || r.has("owner2Quantity"))
                if (inventory().sourceParts(r, true).stream().anyMatch(part -> part.n("quantity") > 0 && part.i("owner") != a.i("owner_id"))) throw new BusinessError("OWNER_CONFLICT", "归还数量必须全部沿用原出库资产归属", 422);
            r.put("ownerId", a.get("owner_id")); r.remove("owner2Quantity"); r.remove("partNo"); r.put("equipmentId", a.get("equipment_id"));
            String wh = r.s("warehouseId", p.s("warehouseId"));
            if (wh.isEmpty() || wh.equals("unknown")) throw new BusinessError("WAREHOUSE_REQUIRED", "请选择接收仓", 422);
            String aid = resource.s("id");
            if (!wh.equals(resource.s("warehouse_id"))) {
                store.run("UPDATE assets SET quantity=quantity-? WHERE id=?", r.get("quantity"), resource.get("id"));
                if (resource.has("serial")) store.run("UPDATE assets SET serial=NULL WHERE id=?", resource.get("id"));
                aid = newAsset(resource.s("equipment_id"), resource.i("owner_id"), wh, r.n("quantity"), resource.s("serial"));
            }
            store.run("UPDATE allocations SET returned_quantity=returned_quantity+? WHERE id=?", r.get("quantity"), a.get("id"));
            store.run("INSERT INTO holds VALUES (?,?,?,?,?,?,?)", id(), aid, r.get("quantity"), date, available, "归还检查", did);
            store.run("INSERT INTO returns VALUES (?,?,?,?,?,?,?)", id(), did, a.get("id"), r.get("quantity"), wh, date, available);
            if (store.one("SELECT id FROM allocations WHERE document_id=? AND quantity>returned_quantity", a.get("document_id")) == null) store.run("UPDATE documents SET status='CLOSED',version=version+1,updated_at=? WHERE id=?", now(), a.get("document_id"));
            else store.run("UPDATE documents SET version=version+1,updated_at=? WHERE id=?", now(), a.get("document_id"));
        }
    }
    public Data dispatch(String recordId, int version, Data actor) {
        return store.tx(() -> {
            Data before = document(recordId); checkVersion(before, version);
            if (!before.s("status").equals("RESERVED")) throw new BusinessError("STATE_INVALID", "仅已预留单据可确认发货");
            for (Data a : before.rows("allocations")) {
                if (a.s("start_date").compareTo(today()) > 0) throw new BusinessError("DISPATCH_EARLY", "尚未到计划发货日期");
                if (!a.has("available_date") || a.s("available_date").compareTo(today()) <= 0) throw new BusinessError("DISPATCH_PERIOD", "预留归期已过，请先核实新的可供日期");
                Inventory inv = inventory(); Data resource = inv.assets.stream().filter(r -> r.s("id").equals(a.s("asset_id"))).findFirst().orElseThrow();
                if (inv.physical(resource) < a.n("quantity") || inv.free(resource, today(), a.s("available_date"), recordId) < a.n("quantity")) throw new BusinessError("DISPATCH_CONFLICT", "设备尚未回库、仍在检查或存在冲突");
            }
            store.run("UPDATE documents SET status='OUT',type='OUTBOUND',business_date=?,version=version+1,updated_at=? WHERE id=?", today(), now(), recordId);
            audit(actor, "DISPATCH", recordId, before, ""); return document(recordId);
        });
    }
    public Data cancel(String recordId, int version, Data actor) {
        return store.tx(() -> {
            Data before = document(recordId); checkVersion(before, version);
            if (!Set.of("DRAFT", "RESERVED").contains(before.s("status"))) throw new BusinessError("STATE_INVALID", "设备已出库，请办理归还；记录有误请使用更正");
            store.run("UPDATE documents SET status='CANCELLED',version=version+1,updated_at=? WHERE id=?", now(), recordId);
            audit(actor, "CANCEL", recordId, before, ""); return document(recordId);
        });
    }
    public Data extend(String recordId, int version, String availableDate, String reason, Data actor) {
        return store.tx(() -> {
            Data before = document(recordId); checkVersion(before, version);
            if (!Set.of("OUT", "RESERVED").contains(before.s("status"))) throw new BusinessError("STATE_INVALID", "仅在外或预留记录可延期");
            if (availableDate.compareTo(today()) <= 0 || before.rows("allocations").stream().anyMatch(a -> a.has("available_date") && availableDate.compareTo(a.s("available_date")) <= 0)) throw new BusinessError("PERIOD_INVALID", "新可供日须晚于原日期及今天", 422);
            store.run("UPDATE allocations SET available_date=? WHERE document_id=? AND quantity>returned_quantity", availableDate, recordId);
            Data p = before.obj("payload"); p.put("availableDate", availableDate);
            store.run("UPDATE documents SET payload_json=?,version=version+1,updated_at=? WHERE id=?", json(p), now(), recordId);
            audit(actor, "EXTEND", recordId, before, reason); return of("document", document(recordId), "conflicts", inventory().conflicts());
        });
    }
    public Data correct(String recordId, int version, List<Data> rows, String reason, Data actor) {
        return store.tx(() -> {
            Data before = document(recordId); checkVersion(before, version);
            if (!Set.of("OUT", "RESERVED").contains(before.s("status"))) throw new BusinessError("STATE_INVALID", "此记录暂不支持分配更正");
            if (before.rows("allocations").stream().anyMatch(a -> a.n("returned_quantity") > 0)) throw new BusinessError("PARTIAL_RETURN_REVIEW", "此单已有归还记录，暂不支持整单更正，请分笔核对", 422);
            Data p = new Data(before.obj("payload")).with("rows", rows, "startDate", today());
            List<Data> plans = inventory().plan(p, recordId, before.s("status").equals("OUT"));
            store.run("DELETE FROM allocations WHERE document_id=?", recordId);
            for (Data a : plans) saveAllocation(recordId, a.with("start_date", before.rows("allocations").isEmpty() ? today() : before.rows("allocations").get(0).get("start_date")));
            store.run("UPDATE documents SET payload_json=?,version=version+1,updated_at=? WHERE id=?", json(new Data(before.obj("payload")).with("rows", rows)), now(), recordId);
            audit(actor, "CORRECTION", recordId, before, reason); return document(recordId);
        });
    }
    void transfer(String did, Data p) {
        for (Data row : p.rows("rows")) {
            if (row.b("unmanaged")) continue;
            if (row.s("assetId").isEmpty()) throw new BusinessError("ASSET_REQUIRED", "调拨需要具体实物或数量批次", 422);
            Inventory inv = inventory(); Data a = inv.assets.stream().filter(x -> x.s("id").equals(row.s("assetId"))).findFirst().orElse(null);
            if (a == null || p.s("targetWarehouseId").isEmpty() || p.s("targetWarehouseId").equals(a.s("warehouse_id")) || p.s("targetWarehouseId").equals("unknown")) throw new BusinessError("WAREHOUSE_INVALID", "请选择不同的实际接收仓", 422);
            if (inv.free(a, today(), "9999-12-31") < row.n("quantity") || inv.physical(a) < row.n("quantity")) throw new BusinessError("TRANSFER_CONFLICT", "数量不足或存在已确认预留");
            store.run("INSERT INTO transfers VALUES (?,?,?,?,?,?)", id(), did, a.get("id"), row.get("quantity"), p.s("targetWarehouseId"), "IN_TRANSIT");
        }
    }
    public Data receive(String recordId, int version, Data actor) {
        return store.tx(() -> {
            Data before = document(recordId); checkVersion(before, version);
            List<Data> transfers = store.all("SELECT * FROM transfers WHERE document_id=? AND status='IN_TRANSIT'", recordId);
            if (transfers.isEmpty()) throw new BusinessError("TRANSFER_RECEIVED", "没有待接收的调拨");
            for (Data t : transfers) {
                Data a = store.required("assets", t.s("asset_id"));
                store.run("UPDATE assets SET quantity=quantity-? WHERE id=?", t.get("quantity"), a.get("id"));
                if (a.has("serial")) store.run("UPDATE assets SET serial=NULL WHERE id=?", a.get("id"));
                newAsset(a.s("equipment_id"), a.i("owner_id"), t.s("target_warehouse_id"), t.n("quantity"), a.s("serial"));
                store.run("UPDATE transfers SET status='RECEIVED' WHERE id=?", t.get("id"));
            }
            store.run("UPDATE documents SET version=version+1,updated_at=? WHERE id=?", now(), recordId);
            audit(actor, "TRANSFER_RECEIVE", recordId, before, ""); return document(recordId);
        });
    }
    public Data assetAction(String assetId, String action, double quantity, String reason, Data actor) {
        return store.tx(() -> {
            Data before = store.required("assets", assetId); Inventory inv = inventory();
            Data a = inv.assets.stream().filter(r -> r.s("id").equals(assetId)).findFirst().orElseThrow();
            double delta = action.equals("ADJUST") ? quantity - inv.physical(a) : -quantity;
            if (!action.equals("ADJUST") && quantity <= 0) throw new BusinessError("QUANTITY_INVALID", "处置数量须大于零", 422);
            if (delta < 0 && (inv.free(a, today(), "9999-12-31") < -delta || inv.physical(a) < -delta)) throw new BusinessError("ASSET_IN_USE", "数量不足或影响已确认占用，请先核对");
            if (a.has("serial") && a.n("quantity") + delta > 1) throw new BusinessError("SERIAL_QUANTITY", "单件编号不能对应多件设备", 422);
            store.run("UPDATE assets SET quantity=quantity+? WHERE id=?", delta, assetId);
            Data after = store.required("assets", assetId); String did = id();
            Data row = of("equipmentId", a.get("equipment_id"), "rawName", a.get("name"), "quantity", Math.abs(delta), "ownerId", a.get("owner_id"), "warehouseId", a.get("warehouse_id"), "note", reason);
            store.run("INSERT INTO documents(id,number,type,status,business_date,payload_json,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)", did, action + "-" + did.substring(0, 12), action, "CONFIRMED", today(), json(of("rows", List.of(row), "reason", reason, "assetId", assetId)), actor.s("id"), now(), now());
            store.audit(actor, action, assetId, before, after, reason);
            return after.with("documentId", did);
        });
    }
    public Data correctAsset(String assetId, Data b, Data actor) {
        return store.tx(() -> {
            Data before = store.required("assets", assetId);
            if (!b.s("serial").isEmpty() && before.n("quantity") != 1) throw new BusinessError("SERIAL_QUANTITY", "请先拆分数量批次，再给单件设备编号", 422);
            if (store.one("SELECT a.id FROM allocations a JOIN documents d ON d.id=a.document_id WHERE a.asset_id=? AND a.returned_quantity>0 AND d.status='OUT'", assetId) != null) throw new BusinessError("PARTIAL_RETURN_REVIEW", "存在部分归还，请分笔核对归属", 422);
            store.run("UPDATE assets SET owner_id=?,serial=? WHERE id=?", b.get("ownerId"), b.s("serial").isEmpty() ? null : b.s("serial"), assetId);
            store.run("UPDATE allocations SET owner_id=? WHERE asset_id=? AND document_id IN (SELECT id FROM documents WHERE status IN ('OUT','RESERVED'))", b.get("ownerId"), assetId);
            Data after = store.required("assets", assetId); store.audit(actor, "ASSET_CORRECTION", assetId, before, after, b.s("reason")); return after;
        });
    }
}
