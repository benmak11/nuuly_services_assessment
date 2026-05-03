package app.inventory;

import app.inventory.db.Database;
import app.inventory.model.InventoryItem;
import app.inventory.repository.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryAddStockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void createsSkuWhenItDoesNotExist() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinHarness.run(app, (server, client) -> {
            var response = client.post("/inventory/NEW-SKU", "{\"quantity\":5}");
            assertEquals(200, response.statusCode());
            InventoryItem item = MAPPER.readValue(response.body(), InventoryItem.class);
            assertEquals(new InventoryItem("NEW-SKU", 5), item);
        });
    }

    @Test
    void addsToExistingSkuQuantity() throws Exception {
        Database db = setupDb();
        seed(db, "WIDGET-1", 10);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinHarness.run(app, (server, client) -> {
            var response = client.post("/inventory/WIDGET-1", "{\"quantity\":3}");
            assertEquals(200, response.statusCode());
            InventoryItem item = MAPPER.readValue(response.body(), InventoryItem.class);
            assertEquals(new InventoryItem("WIDGET-1", 13), item);
        });
    }

    @Test
    void persistsAcrossMultipleAdds() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinHarness.run(app, (server, client) -> {
            client.post("/inventory/MULTI", "{\"quantity\":4}");
            client.post("/inventory/MULTI", "{\"quantity\":6}");
            var response = client.post("/inventory/MULTI", "{\"quantity\":1}");
            assertEquals(200, response.statusCode());
            InventoryItem item = MAPPER.readValue(response.body(), InventoryItem.class);
            assertEquals(new InventoryItem("MULTI", 11), item);
        });
    }

    @Test
    void returns400WhenQuantityIsZero() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinHarness.run(app, (server, client) -> {
            var response = client.post("/inventory/SKU-A", "{\"quantity\":0}");
            assertEquals(400, response.statusCode());
        });
    }

    @Test
    void returns400WhenQuantityIsNegative() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinHarness.run(app, (server, client) -> {
            var response = client.post("/inventory/SKU-A", "{\"quantity\":-3}");
            assertEquals(400, response.statusCode());
        });
    }

    @Test
    void returns400WhenBodyIsMalformed() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinHarness.run(app, (server, client) -> {
            var response = client.post("/inventory/SKU-A", "{not json");
            assertEquals(400, response.statusCode());
        });
    }

    @Test
    void returns400WhenQuantityIsMissing() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinHarness.run(app, (server, client) -> {
            var response = client.post("/inventory/SKU-A", "{}");
            assertEquals(400, response.statusCode());
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
}
