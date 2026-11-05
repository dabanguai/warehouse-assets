package com.yingchang.warehouse;

import java.util.*;
import static com.yingchang.warehouse.Data.*;

public class Inventory {
    private final Store store;
    private final String date;
    final List<Data> assets, allocations, holds, transfers;
    public Inventory(Store store, String date) {
        this.store = store; this.date = date;
        assets = store.all("SELECT a.*,e.name,e.unit FROM assets a JOIN equipment e ON e.id=a.equipment_id");
        allocations = store.all("SELECT a.*,d.status,d.crew_id,d.related_crew_id,d.group_name FROM allocations a JOIN documents d ON d.id=a.document_id WHERE d.status IN ('OUT','RESERVED')");
        holds = store.all("SELECT * FROM holds");
        transfers = store.all("SELECT * FROM transfers WHERE status='IN_TRANSIT'");
    }
    String end(Data a) {
        return a.s("status").equals("OUT") && a.n("quantity") > a.n("returned_quantity") && (a.s("available_date").isEmpty() || a.s("available_date").compareTo(date) <= 0)
            ? "9999-12-31" : a.s("available_date", "9999-12-31");
    }
    double load(String assetId, String day, String exclude) {
        double total = 0;
        for (Data a : allocations)
            if (a.s("asset_id").equals(assetId) && !a.s("document_id").equals(exclude) && a.s("start_date").compareTo(day) <= 0 && day.compareTo(end(a)) < 0)
                total += a.n("quantity") - (a.s("status").equals("OUT") ? a.n("returned_quantity") : 0);
        for (Data h : holds)
            if (h.s("asset_id").equals(assetId) && h.s("start_date").compareTo(day) <= 0 && (!h.has("available_date") || day.compareTo(h.s("available_date")) < 0)) total += h.n("quantity");
        for (Data t : transfers) if (t.s("asset_id").equals(assetId)) total += t.n("quantity");
        return total;
    }
    public double free(Data asset, String start, String end) { return free(asset, start, end, ""); }
    public double free(Data asset, String start, String end, String exclude) {
        Set<String> dates = new HashSet<>(); dates.add(start);
        for (List<Data> rows : List.of(allocations, holds))
            for (Data row : rows) for (String key : List.of("start_date", "available_date")) {
                String day = row.s(key);
                if (!day.isEmpty() && day.compareTo(start) >= 0 && day.compareTo(end) < 0) dates.add(day);
            }
        return Math.max(0, dates.stream().mapToDouble(day -> asset.n("quantity") - load(asset.s("id"), day, exclude)).min().orElse(0));
    }
    public double physical(Data asset) { return physical(asset, ""); }
    public double physical(Data asset, String exclude) {
        return asset.n("quantity") - allocations.stream().filter(a -> a.s("asset_id").equals(asset.s("id")) && a.s("status").equals("OUT") && !a.s("document_id").equals(exclude)).mapToDouble(a -> a.n("quantity") - a.n("returned_quantity")).sum()
            - transfers.stream().filter(t -> t.s("asset_id").equals(asset.s("id"))).mapToDouble(t -> t.n("quantity")).sum();
    }
    List<Data> sourceParts(Data row, boolean requireSource) {
        if (row.has("owner2Quantity")) {
            if (row.n("owner2Quantity") < 0 || row.n("owner2Quantity") > row.n("quantity")) throw new BusinessError("SOURCE_INVALID", "2号库提供数量须在0至总数量之间", 400);
            return List.of(of("owner", 1, "quantity", row.n("quantity") - row.n("owner2Quantity")), of("owner", 2, "quantity", row.n("owner2Quantity")));
        }
        if (row.i("ownerId") != 0) return List.of(of("owner", row.i("ownerId"), "quantity", row.n("quantity")));
        if (row.has("partNo") || requireSource) {
            double two = ownerTwo(row.s("partNo"), row.n("quantity"));
            return List.of(of("owner", 1, "quantity", row.n("quantity") - two), of("owner", 2, "quantity", two));
        }
        return List.of(of("owner", 0, "quantity", row.n("quantity")));
    }
    static Data activeEquipment(Store store, String equipmentId) {
        Data equipment = store.one("SELECT e.*,c.active category_active,COALESCE(g.active,1) group_active FROM equipment e JOIN categories c ON c.id=e.category_id LEFT JOIN category_groups g ON g.id=c.group_id WHERE e.id=?", equipmentId);
        if (equipment == null) throw new BusinessError("NOT_FOUND", "设备资料不存在，请重新匹配", 404);
        if (equipment.i("active") == 0 || equipment.i("category_active") == 0 || equipment.i("group_active") == 0) throw new BusinessError("EQUIPMENT_DISABLED", "设备或所属类别已停用", 422);
        return equipment;
    }
    public List<Data> plan(Data payload, String exclude, boolean stockOnly) {
        String start = payload.s("startDate", date), end = payload.s("availableDate");
        if (end.isEmpty() || end.compareTo(start) <= 0) throw new BusinessError("PERIOD_INVALID", "请设置晚于发货日的可再次发出日期", 400);
        if (start.compareTo(date) < 0) throw new BusinessError("HISTORICAL_REVIEW", "当前版本不接受历史出库补录，请使用期初或人工核对", 422);
        List<Data> plans = new ArrayList<>();
        for (Data row : payload.rows("rows")) {
            if (row.b("unmanaged")) continue;
            if (row.s("equipmentId").isEmpty()) throw new BusinessError("MATCH_REQUIRED", "请先匹配或新增设备", 422);
            if (row.n("quantity") <= 0) throw new BusinessError("QUANTITY_INVALID", "设备数量须大于零", 400);
            Data equipment = activeEquipment(store, row.s("equipmentId"));
            List<String> serials = row.strings("serials");
            if (!serials.isEmpty() && (serials.size() != row.n("quantity") || new HashSet<>(serials).size() != serials.size())) throw new BusinessError("SERIAL_COUNT", "编号数量与出库数量不一致或存在重复编号", 422);
            String wh = row.s("warehouseId", payload.s("warehouseId"));
            if (wh.isEmpty() || wh.equals("unknown")) throw new BusinessError("WAREHOUSE_REQUIRED", "请选择实际发货仓库", 422);
            for (Data part : sourceParts(row, stockOnly)) {
                double need = part.n("quantity");
                List<Data> resources = assets.stream().filter(a -> a.s("equipment_id").equals(row.s("equipmentId")) && a.s("warehouse_id").equals(wh) && (part.i("owner") == 0 || a.i("owner_id") == part.i("owner")) && a.n("quantity") > 0 && (serials.isEmpty() || serials.contains(a.s("serial")))).sorted(Comparator.comparing(a -> a.s("id"))).toList();
                for (Data asset : resources) {
                    double allocated = plans.stream().filter(a -> a.s("asset_id").equals(asset.s("id"))).mapToDouble(a -> a.n("quantity")).sum();
                    double available = Math.max(0, Math.min(free(asset, start, end, exclude) - allocated, stockOnly ? physical(asset, exclude) - allocated : Double.POSITIVE_INFINITY));
                    double qty = Math.min(need, available);
                    if (qty <= 0) continue;
                    plans.add(of("asset_id", asset.get("id"), "equipment_id", asset.get("equipment_id"), "owner_id", asset.get("owner_id"), "warehouse_id", asset.get("warehouse_id"), "quantity", qty, "returned_quantity", 0, "start_date", start, "available_date", end));
                    need -= qty;
                    if (need <= 0) break;
                }
                if (need > 0.000001) throw new BusinessError("STOCK_CONFLICT", equipment.s("name") + "在完整租期内缺" + need + equipment.s("unit"), 409,
                    of("equipmentId", row.get("equipmentId"), "warehouseId", wh, "ownerId", part.i("owner") == 0 ? null : part.get("owner"), "missing", need, "startDate", start, "availableDate", end,
                    "conflicts", allocations.stream().filter(a -> a.s("equipment_id").equals(row.s("equipmentId")) && !a.s("document_id").equals(exclude) && a.s("start_date").compareTo(end) < 0 && end(a).compareTo(start) > 0).toList()));
            }
        }
        return plans;
    }
    public Data query(Data payload) {
        String start = payload.s("startDate", date), end = payload.s("availableDate");
        if (end.isEmpty() || end.compareTo(start) <= 0) throw new BusinessError("PERIOD_INVALID", "可再次发出日期须晚于发货日", 400);
        List<Data> rows = new ArrayList<>();
        for (Data row : payload.rows("rows")) {
            List<Data> locations = new ArrayList<>();
            if (!row.s("equipmentId").isEmpty()) for (int owner : List.of(1, 2)) for (String wh : List.of("beijing", "hengdian")) {
                double quantity = assets.stream().filter(a -> a.s("equipment_id").equals(row.s("equipmentId")) && a.i("owner_id") == owner && a.s("warehouse_id").equals(wh)).mapToDouble(a -> free(a, start, end)).sum();
                List<Data> dependencies = allocations.stream().filter(a -> a.s("equipment_id").equals(row.s("equipmentId")) && a.i("owner_id") == owner && a.s("warehouse_id").equals(wh) && a.s("status").equals("OUT") && a.n("quantity") > a.n("returned_quantity") && a.s("available_date").compareTo(date) > 0 && a.s("available_date").compareTo(start) <= 0)
                    .map(a -> of("documentId", a.get("document_id"), "crewId", a.get("crew_id"), "availableDate", a.get("available_date"))).toList();
                locations.add(of("ownerId", owner, "warehouseId", wh, "quantity", quantity, "dependencies", dependencies));
            }
            rows.add(of("row", row, "unmatched", row.s("equipmentId").isEmpty(), "locations", locations));
        }
        List<Data> plans = List.of(); Data issue = null;
        try { plans = plan(payload, "", false); }
        catch (BusinessError e) { issue = of("code", e.code, "message", e.getMessage(), "details", e.details); }
        return of("startDate", start, "availableDate", end, "measuredAt", now(), "rows", rows, "allocations", plans, "issue", issue);
    }
    public List<Data> list(String equipmentId, int ownerId, String warehouseId) {
        List<Data> result = new ArrayList<>();
        for (Data equipment : store.all("SELECT e.*,c.name category_name,c.group_id,g.name group_name FROM equipment e JOIN categories c ON c.id=e.category_id LEFT JOIN category_groups g ON g.id=c.group_id")) {
            if (!equipmentId.isEmpty() && !equipment.s("id").equals(equipmentId)) continue;
            List<Data> resources = assets.stream().filter(a -> a.s("equipment_id").equals(equipment.s("id")) && (ownerId == 0 || a.i("owner_id") == ownerId) && (warehouseId.isEmpty() || a.s("warehouse_id").equals(warehouseId))).toList();
            List<Data> breakdown = new ArrayList<>();
            for (int owner : List.of(1, 2)) for (String wh : List.of("beijing", "hengdian", "unknown")) {
                List<Data> items = resources.stream().filter(a -> a.i("owner_id") == owner && a.s("warehouse_id").equals(wh)).toList();
                List<Data> assigned = allocations.stream().filter(a -> items.stream().anyMatch(r -> r.s("id").equals(a.s("asset_id")))).toList();
                double outsideQuantity = assigned.stream().filter(a -> a.s("status").equals("OUT")).mapToDouble(a -> a.n("quantity") - a.n("returned_quantity")).sum();
                double reserved = assigned.stream().filter(a -> a.s("status").equals("RESERVED") && a.s("start_date").compareTo(date) <= 0 && date.compareTo(end(a)) < 0).mapToDouble(a -> a.n("quantity")).sum();
                double maintenance = holds.stream().filter(h -> items.stream().anyMatch(a -> a.s("id").equals(h.s("asset_id"))) && h.s("start_date").compareTo(date) <= 0 && (!h.has("available_date") || date.compareTo(h.s("available_date")) < 0)).mapToDouble(h -> h.n("quantity")).sum();
                double inTransit = transfers.stream().filter(t -> items.stream().anyMatch(a -> a.s("id").equals(t.s("asset_id")))).mapToDouble(t -> t.n("quantity")).sum();
                breakdown.add(of("ownerId", owner, "warehouseId", wh, "total", items.stream().mapToDouble(a -> a.n("quantity")).sum(), "inStock", items.stream().mapToDouble(this::physical).sum(), "available", items.stream().mapToDouble(a -> free(a, date, dayAfter(date))).sum(), "outside", outsideQuantity, "reserved", reserved, "maintenance", maintenance, "inTransit", inTransit));
            }
            double outside = allocations.stream().filter(a -> a.s("equipment_id").equals(equipment.s("id")) && a.s("status").equals("OUT") && resources.stream().anyMatch(r -> r.s("id").equals(a.s("asset_id")))).mapToDouble(a -> a.n("quantity") - a.n("returned_quantity")).sum();
            result.add(equipment.with("breakdown", breakdown, "total", resources.stream().mapToDouble(a -> a.n("quantity")).sum(), "inStock", resources.stream().mapToDouble(this::physical).sum(), "available", breakdown.stream().mapToDouble(a -> a.n("available")).sum(), "outside", outside));
        }
        return result;
    }
    public List<Data> conflicts() {
        List<Data> result = new ArrayList<>();
        for (Data a : allocations) if (a.s("status").equals("RESERVED")) {
            Data resource = assets.stream().filter(r -> r.s("id").equals(a.s("asset_id"))).findFirst().orElseThrow();
            if (free(resource, a.s("start_date"), a.s("available_date", "9999-12-31"), a.s("document_id")) < a.n("quantity")) result.add(of("allocation", a, "resource", resource));
        }
        return result;
    }
}
