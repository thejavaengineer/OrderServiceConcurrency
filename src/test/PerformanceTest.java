package test;

import main.Order;
import main.OrderProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Performance comparison test
public class PerformanceTest {
    public static void main(String[] args) throws InterruptedException {
        OrderProcessor processor = new OrderProcessor();
        ExecutorService executorService = Executors.newFixedThreadPool(20);

        // Create 1000 orders
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            List<String> items = Arrays.asList(
                    i % 2 == 0 ? "Laptop" : "iPhone",
                    i % 3 == 0 ? "Mouse" : "Keyboard"
            );
            orders.add(new Order(1000 + i, 100 + i, items, i % 5 == 0, 999.99));
        }

        long startTime = System.currentTimeMillis();

        for (Order order : orders) {
            executorService.submit(() -> processor.processOrder(order));
        }

        executorService.shutdown();
        executorService.awaitTermination(60, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        System.out.println("\nProcessed 1000 orders in: " + (endTime - startTime) + " ms");
        System.out.println("With ConcurrentHashMap and AtomicInteger - no locks needed!");
    }
}