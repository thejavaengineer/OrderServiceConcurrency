package test;

import main.Order;
import main.OrderProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadSafetyTest {
    public static void main(String[] args) throws InterruptedException {
        OrderProcessor processor = new OrderProcessor();
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        // Create 100 orders for the same item to test race conditions
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            orders.add(new Order(1000 + i, 100 + i,
                    Arrays.asList("Laptop"), false, 1299.99));
        }

        for (Order order : orders) {
            executorService.submit(() -> processor.processOrder(order));
        }

        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\nTest completed. Check if exactly 50 orders were processed successfully.");
    }
}