package com.yingchang.warehouse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.yingchang.warehouse.Data.*;
import static com.yingchang.warehouse.Auth.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final WarehouseService service;
    private final Store store;
    private final RequestSchemas schemas;
    public DocumentController(WarehouseService service, Store store, RequestSchemas schemas) { this.service = service; this.store = store; this.schemas = schemas; }
    @GetMapping public List<Data> list(@RequestParam(defaultValue="") String crewId, @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="") String type) {
        Set<String> related = new HashSet<>();
        if (!crewId.isEmpty()) store.all("SELECT DISTINCT r.document_id FROM returns r JOIN allocations a ON a.id=r.allocation_id JOIN documents d ON d.id=a.document_id WHERE d.crew_id=? OR d.related_crew_id=?", crewId, crewId).forEach(r -> related.add(r.s("document_id")));
        return store.all("SELECT d.*,c.name crew_name,rc.name related_crew_name,c.kind recipient_kind FROM documents d LEFT JOIN crews c ON c.id=d.crew_id LEFT JOIN crews rc ON rc.id=d.related_crew_id ORDER BY d.created_at DESC").stream()
            .filter(d -> (crewId.isEmpty() || d.s("crew_id").equals(crewId) || d.s("related_crew_id").equals(crewId) || related.contains(d.s("id"))) && (status.isEmpty() || d.s("status").equals(status)) && (type.isEmpty() || d.s("type").equals(type)))
            .map(d -> d.with("payload", parse(d.s("payload_json")))).toList();
    }
    @PatchMapping("/{id}/related-crew") public Data relateCrew(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data actor = guard(req, "ADMIN"); schemas.validate("relatedCrew", b); return service.relateCrew(id, b, actor); }
    @GetMapping("/{id}") public Data get(@PathVariable String id) { return service.document(id); }
    @PostMapping public Data create(@RequestBody Data b, HttpServletRequest req) { Data a = guard(req); schemas.validate("draft", b); return service.create(b, a); }
    @PatchMapping("/{id}") public Data update(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { guard(req); schemas.validate("update", b); return service.update(id, b.i("expectedVersion"), b.obj("payload")); }
    @PostMapping("/{id}/confirm") public Data confirm(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data a = guard(req); schemas.validate("confirm", b); return service.confirm(id, b.i("expectedVersion"), b.s("idempotencyKey"), a); }
    @PostMapping("/{id}/dispatch") public Data dispatch(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data a = guard(req); schemas.validate("action", b); return service.dispatch(id, b.i("expectedVersion"), a); }
    @PostMapping("/{id}/cancel") public Data cancel(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data a = guard(req); schemas.validate("action", b); return service.cancel(id, b.i("expectedVersion"), a); }
    @PostMapping("/{id}/receive") public Data receive(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data a = guard(req); schemas.validate("action", b); return service.receive(id, b.i("expectedVersion"), a); }
    @PostMapping("/{id}/extend") public Data extend(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data a = guard(req); schemas.validate("extend", b); return service.extend(id, b.i("expectedVersion"), b.s("availableDate"), b.s("reason"), a); }
    @PostMapping("/{id}/corrections") public Data correct(@PathVariable String id, @RequestBody Data b, HttpServletRequest req) { Data a = guard(req, "ADMIN"); schemas.validate("corrections", b); return service.correct(id, b.i("expectedVersion"), b.rows("rows"), b.s("reason"), a); }
}
