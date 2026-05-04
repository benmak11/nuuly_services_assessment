package app.inventory.repository;

import app.inventory.db.Database;
import app.inventory.model.InventoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class InventoryRepository {

    private static final Logger log = LoggerFactory.getLogger(InventoryRepository.class);

    private final Database database;

    public InventoryRepository(Database database) {
        this.database = database;
    }

    public Optional<InventoryItem> findBySkuId(String skuId) throws SQLException {
        log.info("DB findBySkuId sku={}", skuId);
        try (Connection conn = database.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT sku_id, quantity FROM inventory WHERE sku_id = ?")) {
            ps.setString(1, skuId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    log.info("DB findBySkuId sku={} - no row", skuId);
                    return Optional.empty();
                }
                InventoryItem item = new InventoryItem(
                        rs.getString("sku_id"),
                        rs.getInt("quantity"));
                log.info("DB findBySkuId sku={} - found quantity={}", skuId, item.quantity());
                return Optional.of(item);
            }
        } catch (SQLException e) {
            log.error("DB findBySkuId sku={} - failed: {}", skuId, e.getMessage());
            throw e;
        }
    }

    public InventoryItem addStock(String skuId, int quantity) throws SQLException {
        log.info("DB addStock sku={} quantity={}", skuId, quantity);
        try (Connection conn = database.connect();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO inventory (sku_id, quantity) VALUES (?, ?)
                     ON CONFLICT(sku_id) DO UPDATE
                         SET quantity = inventory.quantity + excluded.quantity
                     RETURNING sku_id, quantity
                     """)) {
            ps.setString(1, skuId);
            ps.setInt(2, quantity);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    log.error("DB addStock sku={} - upsert returned no row", skuId);
                    throw new SQLException("Upsert returned no row for sku=" + skuId);
                }
                InventoryItem item = new InventoryItem(
                        rs.getString("sku_id"),
                        rs.getInt("quantity"));
                log.info("DB addStock sku={} - success newQuantity={}", skuId, item.quantity());
                return item;
            }
        } catch (SQLException e) {
            log.error("DB addStock sku={} quantity={} - failed: {}", skuId, quantity, e.getMessage());
            throw e;
        }
    }

    public PurchaseResult purchase(String skuId, int quantity) throws SQLException {
        log.info("DB purchase sku={} quantity={}", skuId, quantity);
        try (Connection conn = database.connect()) {
            conn.setAutoCommit(false);
            try {
                InventoryItem updated = null;
                try (PreparedStatement update = conn.prepareStatement("""
                        UPDATE inventory
                           SET quantity = quantity - ?
                         WHERE sku_id = ? AND quantity >= ?
                        RETURNING sku_id, quantity
                        """)) {
                    update.setInt(1, quantity);
                    update.setString(2, skuId);
                    update.setInt(3, quantity);
                    try (ResultSet rs = update.executeQuery()) {
                        if (rs.next()) {
                            updated = new InventoryItem(
                                    rs.getString("sku_id"),
                                    rs.getInt("quantity"));
                        }
                    }
                }

                if (updated != null) {
                    conn.commit();
                    log.info("DB purchase sku={} - success remaining={}", skuId, updated.quantity());
                    return new PurchaseResult.Success(updated);
                }

                boolean exists;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT 1 FROM inventory WHERE sku_id = ?")) {
                    select.setString(1, skuId);
                    try (ResultSet rs = select.executeQuery()) {
                        exists = rs.next();
                    }
                }

                conn.commit();
                if (exists) {
                    log.warn("DB purchase sku={} quantity={} - insufficient inventory", skuId, quantity);
                    return new PurchaseResult.Insufficient();
                }
                log.warn("DB purchase sku={} - sku not found", skuId);
                return new PurchaseResult.NotFound();
            } catch (SQLException e) {
                log.error("DB purchase sku={} quantity={} - rolling back: {}", skuId, quantity, e.getMessage());
                conn.rollback();
                throw e;
            }
        }
    }
}
