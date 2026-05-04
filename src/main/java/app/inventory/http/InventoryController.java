package app.inventory.http;

import app.inventory.model.InventoryItem;
import app.inventory.model.InventoryQuantity;
import app.inventory.repository.InventoryRepository;
import app.inventory.repository.PurchaseResult;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;

public final class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryRepository repository;

    public InventoryController(InventoryRepository repository) {
        this.repository = repository;
    }

    public void getInventory(Context ctx) throws SQLException {
        String skuId = ctx.pathParam("skuId");
        log.info("GET /inventory/{} - fetching inventory", skuId);
        Optional<InventoryItem> item = repository.findBySkuId(skuId);
        if (item.isEmpty()) {
            log.warn("GET /inventory/{} - 404 sku not found", skuId);
            ctx.status(404).result("SKU not found");
            return;
        }
        log.info("GET /inventory/{} - 200 quantity={}", skuId, item.get().quantity());
        ctx.json(item.get());
    }

    public void addStock(Context ctx) throws SQLException {
        String skuId = ctx.pathParam("skuId");
        log.info("POST /inventory/{} - adding stock", skuId);

        InventoryQuantity body;
        try {
            body = ctx.bodyAsClass(InventoryQuantity.class);
        } catch (Exception e) {
            log.warn("POST /inventory/{} - 400 invalid request body: {}", skuId, e.getMessage());
            ctx.status(400).result("Invalid request body");
            return;
        }
        if (body == null || body.quantity() < 1) {
            log.warn("POST /inventory/{} - 400 invalid quantity={}", skuId, body == null ? null : body.quantity());
            ctx.status(400).result("quantity must be at least 1");
            return;
        }

        InventoryItem item = repository.addStock(skuId, body.quantity());
        log.info("POST /inventory/{} - 200 added={} newQuantity={}", skuId, body.quantity(), item.quantity());
        ctx.json(item);
    }

    public void purchase(Context ctx) throws SQLException {
        String skuId = ctx.pathParam("skuId");
        log.info("POST /inventory/{}/purchase - purchase request", skuId);

        InventoryQuantity body;
        try {
            body = ctx.bodyAsClass(InventoryQuantity.class);
        } catch (Exception e) {
            log.warn("POST /inventory/{}/purchase - 400 invalid request body: {}", skuId, e.getMessage());
            ctx.status(400).result("Invalid request body");
            return;
        }
        if (body == null || body.quantity() < 1) {
            log.warn("POST /inventory/{}/purchase - 400 invalid quantity={}", skuId, body == null ? null : body.quantity());
            ctx.status(400).result("quantity must be at least 1");
            return;
        }

        PurchaseResult result = repository.purchase(skuId, body.quantity());
        switch (result) {
            case PurchaseResult.Success(InventoryItem item) -> {
                log.info("POST /inventory/{}/purchase - 200 purchased={} remaining={}",
                        skuId, body.quantity(), item.quantity());
                ctx.json(item);
            }
            case PurchaseResult.NotFound() -> {
                log.warn("POST /inventory/{}/purchase - 404 sku not found", skuId);
                ctx.status(404).result("SKU not found");
            }
            case PurchaseResult.Insufficient() -> {
                log.warn("POST /inventory/{}/purchase - 400 insufficient inventory for requested={}",
                        skuId, body.quantity());
                ctx.status(400).result("Insufficient inventory");
            }
        }
    }
}
