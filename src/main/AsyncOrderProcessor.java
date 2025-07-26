package main;

// AsyncOrderProcessor.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;

public class AsyncOrderProcessor {
    private final ConcurrentHashMap<String, AtomicInteger> inventory;
    private final ExecutorService asyncExecutor;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final ShippingService shippingService;

    public AsyncOrderProcessor() {
        inventory = new ConcurrentHashMap<>();
        inventory.put("Laptop", new AtomicInteger(100));
        inventory.put("Mouse", new AtomicInteger(200));
        inventory.put("Keyboard", new AtomicInteger(150));
        inventory.put("Monitor", new AtomicInteger(75));
        inventory.put("iPhone", new AtomicInteger(100));
        inventory.put("AirPods", new AtomicInteger(120));

        // Dedicated thread pool for async tasks
        asyncExecutor = Executors.newFixedThreadPool(10);

        paymentService = new PaymentService();
        notificationService = new NotificationService();
        shippingService = new ShippingService();
    }

    public CompletableFuture<OrderResult> processOrderAsync(Order order) {
        System.out.println("Starting async processing for order: " + order.getOrderId());

        // Step 1: Validate order asynchronously
        return CompletableFuture
                .supplyAsync(() -> validateOrder(order), asyncExecutor)
                .thenCompose(validationResult -> {
                    if (!validationResult.isSuccess()) {
                        return CompletableFuture.completedFuture(
                                new OrderResult(order.getOrderId(), false, "Validation failed"));
                    }

                    // Step 2: Check and update inventory
                    return CompletableFuture.supplyAsync(() ->
                            checkAndUpdateInventory(order), asyncExecutor);
                })
                .thenCompose(inventoryResult -> {
                    if (!inventoryResult.isSuccess()) {
                        return CompletableFuture.completedFuture(inventoryResult);
                    }

                    // Step 3: Process payment asynchronously
                    return paymentService.processPaymentAsync(order, asyncExecutor)
                            .thenApply(paymentResult -> {
                                if (!paymentResult) {
                                    // Rollback inventory
                                    rollbackInventory(order);
                                    return new OrderResult(order.getOrderId(), false, "Payment failed");
                                }
                                return new OrderResult(order.getOrderId(), true, "Payment successful");
                            });
                })
                .thenCompose(paymentResult -> {
                    if (!paymentResult.isSuccess()) {
                        return CompletableFuture.completedFuture(paymentResult);
                    }

                    // Step 4: Parallel execution of notification and shipping prep
                    CompletableFuture<Void> notificationFuture =
                            notificationService.sendNotificationAsync(order, asyncExecutor);

                    CompletableFuture<String> shippingFuture =
                            shippingService.prepareShippingAsync(order, asyncExecutor);

                    // Combine both futures
                    return CompletableFuture.allOf(notificationFuture, shippingFuture)
                            .thenApply(v -> {
                                String trackingId = shippingFuture.join();
                                return new OrderResult(order.getOrderId(), true,
                                        "Order completed. Tracking: " + trackingId);
                            });
                })
                .exceptionally(throwable -> {
                    System.err.println("Error processing order " + order.getOrderId() +
                            ": " + throwable.getMessage());
                    return new OrderResult(order.getOrderId(), false,
                            "Error: " + throwable.getMessage());
                });
    }

    private OrderResult validateOrder(Order order) {
        System.out.println("Validating order " + order.getOrderId() +
                " on thread " + Thread.currentThread().getName());

        if (order.getTotalAmount() <= 0 || order.getItems().isEmpty()) {
            return new OrderResult(order.getOrderId(), false, "Invalid order");
        }

        return new OrderResult(order.getOrderId(), true, "Validation passed");
    }

    private OrderResult checkAndUpdateInventory(Order order) {
        System.out.println("Checking inventory for order " + order.getOrderId() +
                " on thread " + Thread.currentThread().getName());

        Map<String, Integer> itemsNeeded = new HashMap<>();
        for (String item : order.getItems()) {
            itemsNeeded.merge(item, 1, Integer::sum);
        }

        // Check availability
        for (Map.Entry<String, Integer> entry : itemsNeeded.entrySet()) {
            String item = entry.getKey();
            int needed = entry.getValue();
            AtomicInteger stock = inventory.get(item);

            if (stock == null || stock.get() < needed) {
                return new OrderResult(order.getOrderId(), false,
                        "Insufficient inventory for " + item);
            }
        }

        // Update inventory
        for (Map.Entry<String, Integer> entry : itemsNeeded.entrySet()) {
            inventory.get(entry.getKey()).addAndGet(-entry.getValue());
        }

        return new OrderResult(order.getOrderId(), true, "Inventory updated");
    }

    private void rollbackInventory(Order order) {
        for (String item : order.getItems()) {
            AtomicInteger stock = inventory.get(item);
            if (stock != null) {
                stock.incrementAndGet();
            }
        }
    }

    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
        }
    }
}