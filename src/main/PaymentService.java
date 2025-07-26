package main;

// PaymentService.java
import java.util.Random;
import java.util.concurrent.*;

public class PaymentService {
    private final Random random = new Random();

    public CompletableFuture<Boolean> processPaymentAsync(Order order, ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("Processing payment for order " + order.getOrderId() +
                    " on thread " + Thread.currentThread().getName());

            try {
                // Simulate payment processing
                Thread.sleep(100 + random.nextInt(200));

                // 95% success rate
                boolean success = random.nextDouble() < 0.95;

                if (success) {
                    System.out.println("Payment successful for order " + order.getOrderId());
                } else {
                    System.out.println("Payment failed for order " + order.getOrderId());
                }

                return success;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }, executor);
    }
}