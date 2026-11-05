package com.yingchang.warehouse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import static com.yingchang.warehouse.Data.*;

@Component
public class Auth implements HandlerInterceptor {
    private final Store store;
    private static final SecureRandom RANDOM = new SecureRandom();
    public Auth(Store store) { this.store = store; }
    private static String random(int size) { byte[] bytes = new byte[size]; RANDOM.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    public static String passwordHash(String password) {
        String salt = random(16);
        return salt + ":" + HexFormat.of().formatHex(derive(password, salt));
    }
    private static byte[] derive(String password, String salt) { return SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt.getBytes(StandardCharsets.UTF_8), 16384, 8, 1, 32); }
    private static boolean matches(String password, String value) {
        String[] parts = value.split(":");
        return parts.length == 2 && MessageDigest.isEqual(derive(password, parts[0]), HexFormat.of().parseHex(parts[1]));
    }
    @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse response, Object handler) {
        String path = req.getRequestURI();
        if (req.getMethod().equals("OPTIONS") || path.equals("/api/health") || (path.equals("/api/auth/login") && req.getMethod().equals("POST"))) return true;
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new BusinessError("UNAUTHENTICATED", "请先登录", 401);
        Data actor = store.one("SELECT u.id,u.username,u.role FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>? AND u.active=1", hash(header.substring(7)), now());
        if (actor == null) throw new BusinessError("UNAUTHENTICATED", "登录已失效，请重新登录", 401);
        req.setAttribute("actor", actor);
        return true;
    }
    public static Data actor(HttpServletRequest req) { return (Data) req.getAttribute("actor"); }
    public static Data guard(HttpServletRequest req, String... roles) {
        Data actor = actor(req);
        Set<String> allowed = roles.length == 0 ? Set.of("ADMIN", "KEEPER") : Set.of(roles);
        if (!allowed.contains(actor.s("role"))) throw new BusinessError("FORBIDDEN", "当前账号没有此操作权限", 403);
        return actor;
    }
    public Data login(Data body) {
        Data user = store.one("SELECT * FROM users WHERE username=? AND active=1", body.s("username"));
        if (user == null || !matches(body.s("password"), user.s("password_hash"))) throw new BusinessError("LOGIN_FAILED", "用户名或密码不正确", 401);
        String token = random(32);
        store.run("INSERT INTO sessions VALUES (?,?,?)", hash(token), user.s("id"), Instant.now().plusSeconds(7 * 86400).toString());
        return of("token", token, "user", of("id", user.get("id"), "username", user.get("username"), "role", user.get("role")));
    }
}
