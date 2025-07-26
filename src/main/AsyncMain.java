package main;

// AsyncMain.java - Main class for Story 9
import java.util.concurrent.*;
import java.util.*;

public class AsyncMain {
    public static void main(String[] args) throws Exception {
        AsyncOrderProcessor processor = new AsyncOrderProcessor();

        // Create sample orders
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            List<String> items = Arrays.asList(
                    i % 2 == 0 ? "Laptop" : "iPhone",
                    i % 3 == 0 ? "Mouse" : "Keyboard"
            );
            orders.add(new Order(5000 + i, 500 + i, items, i % 3 == 0, 999.99));
        }

        // Process orders asynchronously and collect futures
        List<CompletableFuture<OrderResult>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (Order order : orders) {
            CompletableFuture<OrderResult> future = processor.processOrderAsync(order);
            futures.add(future);

            // Add callback for each order
            future.thenAccept(result -> {
                System.out.println("✅ Completed: " + result);
            });
        }

        // Wait for all orders to complete
        CompletableFuture<Void> allOrders = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        allOrders.get(); // Wait for completion

        long endTime = System.currentTimeMillis();

        // Print statistics
        System.out.println("\n=== Processing Statistics ===");
        System.out.println("Total orders: " + orders.size());
        System.out.println("Total time: " + (endTime - startTime) + " ms");
        System.out.println("Average time per order: " +
                (endTime - startTime) / orders.size() + " ms");

        long successCount = futures.stream()
                .map(CompletableFuture::join)
                .filter(OrderResult::isSuccess)
                .count();

        System.out.println("Success rate: " + (successCount * 100.0 / orders.size()) + "%");

        processor.shutdown();
    }
}