package com.yingchang.warehouse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.yingchang.warehouse.Data.*;
import static com.yingchang.warehouse.Auth.*;

@RestController
@RequestMapping("/api")
public class ImportController {
    private final Store store;
    private final WarehouseService service;
    private final CatalogController catalog;
    private final Workbooks books;
    private final RequestSchemas schemas;
    public ImportController(Store store, WarehouseService service, CatalogController catalog, Workbooks books, RequestSchemas schemas) { this.store = store; this.service = service; this.catalog = catalog; this.books = books; this.schemas = schemas; }
    @PostMapping("/imports") public Data upload(@RequestParam String kind, @RequestPart("file") MultipartFile file, HttpServletRequest req) throws Exception {
        Data user = guard(req);
        if (!Set.of("INITIAL", "PURCHASE", "OUTBOUND", "RETURN", "RESERVATION").contains(kind)) throw new BusinessError("VALIDATION_ERROR", "上传业务类型无效", 400);
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = filename.substring(Math.max(0, filename.lastIndexOf('.'))).toLowerCase(Locale.ROOT);
        if (!Set.of(".xls", ".xlsx").contains(ext)) throw new BusinessError("FILE_FORMAT", "仅支持xls和xlsx", 400);
        byte[] bytes = file.getBytes(); String fid = id(), stored = fid + ext; Path path = books.file(stored);
        Files.write(path, bytes);
        try {
            Data info = books.read("inspect", path, kind, of()); String digest = hash(bytes);
            return store.tx(() -> {
                List<Data> duplicates = store.all("SELECT id,filename,created_at FROM imports WHERE file_hash=? AND kind=?", digest, kind);
                store.run("INSERT INTO imports VALUES (?,?,?,?,?,?,?,?,?)", fid, filename, stored, digest, kind, json(info.get("sheets")), null, user.s("id"), now());
                return of("id", fid, "filename", filename, "sheets", info.get("sheets"), "duplicates", duplicates);
            });
        } catch (Exception e) { Files.deleteIfExists(path); throw e; }
    }
    @PostMapping("/imports/{id}/parse") public Data parseImport(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) {
        guard(req); schemas.validate("parse", b);
        Data upload = store.required("imports", id), options = new Data(b);
        if (b.has("templateId")) { Data template = store.one("SELECT * FROM templates WHERE id=?", b.s("templateId")); options.put("mapping", template == null ? of() : parse(template.s("mapping_json"))); }
        Data result = books.read("parse", books.file(upload.s("stored_name")), upload.s("kind"), options);
        List<Data> equipment = catalog.equipment().stream().filter(e -> upload.s("kind").equals("RETURN") || e.i("active") != 0 && e.i("category_active") != 0 && e.i("group_active") != 0).toList();
        result.put("rows", result.rows("rows").stream().map(r -> match(r, equipment, upload.s("kind"))).toList());
        return store.tx(() -> { store.run("UPDATE imports SET preview_json=? WHERE id=?", json(result), id); return result; });
    }
    @PostMapping("/imports/{id}/categories") public Data importCategories(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) {
        guard(req, "ADMIN"); schemas.validate("importCategories", b);
        return store.tx(() -> {
            Data upload = store.required("imports", id); String groupName = b.s("groupName").trim();
            if (!upload.s("kind").equals("INITIAL")) throw new BusinessError("IMPORT_KIND", "仅期初总库可导入类别", 422);
            Data preview = parse(upload.s("preview_json", "{}")); List<Data> rows = preview.rows("rows");
            if (rows.isEmpty()) throw new BusinessError("PARSE_REQUIRED", "请先解析总库", 422);
            if (rows.stream().noneMatch(r -> !r.s("category").isBlank())) throw new BusinessError("CATEGORY_REQUIRED", "总库中未识别到类别", 422);
            // 类别资料可先建立，不受数量核对及是否计库存的限制。
            List<Data> categoryRows = rows.stream().map(r -> { Data row = new Data(r).with("categoryGroup", groupName, "unmanaged", false); row.remove("categoryId"); return row; }).toList();
            Data result = service.initializeCategories(categoryRows);
            preview.put("context", preview.obj("context").with("categoryGroup", groupName));
            for (int i = 0; i < rows.size(); i++) rows.get(i).with("categoryId", rows.get(i).has("categoryId") ? rows.get(i).get("categoryId") : categoryRows.get(i).get("categoryId"), "categoryGroup", groupName);
            store.run("UPDATE imports SET preview_json=? WHERE id=?", json(preview), id);
            return result.with("groupName", groupName);
        });
    }
    @GetMapping("/imports/{id}") public Data getImport(@PathVariable String id) { Data i = store.required("imports", id); return of("id", i.get("id"), "filename", i.get("filename"), "kind", i.get("kind"), "sheets", parseValue(i.s("sheets_json")), "preview", i.has("preview_json") ? parse(i.s("preview_json")) : null); }
    @PatchMapping("/imports/{id}/rows") public Data rows(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) {
        guard(req); schemas.validate("importRows", b);
        return store.tx(() -> { Data i = store.required("imports", id), preview = parse(i.s("preview_json", "{}")); preview.put("rows", b.get("rows")); store.run("UPDATE imports SET preview_json=? WHERE id=?", json(preview), id); return preview; });
    }
    @GetMapping("/templates") public List<Data> templates() { return store.all("SELECT * FROM templates").stream().map(t -> t.with("mapping", parse(t.s("mapping_json")))).toList(); }
    @PostMapping("/templates") public Data template(@RequestBody Data b, HttpServletRequest req) {
        guard(req, "ADMIN"); schemas.validate("template", b);
        return store.tx(() -> { String tid = id(); store.run("INSERT INTO templates VALUES (?,?,?,?)", tid, b.s("name"), b.s("kind"), json(b.obj("mapping"))); return of("id", tid); });
    }
    @PostMapping("/documents/{id}/attachments") public Data photo(@PathVariable String id, @RequestPart("file") MultipartFile file, HttpServletRequest req) throws Exception {
        Data user = guard(req); store.required("documents", id); byte[] bytes = file.getBytes();
        boolean jpg = bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
        boolean webp = bytes.length >= 12 && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
        if (!jpg && !png && !webp) throw new BusinessError("IMAGE_FORMAT", "仅支持JPG、PNG和WebP照片", 400);
        String aid = id(), stored = aid + (jpg ? ".jpg" : png ? ".png" : ".webp"), mime = jpg ? "image/jpeg" : png ? "image/png" : "image/webp";
        Path path = books.file(stored); Files.write(path, bytes);
        try { return store.tx(() -> { store.run("INSERT INTO attachments VALUES (?,?,?,?,?,?,?,?,?)", aid, id, null, file.getOriginalFilename(), stored, mime, bytes.length, user.s("id"), now()); return of("id", aid); }); }
        catch (Exception e) { Files.deleteIfExists(path); throw e; }
    }
    @GetMapping("/documents/{id}/attachments") public List<Data> photos(@PathVariable String id) { return store.all("SELECT id,filename,mime_type,size FROM attachments WHERE document_id=?", id); }
    @GetMapping("/attachments/{id}") public ResponseEntity<byte[]> photo(@PathVariable String id) throws Exception {
        Data a = store.required("attachments", id); return ResponseEntity.ok().header("Content-Type", a.s("mime_type")).header("X-Content-Type-Options", "nosniff").body(Files.readAllBytes(books.file(a.s("stored_name"))));
    }
    @PostMapping("/exports/availability") public ResponseEntity<byte[]> exportAvailability(@RequestBody Data p) {
        schemas.validate("payload", p); Data result = service.inventory().query(p); List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("原始名称", "标准设备", "数量", "单位", "发货日", "可再次发出日", "资产方", "仓库", "全租期可供", "条件", "整单结果"));
        for (Data r : result.rows("rows")) {
            Data row = r.obj("row"), equipment = row.has("equipmentId") ? store.required("equipment", row.s("equipmentId")) : of();
            List<Data> locations = r.rows("locations");
            for (Data l : locations.isEmpty() ? List.of(of()) : locations) rows.add(Arrays.asList(row.s("rawName"), equipment.s("name", "待确认"), row.get("quantity"), equipment.s("unit"), result.get("startDate"), result.get("availableDate"), l.getOrDefault("ownerId", ""), l.s("warehouseId").equals("beijing") ? "北京" : l.s("warehouseId").equals("hengdian") ? "横店" : "", l.getOrDefault("quantity", "待确认"), l.rows("dependencies").isEmpty() ? "" : "依赖前序按时归还", result.obj("issue").s("message", "整单分配通过")));
        }
        return xlsx(books.export("租期供货反馈", rows), "availability.xlsx");
    }
    @PostMapping("/exports/crew/{id}") public ResponseEntity<byte[]> exportCrew(@PathVariable String id) {
        Data c = store.required("crews", id); List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("剧组", "组别", "设备", "单位", "编号", "资产方", "发货仓", "已出/预留", "已还", "未还", "状态", "发货日", "可再次发出日", "出库单号", "出库对象", "出库分类"));
        for (Data a : store.all("SELECT a.*,e.name,e.unit,d.status,d.group_name,d.number,c.name recipient_name,c.kind recipient_kind,COALESCE(a.serial_snapshot,r.serial) serial FROM allocations a JOIN documents d ON d.id=a.document_id JOIN crews c ON c.id=d.crew_id JOIN equipment e ON e.id=a.equipment_id JOIN assets r ON r.id=a.asset_id WHERE d.crew_id=? OR d.related_crew_id=?", id, id)) rows.add(Arrays.asList(c.s("name"), a.get("group_name"), a.get("name"), a.get("unit"), a.s("serial"), a.get("owner_id"), a.get("warehouse_id"), a.get("quantity"), a.get("returned_quantity"), a.n("quantity") - a.n("returned_quantity"), a.get("status"), a.get("start_date"), a.s("available_date", "归期待确认"), a.s("number"), a.s("recipient_name"), a.s("recipient_kind").equals("COMPANY") ? "其他出库" : "剧组出库"));
        return xlsx(books.export("剧组设备来源", rows), "crew.xlsx");
    }
    private ResponseEntity<byte[]> xlsx(byte[] bytes, String name) { return ResponseEntity.ok().header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").header("Content-Disposition", "attachment; filename=\"" + name + "\"").body(bytes); }
    private List<String> digits(String value) { return Pattern.compile("\\d+(?:\\.\\d+)?").matcher(value).results().map(java.util.regex.MatchResult::group).toList(); }
    Data match(Data row, List<Data> equipment, String kind) {
        List<Data> exact = equipment.stream().filter(e -> {
            List<String> names = new ArrayList<>(e.strings("aliases")); names.add(e.s("name")); names.add(e.s("english_name"));
            return names.stream().anyMatch(v -> !v.isEmpty() && normalize(v).equals(normalize(row.s("rawName"))));
        }).toList();
        List<String> digits = digits(row.s("rawName")), brands = equipment.stream().map(e -> e.s("brand")).filter(v -> !v.isEmpty() && normalize(row.s("rawName")).contains(normalize(v))).toList();
        List<Data> candidates = exact.isEmpty() ? equipment.stream().filter(e -> !digits.isEmpty() && digits(e.s("name") + " " + e.s("english_name") + " " + e.s("specification")).containsAll(digits) && (brands.isEmpty() || brands.contains(e.s("brand")))).toList() : exact;
        Data result = new Data(row).with("candidates", candidates.stream().map(e -> of("id", e.get("id"), "name", e.get("name"), "specification", e.get("specification"))).toList(), "createEquipment", kind.equals("INITIAL") && exact.isEmpty());
        if (exact.size() == 1) result.put("equipmentId", exact.get(0).get("id")); else result.remove("equipmentId");
        List<String> issues = new ArrayList<>(row.strings("parserIssues"));
        if (!result.has("equipmentId") && !result.b("createEquipment")) issues.add(candidates.isEmpty() ? "未找到对应设备，请选择已有设备、新增或标记不计库存" : "找到候选设备，请选择");
        if (!row.has("quantity")) issues.add("请填写数量");
        else try { ownerTwo(row.s("partNo"), row.n("quantity")); } catch (BusinessError e) { issues.add(e.getMessage()); }
        result.put("issues", issues);
        if (kind.equals("RETURN") && result.has("equipmentId")) {
            List<Data> allocations = store.all("SELECT a.* FROM allocations a JOIN documents d ON d.id=a.document_id WHERE a.equipment_id=? AND d.status='OUT' AND a.quantity>a.returned_quantity", result.s("equipmentId")).stream().filter(a -> row.strings("serials").isEmpty() || row.strings("serials").contains(store.required("assets", a.s("asset_id")).s("serial"))).toList();
            if (allocations.size() == 1) result.put("allocationId", allocations.get(0).get("id"));
        }
        return result;
    }
}
