package ph.attendance;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Security {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private Security() {
    }

    private static byte[] derive(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100000, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HEX.formatHex(salt) + ":" + HEX.formatHex(derive(password, salt));
    }

    public static boolean verify(String password, String stored) {
        String[] parts = stored.split(":");
        if (parts.length != 2) {
            return false;
        }
        return MessageDigest.isEqual(derive(password, HEX.parseHex(parts[0])), HEX.parseHex(parts[1]));
    }

    public static String token() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    public static String sha256(String text) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static void setCookie(Context ctx, String value, int maxAge) {
        String secure = "true".equals(System.getenv("COOKIE_SECURE")) ? "; Secure" : "";
        ctx.header("Set-Cookie", "sid=" + value + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=" + maxAge + secure);
    }

    public static Map<String, Object> user(Context ctx) {
        String sid = ctx.cookie("sid");
        if (sid == null || !sid.matches("[a-f0-9]+")) {
            return null;
        }
        return Db.one(
            "SELECT u.id, u.role, u.employee_no, u.first_name, u.last_name, u.email FROM auth_sessions s "
                + "JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > UTC_TIMESTAMP() AND u.status = 'ACTIVE'",
            sha256(sid));
    }

    public static long uid(Context ctx) {
        Map<String, Object> user = ctx.attribute("user");
        return ((Number) user.get("id")).longValue();
    }

    public static String role(Context ctx) {
        Map<String, Object> user = ctx.attribute("user");
        return (String) user.get("role");
    }

    public static Handler secured(Handler next, String... roles) {
        return ctx -> {
            Map<String, Object> user = user(ctx);
            if (user == null) {
                Http.fail(ctx, 401, "Please log in first");
                return;
            }
            if (roles.length > 0 && !Arrays.asList(roles).contains(user.get("role"))) {
                Http.fail(ctx, 403, "You do not have access to this action");
                return;
            }
            ctx.attribute("user", user);
            next.handle(ctx);
        };
    }
}
