package main;

// OptimizedOrderProcessor.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

public class OptimizedOrderProcessor {
    private final ConcurrentHashMap<String, AtomicInteger> inventory;
    private final ThreadPoolExecutor mainExecutor;
    private final ForkJoinPool computeExecutor;
    private final ScheduledExecutorService monitorExecutor;

    // Monitoring metrics
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger activeThreads = new AtomicInteger(0);

    // Services
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final ShippingService shippingService;

    public OptimizedOrderProcessor() {
        // Initialize inventory
        inventory = new ConcurrentHashMap<>();
        inventory.put("Laptop", new AtomicInteger(1000));
        inventory.put("Mouse", new AtomicInteger(2000));
        inventory.put("Keyboard", new AtomicInteger(1500));
        inventory.put("Monitor", new AtomicInteger(750));
        inventory.put("iPhone", new AtomicInteger(1000));
        inventory.put("AirPods", new AtomicInteger(1200));

        // Optimize thread pool based on CPU cores
        int cpuCores = Runtime.getRuntime().availableProcessors();
        System.out.println("🖥️  Detected CPU cores: " + cpuCores);

        // Main executor: CPU-bound tasks (2x CPU cores for mixed workload)
        int corePoolSize = cpuCores * 2;
        int maxPoolSize = cpuCores * 4;
        mainExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "OrderProcessor-" + threadNumber.getAndIncrement());
                        t.setPriority(Thread.NORM_PRIORITY);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Allow core threads to timeout for dynamic scaling
        mainExecutor.allowCoreThreadTimeOut(true);

        // ForkJoinPool for compute-intensive parallel tasks
        computeExecutor = new ForkJoinPool(cpuCores);

        // Monitor executor for statistics
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Monitor-Thread");
            t.setDaemon(true);
            return t;
        });

        // Start monitoring
        startMonitoring();

        // Initialize services
        paymentService = new PaymentService();
        notificationService = new NotificationService();
        shippingService = new ShippingService();
    }

    private void startMonitoring() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            int active = mainExecutor.getActiveCount();
            int poolSize = mainExecutor.getPoolSize();
            long completed = mainExecutor.getCompletedTaskCount();
            int queued = mainExecutor.getQueue().size();

            double cpuUtilization = calculateCpuUtilization();

            System.out.printf("📊 [Monitor] Active: %d/%d | Completed: %d | Queued: %d | CPU: %.1f%%\n",
                    active, poolSize, completed, queued, cpuUtilization);
        }, 2, 2, TimeUnit.SECONDS);
    }

    private double calculateCpuUtilization() {
        int totalThreads = mainExecutor.getPoolSize() + computeExecutor.getPoolSize();
        int activeThreads = mainExecutor.getActiveCount() + computeExecutor.getActiveThreadCount();
        int cpuCores = Runtime.getRuntime().availableProcessors();

        // Estimate CPU utilization based on active threads and cores
        return Math.min(100.0, (activeThreads * 100.0) / cpuCores);
    }

    public CompletableFuture<OrderResult> processOrderAsync(Order order) {
        long startTime = System.nanoTime();
        activeThreads.incrementAndGet();

        return CompletableFuture
                .supplyAsync(() -> {
                    // CPU-intensive validation with parallel checks
                    return validateOrderParallel(order);
                }, computeExecutor)
                .thenCompose(validationResult -> {
                    if (!validationResult.isSuccess()) {
                        return CompletableFuture.completedFuture(validationResult);
                    }

                    // Inventory check on main executor
                    return CompletableFuture.supplyAsync(() ->
                            checkAndUpdateInventory(order), mainExecutor);
                })
                .thenCompose(inventoryResult -> {
                    if (!inventoryResult.isSuccess()) {
                        return CompletableFuture.completedFuture(inventoryResult);
                    }

                    // Parallel execution of independent tasks
                    return processParallelTasks(order);
                })
                .whenComplete((result, throwable) -> {
                    activeThreads.decrementAndGet();
                    long duration = System.nanoTime() - startTime;
                    totalProcessingTime.addAndGet(duration);
                    totalProcessed.incrementAndGet();

                    if (totalProcessed.get() % 100 == 0) {
                        printPerformanceStats();
                    }
                });
    }

    private OrderResult validateOrderParallel(Order order) {
        // Use ForkJoinPool for parallel validation of items
        try {
            boolean allValid = computeExecutor.submit(() ->
                    order.getItems().parallelStream().allMatch(item ->
                            validateItem(item)
                    )
            ).get();

            if (!allValid || order.getTotalAmount() <= 0) {
                return new OrderResult(order.getOrderId(), false, "Validation failed");
            }

            return new OrderResult(order.getOrderId(), true, "Validation passed");
        } catch (Exception e) {
            return new OrderResult(order.getOrderId(), false, "Validation error: " + e.getMessage());
        }
    }

    private boolean validateItem(String item) {
        // Simulate CPU-intensive validation
        simulateCpuWork(10);
        return inventory.containsKey(item);
    }

    private void simulateCpuWork(int milliseconds) {
        // CPU-intensive work simulation
        long start = System.nanoTime();
        long duration = milliseconds * 1_000_000L;
        while (System.nanoTime() - start < duration) {
            // Busy wait to simulate CPU work
            Math.sqrt(Math.random());
        }
    }

    private CompletableFuture<OrderResult> processParallelTasks(Order order) {
        // Execute payment, notification, and shipping in parallel
        CompletableFuture<Boolean> paymentFuture =
                paymentService.processPaymentAsync(order, mainExecutor);

        CompletableFuture<Void> notificationFuture =
                notificationService.sendNotificationAsync(order, mainExecutor);

        CompletableFuture<String> shippingFuture =
                shippingService.prepareShippingAsync(order, mainExecutor);

        // Additional parallel compute task
        CompletableFuture<Double> pricingFuture = CompletableFuture.supplyAsync(() ->
                calculateDynamicPricing(order), computeExecutor);

        return CompletableFuture.allOf(paymentFuture, notificationFuture, shippingFuture, pricingFuture)
                .thenApply(v -> {
                    boolean paymentSuccess = paymentFuture.join();
                    if (!paymentSuccess) {
                        rollbackInventory(order);
                        return new OrderResult(order.getOrderId(), false, "Payment failed");
                    }

                    String trackingId = shippingFuture.join();
                    double finalPrice = pricingFuture.join();

                    return new OrderResult(order.getOrderId(), true,
                            String.format("Order completed. Tracking: %s, Final price: $%.2f",
                                    trackingId, finalPrice));
                });
    }

    private double calculateDynamicPricing(Order order) {
        // CPU-intensive pricing calculation
        simulateCpuWork(20);

        double basePrice = order.getTotalAmount();
        double discount = order.isPremiumUser() ? 0.1 : 0.0;

        // Parallel stream for complex calculations
        double additionalDiscount = IntStream.range(0, 1000)
                .parallel()
                .mapToDouble(i -> Math.sin(i) * 0.00001)
                .sum();

        return basePrice * (1 - discount - additionalDiscount);
    }

    private OrderResult checkAndUpdateInventory(Order order) {
        Map<String, Integer> itemsNeeded = new HashMap<>();
        for (String item : order.getItems()) {
            itemsNeeded.merge(item, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : itemsNeeded.entrySet()) {
            String item = entry.getKey();
            int needed = entry.getValue();
            AtomicInteger stock = inventory.get(item);

            if (stock == null || stock.get() < needed) {
                return new OrderResult(order.getOrderId(), false,
                        "Insufficient inventory for " + item);
            }
        }

        for (Map.Entry<String, Integer> entry : itemsNeeded.entrySet()) {
            inventory.get(entry.getKey()).addAndGet(-entry.getValue());
        }

        return new OrderResult(order.getOrderId(), true, "Inventory updated");
    }

    private void rollbackInventory(Order order) {
        for (String item : order.getItems()) {
            AtomicInteger stock = inventory.get(item);
            if (stock != null) {
                stock.incrementAndGet();
            }
        }
    }

    private void printPerformanceStats() {
        long processed = totalProcessed.get();
        long totalNanos = totalProcessingTime.get();
        double avgMillis = (totalNanos / 1_000_000.0) / processed;

        System.out.printf("\n📈 Performance Stats: Processed=%d, Avg Time=%.2f ms, Throughput=%.0f orders/sec\n\n",
                processed, avgMillis, 1000.0 / avgMillis);
    }

    public void adjustThreadPool(int newSize) {
        System.out.println("🔧 Adjusting thread pool size to: " + newSize);
        mainExecutor.setCorePoolSize(newSize);
        mainExecutor.setMaximumPoolSize(newSize * 2);
    }

    public void shutdown() {
        System.out.println("Shutting down processors...");
        mainExecutor.shutdown();
        computeExecutor.shutdown();
        monitorExecutor.shutdown();

        try {
            if (!mainExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                mainExecutor.shutdownNow();
            }
            if (!computeExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                computeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            mainExecutor.shutdownNow();
            computeExecutor.shutdownNow();
        }
    }
}