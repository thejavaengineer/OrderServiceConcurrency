package main;

// OptimizedMain.java - Main class for Story 10
import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class OptimizedMain {
    public static void main(String[] args) throws Exception {
        OptimizedOrderProcessor processor = new OptimizedOrderProcessor();
        LoadGenerator loadGenerator = new LoadGenerator();

        System.out.println("🚀 Starting optimized order processing system...\n");

        // Warm up the system
        System.out.println("🔥 Warming up...");
        List<Order> warmupOrders = loadGenerator.generateBurst(100, 1000);
        processOrders(processor, warmupOrders);
        Thread.sleep(2000);

        // Test 1: Normal load
        System.out.println("\n📊 Test 1: Normal Load (200 orders)");
        List<Order> normalLoad = loadGenerator.generateBurst(200, 2000);
        long startTime = System.currentTimeMillis();
        processOrders(processor, normalLoad);
        long normalLoadTime = System.currentTimeMillis() - startTime;

        Thread.sleep(3000);

        // Test 2: High load burst
        System.out.println("\n📊 Test 2: High Load Burst (500 orders)");
        List<Order> highLoad = loadGenerator.generateBurst(500, 3000);
        startTime = System.currentTimeMillis();
        processOrders(processor, highLoad);
        long highLoadTime = System.currentTimeMillis() - startTime;

        Thread.sleep(3000);

        // Test 3: Sustained load with dynamic adjustment
        System.out.println("\n📊 Test 3: Sustained Load with Dynamic Scaling");
        int cpuCores = Runtime.getRuntime().availableProcessors();

        // Start with normal pool size
        processor.adjustThreadPool(cpuCores * 2);

        // Generate continuous load
        ScheduledExecutorService loadExecutor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger orderId = new AtomicInteger(4000);

        loadExecutor.scheduleAtFixedRate(() -> {
            List<Order> burst = loadGenerator.generateBurst(50, orderId.getAndAdd(50));
            processOrdersAsync(processor, burst);
        }, 0, 1, TimeUnit.SECONDS);

        // Simulate varying load by adjusting thread pool
        Thread.sleep(5000);
        System.out.println("\n⚡ Increasing thread pool for peak load...");
        processor.adjustThreadPool(cpuCores * 3);

        Thread.sleep(5000);
        System.out.println("\n📉 Reducing thread pool for normal load...");
        processor.adjustThreadPool(cpuCores * 2);

        Thread.sleep(5000);

        // Shutdown
        loadExecutor.shutdown();
        Thread.sleep(2000);

        // Print final statistics
        System.out.println("\n=== Final Performance Report ===");
        System.out.printf("Normal Load Time: %d ms for 200 orders (%.1f ms/order)\n",
                normalLoadTime, normalLoadTime / 200.0);
        System.out.printf("High Load Time: %d ms for 500 orders (%.1f ms/order)\n",
                highLoadTime, highLoadTime / 500.0);
        System.out.println("CPU Cores: " + cpuCores);
        System.out.println("Speedup achieved through parallelization!");

        processor.shutdown();
    }

    private static void processOrders(OptimizedOrderProcessor processor, List<Order> orders)
            throws Exception {
        List<CompletableFuture<OrderResult>> futures = new ArrayList<>();

        for (Order order : orders) {
            futures.add(processor.processOrderAsync(order));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
    }

    private static void processOrdersAsync(OptimizedOrderProcessor processor, List<Order> orders) {
        for (Order order : orders) {
            processor.processOrderAsync(order);
        }
    }
}