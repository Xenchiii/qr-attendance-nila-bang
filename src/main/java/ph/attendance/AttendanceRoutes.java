package ph.attendance;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AttendanceRoutes {
    private static final String STAMP = "'%Y-%m-%dT%H:%i:%s.000Z'";

    private AttendanceRoutes() {
    }

    public static void mount(Javalin app) {
        app.post("/api/qr", Security.secured(AttendanceRoutes::generate, "teacher"));
        app.get("/api/attendance", Security.secured(AttendanceRoutes::list));
        app.post("/api/attendance", Security.secured(AttendanceRoutes::scan, "staff"));
        app.put("/api/attendance", Security.secured(AttendanceRoutes::timeOut, "teacher"));
    }

    private static void generate(Context ctx) {
        long teacher = Security.uid(ctx);
        Map<String, Object> room = Db.one("SELECT id FROM rooms WHERE id = ? AND status = 'AVAILABLE'", Http.str(Http.body(ctx), "room_id"));
        if (room == null) {
            Http.fail(ctx, 400, "That room is not available");
            return;
        }
        if (Db.one("SELECT id FROM attendance WHERE teacher_id = ? AND status = 'ACTIVE'", teacher) != null) {
            Http.fail(ctx, 400, "Time out of your current class before generating a new code");
            return;
        }
        String token = Security.token();
        Db.tx(connection -> {
            Db.exec(connection, "UPDATE qr_sessions SET status = 'CANCELLED' WHERE teacher_id = ? AND status = 'ACTIVE'", teacher);
            Db.exec(connection,
                "INSERT INTO qr_sessions (teacher_id, room_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, UTC_TIMESTAMP(), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 5 MINUTE))",
                teacher, room.get("id"), Security.sha256(token));
        });
        ctx.json(Map.of("success", true, "data", Map.of("token", token, "expires_at", Instant.now().plusSeconds(300).toString())));
    }

    private static void list(Context ctx) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (Security.role(ctx).equals("teacher")) {
            where.append(" AND a.teacher_id = ?");
            args.add(Security.uid(ctx));
        }
        String status = ctx.queryParam("status");
        String date = ctx.queryParam("date");
        String q = ctx.queryParam("q");
        if (status != null && !status.isEmpty()) {
            where.append(" AND a.status = ?");
            args.add(status);
        }
        if (date != null && !date.isEmpty()) {
            where.append(" AND DATE(DATE_ADD(a.time_in, INTERVAL ? MINUTE)) = ?");
            args.add(Http.num(ctx.queryParam("tz")));
            args.add(date);
        }
        if (q != null && !q.isEmpty()) {
            where.append(" AND (CONCAT(t.first_name, ' ', t.last_name) LIKE ? OR r.name LIKE ?)");
            args.add("%" + q + "%");
            args.add("%" + q + "%");
        }
        String sql = "SELECT a.id, DATE_FORMAT(a.time_in, " + STAMP + ") AS time_in, DATE_FORMAT(a.time_out, " + STAMP + ") AS time_out, "
            + "a.duration_minutes, a.status, CONCAT(t.first_name, ' ', t.last_name) AS teacher_name, r.name AS room_name "
            + "FROM attendance a JOIN users t ON t.id = a.teacher_id JOIN rooms r ON r.id = a.room_id"
            + where + " ORDER BY a.time_in DESC LIMIT 500";
        ctx.json(Map.of("success", true, "data", Db.query(sql, args.toArray())));
    }

    private static void scan(Context ctx) {
        String token = Http.str(Http.body(ctx), "token");
        if (token.isEmpty()) {
            Http.fail(ctx, 400, "No QR code data received");
            return;
        }
        Map<String, Object> session = Db.one(
            "SELECT id, teacher_id, room_id, status, expires_at < UTC_TIMESTAMP() AS expired FROM qr_sessions WHERE token_hash = ?",
            Security.sha256(token));
        if (session == null) {
            Http.fail(ctx, 400, "This QR code is not recognised");
            return;
        }
        if (!session.get("status").equals("ACTIVE")) {
            Http.fail(ctx, 400, "This QR code was already used or cancelled");
            return;
        }
        if (Http.truthy(session.get("expired"))) {
            Db.update("UPDATE qr_sessions SET status = 'EXPIRED' WHERE id = ?", session.get("id"));
            Http.fail(ctx, 400, "This QR code has expired. Ask the teacher to generate a new one");
            return;
        }
        if (Db.one("SELECT id FROM attendance WHERE teacher_id = ? AND status = 'ACTIVE'", session.get("teacher_id")) != null) {
            Http.fail(ctx, 400, "This teacher already has an active attendance record");
            return;
        }
        Map<String, Object> room = Db.one("SELECT name, status FROM rooms WHERE id = ?", session.get("room_id"));
        if (!room.get("status").equals("AVAILABLE")) {
            Http.fail(ctx, 400, room.get("name") + " is not available");
            return;
        }
        long staff = Security.uid(ctx);
        try {
            Db.tx(connection -> {
                Db.exec(connection, "INSERT INTO attendance (teacher_id, room_id, qr_session_id, scanned_by, time_in) VALUES (?, ?, ?, ?, UTC_TIMESTAMP())",
                    session.get("teacher_id"), session.get("room_id"), session.get("id"), staff);
                Db.exec(connection, "UPDATE qr_sessions SET status = 'USED', used_at = UTC_TIMESTAMP() WHERE id = ?", session.get("id"));
                Db.exec(connection, "UPDATE rooms SET status = 'OCCUPIED' WHERE id = ?", session.get("room_id"));
            });
        } catch (RuntimeException error) {
            if (Db.isDuplicate(error)) {
                Http.fail(ctx, 400, "This teacher already has an active attendance record");
                return;
            }
            throw error;
        }
        Map<String, Object> teacher = Db.one("SELECT CONCAT(first_name, ' ', last_name) AS name FROM users WHERE id = ?", session.get("teacher_id"));
        Http.ok(ctx, "Time In recorded for " + teacher.get("name") + " in " + room.get("name"));
    }

    private static void timeOut(Context ctx) {
        Map<String, Object> open = Db.one(
            "SELECT id, room_id, GREATEST(1, ROUND(TIMESTAMPDIFF(SECOND, time_in, UTC_TIMESTAMP()) / 60)) AS minutes FROM attendance WHERE teacher_id = ? AND status = 'ACTIVE'",
            Security.uid(ctx));
        if (open == null) {
            Http.fail(ctx, 400, "You have no active attendance record");
            return;
        }
        Db.tx(connection -> {
            Db.exec(connection, "UPDATE attendance SET time_out = UTC_TIMESTAMP(), duration_minutes = ?, status = 'COMPLETED' WHERE id = ?",
                ((Number) open.get("minutes")).intValue(), open.get("id"));
            Db.exec(connection, "UPDATE rooms SET status = 'AVAILABLE' WHERE id = ?", open.get("room_id"));
        });
        Http.ok(ctx, "Time Out recorded");
    }
}
