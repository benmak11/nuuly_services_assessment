package app.inventory.http;

import app.inventory.model.InventoryItem;
import app.inventory.repository.InventoryRepository;
import io.javalin.http.Context;

import java.sql.SQLException;
import java.util.Optional;

public final class InventoryController {

    private final InventoryRepository repository;

    public InventoryController(InventoryRepository repository) {
        this.repository = repository;
    }

    public void getInventory(Context ctx) throws SQLException {
        String skuId = ctx.pathParam("skuId");
        Optional<InventoryItem> item = repository.findBySkuId(skuId);
        if (item.isEmpty()) {
            ctx.status(404).result("SKU not found");
            return;
        }
        ctx.json(item.get());
    }
}
