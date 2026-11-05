package com.yingchang.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.yingchang.warehouse.Data.*;
import java.util.List;
import static org.mockito.Mockito.*;

class CoreTest {
    @Test void inventoryStatusesKeepSourceAndDateBoundaries() {
        Store store = mock(Store.class);
        when(store.all(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM assets")) return List.of(
                of("id", "bj", "equipment_id", "e", "quantity", 20, "owner_id", 1, "warehouse_id", "beijing"),
                of("id", "hd", "equipment_id", "e", "quantity", 4, "owner_id", 2, "warehouse_id", "hengdian"));
            if (sql.contains("FROM allocations")) return List.of(
                of("asset_id", "bj", "document_id", "out", "equipment_id", "e", "quantity", 5, "returned_quantity", 2, "status", "OUT", "start_date", "2026-10-01", "available_date", "2026-10-31"),
                of("asset_id", "bj", "document_id", "current", "quantity", 2, "status", "RESERVED", "start_date", "2026-11-02", "available_date", "2026-11-04"),
                of("asset_id", "bj", "document_id", "future", "quantity", 3, "status", "RESERVED", "start_date", "2026-11-03", "available_date", "2026-11-05"),
                of("asset_id", "bj", "document_id", "past", "quantity", 4, "status", "RESERVED", "start_date", "2026-10-31", "available_date", "2026-11-02"));
            if (sql.contains("FROM holds")) return List.of(
                of("asset_id", "bj", "quantity", 2, "start_date", "2026-11-02", "available_date", "2026-11-03"),
                of("asset_id", "bj", "quantity", 1, "start_date", "2026-11-01", "available_date", "2026-11-02"),
                of("asset_id", "bj", "quantity", 1, "start_date", "2026-11-03"),
                of("asset_id", "hd", "quantity", 1, "start_date", "2026-11-01"));
            if (sql.contains("FROM transfers")) return List.of(of("asset_id", "bj", "quantity", 1));
            return List.of(of("id", "e", "name", "轨道"));
        });
        Inventory inventory = new Inventory(store, "2026-11-02");
        Data result = inventory.list("e", 0, "").get(0);
        Data bj = result.rows("breakdown").stream().filter(r -> r.i("ownerId") == 1 && r.s("warehouseId").equals("beijing")).findFirst().orElseThrow();
        assertEquals(20, bj.n("total")); assertEquals(16, bj.n("inStock")); assertEquals(12, bj.n("available"));
        assertEquals(3, bj.n("outside")); assertEquals(2, bj.n("reserved")); assertEquals(2, bj.n("maintenance")); assertEquals(1, bj.n("inTransit"));
        Data hd = inventory.list("e", 2, "hengdian").get(0);
        assertEquals(4, hd.n("total")); assertEquals(0, hd.n("outside"));
        assertEquals(1, hd.rows("breakdown").stream().mapToDouble(r -> r.n("maintenance")).sum());
        assertEquals(0, hd.rows("breakdown").stream().mapToDouble(r -> r.n("reserved") + r.n("inTransit")).sum());
    }
    @Test void sourceRules() {
        assertEquals(0, ownerTwo("", 4)); assertEquals(4, ownerTwo("2", 4)); assertEquals(2, ownerTwo("2*2", 4));
        assertThrows(BusinessError.class, () -> ownerTwo("2*5", 4)); assertThrows(BusinessError.class, () -> ownerTwo("abc", 4));
    }
    @Test void schemaPreservesBoundaryValidation() throws Exception {
        RequestSchemas schemas = new RequestSchemas(new ObjectMapper());
        schemas.validate("equipment", of("name", "稳定器", "categoryId", "stabilizer", "unit", "套", "brand", "DJI", "model", "RS 4 Pro"));
        assertThrows(BusinessError.class, () -> schemas.validate("equipment", of("name", "稳定器", "categoryId", "stabilizer", "unit", "套", "model", "x".repeat(501))));
        assertThrows(BusinessError.class, () -> schemas.validate("payload", of("rows", java.util.List.of(of("quantity", "4")))));
        assertThrows(BusinessError.class, () -> schemas.validate("draft", of("number", "x", "type", "PURCHASE", "businessDate", "2026-02-30", "payload", of("rows", java.util.List.of(of("quantity", 1))))));
        assertThrows(BusinessError.class, () -> schemas.validate("categoryGroup", of("name", "   ")));
        schemas.validate("payload", of("rows", java.util.List.of(of("quantity", 0, "parserIssues", java.util.List.of("待核对")))));
    }
}
