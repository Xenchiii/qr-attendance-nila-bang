package ph.attendance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Db {
    public interface Work {
        void run(Connection connection) throws SQLException;
    }

    private static HikariDataSource pool;

    private Db() {
    }

    public static void init() {
        Map<String, String> env = System.getenv();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env.getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/qr_attendance"));
        config.setUsername(env.getOrDefault("DB_USER", "root"));
        config.setPassword(env.getOrDefault("DB_PASS", ""));
        config.setMaximumPoolSize(8);
        pool = new HikariDataSource(config);
    }

    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }

    public static List<Map<String, Object>> query(String sql, Object... args) {
        try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException error) {
            throw new RuntimeException(error);
        }
    }

    public static Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = query(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static void exec(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            statement.executeUpdate();
        }
    }

    public static void update(String sql, Object... args) {
        tx(connection -> exec(connection, sql, args));
    }

    public static void tx(Work work) {
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new RuntimeException(error);
        }
    }

    public static boolean isDuplicate(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }
}
