package test;

// ShutdownTest.java - Unit tests for shutdown scenarios
import main.GracefulShutdownProcessor;
import main.Order;
import main.OrderResult;

import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShutdownTest {
    public static void main(String[] args) throws Exception {
        System.out.println("🧪 Testing Graceful Shutdown Scenarios\n");

        testNormalShutdown();
        testShutdownWithInFlightRequests();
        testShutdownUnderLoad();

        System.out.println("\n✅ All shutdown tests completed");
    }

    private static void testNormalShutdown() throws Exception {
        System.out.println("Test 1: Normal Shutdown");
        System.out.println("------------------------");

        GracefulShutdownProcessor processor = new GracefulShutdownProcessor();

        // Process some orders
        List<CompletableFuture<OrderResult>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Order order = new Order(8000 + i, 800, Arrays.asList("Laptop"), true, 999.99);
            futures.add(processor.processOrder(order));
        }

        // Wait for completion
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        // Shutdown
        processor.initiateGracefulShutdown();
        processor.awaitShutdown();

        System.out.println("✅ Normal shutdown completed\n");
    }

    private static void testShutdownWithInFlightRequests() throws Exception {
        System.out.println("Test 2: Shutdown with In-Flight Requests");
        System.out.println("----------------------------------------");

        GracefulShutdownProcessor processor = new GracefulShutdownProcessor();

        // Start many orders but don't wait
        List<CompletableFuture<OrderResult>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Order order = new Order(9000 + i, 900, Arrays.asList("iPhone", "AirPods"), false, 1299.99);
            futures.add(processor.processOrder(order));
        }

        // Immediately start shutdown
        Thread.sleep(100); // Let some orders start processing
        processor.initiateGracefulShutdown();
        processor.awaitShutdown();

        // Check how many completed
        long completed = futures.stream().filter(CompletableFuture::isDone).count();
        System.out.println("Completed: " + completed + " out of 50");
        System.out.println("✅ Shutdown with in-flight requests completed\n");
    }

    private static void testShutdownUnderLoad() throws Exception {
        System.out.println("Test 3: Shutdown Under Heavy Load");
        System.out.println("---------------------------------");

        GracefulShutdownProcessor processor = new GracefulShutdownProcessor();
        AtomicBoolean keepRunning = new AtomicBoolean(true);

        // Create load generator thread
        Thread loadGenerator = new Thread(() -> {
            int orderId = 10000;
            Random random = new Random();

            while (keepRunning.get()) {
                try {
                    Order order = new Order(orderId++, 1000,
                            Arrays.asList("Monitor", "Keyboard"), true, 599.99);
                    processor.processOrder(order);
                    Thread.sleep(10); // High frequency
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        loadGenerator.start();

        // Let it run under load
        Thread.sleep(2000);

        // Stop load and shutdown
        keepRunning.set(false);
        loadGenerator.interrupt();

        processor.initiateGracefulShutdown();
        processor.awaitShutdown();

        System.out.println("✅ Shutdown under load completed\n");
    }
}