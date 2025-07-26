package main;

// SignalingMain.java - Main class for Story 8
import java.util.concurrent.*;
import java.util.*;

public class SignalingMain {
    public static void main(String[] args) throws InterruptedException {
        OrderProcessorWithSignaling processor = new OrderProcessorWithSignaling();

        // Start restock manager
        RestockManager restockManager = new RestockManager(processor);
        Thread restockThread = new Thread(restockManager, "RestockManager");
        restockThread.start();

        // Create thread pool for order processing
        ExecutorService orderExecutor = Executors.newFixedThreadPool(5);

        // Generate orders that will cause stock shortage
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            // Many orders for Laptop and iPhone (low stock items)
            List<String> items = i % 2 == 0 ?
                    Arrays.asList("Laptop", "Mouse") :
                    Arrays.asList("iPhone", "AirPods");

            orders.add(new Order(4000 + i, 400 + i, items, i % 3 == 0, 999.99));
        }

        // Submit orders
        for (Order order : orders) {
            orderExecutor.submit(() -> processor.processOrder(order));
            Thread.sleep(200); // Stagger order submissions
        }

        // Monitor for a while
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3000);
            processor.printInventoryStatus();
        }

        // Shutdown
        orderExecutor.shutdown();
        restockManager.stop();
        orderExecutor.awaitTermination(10, TimeUnit.SECONDS);
        restockThread.interrupt();

        System.out.println("Signaling system shutdown complete");
    }
}