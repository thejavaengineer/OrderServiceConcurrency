package main;

// AmdahlBenchmark.java - Benchmarking tool
import java.util.*;
import java.util.concurrent.*;

public class AmdahlBenchmark {
    public static void main(String[] args) throws Exception {
        AmdahlOptimizedProcessor processor = new AmdahlOptimizedProcessor();

        System.out.println("🔬 Amdahl's Law Optimization Benchmark\n");

        // Test different load patterns
        runBenchmark(processor, "Light Load", 100, 1);
        runBenchmark(processor, "Medium Load", 500, 2);
        runBenchmark(processor, "Heavy Load", 1000, 4);
        runBenchmark(processor, "Extreme Load", 2000, 8);

        processor.shutdown();
    }

    private static void runBenchmark(AmdahlOptimizedProcessor processor,
                                     String testName, int orderCount, int concurrentProducers)
            throws Exception {
        System.out.printf("\n🧪 Running %s: %d orders, %d concurrent producers\n",
                testName, orderCount, concurrentProducers);

        List<Order> orders = generateOrders(orderCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(concurrentProducers);

        ExecutorService producerExecutor = Executors.newFixedThreadPool(concurrentProducers);
        List<CompletableFuture<OrderResult>> allFutures = new CopyOnWriteArrayList<>();

        // Split orders among producers
        int ordersPerProducer = orderCount / concurrentProducers;

        long startTime = System.nanoTime();

        for (int i = 0; i < concurrentProducers; i++) {
            final int producerId = i;
            final int startIdx = i * ordersPerProducer;
            final int endIdx = (i == concurrentProducers - 1) ? orderCount : (i + 1) * ordersPerProducer;

            producerExecutor.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = startIdx; j < endIdx; j++) {
                        allFutures.add(processor.processOrderOptimized(orders.get(j)));
                    }

                    completeLatch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Start all producers simultaneously
        startLatch.countDown();

        // Wait for all orders to be submitted
        completeLatch.await();

        // Wait for all orders to complete
        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).get();

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.printf("✅ Completed in %d ms (%.2f orders/sec)\n",
                durationMs, (orderCount * 1000.0) / durationMs);

        // Print Amdahl analysis
        processor.printAmdahlAnalysis();

        producerExecutor.shutdown();
    }

    private static List<Order> generateOrders(int count) {
        List<Order> orders = new ArrayList<>();
        Random random = new Random();
        String[] items = {"Laptop", "Mouse", "Keyboard", "Monitor", "iPhone", "AirPods"};

        for (int i = 0; i < count; i++) {
            List<String> orderItems = new ArrayList<>();
            int itemCount = 1 + random.nextInt(3);

            for (int j = 0; j < itemCount; j++) {
                orderItems.add(items[random.nextInt(items.length)]);
            }

            orders.add(new Order(6000 + i, 600 + random.nextInt(100),
                    orderItems, random.nextBoolean(), 500 + random.nextDouble() * 1000));
        }

        return orders;
    }
}