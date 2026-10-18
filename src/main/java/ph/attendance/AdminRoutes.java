package ph.attendance;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;

public final class AdminRoutes {
    private AdminRoutes() {
    }

    public static void mount(Javalin app) {
        app.get("/api/rooms", Security.secured(AdminRoutes::rooms));
        app.post("/api/rooms", Security.secured(AdminRoutes::addRoom, "admin"));
        app.get("/api/users", Security.secured(AdminRoutes::users, "admin"));
        app.put("/api/users", Security.secured(AdminRoutes::setStatus, "admin"));
        app.get("/api/stats", Security.secured(AdminRoutes::stats, "admin", "staff"));
    }

    private static void rooms(Context ctx) {
        ctx.json(Map.of("success", true, "data",
            Db.query("SELECT id, code, name, building, `floor` AS floor, capacity, status FROM rooms ORDER BY building, name")));
    }

    private static void addRoom(Context ctx) {
        Map<String, Object> data = Http.body(ctx);
        String code = Http.str(data, "code");
        String name = Http.str(data, "name");
        String building = Http.str(data, "building");
        if (code.isEmpty() || name.isEmpty() || building.isEmpty()) {
            Http.fail(ctx, 400, "Code, name and building are required");
            return;
        }
        try {
            Db.update("INSERT INTO rooms (code, name, building, `floor`, capacity) VALUES (?, ?, ?, ?, ?)",
                code, name, building, Http.str(data, "floor"), Math.max(0, Http.num(Http.str(data, "capacity"))));
        } catch (RuntimeException error) {
            if (Db.isDuplicate(error)) {
                Http.fail(ctx, 400, "That room code already exists");
                return;
            }
            throw error;
        }
        Http.ok(ctx, "Room added");
    }

    private static void users(Context ctx) {
        ctx.json(Map.of("success", true, "data", Db.query(
            "SELECT id, role, employee_no, first_name, last_name, email, status FROM users WHERE role <> 'admin' ORDER BY status, role, last_name")));
    }

    private static void setStatus(Context ctx) {
        Map<String, Object> data = Http.body(ctx);
        String status = Http.str(data, "status");
        if (!List.of("ACTIVE", "INACTIVE", "SUSPENDED").contains(status)) {
            Http.fail(ctx, 400, "Invalid status");
            return;
        }
        int id = Http.num(Http.str(data, "id"));
        Db.tx(connection -> {
            Db.exec(connection, "UPDATE users SET status = ? WHERE id = ? AND role <> 'admin'", status, id);
            if (!status.equals("ACTIVE")) {
                Db.exec(connection, "DELETE s FROM auth_sessions s JOIN users u ON u.id = s.user_id WHERE s.user_id = ? AND u.role <> 'admin'", id);
            }
        });
        Http.ok(ctx, "Account updated");
    }

    private static void stats(Context ctx) {
        String date = ctx.queryParam("date");
        Map<String, Object> row = Db.one(
            "SELECT (SELECT COUNT(*) FROM users WHERE role = 'teacher' AND status = 'ACTIVE') AS teachers, "
                + "(SELECT COUNT(*) FROM users WHERE role = 'staff' AND status = 'ACTIVE') AS staff, "
                + "(SELECT COUNT(*) FROM users WHERE status = 'INACTIVE') AS pending, "
                + "(SELECT COUNT(*) FROM attendance WHERE status = 'ACTIVE') AS in_use, "
                + "(SELECT COUNT(*) FROM attendance WHERE DATE(DATE_ADD(time_in, INTERVAL ? MINUTE)) = ?) AS today",
            Http.num(ctx.queryParam("tz")), date == null ? "" : date);
        ctx.json(Map.of("success", true, "data", row));
    }
}
