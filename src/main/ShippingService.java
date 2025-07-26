package main;

// ShippingService.java
import java.util.Random;
import java.util.concurrent.*;

public class ShippingService {
    private final Random random = new Random();

    public CompletableFuture<String> prepareShippingAsync(Order order, ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("Preparing shipping for order " + order.getOrderId() +
                    " on thread " + Thread.currentThread().getName());

            try {
                // Simulate shipping preparation
                Thread.sleep(150);

                String trackingId = "TRACK-" + order.getOrderId() + "-" +
                        random.nextInt(10000);

                System.out.println("📦 Shipping prepared for order " + order.getOrderId() +
                        " with tracking: " + trackingId);

                return trackingId;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ERROR";
            }
        }, executor);
    }
}