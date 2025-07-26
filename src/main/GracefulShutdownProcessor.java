package main;

// GracefulShutdownProcessor.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

public class GracefulShutdownProcessor {
    private final ConcurrentHashMap<String, AtomicInteger> inventory;
    private final ThreadPoolExecutor mainExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService criticalExecutor;

    // Shutdown coordination
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final Set<CompletableFuture<?>> inFlightRequests = ConcurrentHashMap.newKeySet();

    // Persistent state management
    private final BlockingQueue<PersistentState> stateQueue;
    private final Thread persistenceThread;

    // Metrics for shutdown reporting
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger rejectedDuringShutdown = new AtomicInteger(0);
    private final AtomicInteger completedDuringShutdown = new AtomicInteger(0);

    // Services
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    // Configuration
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final String STATE_FILE = "order_processor_state.dat";

    public GracefulShutdownProcessor() {
        inventory = new ConcurrentHashMap<>();
        initializeInventory();

        // Configure executors with custom rejection handlers
        int cpuCores = Runtime.getRuntime().availableProcessors();

        mainExecutor = new ThreadPoolExecutor(
                cpuCores,
                cpuCores * 2,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "Order-Worker-" + threadNumber.getAndIncrement());
                        t.setUncaughtExceptionHandler((thread, ex) -> {
                            System.err.println("Uncaught exception in " + thread.getName() + ": " + ex);
                            ex.printStackTrace();
                        });
                        return t;
                    }
                },
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        if (!shutdownInitiated.get()) {
                            // Normal rejection - queue full
                            System.err.println("Task rejected - queue full. Consider increasing capacity.");
                        } else {
                            // Rejection during shutdown
                            rejectedDuringShutdown.incrementAndGet();
                            System.out.println("Task rejected during shutdown.");
                        }
                    }
                }
        );

        // Critical tasks executor - for important cleanup operations
        criticalExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Critical-Task");
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });

        scheduledExecutor = Executors.newScheduledThreadPool(2);

        // State persistence
        stateQueue = new LinkedBlockingQueue<>();
        persistenceThread = new Thread(this::runPersistence, "State-Persistence");
        persistenceThread.start();

        // Services
        paymentService = new PaymentService();
        notificationService = new NotificationService();

        // Register shutdown hook
        registerShutdownHook();

        // Start monitoring
        startMonitoring();

        // Load previous state if exists
        loadState();

        System.out.println("✅ GracefulShutdownProcessor initialized");
    }

    private void initializeInventory() {
        inventory.put("Laptop", new AtomicInteger(1000));
        inventory.put("Mouse", new AtomicInteger(2000));
        inventory.put("Keyboard", new AtomicInteger(1500));
        inventory.put("Monitor", new AtomicInteger(750));
        inventory.put("iPhone", new AtomicInteger(1000));
        inventory.put("AirPods", new AtomicInteger(1200));
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutdown hook triggered");
            try {
                initiateGracefulShutdown();
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e);
                e.printStackTrace();
            }
        }, "Shutdown-Hook"));
    }

    private void startMonitoring() {
        // Monitor in-flight requests
        scheduledExecutor.scheduleAtFixedRate(() -> {
            if (!shutdownInitiated.get()) {
                System.out.printf("📊 Active requests: %d, Total processed: %d\n",
                        inFlightRequests.size(), totalProcessed.get());
            }
        }, 5, 5, TimeUnit.SECONDS);

        // Periodic state save
        scheduledExecutor.scheduleAtFixedRate(this::saveState, 30, 30, TimeUnit.SECONDS);
    }

    public CompletableFuture<OrderResult> processOrder(Order order) {
        // Check if shutdown is in progress
        if (shutdownInitiated.get()) {
            rejectedDuringShutdown.incrementAndGet();
            return CompletableFuture.completedFuture(
                    new OrderResult(order.getOrderId(), false, "System shutting down"));
        }

        // Track this request
        CompletableFuture<OrderResult> future = new CompletableFuture<>();
        inFlightRequests.add(future);

        // Process order
        CompletableFuture<OrderResult> processingFuture = CompletableFuture
                .supplyAsync(() -> {
                    System.out.println("Processing order: " + order.getOrderId());
                    return validateOrder(order);
                }, mainExecutor)
                .thenCompose(validationResult -> {
                    if (!validationResult.isSuccess() || shutdownInitiated.get()) {
                        return CompletableFuture.completedFuture(validationResult);
                    }

                    return CompletableFuture.supplyAsync(() ->
                            updateInventory(order), mainExecutor);
                })
                .thenCompose(inventoryResult -> {
                    if (!inventoryResult.isSuccess() || shutdownInitiated.get()) {
                        return CompletableFuture.completedFuture(inventoryResult);
                    }

                    // Process payment
                    return paymentService.processPaymentAsync(order, mainExecutor)
                            .thenApply(paymentSuccess -> {
                                if (!paymentSuccess) {
                                    rollbackInventory(order);
                                    return new OrderResult(order.getOrderId(), false, "Payment failed");
                                }

                                // Queue state for persistence
                                stateQueue.offer(new PersistentState(order, "COMPLETED"));

                                return new OrderResult(order.getOrderId(), true, "Order completed");
                            });
                })
                .whenComplete((result, throwable) -> {
                    // Remove from tracking
                    inFlightRequests.remove(future);
                    totalProcessed.incrementAndGet();

                    if (shutdownInitiated.get()) {
                        completedDuringShutdown.incrementAndGet();
                    }

                    // Complete the tracking future
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(result);
                    }
                });

        return future;
    }

    public void initiateGracefulShutdown() throws InterruptedException {
        if (shutdownInitiated.compareAndSet(false, true)) {
            System.out.println("\n🔄 Initiating graceful shutdown...");
            long startTime = System.currentTimeMillis();

            // Step 1: Stop accepting new requests
            System.out.println("1️⃣ Stopping new request acceptance");

            // Step 2: Cancel scheduled tasks
            System.out.println("2️⃣ Cancelling scheduled tasks");
            scheduledExecutor.shutdown();

            // Step 3: Wait for in-flight requests
            System.out.println("3️⃣ Waiting for " + inFlightRequests.size() + " in-flight requests...");

            // Create a future that completes when all in-flight requests are done
            CompletableFuture<Void> allInFlight = CompletableFuture.allOf(
                    inFlightRequests.toArray(new CompletableFuture[0])
            );

            try {
                allInFlight.get(SHUTDOWN_TIMEOUT_SECONDS / 2, TimeUnit.SECONDS);
                System.out.println("✅ All in-flight requests completed");
            } catch (TimeoutException e) {
                System.out.println("⚠️  Timeout waiting for in-flight requests. " +
                        inFlightRequests.size() + " still pending.");

                // Cancel remaining requests
                inFlightRequests.forEach(f -> f.cancel(true));
            } catch (ExecutionException e) {
                System.err.println("Error during in-flight request completion: " + e);
            }

            // Step 4: Shutdown executors
            System.out.println("4️⃣ Shutting down executors");
            mainExecutor.shutdown();

            if (!mainExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println("⚠️  Force shutting down main executor");
                List<Runnable> pending = mainExecutor.shutdownNow();
                System.out.println("   Cancelled " + pending.size() + " pending tasks");
            }

            // Step 5: Save final state
            System.out.println("5️⃣ Saving final state");
            saveState();

            // Step 6: Shutdown persistence
            System.out.println("6️⃣ Finalizing persistence");
            stateQueue.offer(new PersistentState(null, "SHUTDOWN")); // Poison pill
            persistenceThread.join(5000);

            // Step 7: Cleanup critical resources
            System.out.println("7️⃣ Cleaning up critical resources");
            cleanupResources();

            // Step 8: Final report
            long shutdownTime = System.currentTimeMillis() - startTime;
            printShutdownReport(shutdownTime);

            // Signal shutdown complete
            shutdownLatch.countDown();

            // Shutdown critical executor last
            criticalExecutor.shutdown();
            criticalExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void runPersistence() {
        try {
            while (true) {
                PersistentState state = stateQueue.take();

                // Check for shutdown signal
                if (state.order == null && "SHUTDOWN".equals(state.status)) {
                    System.out.println("Persistence thread shutting down");
                    break;
                }

                // Simulate writing to persistent storage
                Thread.sleep(10);

                // In real implementation, would write to database or file
                System.out.println("💾 Persisted order: " + state.order.getOrderId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Persistence thread interrupted");
        }
    }

    private void saveState() {
        try {
            criticalExecutor.submit(() -> {
                try (ObjectOutputStream oos = new ObjectOutputStream(
                        Files.newOutputStream(Paths.get(STATE_FILE)))) {

                    // Save inventory state
                    Map<String, Integer> inventorySnapshot = new HashMap<>();
                    inventory.forEach((k, v) -> inventorySnapshot.put(k, v.get()));

                    oos.writeObject(inventorySnapshot);
                    oos.writeInt(totalProcessed.get());
                    oos.writeLong(System.currentTimeMillis());

                    System.out.println("💾 State saved successfully");
                } catch (IOException e) {
                    System.err.println("Failed to save state: " + e);
                }
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Error during state save: " + e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadState() {
        Path stateFile = Paths.get(STATE_FILE);
        if (Files.exists(stateFile)) {
            try (ObjectInputStream ois = new ObjectInputStream(
                    Files.newInputStream(stateFile))) {

                Map<String, Integer> savedInventory = (Map<String, Integer>) ois.readObject();
                savedInventory.forEach((k, v) -> {
                    AtomicInteger current = inventory.get(k);
                    if (current != null) {
                        current.set(v);
                    }
                });

                int savedProcessed = ois.readInt();
                long savedTime = ois.readLong();

                totalProcessed.set(savedProcessed);

                System.out.printf("📂 Loaded previous state: %d orders, saved at %s\n",
                        savedProcessed, new Date(savedTime));
            } catch (Exception e) {
                System.err.println("Failed to load state: " + e);
            }
        }
    }

    private void cleanupResources() {
        try {
            // Close database connections, file handles, etc.
            System.out.println("🧹 Cleaning up resources...");

            // Example: Close any open connections
            Thread.sleep(100); // Simulate cleanup

            System.out.println("✅ Resources cleaned up");
        } catch (Exception e) {
            System.err.println("Error during resource cleanup: " + e);
        }
    }

    private void printShutdownReport(long shutdownTime) {
        System.out.println("\n========== SHUTDOWN REPORT ==========");
        System.out.println("Total orders processed: " + totalProcessed.get());
        System.out.println("Orders completed during shutdown: " + completedDuringShutdown.get());
        System.out.println("Orders rejected during shutdown: " + rejectedDuringShutdown.get());
        System.out.println("Shutdown time: " + shutdownTime + " ms");
        System.out.println("Final inventory state:");
        inventory.forEach((item, count) ->
                System.out.println("  " + item + ": " + count.get()));
        System.out.println("====================================\n");
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    private OrderResult validateOrder(Order order) {
        simulateWork(20);
        return new OrderResult(order.getOrderId(), true, "Valid");
    }

    private OrderResult updateInventory(Order order) {
        for (String item : order.getItems()) {
            AtomicInteger stock = inventory.get(item);
            if (stock == null || stock.get() < 1) {
                return new OrderResult(order.getOrderId(), false, "No stock");
            }
            stock.decrementAndGet();
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

    private void simulateWork(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Helper class for state persistence
    private static class PersistentState {
        final Order order;
        final String status;
        final long timestamp;

        PersistentState(Order order, String status) {
            this.order = order;
            this.status = status;
            this.timestamp = System.currentTimeMillis();
        }
    }
}