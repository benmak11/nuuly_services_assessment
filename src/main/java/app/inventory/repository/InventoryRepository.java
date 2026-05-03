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
}
