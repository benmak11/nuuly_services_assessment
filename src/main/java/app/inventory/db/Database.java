package app.inventory.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS inventory (
                sku_id   TEXT PRIMARY KEY NOT NULL,
                quantity INTEGER NOT NULL CHECK (quantity >= 0)
            );
            """;

    private final String jdbcUrl;

    public Database(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public static Database file(String path) {
        return new Database("jdbc:sqlite:" + path);
    }

    public Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA journal_mode = WAL");
        }
        return conn;
    }

    public void migrate() throws SQLException {
        log.info("Running database migration on {}", jdbcUrl);
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(SCHEMA);
            log.info("Database migration completed");
        } catch (SQLException e) {
            log.error("Database migration failed: {}", e.getMessage());
            throw e;
        }
    }
}
