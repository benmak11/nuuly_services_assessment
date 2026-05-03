package app.inventory.http;

import app.inventory.model.InventoryItem;
import app.inventory.model.InventoryQuantity;
import app.inventory.repository.InventoryRepository;
import app.inventory.repository.PurchaseResult;
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

    public void addStock(Context ctx) throws SQLException {
        String skuId = ctx.pathParam("skuId");

        InventoryQuantity body;
        try {
            body = ctx.bodyAsClass(InventoryQuantity.class);
        } catch (Exception e) {
            ctx.status(400).result("Invalid request body");
            return;
        }
        if (body == null || body.quantity() < 1) {
            ctx.status(400).result("quantity must be at least 1");
            return;
        }

        InventoryItem item = repository.addStock(skuId, body.quantity());
        ctx.json(item);
    }

    public void purchase(Context ctx) throws SQLException {
        String skuId = ctx.pathParam("skuId");

        InventoryQuantity body;
        try {
            body = ctx.bodyAsClass(InventoryQuantity.class);
        } catch (Exception e) {
            ctx.status(400).result("Invalid request body");
            return;
        }
        if (body == null || body.quantity() < 1) {
            ctx.status(400).result("quantity must be at least 1");
            return;
        }

        PurchaseResult result = repository.purchase(skuId, body.quantity());
        switch (result) {
            case PurchaseResult.Success(InventoryItem item) -> ctx.json(item);
            case PurchaseResult.NotFound() -> ctx.status(404).result("SKU not found");
            case PurchaseResult.Insufficient() -> ctx.status(400).result("Insufficient inventory");
        }
    }
}
