package com.hss.scalecart.service;

import com.hss.scalecart.entity.Inventory;
import com.hss.scalecart.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 100;

    private final InventoryRepository inventoryRepository;

    @Transactional
    public void reserveStock(UUID productId, int quantity) {
        executeWithRetry(() -> doReserveStock(productId, quantity), "reserveStock", productId);
    }

    @Transactional
    public void releaseStock(UUID productId, int quantity) {
        executeWithRetry(() -> doReleaseStock(productId, quantity), "releaseStock", productId);
    }

    @Transactional
    public void confirmStock(UUID productId, int quantity) {
        executeWithRetry(() -> doConfirmStock(productId, quantity), "confirmStock", productId);
    }

    private void doReserveStock(UUID productId, int quantity) {
        Inventory inventory = getInventory(productId);
        if (inventory.getAvailableQuantity() < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock for product " + productId +
                            ". Available: " + inventory.getAvailableQuantity() +
                            ", Requested: " + quantity
            );
        }
        inventory.setReserved(inventory.getReserved() + quantity);
        inventoryRepository.save(inventory);
    }

    private void doReleaseStock(UUID productId, int quantity) {
        Inventory inventory = getInventory(productId);
        int newReserved = Math.max(0, inventory.getReserved() - quantity);
        inventory.setReserved(newReserved);
        inventoryRepository.save(inventory);
    }

    private void doConfirmStock(UUID productId, int quantity) {
        Inventory inventory = getInventory(productId);
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setReserved(Math.max(0, inventory.getReserved() - quantity));
        inventoryRepository.save(inventory);
    }

    private Inventory getInventory(UUID productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found for product: " + productId));
    }

    private void executeWithRetry(Runnable operation, String operationName, UUID productId) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                operation.run();
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                attempt++;
                log.warn("[{}] Optimistic lock conflict for product {} — attempt {}/{}",
                        operationName, productId, attempt, MAX_RETRIES);
                if (attempt >= MAX_RETRIES) {
                    throw new IllegalStateException(
                            "Failed to update inventory for product " + productId +
                                    " after " + MAX_RETRIES + " attempts due to concurrent modification."
                    );
                }
                try {
                    Thread.sleep(RETRY_BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Inventory operation interrupted", ie);
                }
            }
        }
    }
}