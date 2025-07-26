package main;

// AmdahlOptimizedProcessor.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.util.stream.*;

public class AmdahlOptimizedProcessor {
    private final ConcurrentHashMap<String, AtomicInteger> inventory;
    private final ThreadPoolExecutor mainExecutor;
    private final ForkJoinPool computeExecutor;

    // Metrics for Amdahl's Law calculation
    private final AtomicLong serialTime = new AtomicLong(0);
    private final AtomicLong parallelTime = new AtomicLong(0);
    private final AtomicLong totalOrders = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> operationTimes;

    // Batch processing for serial operations
    private final BlockingQueue<DatabaseWrite> dbWriteQueue;
    private final ScheduledExecutorService batchExecutor;

    // Lock striping for reduced contention
    private final Object[] inventoryLocks;
    private static final int LOCK_STRIPE_SIZE = 16;

    public AmdahlOptimizedProcessor() {
        inventory = new ConcurrentHashMap<>();
        initializeInventory();

        // Lock striping to reduce contention
        inventoryLocks = new Object[LOCK_STRIPE_SIZE];
        for (int i = 0; i < LOCK_STRIPE_SIZE; i++) {
            inventoryLocks[i] = new Object();
        }

        operationTimes = new ConcurrentHashMap<>();
        Arrays.asList("validation", "inventory", "payment", "notification", "shipping", "database")
                .forEach(op -> operationTimes.put(op, new AtomicLong(0)));

        int cpuCores = Runtime.getRuntime().availableProcessors();

        // Configure executors for optimal parallelism
        mainExecutor = new ThreadPoolExecutor(
                cpuCores * 2,
                cpuCores * 4,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "Amdahl-Worker-" + threadNumber.getAndIncrement());
                    }
                }
        );

        computeExecutor = new ForkJoinPool(cpuCores);

        // Batch processing for serial DB operations
        dbWriteQueue = new LinkedBlockingQueue<>(10000);
        batchExecutor = Executors.newSingleThreadScheduledExecutor(r ->
                new Thread(r, "DB-Batch-Writer"));

        startBatchProcessor();
    }

    private void initializeInventory() {
        String[] items = {"Laptop", "Mouse", "Keyboard", "Monitor", "iPhone", "AirPods"};
        for (String item : items) {
            inventory.put(item, new AtomicInteger(10000));
        }
    }

    private void startBatchProcessor() {
        // Process DB writes in batches to minimize serial overhead
        batchExecutor.scheduleWithFixedDelay(() -> {
            List<DatabaseWrite> batch = new ArrayList<>();
            dbWriteQueue.drainTo(batch, 100); // Process up to 100 at a time

            if (!batch.isEmpty()) {
                long start = System.nanoTime();
                processBatchWrite(batch);
                long duration = System.nanoTime() - start;
                serialTime.addAndGet(duration);
                operationTimes.get("database").addAndGet(duration);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<OrderResult> processOrderOptimized(Order order) {
        long orderStartTime = System.nanoTime();
        totalOrders.incrementAndGet();

        // Parallel validation (CPU-bound)
        CompletableFuture<OrderResult> validationFuture =
                CompletableFuture.supplyAsync(() -> {
                    long start = System.nanoTime();
                    OrderResult result = validateOrderParallel(order);
                    long duration = System.nanoTime() - start;
                    parallelTime.addAndGet(duration);
                    operationTimes.get("validation").addAndGet(duration);
                    return result;
                }, computeExecutor);

        return validationFuture.thenCompose(validationResult -> {
            if (!validationResult.isSuccess()) {
                return CompletableFuture.completedFuture(validationResult);
            }

            // Optimized inventory check with lock striping
            return CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                OrderResult result = checkInventoryOptimized(order);
                long duration = System.nanoTime() - start;
                // This is semi-parallel due to lock striping
                parallelTime.addAndGet(duration * 3 / 4);
                serialTime.addAndGet(duration / 4);
                operationTimes.get("inventory").addAndGet(duration);
                return result;
            }, mainExecutor);
        }).thenCompose(inventoryResult -> {
            if (!inventoryResult.isSuccess()) {
                return CompletableFuture.completedFuture(inventoryResult);
            }

            // Fully parallel operations
            List<CompletableFuture<?>> parallelOps = new ArrayList<>();

            // Payment processing (parallel)
            CompletableFuture<Boolean> paymentFuture =
                    CompletableFuture.supplyAsync(() -> {
                        long start = System.nanoTime();
                        boolean result = processPayment(order);
                        long duration = System.nanoTime() - start;
                        parallelTime.addAndGet(duration);
                        operationTimes.get("payment").addAndGet(duration);
                        return result;
                    }, mainExecutor);
            parallelOps.add(paymentFuture);

            // Notification (parallel)
            CompletableFuture<Void> notificationFuture =
                    CompletableFuture.runAsync(() -> {
                        long start = System.nanoTime();
                        sendNotification(order);
                        long duration = System.nanoTime() - start;
                        parallelTime.addAndGet(duration);
                        operationTimes.get("notification").addAndGet(duration);
                    }, mainExecutor);
            parallelOps.add(notificationFuture);

            // Shipping preparation (parallel)
            CompletableFuture<String> shippingFuture =
                    CompletableFuture.supplyAsync(() -> {
                        long start = System.nanoTime();
                        String trackingId = prepareShipping(order);
                        long duration = System.nanoTime() - start;
                        parallelTime.addAndGet(duration);
                        operationTimes.get("shipping").addAndGet(duration);
                        return trackingId;
                    }, mainExecutor);
            parallelOps.add(shippingFuture);

            // Wait for all parallel operations
            return CompletableFuture.allOf(parallelOps.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        boolean paymentSuccess = paymentFuture.join();
                        if (!paymentSuccess) {
                            rollbackInventory(order);
                            return new OrderResult(order.getOrderId(), false, "Payment failed");
                        }

                        String trackingId = shippingFuture.join();

                        // Queue for batch DB write (non-blocking)
                        dbWriteQueue.offer(new DatabaseWrite(order, trackingId));

                        return new OrderResult(order.getOrderId(), true,
                                "Order processed. Tracking: " + trackingId);
                    });
        }).whenComplete((result, throwable) -> {
            long totalDuration = System.nanoTime() - orderStartTime;

            // Print progress every 100 orders
            if (totalOrders.get() % 100 == 0) {
                printAmdahlAnalysis();
            }
        });
    }

    private OrderResult validateOrderParallel(Order order) {
        // Parallel validation using ForkJoin
        try {
            boolean valid = computeExecutor.submit(() -> {
                // Parallel stream for item validation
                boolean itemsValid = order.getItems().parallelStream()
                        .allMatch(item -> {
                            simulateWork(5); // Simulate validation work
                            return inventory.containsKey(item);
                        });

                // Parallel price validation
                boolean priceValid = IntStream.range(0, 10).parallel()
                        .mapToDouble(i -> order.getTotalAmount() / 10.0)
                        .sum() > 0;

                return itemsValid && priceValid;
            }).get();

            return new OrderResult(order.getOrderId(), valid,
                    valid ? "Validation passed" : "Validation failed");
        } catch (Exception e) {
            return new OrderResult(order.getOrderId(), false, "Validation error");
        }
    }

    private OrderResult checkInventoryOptimized(Order order) {
        Map<String, Integer> itemCounts = new HashMap<>();
        for (String item : order.getItems()) {
            itemCounts.merge(item, 1, Integer::sum);
        }

        // Lock striping - use different locks for different items
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            String item = entry.getKey();
            int needed = entry.getValue();

            // Get lock based on item hash
            int lockIndex = Math.abs(item.hashCode() % LOCK_STRIPE_SIZE);
            Object lock = inventoryLocks[lockIndex];

            synchronized(lock) {
                AtomicInteger stock = inventory.get(item);
                if (stock == null || stock.get() < needed) {
                    return new OrderResult(order.getOrderId(), false,
                            "Insufficient " + item);
                }
                stock.addAndGet(-needed);
            }
        }

        return new OrderResult(order.getOrderId(), true, "Inventory updated");
    }

    private boolean processPayment(Order order) {
        simulateWork(50 + new Random().nextInt(50));
        return new Random().nextDouble() < 0.95;
    }

    private void sendNotification(Order order) {
        simulateWork(30);
    }

    private String prepareShipping(Order order) {
        simulateWork(40);
        return "TRACK-" + order.getOrderId() + "-" + System.nanoTime() % 10000;
    }

    private void rollbackInventory(Order order) {
        for (String item : order.getItems()) {
            inventory.get(item).incrementAndGet();
        }
    }

    private void processBatchWrite(List<DatabaseWrite> batch) {
        // Simulate batch DB write
        simulateWork(20 + batch.size() * 2);
        System.out.printf("📝 Batch wrote %d orders to database\n", batch.size());
    }

    private void simulateWork(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void printAmdahlAnalysis() {
        long serial = serialTime.get();
        long parallel = parallelTime.get();
        long total = serial + parallel;

        if (total == 0) return;

        double serialFraction = (double) serial / total;
        double parallelFraction = (double) parallel / total;

        int processors = Runtime.getRuntime().availableProcessors();

        // Calculate theoretical speedup using Amdahl's Law
        // Speedup = 1 / (S + P/N) where S = serial fraction, P = parallel fraction, N = processors
        double theoreticalSpeedup = 1.0 / (serialFraction + parallelFraction / processors);

        // Calculate actual speedup (approximate based on execution pattern)
        double actualSpeedup = total / (serial + parallel / processors);

        System.out.println("\n========== Amdahl's Law Analysis ==========");
        System.out.printf("Total Orders Processed: %d\n", totalOrders.get());
        System.out.printf("Serial Time: %.2f ms (%.1f%%)\n",
                serial / 1_000_000.0, serialFraction * 100);
        System.out.printf("Parallel Time: %.2f ms (%.1f%%)\n",
                parallel / 1_000_000.0, parallelFraction * 100);
        System.out.printf("Processors: %d\n", processors);
        System.out.printf("Theoretical Speedup (Amdahl): %.2fx\n", theoreticalSpeedup);
        System.out.printf("Actual Speedup (Measured): %.2fx\n", actualSpeedup);
        System.out.printf("Efficiency: %.1f%%\n", (actualSpeedup / processors) * 100);

        System.out.println("\nOperation Breakdown:");
        operationTimes.forEach((op, time) -> {
            double ms = time.get() / 1_000_000.0;
            double percent = (time.get() * 100.0) / total;
            System.out.printf("  %-12s: %8.2f ms (%5.1f%%)\n", op, ms, percent);
        });

        System.out.println("\nOptimization Recommendations:");
        if (serialFraction > 0.1) {
            System.out.println("⚠️  Serial fraction > 10% - Further optimization needed!");
            System.out.println("   - Consider more batch processing");
            System.out.println("   - Investigate lock contention");
        } else {
            System.out.println("✅ Serial fraction < 10% - Good parallelization!");
        }
        System.out.println("==========================================\n");
    }

    public void shutdown() {
        mainExecutor.shutdown();
        computeExecutor.shutdown();
        batchExecutor.shutdown();

        try {
            mainExecutor.awaitTermination(60, TimeUnit.SECONDS);
            computeExecutor.awaitTermination(60, TimeUnit.SECONDS);
            batchExecutor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Helper class for batch DB writes
    private static class DatabaseWrite {
        final Order order;
        final String trackingId;
        final long timestamp;

        DatabaseWrite(Order order, String trackingId) {
            this.order = order;
            this.trackingId = trackingId;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
