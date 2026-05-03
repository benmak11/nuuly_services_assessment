package app.inventory;

import app.inventory.db.Database;
import app.inventory.model.InventoryItem;
import app.inventory.repository.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryPurchaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void deductsAndReturnsRemainingQuantity() throws Exception {
        Database db = setupDb();
        seed(db, "WIDGET-1", 10);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/inventory/WIDGET-1/purchase", "{\"quantity\":3}");
            assertEquals(200, response.code());
            InventoryItem item = MAPPER.readValue(
                    response.body().string(), InventoryItem.class);
            assertEquals(new InventoryItem("WIDGET-1", 7), item);
        });
    }

    @Test
    void allowsPurchaseDownToZero() throws Exception {
        Database db = setupDb();
        seed(db, "EXACT", 5);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/inventory/EXACT/purchase", "{\"quantity\":5}");
            assertEquals(200, response.code());
            InventoryItem item = MAPPER.readValue(
                    response.body().string(), InventoryItem.class);
            assertEquals(new InventoryItem("EXACT", 0), item);
        });
    }

    @Test
    void persistsRemainingQuantity() throws Exception {
        Database db = setupDb();
        seed(db, "PERSIST", 10);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinTest.test(app, (server, client) -> {
            client.post("/inventory/PERSIST/purchase", "{\"quantity\":4}");
            var response = client.get("/inventory/PERSIST");
            assertEquals(200, response.code());
            InventoryItem item = MAPPER.readValue(
                    response.body().string(), InventoryItem.class);
            assertEquals(new InventoryItem("PERSIST", 6), item);
        });
    }

    @Test
    void returns404WhenSkuMissing() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/inventory/MISSING/purchase", "{\"quantity\":1}");
            assertEquals(404, response.code());
            assertEquals("SKU not found", response.body().string());
        });
    }

    @Test
    void returns400WhenInsufficientInventory() throws Exception {
        Database db = setupDb();
        seed(db, "LOW", 2);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/inventory/LOW/purchase", "{\"quantity\":5}");
            assertEquals(400, response.code());
            assertEquals("Insufficient inventory", response.body().string());
        });
    }

    @Test
    void doesNotMutateStateOnInsufficientInventory() throws Exception {
        Database db = setupDb();
        seed(db, "LOW", 2);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinTest.test(app, (server, client) -> {
            client.post("/inventory/LOW/purchase", "{\"quantity\":5}");
            assertEquals(2, readQuantity(db, "LOW"));
        });
    }

    @Test
    void returns400WhenQuantityIsZero() throws Exception {
        Database db = setupDb();
        seed(db, "WIDGET", 5);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/inventory/WIDGET/purchase", "{\"quantity\":0}");
            assertEquals(400, response.code());
        });
    }

    @Test
    void returns400WhenQuantityIsNegative() throws Exception {
        Database db = setupDb();
        seed(db, "WIDGET", 5);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/inventory/WIDGET/purchase", "{\"quantity\":-2}");
            assertEquals(400, response.code());
        });
    }

    @Test
    void returns400WhenBodyIsMalformed() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/inventory/WIDGET/purchase", "not json");
            assertEquals(400, response.code());
        });
    }

    @Test
    void invalidRequestTakesPriorityOverNotFound() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/inventory/MISSING/purchase", "{\"quantity\":0}");
            assertEquals(400, response.code());
            assertTrue(response.body().string().toLowerCase().contains("quantity"));
        });
    }

    private Database setupDb() throws SQLException {
        Database db = Database.file(tempDir.resolve("test.db").toString());
        db.migrate();
        return db;
    }

    private void seed(Database db, String skuId, int quantity) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO inventory (sku_id, quantity) VALUES (?, ?)")) {
            ps.setString(1, skuId);
            ps.setInt(2, quantity);
            ps.executeUpdate();
        }
    }

    private int readQuantity(Database db, String skuId) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT quantity FROM inventory WHERE sku_id = ?")) {
            ps.setString(1, skuId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Expected row for " + skuId);
                }
                return rs.getInt("quantity");
            }
        }
    }
}
