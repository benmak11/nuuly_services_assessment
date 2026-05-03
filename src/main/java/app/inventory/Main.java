package app.inventory;

import app.inventory.db.Database;
import app.inventory.http.InventoryController;
import app.inventory.repository.InventoryRepository;
import io.javalin.Javalin;

public class Main {

    private static final int PORT = 7070;
    private static final String DB_PATH = "inventory.db";

    public static void main(String[] args) throws Exception {
        Database database = Database.file(DB_PATH);
        database.migrate();

        InventoryRepository repository = new InventoryRepository(database);
        Javalin app = createApp(repository).start(PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }

    public static Javalin createApp(InventoryRepository repository) {
        InventoryController controller = new InventoryController(repository);
        return Javalin.create(config -> {
            config.routes.get("/inventory/{skuId}", controller::getInventory)
                    .post("/inventory/{skuId}", controller::addStock)
                    .post("/inventory/{skuId}/purchase", controller::purchase);
        });
    }
}
