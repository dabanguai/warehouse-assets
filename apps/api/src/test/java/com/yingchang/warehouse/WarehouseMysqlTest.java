package com.yingchang.warehouse;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.yingchang.warehouse.Data.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"warehouse.admin-password=Test-only-password-2026", "warehouse.data-dir=target/test-data"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="WAREHOUSE_TEST_MYSQL_URL", matches=".+")
class WarehouseMysqlTest {
    @TestConfiguration static class Dates {
        @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse("2026-11-02T04:00:00Z"), ZoneId.of("Asia/Shanghai")); }
    }
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) {
        String url = System.getenv("WAREHOUSE_TEST_MYSQL_URL");
        if (!url.matches("jdbc:mysql://[^/]+/warehouse_test(?:_[A-Za-z0-9]+)?(?:\\?.*)?")) throw new IllegalArgumentException("测试只允许使用 warehouse_test 或 warehouse_test_ 开头的独立库");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("WAREHOUSE_TEST_MYSQL_USER", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("WAREHOUSE_TEST_MYSQL_PASSWORD", ""));
    }
    @Autowired MockMvc mvc;
    @Autowired Store store;
    @Autowired WarehouseService service;
    @Autowired Bootstrap bootstrap;
    @Autowired Auth auth;
    String token, eid, cid;
    @BeforeEach void fixture() throws Exception {
        store.tx(() -> {
            store.run("DELETE FROM sessions");
            List<String> tables = new ArrayList<>(Bootstrap.TABLES); Collections.reverse(tables);
            for (String table : tables) if (!Set.of("users", "owners", "warehouses").contains(table)) store.run("DELETE FROM `" + table + "`");
            store.run("DELETE FROM users WHERE username<>'admin'"); return null;
        });
        token = ok("POST", "/auth/login", of("username", "admin", "password", "Test-only-password-2026")).s("token");
        String category = ok("POST", "/categories", of("name", "轨道")).s("id");
        eid = ok("POST", "/equipment", of("name", "重型直轨2.2米", "categoryId", category, "unit", "节", "aliases", List.of("620重型直轨"))).s("id");
        cid = ok("POST", "/crews", of("name", "测试剧组", "kind", "CREW", "groups", List.of("A组", "B组"))).s("id");
    }
    MvcResult call(String method, String path, Data body) throws Exception {
        var req = request(org.springframework.http.HttpMethod.valueOf(method), "/api" + path).header("Authorization", "Bearer " + (token == null ? "" : token));
        if (body != null) req.contentType("application/json").content(json(body));
        return mvc.perform(req).andReturn();
    }
    Data response(MvcResult r) throws Exception { return parse(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); }
    Data ok(String method, String path, Data body) throws Exception { MvcResult r = call(method, path, body); assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString()); return response(r); }
    Data draft(String type, List<Data> rows, Data extra) throws Exception {
        Data p = of("rows", rows, "warehouseId", "beijing", "crewId", cid, "groupName", "A组", "startDate", "2026-11-02", "availableDate", "2026-11-06"); p.putAll(extra);
        return ok("POST", "/documents", of("number", "TEST-" + id(), "type", type, "businessDate", "2026-11-02", "payload", p));
    }
    Data draft(String type, List<Data> rows) throws Exception { return draft(type, rows, of()); }
    Data row(double quantity, int owner) { return of("equipmentId", eid, "quantity", quantity, "ownerId", owner); }
    Data confirm(Data d) throws Exception { return ok("POST", "/documents/" + d.s("id") + "/confirm", of("expectedVersion", d.get("version"), "idempotencyKey", id())); }
    Data inventory() { return service.inventory().list(eid, 0, "").get(0); }
    void purchase(double quantity, int owner) throws Exception { confirm(draft("PURCHASE", List.of(row(quantity, owner)))); }
    @Test void initialOutboundsMergeByCrewAndGroupAndKeepOtherRecipient() throws Exception {
        String category = store.required("equipment", eid).s("category_id");
        String second = ok("POST", "/equipment", of("name", "摇臂", "categoryId", category, "unit", "套")).s("id");
        Data a = of("name", "《测试剧组》A组", "kind", "CREW", "quantity", 1);
        Data b = of("name", "《测试剧组》B组", "kind", "CREW", "quantity", 1);
        Data other = of("name", "测试剧组（灯）", "kind", "OTHER", "relatedCrewId", cid, "quantity", 2);
        Data d = draft("INITIAL", List.of(
            row(6, 1).with("initial", of("total", 6, "beijing", 2, "hengdian", 0, "outbounds", List.of(a, b, other))),
            of("equipmentId", second, "quantity", 3, "ownerId", 2, "initial", of("total", 3, "beijing", 0, "hengdian", 1, "outbounds", List.of(new Data(a), new Data(other).with("quantity", 1))))));
        Data request = of("expectedVersion", d.get("version"), "idempotencyKey", id());
        Data result = ok("POST", "/documents/" + d.s("id") + "/confirm", request);
        assertEquals(result, ok("POST", "/documents/" + d.s("id") + "/confirm", request));
        List<Data> outs = store.all("SELECT * FROM documents WHERE type='OUTBOUND'");
        assertEquals(3, outs.size());
        Data groupA = outs.stream().filter(x -> x.s("group_name").equals("A组")).findFirst().orElseThrow();
        Data groupB = outs.stream().filter(x -> x.s("group_name").equals("B组")).findFirst().orElseThrow();
        Data lamp = outs.stream().filter(x -> x.s("related_crew_id").equals(cid)).findFirst().orElseThrow();
        assertEquals(2, service.document(groupA.s("id")).rows("allocations").size());
        assertEquals(1, service.document(groupB.s("id")).rows("allocations").size());
        Data lampDoc = service.document(lamp.s("id"));
        assertEquals(2, lampDoc.rows("allocations").size());
        assertEquals(3, lampDoc.obj("payload").rows("rows").stream().mapToDouble(r -> r.n("quantity")).sum());
        assertEquals("测试剧组（灯）", store.required("crews", lamp.s("crew_id")).s("name"));
        assertEquals(3, ((List<?>) parseValue(call("GET", "/documents?crewId=" + cid + "&type=OUTBOUND", null).getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))).size());
        assertEquals(5, ((List<?>) parseValue(call("GET", "/crews/" + cid + "/sources", null).getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))).size());
        assertEquals(6, inventory().n("total"));
        Data allocation = service.document(groupA.s("id")).rows("allocations").get(0);
        confirm(draft("RETURN", List.of(of("allocationId", allocation.s("id"), "quantity", 1)), of("availableDate", "2026-11-03")));
        assertEquals("OUT", service.document(groupA.s("id")).s("status"));
        assertEquals(1, service.document(groupA.s("id")).rows("allocations").stream().mapToDouble(x -> x.n("returned_quantity")).sum());
        assertEquals(0, service.document(groupB.s("id")).rows("allocations").get(0).n("returned_quantity"));
        assertEquals(0, service.document(lamp.s("id")).rows("allocations").get(0).n("returned_quantity"));
        assertEquals(200, call("POST", "/exports/crew/" + cid, of()).getResponse().getStatus());
    }
    @Test void otherOutboundAssociationIsVersionedAndIncludesLinkedReturns() throws Exception {
        String company = ok("POST", "/crews", of("name", "独立灯光出库", "kind", "COMPANY", "groups", List.of("未分组"))).s("id");
        purchase(3, 1);
        Data out = confirm(draft("OUTBOUND", List.of(row(2, 1)), of("crewId", company, "groupName", "未分组")));
        Data returned = confirm(draft("RETURN", List.of(of("allocationId", out.rows("allocations").get(0).s("id"), "quantity", 1)), of("availableDate", "2026-11-03")));
        out = service.document(out.s("id"));
        Data linked = ok("PATCH", "/documents/" + out.s("id") + "/related-crew", of("expectedVersion", out.get("version"), "relatedCrewId", cid));
        assertEquals(cid, linked.s("related_crew_id"));
        assertEquals(409, call("PATCH", "/documents/" + out.s("id") + "/related-crew", of("expectedVersion", out.get("version"), "relatedCrewId", "")).getResponse().getStatus());
        String list = call("GET", "/documents?crewId=" + cid, null).getResponse().getContentAsString();
        assertTrue(list.contains(out.s("id"))); assertTrue(list.contains(returned.s("id")));
        assertEquals(422, call("PATCH", "/documents/" + out.s("id") + "/related-crew", of("expectedVersion", linked.get("version"), "relatedCrewId", company)).getResponse().getStatus());
        Data unlinked = ok("PATCH", "/documents/" + out.s("id") + "/related-crew", of("expectedVersion", linked.get("version"), "relatedCrewId", ""));
        assertEquals("", unlinked.s("related_crew_id"));
        assertEquals(out.s("crew_id"), unlinked.s("crew_id"));
        assertEquals(1, unlinked.rows("allocations").get(0).n("returned_quantity"));
    }
    @Test void catalogDeletionProtectsAssetsDraftsAndChildRecords() throws Exception {
        MvcResult preflight = mvc.perform(options("/api/equipment/unused").header("Origin", "http://127.0.0.1:5173").header("Access-Control-Request-Method", "DELETE").header("Access-Control-Request-Headers", "Authorization")).andReturn();
        assertEquals(200, preflight.getResponse().getStatus());
        assertTrue(preflight.getResponse().getHeader("Access-Control-Allow-Methods").contains("DELETE"));
        String gid = ok("POST", "/category-groups", of("name", "待清理大类")).s("id");
        String cat = ok("POST", "/categories", of("name", "待清理类别", "groupId", gid)).s("id");
        String item = ok("POST", "/equipment", of("name", "空设备", "categoryId", cat, "unit", "件", "aliases", List.of("旧称"))).s("id");
        assertEquals(409, call("DELETE", "/categories/" + cat, null).getResponse().getStatus());
        assertEquals(409, call("DELETE", "/category-groups/" + gid, null).getResponse().getStatus());
        ok("DELETE", "/equipment/" + item, null);
        assertEquals(404, call("POST", "/documents", of("number", "STALE", "type", "PURCHASE", "businessDate", "2026-11-02", "payload", of("rows", List.of(of("equipmentId", item, "quantity", 1))))).getResponse().getStatus());
        assertTrue(store.all("SELECT * FROM aliases WHERE equipment_id=?", item).isEmpty());
        ok("DELETE", "/categories/" + cat, null); ok("DELETE", "/category-groups/" + gid, null);
        draft("PURCHASE", List.of(row(1, 1)));
        assertEquals(409, call("DELETE", "/equipment/" + eid, null).getResponse().getStatus());
        purchase(1, 1);
        assertEquals(409, call("DELETE", "/equipment/" + eid, null).getResponse().getStatus());
        ok("POST", "/users", of("username", "deletion-viewer", "password", "Viewer-password-2026", "role", "VIEWER"));
        token = ok("POST", "/auth/login", of("username", "deletion-viewer", "password", "Viewer-password-2026")).s("token");
        assertEquals(403, call("DELETE", "/equipment/" + eid, null).getResponse().getStatus());
    }
    @Test void initialInvalidRelatedCrewRollsBackAllGroupedOutbounds() throws Exception {
        String company = ok("POST", "/crews", of("name", "公司", "kind", "COMPANY", "groups", List.of("未分组"))).s("id");
        Data d = draft("INITIAL", List.of(row(2, 1).with("initial", of("total", 2, "beijing", 0, "hengdian", 0, "outbounds", List.of(
            of("name", "《测试剧组》A组", "kind", "CREW", "quantity", 1),
            of("name", "其他出库", "kind", "OTHER", "relatedCrewId", company, "quantity", 1))))));
        assertEquals(422, call("POST", "/documents/" + d.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id())).getResponse().getStatus());
        assertTrue(store.all("SELECT * FROM assets").isEmpty());
        assertTrue(store.all("SELECT * FROM documents WHERE type='OUTBOUND'").isEmpty());
    }
    @Test void disabledCategoryBlocksNewBusinessButAllowsExistingReturn() throws Exception {
        purchase(3, 1);
        Data out = confirm(draft("OUTBOUND", List.of(row(2, 1))));
        String category = store.required("equipment", eid).s("category_id");
        ok("PATCH", "/categories/" + category, of("name", "轨道", "active", false, "groupId", null));
        Data next = draft("OUTBOUND", List.of(row(1, 1)));
        assertEquals(422, call("POST", "/documents/" + next.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id())).getResponse().getStatus());
        Data intake = draft("PURCHASE", List.of(row(1, 1)));
        assertEquals(422, call("POST", "/documents/" + intake.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id())).getResponse().getStatus());
        confirm(draft("RETURN", List.of(of("allocationId", out.rows("allocations").get(0).s("id"), "quantity", 1)), of("availableDate", "2026-11-03")));
        assertEquals(1, service.document(out.s("id")).rows("allocations").get(0).n("returned_quantity"));
    }
    @Test void equipmentBrandAndModelRoundTrip() throws Exception {
        String category = store.required("equipment", eid).s("category_id");
        Data created = ok("POST", "/equipment", of("name", "稳定器", "categoryId", category, "unit", "套", "brand", " DJI ", "model", " RS 4 Pro ", "specification", "载重4.5kg"));
        assertEquals("DJI", created.s("brand")); assertEquals("RS 4 Pro", created.s("model"));
        assertEquals("载重4.5kg", created.s("specification"));
        Data edited = ok("PATCH", "/equipment/" + created.s("id"), of("name", "稳定器", "categoryId", category, "unit", "套", "brand", "DJI", "model", "RS 3 Pro"));
        assertEquals("RS 3 Pro", edited.s("model"));
        Data inventoryItem = service.inventory().list(created.s("id"), 0, "").get(0);
        assertEquals("DJI", inventoryItem.s("brand")); assertEquals("RS 3 Pro", inventoryItem.s("model"));
        assertEquals("", store.required("equipment", eid).s("model"));
    }
    @Test void mixedSourcesPermissionsAndIdempotency() throws Exception {
        purchase(4, 1); purchase(4, 2);
        Data out = draft("OUTBOUND", List.of(of("equipmentId", eid, "quantity", 4, "partNo", "2*2"), of("rawName", "线材", "quantity", 100, "unmanaged", true)));
        Data b = of("expectedVersion", 1, "idempotencyKey", id());
        Data result = ok("POST", "/documents/" + out.s("id") + "/confirm", b);
        assertEquals(2, result.rows("allocations").size()); assertEquals(4, inventory().n("inStock"));
        assertEquals(result, ok("POST", "/documents/" + out.s("id") + "/confirm", b));
        assertEquals(409, call("POST", "/documents/" + out.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id())).getResponse().getStatus());
        ok("POST", "/users", of("username", "viewer", "password", "Viewer-password-2026", "role", "VIEWER"));
        token = ok("POST", "/auth/login", of("username", "viewer", "password", "Viewer-password-2026")).s("token");
        assertEquals(403, call("POST", "/categories", of("name", "禁止")).getResponse().getStatus());
        assertEquals(403, call("GET", "/users", null).getResponse().getStatus());
        ok("POST", "/auth/logout", null); assertEquals(401, call("GET", "/inventory", null).getResponse().getStatus());
    }
    @Test void failedWholeDocumentRollsBack() throws Exception {
        purchase(3, 2); Data out = draft("OUTBOUND", List.of(row(2, 2), row(2, 2)));
        assertEquals(409, call("POST", "/documents/" + out.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id())).getResponse().getStatus());
        assertEquals(3, inventory().n("inStock")); assertEquals("DRAFT", service.document(out.s("id")).s("status")); assertTrue(store.all("SELECT * FROM allocations").isEmpty());
    }
    @Test void partialRemoteReturnPreservesOwnershipAndInspection() throws Exception {
        purchase(5, 2); Data out = confirm(draft("OUTBOUND", List.of(row(4, 2))));
        Data r = row(2, 2).with("allocationId", out.rows("allocations").get(0).get("id"));
        confirm(draft("RETURN", List.of(r), of("warehouseId", "hengdian", "availableDate", "2026-11-03")));
        Data inv = inventory(); assertEquals(5, inv.n("total")); assertEquals(2, inv.n("outside")); assertEquals(3, inv.n("inStock"));
        assertEquals(0, inv.rows("breakdown").stream().filter(x -> x.i("ownerId") == 2 && x.s("warehouseId").equals("hengdian")).findFirst().orElseThrow().n("available"));
        assertEquals(2, service.document(out.s("id")).rows("allocations").get(0).n("returned_quantity"));
    }
    @Test void reservationDispatchVersionAndCancel() throws Exception {
        confirm(draft("PURCHASE", List.of(row(1, 2).with("serials", List.of("058")))));
        Data reserved = confirm(draft("RESERVATION", List.of(row(1, 2))));
        Data q = ok("POST", "/availability/query", of("rows", List.of(row(1, 2)), "warehouseId", "beijing", "startDate", "2026-11-04", "availableDate", "2026-11-08")); assertEquals("STOCK_CONFLICT", q.obj("issue").s("code"));
        assertEquals(409, call("POST", "/documents/" + reserved.s("id") + "/dispatch", of("expectedVersion", 1)).getResponse().getStatus());
        Data dispatched = ok("POST", "/documents/" + reserved.s("id") + "/dispatch", of("expectedVersion", reserved.get("version")));
        assertEquals("OUT", dispatched.s("status")); assertEquals(1, inventory().n("outside"));
        Data empty = draft("RESERVATION", List.of(row(1, 2))); Data cancelled = ok("POST", "/documents/" + empty.s("id") + "/cancel", of("expectedVersion", 1)); assertEquals("CANCELLED", cancelled.s("status"));
    }
    @Test void initialCategoriesAndInventoryRollbackTogether() throws Exception {
        Data r = of("rawName", "天蝎45呎", "createEquipment", true, "category", "摇臂类", "categoryGroup", "机械器材", "unit", "套", "quantity", 2, "ownerId", 2, "note", "维修待核对", "initial", of("total", 2, "beijing", null, "hengdian", 0, "outbounds", List.of()));
        Data d = draft("INITIAL", List.of(r));
        MvcResult fail = call("POST", "/documents/" + d.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id())); assertEquals("INITIAL_REVIEW", response(fail).s("code"));
        assertNull(store.one("SELECT id FROM equipment WHERE name='天蝎45呎'")); assertTrue(store.all("SELECT * FROM category_groups").isEmpty()); assertTrue(store.all("SELECT * FROM assets").isEmpty());
        r.obj("initial").put("beijing", 1);
        Data updated = ok("PATCH", "/documents/" + d.s("id"), of("expectedVersion", 1, "payload", d.obj("payload").with("rows", List.of(r))));
        Data confirmed = confirm(updated); assertEquals("维修待核对", confirmed.obj("payload").rows("rows").get(0).s("note"));
        assertEquals(1, store.all("SELECT * FROM category_groups").size()); assertEquals(1, store.one("SELECT SUM(quantity) qty FROM assets").n("qty"));
    }
    @Test void initialDifferenceImportsAndCanBeReconciledWithoutInventingStock() throws Exception {
        Data d = confirm(draft("INITIAL", List.of(row(5, 1).with("initial", of("total", 5, "beijing", 1, "hengdian", 0, "outbounds", List.of())))));
        assertEquals(1, inventory().n("total"));
        Data review = service.initialReviews(eid).get(0);
        assertTrue(review.b("needsReview")); assertEquals(4, review.n("difference"));
        String path = "/equipment/" + eid + "/initial-reviews/" + d.s("id") + "/0";
        Data input = of("expectedVersion", d.get("version"), "total", 5, "beijing", 2, "hengdian", 3, "reason", "核对总库");
        ok("PATCH", path, input);
        assertEquals(5, inventory().n("total")); assertFalse(service.initialReviews(eid).get(0).b("needsReview"));
        assertEquals("VERSION_CONFLICT", response(call("PATCH", path, input)).s("code"));
        Data asset = store.one("SELECT * FROM assets WHERE warehouse_id='beijing'");
        ok("POST", "/assets/" + asset.s("id") + "/actions", of("action", "SALE", "quantity", 1, "reason", "出售"));
        assertFalse(service.initialReviews(eid).get(0).b("needsReview"));
        assertEquals(4, inventory().n("total"));
    }
    @Test void initialCorrectionCannotRemoveOccupiedStock() throws Exception {
        Data d = confirm(draft("INITIAL", List.of(row(3, 1).with("initial", of("total", 3, "beijing", 2, "hengdian", 0, "outbounds", List.of())))));
        confirm(draft("OUTBOUND", List.of(row(2, 1))));
        Data result = response(call("PATCH", "/equipment/" + eid + "/initial-reviews/" + d.s("id") + "/0", of("expectedVersion", d.get("version"), "total", 1, "beijing", 1, "hengdian", 0, "reason", "核对")));
        assertEquals("ASSET_IN_USE", result.s("code")); assertEquals(2, inventory().n("total"));
        assertEquals(1, service.initialReviews(eid).get(0).n("difference"));
    }
    @Test void missingInitialTotalRemainsPendingEvenWithZeroDifference() throws Exception {
        Data d = confirm(draft("INITIAL", List.of(row(2, 1).with("initial", of("total", null, "beijing", 2, "hengdian", 0, "outbounds", List.of())))));
        assertTrue(service.initialReviews(eid).get(0).b("needsReview"));
        ok("PATCH", "/equipment/" + eid + "/initial-reviews/" + d.s("id") + "/0", of("expectedVersion", d.get("version"), "total", 2, "beijing", 2, "hengdian", 0, "reason", "核实总数"));
        assertFalse(service.initialReviews(eid).get(0).b("needsReview")); assertEquals(2, inventory().n("total"));
    }
    @Test void initialReviewRequiresAdminAndProtectsFutureReservations() throws Exception {
        Data d = confirm(draft("INITIAL", List.of(row(5, 1).with("initial", of("total", 5, "beijing", 0, "hengdian", 2, "outbounds", List.of())))));
        confirm(draft("RESERVATION", List.of(row(2, 1)), of("warehouseId", "hengdian", "startDate", "2026-11-07", "availableDate", "2026-11-10")));
        String path = "/equipment/" + eid + "/initial-reviews/" + d.s("id") + "/0";
        Data input = of("expectedVersion", d.get("version"), "total", 5, "beijing", 3, "hengdian", 1, "reason", "核对");
        assertEquals("ASSET_IN_USE", response(call("PATCH", path, input)).s("code"));
        assertEquals(2, inventory().n("total"));
        assertNull(store.one("SELECT id FROM assets WHERE warehouse_id='beijing'"));
        ok("POST", "/users", of("username", "keeper", "password", "Keeper-password-2026", "role", "KEEPER"));
        token = ok("POST", "/auth/login", of("username", "keeper", "password", "Keeper-password-2026")).s("token");
        assertEquals(403, call("PATCH", path, input).getResponse().getStatus());
    }
    @Test void initialOverDistributionAndZeroStockEquipmentRemainEditable() throws Exception {
        Data d = confirm(draft("INITIAL", List.of(row(1, 1).with("initial", of("total", 1, "beijing", 2, "hengdian", 0, "outbounds", List.of())))));
        assertEquals(-1, service.initialReviews(eid).get(0).n("difference")); assertEquals(2, inventory().n("total"));
        ok("PATCH", "/equipment/" + eid + "/initial-reviews/" + d.s("id") + "/0", of("expectedVersion", d.get("version"), "total", 2, "beijing", 2, "hengdian", 0, "reason", "核实总数"));
        assertFalse(service.initialReviews(eid).get(0).b("needsReview")); assertEquals(2, inventory().n("total"));
        Data other = confirm(draft("INITIAL", List.of(of("rawName", "零库存设备", "quantity", 4, "ownerId", 1, "createEquipment", true, "unit", "件", "initial", of("total", 4, "beijing", 0, "hengdian", 0, "outbounds", List.of())))));
        String otherId = other.obj("payload").rows("rows").get(0).s("equipmentId");
        assertTrue(service.initialReviews(otherId).get(0).b("needsReview"));
        assertEquals(0, service.inventory().list(otherId, 0, "").get(0).n("total"));
    }
    @Test void initialMasterWorkbookImportsWithDifferencesAndMissingTotals() throws Exception {
        Path file = Path.of("../../../机械总库2026-10-18.xls");
        Assumptions.assumeTrue(Files.exists(file), "未提供真实总库文件");
        var upload = new MockMultipartFile("file", "master.xls", "application/vnd.ms-excel", Files.readAllBytes(file));
        MvcResult uploaded = mvc.perform(multipart("/api/imports?kind=INITIAL").file(upload).header("Authorization", "Bearer " + token)).andReturn();
        assertEquals(200, uploaded.getResponse().getStatus(), uploaded.getResponse().getContentAsString());
        String importId = response(uploaded).s("id");
        Data parsed = ok("POST", "/imports/" + importId + "/parse", of());
        List<Data> rows = parsed.rows("rows"); assertEquals(232, rows.size());
        double distributed = rows.stream().mapToDouble(r -> r.obj("initial").n("beijing") + r.obj("initial").n("hengdian") + r.obj("initial").rows("outbounds").stream().mapToDouble(o -> o.n("quantity")).sum()).sum();
        Data d = confirm(draft("INITIAL", rows, of("importId", importId)));
        assertEquals("CONFIRMED", d.s("status"));
        assertEquals(distributed, store.one("SELECT SUM(quantity) qty FROM assets").n("qty"), 0.000001);
        List<Data> reviews = service.initialReviews("");
        assertEquals(232, reviews.size()); assertTrue(reviews.stream().anyMatch(r -> r.b("needsReview")));
        assertTrue(reviews.stream().anyMatch(r -> !r.has("total")));
    }
    @Test void initialSameOwnerRowsWithNotesImportWithoutAcknowledgements() throws Exception {
        Data first = row(2, 1).with("note", "已出售数量已扣除", "initial", of("total", 2, "beijing", 1, "hengdian", 1, "outbounds", List.of()));
        Data second = row(4, 1).with("note", "维修记录已核对", "initial", of("total", 4, "beijing", 2, "hengdian", 2, "outbounds", List.of()));
        Data d = confirm(draft("INITIAL", List.of(first, second)));
        assertEquals("CONFIRMED", d.s("status")); assertEquals(6, inventory().n("total"));
        assertEquals(2, service.initialReviews(eid).size());
        assertEquals("已出售数量已扣除", d.obj("payload").rows("rows").get(0).s("note"));
        assertEquals("维修记录已核对", d.obj("payload").rows("rows").get(1).s("note"));
    }
    @Test void initialOutsideKeepsUnknownWarehouseAndCanReturn() throws Exception {
        Data out = of("name", "《青风》A组", "quantity", 2);
        confirm(draft("INITIAL", List.of(row(4, 2).with("initial", of("total", 4, "beijing", 1, "hengdian", 1, "outbounds", List.of(out))))));
        Data a = store.one("SELECT * FROM allocations"); assertEquals("unknown", a.s("warehouse_id"));
        confirm(draft("RETURN", List.of(row(2, 2).with("allocationId", a.get("id"))), of("warehouseId", "hengdian", "availableDate", "2026-11-03")));
        assertEquals(0, inventory().n("outside")); assertEquals(4, inventory().n("total"));
    }
    @Test void duplicateSerialAndInvalidOwnerReturnRollBack() throws Exception {
        confirm(draft("PURCHASE", List.of(row(1, 1).with("serials", List.of("058")))));
        Data duplicate = draft("PURCHASE", List.of(row(1, 1).with("serials", List.of("058"))));
        assertEquals("DATA_CONFLICT", response(call("POST", "/documents/" + duplicate.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id()))).s("code"));
        Data out = confirm(draft("OUTBOUND", List.of(row(1, 1))));
        Data ret = draft("RETURN", List.of(row(1, 2).with("allocationId", out.rows("allocations").get(0).get("id"))), of("availableDate", "2026-11-03"));
        assertEquals("OWNER_CONFLICT", response(call("POST", "/documents/" + ret.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id()))).s("code"));
        assertEquals(1, inventory().n("total")); assertEquals(1, inventory().n("outside"));
    }
    @Test void simultaneousDocumentsCannotOversell() throws Exception {
        purchase(1, 1); Data a = draft("OUTBOUND", List.of(row(1, 1))), b = draft("OUTBOUND", List.of(row(1, 1)));
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Data d : List.of(a, b)) futures.add(pool.submit(() -> { gate.await(); return call("POST", "/documents/" + d.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id())).getResponse().getStatus(); }));
            gate.countDown(); List<Integer> statuses = new ArrayList<>(); for (var f : futures) statuses.add(f.get(15, TimeUnit.SECONDS)); Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses); assertEquals(1, inventory().n("outside")); assertEquals(0, inventory().n("inStock"));
        } finally { pool.shutdownNow(); }
    }
    @Test void simultaneousSameKeyReturnsOneResult() throws Exception {
        Data d = draft("PURCHASE", List.of(row(2, 1))), body = of("expectedVersion", 1, "idempotencyKey", id());
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch gate = new CountDownLatch(1);
        try {
            Callable<Data> operation = () -> { gate.await(); return ok("POST", "/documents/" + d.s("id") + "/confirm", body); };
            Future<Data> first = pool.submit(operation), second = pool.submit(operation); gate.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)); assertEquals(2, inventory().n("total"));
        } finally { pool.shutdownNow(); }
    }
    @Test void transferReceiveAndAssetActions() throws Exception {
        purchase(3, 1); Data asset = store.one("SELECT * FROM assets");
        Data t = confirm(draft("TRANSFER", List.of(row(2, 1).with("assetId", asset.get("id"))), of("targetWarehouseId", "hengdian")));
        assertEquals(1, inventory().n("inStock"));
        ok("POST", "/documents/" + t.s("id") + "/receive", of("expectedVersion", t.get("version")));
        assertEquals(3, inventory().n("inStock"));
        Data remote = store.one("SELECT * FROM assets WHERE warehouse_id='hengdian'");
        ok("POST", "/assets/" + remote.s("id") + "/actions", of("action", "SALE", "quantity", 1, "reason", "出售")); assertEquals(2, inventory().n("total"));
        ok("PATCH", "/assets/" + remote.s("id"), of("ownerId", 2, "serial", "TEST-001", "reason", "归属核对"));
        assertEquals(2, store.required("assets", remote.s("id")).i("owner_id"));
    }
    @Test void extensionAndAllocationCorrection() throws Exception {
        purchase(3, 1); Data d = confirm(draft("OUTBOUND", List.of(row(1, 1))));
        Data extended = ok("POST", "/documents/" + d.s("id") + "/extend", of("expectedVersion", d.get("version"), "availableDate", "2026-11-09", "reason", "延期")).obj("document");
        Data corrected = ok("POST", "/documents/" + d.s("id") + "/corrections", of("expectedVersion", extended.get("version"), "rows", List.of(row(2, 1)), "reason", "增补来源核对"));
        assertEquals(2, corrected.rows("allocations").get(0).n("quantity")); assertEquals(1, inventory().n("inStock"));
    }
    @Test void categoryEditsKeepEquipmentAndStock() throws Exception {
        purchase(2, 1); Data eq = store.required("equipment", eid);
        String group = ok("POST", "/category-groups", of("name", "机械器材")).s("id");
        ok("PATCH", "/categories/" + eq.s("category_id"), of("name", "重型轨道", "active", true, "groupId", group));
        Data meta = ok("GET", "/meta", null); assertEquals(group, meta.rows("equipment").get(0).s("group_id")); assertEquals(2, inventory().n("total"));
        assertEquals(400, call("PATCH", "/categories/" + eq.s("category_id"), of("name", "缺必填", "active", true)).getResponse().getStatus());
    }
    @Test void realMasterWorkbookImportsCategoriesWithoutInventory() throws Exception {
        String configured = System.getenv("WAREHOUSE_TEST_WORKBOOK");
        Assumptions.assumeTrue(configured != null && Files.isRegularFile(Path.of(configured)), "设置 WAREHOUSE_TEST_WORKBOOK 以验证真实总库");
        var file = new MockMultipartFile("file", "mechanical.xls", "application/vnd.ms-excel", Files.readAllBytes(Path.of(configured)));
        MvcResult upload = mvc.perform(multipart("/api/imports?kind=INITIAL").file(file).header("Authorization", "Bearer " + token)).andReturn(); assertEquals(200, upload.getResponse().getStatus(), upload.getResponse().getContentAsString());
        String iid = response(upload).s("id");
        assertEquals(422, call("POST", "/imports/" + iid + "/categories", of("groupName", "机械器材")).getResponse().getStatus());
        Data parsed = ok("POST", "/imports/" + iid + "/parse", of()); assertEquals(232, parsed.rows("rows").size());
        Data imported = ok("POST", "/imports/" + iid + "/categories", of("groupName", "机械器材")); assertEquals(11, imported.i("categoryCount"));
        assertTrue(store.all("SELECT * FROM assets").isEmpty()); assertTrue(store.all("SELECT * FROM allocations").isEmpty());
        assertEquals(0, ok("POST", "/imports/" + iid + "/categories", of("groupName", "机械器材")).i("categoriesCreated"));
        assertEquals(409, call("POST", "/imports/" + iid + "/categories", of("groupName", "其他器材")).getResponse().getStatus());
        assertEquals(1, store.all("SELECT * FROM category_groups").size());
    }
    @Test void attachmentAuthenticationAndActualXlsxExport() throws Exception {
        Data d = draft("PURCHASE", List.of(row(1, 1)));
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5u0AAAAASUVORK5CYII=");
        var file = new MockMultipartFile("file", "photo.png", "image/png", png);
        MvcResult upload = mvc.perform(multipart("/api/documents/" + d.s("id") + "/attachments").file(file).header("Authorization", "Bearer " + token)).andReturn(); assertEquals(200, upload.getResponse().getStatus());
        String aid = response(upload).s("id"); assertArrayEquals(png, call("GET", "/attachments/" + aid, null).getResponse().getContentAsByteArray());
        assertEquals(401, mvc.perform(get("/api/attachments/" + aid)).andReturn().getResponse().getStatus());
        MvcResult exported = call("POST", "/exports/availability", of("rows", List.of(row(1, 1)), "startDate", "2026-11-02", "availableDate", "2026-11-05", "warehouseId", "beijing"));
        assertEquals(200, exported.getResponse().getStatus(), exported.getResponse().getContentAsString());
        byte[] bytes = exported.getResponse().getContentAsByteArray(); assertEquals('P', bytes[0]); assertEquals('K', bytes[1]);
        Files.write(Path.of("target/test-availability.xlsx"), bytes);
    }
    @Test void mysqlConstraintsAreEnforced() {
        var check = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> store.run("INSERT INTO assets VALUES (?,?,?,?,?,?,?)", id(), eid, 1, "beijing", null, -1, now()));
        assertEquals(3819, check.getSQLException().getErrorCode());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> store.run("INSERT INTO assets VALUES (?,?,?,?,?,?,?)", id(), "missing", 1, "beijing", null, 1, now()));
    }
    @Test void legacyPasswordHashSurvivesMigration() {
        // Node.js scryptSync('Migration-password-2026', '00112233445566778899aabbccddeeff', 32)。
        String value = "00112233445566778899aabbccddeeff:3766b3217a8f421466d676106d8b5c9c361cc85b8b35da08922410500f370109";
        store.run("INSERT INTO users(id,username,password_hash,role) VALUES (?,?,?,?)", id(), "legacy", value, "KEEPER");
        assertEquals("KEEPER", auth.login(of("username", "legacy", "password", "Migration-password-2026")).obj("user").s("role"));
    }
    @Test void extensionKeepsLaterReservationAndReportsConflict() throws Exception {
        purchase(1, 2); Data out = confirm(draft("OUTBOUND", List.of(row(1, 2))));
        Data future = confirm(draft("RESERVATION", List.of(row(1, 2)), of("startDate", "2026-11-07", "availableDate", "2026-11-11")));
        Data result = ok("POST", "/documents/" + out.s("id") + "/extend", of("expectedVersion", out.get("version"), "availableDate", "2026-11-09", "reason", "剧组延期"));
        assertEquals(1, result.rows("conflicts").size()); assertEquals("RESERVED", service.document(future.s("id")).s("status"));
    }
    @Test void keeperCannotCreateEquipmentThroughPurchase() throws Exception {
        ok("POST", "/users", of("username", "keeper", "password", "Keeper-password-2026", "role", "KEEPER"));
        token = ok("POST", "/auth/login", of("username", "keeper", "password", "Keeper-password-2026")).s("token");
        Data d = draft("PURCHASE", List.of(of("rawName", "不应被创建", "quantity", 1, "createEquipment", true, "unit", "套")));
        assertEquals(403, call("POST", "/documents/" + d.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id())).getResponse().getStatus());
        assertNull(store.one("SELECT id FROM equipment WHERE name='不应被创建'"));
    }
    @Test void lateOutstandingDoesNotReleaseAndSourceCanBeInherited() throws Exception {
        purchase(3, 2); Data out = confirm(draft("OUTBOUND", List.of(row(2, 2))));
        store.run("UPDATE allocations SET start_date='2026-10-30',available_date='2026-11-01' WHERE document_id=?", out.s("id"));
        Data result = ok("POST", "/availability/query", of("rows", List.of(row(2, 2)), "warehouseId", "beijing", "startDate", "2026-11-04", "availableDate", "2026-11-08"));
        assertEquals("STOCK_CONFLICT", result.obj("issue").s("code"));
        Data returned = confirm(draft("RETURN", List.of(of("allocationId", out.rows("allocations").get(0).get("id"), "quantity", 1)), of("availableDate", "2026-11-03")));
        assertEquals(2, returned.obj("payload").rows("rows").get(0).i("ownerId")); assertEquals(cid, returned.s("crew_id"));
    }
    @Test void sameContentUploadCannotBePostedTwice() throws Exception {
        Data d = draft("PURCHASE", List.of(row(1, 1)));
        byte[] workbook = new Workbooks(System.getenv().getOrDefault("PYTHON", "python"), "python/workbook.py", "target/test-data").export("采购", List.of(List.of("设备", "数量"), List.of("轨道", 2)));
        String first = uploadWorkbook(workbook), second = uploadWorkbook(workbook);
        confirm(draft("PURCHASE", List.of(row(2, 2)), of("importId", first)));
        Data duplicate = draft("PURCHASE", List.of(row(2, 2)), of("importId", second));
        assertEquals("IMPORT_DUPLICATE", response(call("POST", "/documents/" + duplicate.s("id") + "/confirm", of("expectedVersion", 1, "idempotencyKey", id()))).s("code"));
        assertEquals(2, inventory().n("total"));
    }
    private String uploadWorkbook(byte[] workbook) throws Exception {
        var file = new MockMultipartFile("file", "purchase.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);
        MvcResult r = mvc.perform(multipart("/api/imports?kind=PURCHASE").file(file).header("Authorization", "Bearer " + token)).andReturn(); assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString()); return response(r).s("id");
    }
    @Test void migrationIsAtomicAndRefusesPopulatedTarget() {
        Data exported = of("format", "warehouse-sqlite-v1", "tables", of());
        for (String table : Bootstrap.TABLES) exported.obj("tables").put(table, new ArrayList<>(store.all("SELECT * FROM `" + table + "`")));
        assertThrows(IllegalStateException.class, () -> bootstrap.importData(exported));
        store.tx(() -> {
            store.run("DELETE FROM sessions");
            List<String> tables = new ArrayList<>(Bootstrap.TABLES); Collections.reverse(tables);
            for (String table : tables) if (!Set.of("owners", "warehouses").contains(table)) store.run("DELETE FROM `" + table + "`");
            return null;
        });
        Data broken = copy(exported); broken.obj("tables").rows("equipment").get(0).put("category_id", "missing-category");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> bootstrap.importData(broken));
        assertTrue(store.all("SELECT * FROM users").isEmpty()); assertEquals(2, store.all("SELECT * FROM owners").size());
        bootstrap.importData(exported);
        assertEquals(eid, store.one("SELECT * FROM equipment").s("id")); assertEquals("ADMIN", auth.login(of("username", "admin", "password", "Test-only-password-2026")).obj("user").s("role"));
    }
}
