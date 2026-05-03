package app.inventory.repository;

import app.inventory.db.Database;
import app.inventory.model.InventoryItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class InventoryRepository {

    private final Database database;

    public InventoryRepository(Database database) {
        this.database = database;
    }

    public Optional<InventoryItem> findBySkuId(String skuId) throws SQLException {
        try (Connection conn = database.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT sku_id, quantity FROM inventory WHERE sku_id = ?")) {
            ps.setString(1, skuId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new InventoryItem(
                        rs.getString("sku_id"),
                        rs.getInt("quantity")
                ));
            }
        }
    }

    public InventoryItem addStock(String skuId, int quantity) throws SQLException {
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
                    throw new SQLException("Upsert returned no row for sku=" + skuId);
                }
                return new InventoryItem(
                        rs.getString("sku_id"),
                        rs.getInt("quantity")
                );
            }
        }
    }

    public PurchaseResult purchase(String skuId, int quantity) throws SQLException {
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
                return exists
                        ? new PurchaseResult.Insufficient()
                        : new PurchaseResult.NotFound();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
