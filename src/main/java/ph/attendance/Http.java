package ph.attendance;

import io.javalin.http.Context;
import java.util.HashMap;
import java.util.Map;

public final class Http {
    private Http() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> body(Context ctx) {
        try {
            Map<String, Object> data = ctx.bodyAsClass(Map.class);
            return data == null ? new HashMap<>() : data;
        } catch (Exception error) {
            return new HashMap<>();
        }
    }

    public static String raw(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : value.toString();
    }

    public static String str(Map<String, Object> data, String key) {
        return raw(data, key).trim();
    }

    public static int num(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception error) {
            return 0;
        }
    }

    public static boolean truthy(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value instanceof Number number && number.intValue() != 0;
    }

    public static void fail(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of("success", false, "message", message));
    }

    public static void ok(Context ctx, String message) {
        ctx.json(Map.of("success", true, "message", message));
    }
}
