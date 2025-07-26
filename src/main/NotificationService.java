package main;

// NotificationService.java
import java.util.concurrent.*;

public class NotificationService {
    public CompletableFuture<Void> sendNotificationAsync(Order order, ExecutorService executor) {
        return CompletableFuture.runAsync(() -> {
            System.out.println("Sending notification for order " + order.getOrderId() +
                    " on thread " + Thread.currentThread().getName());

            try {
                // Simulate notification sending
                Thread.sleep(50);

                if (order.isPremiumUser()) {
                    System.out.println("📧 Premium notification sent for order " + order.getOrderId());
                } else {
                    System.out.println("📧 Standard notification sent for order " + order.getOrderId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, executor);
    }
}