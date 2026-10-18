package ph.attendance;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;
import java.util.regex.Pattern;

public final class AuthRoutes {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private AuthRoutes() {
    }

    public static void mount(Javalin app) {
        app.post("/api/auth/register", AuthRoutes::signUp);
        app.post("/api/auth/login", AuthRoutes::signIn);
        app.post("/api/auth/logout", AuthRoutes::signOut);
        app.get("/api/auth/me", AuthRoutes::me);
    }

    private static void signUp(Context ctx) {
        Map<String, Object> data = Http.body(ctx);
        String role = Http.str(data, "role");
        String first = Http.str(data, "first_name");
        String last = Http.str(data, "last_name");
        String number = Http.str(data, "employee_no");
        String email = Http.str(data, "email").toLowerCase();
        String password = Http.raw(data, "password");
        if (!(role.equals("teacher") || role.equals("staff")) || first.isEmpty() || last.isEmpty() || number.isEmpty()) {
            Http.fail(ctx, 400, "Complete all fields");
            return;
        }
        if (!EMAIL.matcher(email).matches()) {
            Http.fail(ctx, 400, "Enter a valid email address");
            return;
        }
        if (password.length() < 8) {
            Http.fail(ctx, 400, "Password must be at least 8 characters");
            return;
        }
        String status = role.equals("teacher") ? "ACTIVE" : "INACTIVE";
        try {
            Db.update(
                "INSERT INTO users (role, employee_no, first_name, last_name, email, password_hash, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                role, number, first, last, email, Security.hash(password), status);
        } catch (RuntimeException error) {
            if (Db.isDuplicate(error)) {
                Http.fail(ctx, 400, "That email or ID number is already registered");
                return;
            }
            throw error;
        }
        Http.ok(ctx, status.equals("ACTIVE")
            ? "Registration successful. You can now log in."
            : "Registration successful. An administrator must approve your account before you can log in.");
    }

    private static void signIn(Context ctx) {
        Map<String, Object> data = Http.body(ctx);
        String role = Http.str(data, "role");
        Map<String, Object> user = Db.one("SELECT * FROM users WHERE email = ? AND role = ?", Http.str(data, "email").toLowerCase(), role);
        if (user == null || !Security.verify(Http.raw(data, "password"), (String) user.get("password_hash"))) {
            Http.fail(ctx, 401, "Incorrect email, password or role");
            return;
        }
        String status = (String) user.get("status");
        if (status.equals("INACTIVE")) {
            Http.fail(ctx, 403, "Your account is waiting for administrator approval");
            return;
        }
        if (!status.equals("ACTIVE")) {
            Http.fail(ctx, 403, "Your account is suspended. Contact the administrator");
            return;
        }
        String token = Security.token();
        Object id = user.get("id");
        Db.tx(connection -> {
            Db.exec(connection, "INSERT INTO auth_sessions (token_hash, user_id, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR))",
                Security.sha256(token), id);
            Db.exec(connection, "UPDATE users SET last_login = UTC_TIMESTAMP() WHERE id = ?", id);
        });
        Security.setCookie(ctx, token, 28800);
        ctx.json(Map.of("success", true, "user", Map.of("role", role)));
    }

    private static void signOut(Context ctx) {
        String sid = ctx.cookie("sid");
        if (sid != null) {
            Db.update("DELETE FROM auth_sessions WHERE token_hash = ?", Security.sha256(sid));
        }
        Security.setCookie(ctx, "", 0);
        ctx.json(Map.of("success", true));
    }

    private static void me(Context ctx) {
        Map<String, Object> user = Security.user(ctx);
        if (user == null) {
            ctx.status(401).json(Map.of("success", false));
            return;
        }
        ctx.json(Map.of("success", true, "user", user));
    }
}
