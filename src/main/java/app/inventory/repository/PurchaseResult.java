package app.inventory.repository;

import app.inventory.model.InventoryItem;

public sealed interface PurchaseResult {

    record Success(InventoryItem item) implements PurchaseResult {
    }

    record NotFound() implements PurchaseResult {
    }

    record Insufficient() implements PurchaseResult {
    }
}
