package com.yingchang.warehouse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.yingchang.warehouse.Data.*;
import static com.yingchang.warehouse.Auth.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final Store store;
    private final Auth auth;
    private final RequestSchemas schemas;
    public AuthController(Store store, Auth auth, RequestSchemas schemas) { this.store = store; this.auth = auth; this.schemas = schemas; }
    @PostMapping("/auth/login") public Data login(@RequestBody Data b) { schemas.validate("login", b); return auth.login(b); }
    @GetMapping("/auth/me") public Data me(HttpServletRequest req) { return actor(req); }
    @PostMapping("/auth/logout") public Data logout(HttpServletRequest req) { store.run("DELETE FROM sessions WHERE token_hash=?", hash(req.getHeader("Authorization").substring(7))); return of("ok", true); }
    @GetMapping("/users") public List<Data> users(HttpServletRequest req) { guard(req, "ADMIN"); return store.all("SELECT id,username,role,active FROM users"); }
    @PostMapping("/users") public Data createUser(@RequestBody Data b, HttpServletRequest req) {
        guard(req, "ADMIN"); schemas.validate("user", b);
        return store.tx(() -> { String uid = id(); store.run("INSERT INTO users(id,username,password_hash,role) VALUES (?,?,?,?)", uid, b.s("username"), passwordHash(b.s("password")), b.s("role")); return of("id", uid, "username", b.get("username"), "role", b.get("role")); });
    }
}
