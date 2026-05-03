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
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryGetTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void returns404WhenSkuMissing() throws Exception {
        Javalin app = Main.createApp(new InventoryRepository(setupDb()));

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/inventory/MISSING-SKU");
            assertEquals(404, response.code());
            assertEquals("SKU not found", response.body().string());
        });
    }

    @Test
    void returns200WithJsonWhenSkuExists() throws Exception {
        Database db = setupDb();
        seed(db, "WIDGET-1", 7);
        Javalin app = Main.createApp(new InventoryRepository(db));

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/inventory/WIDGET-1");
            assertEquals(200, response.code());
            InventoryItem item = MAPPER.readValue(
                    response.body().string(), InventoryItem.class);
            assertEquals(new InventoryItem("WIDGET-1", 7), item);
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
