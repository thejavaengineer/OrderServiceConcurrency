package main;

// ShutdownDemo.java - Demonstrates graceful shutdown
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ShutdownDemo {
    public static void main(String[] args) throws Exception {
        GracefulShutdownProcessor processor = new GracefulShutdownProcessor();

        System.out.println("🚀 Starting Graceful Shutdown Demo\n");

        // Start order generation threads
        ExecutorService orderGenerators = Executors.newFixedThreadPool(3);
        AtomicBoolean keepGenerating = new AtomicBoolean(true);
        AtomicInteger orderId = new AtomicInteger(7000);

        // Generator 1: Regular orders
        orderGenerators.submit(() -> {
            Random random = new Random();
            while (keepGenerating.get()) {
                try {
                    Order order = createRandomOrder(orderId.incrementAndGet(), random);
                    processor.processOrder(order);
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // Generator 2: Burst orders
        orderGenerators.submit(() -> {
            Random random = new Random();
            while (keepGenerating.get()) {
                try {
                    // Generate burst of 10 orders
                    for (int i = 0; i < 10; i++) {
                        Order order = createRandomOrder(orderId.incrementAndGet(), random);
                        processor.processOrder(order);
                    }
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // Simulate different shutdown scenarios
        Scanner scanner = new Scanner(System.in);
        System.out.println("\nCommands:");
        System.out.println("  1 - Normal shutdown");
        System.out.println("  2 - Emergency shutdown (Ctrl+C simulation)");
        System.out.println("  3 - Continue running");
        System.out.println("  q - Quit\n");

        while (true) {
            System.out.print("Enter command: ");
            String command = scanner.nextLine().trim();

            switch (command) {
                case "1":
                    System.out.println("\n📋 Initiating normal shutdown...");
                    keepGenerating.set(false);
                    orderGenerators.shutdown();

                    // Wait a bit for pending orders
                    Thread.sleep(1000);

                    // Initiate graceful shutdown
                    processor.initiateGracefulShutdown();
                    processor.awaitShutdown();
                    return;

                case "2":
                    System.out.println("\n🚨 Simulating emergency shutdown (Ctrl+C)...");
                    keepGenerating.set(false);

                    // Simulate abrupt termination by calling shutdown hook
                    Runtime.getRuntime().exit(0);
                    break;

                case "3":
                    System.out.println("Continuing operation...");
                    break;

                case "q":
                    keepGenerating.set(false);
                    orderGenerators.shutdownNow();
                    return;

                default:
                    System.out.println("Unknown command");
            }
        }
    }

    private static Order createRandomOrder(int orderId, Random random) {
        String[] items = {"Laptop", "Mouse", "Keyboard", "Monitor", "iPhone", "AirPods"};
        List<String> orderItems = new ArrayList<>();

        int itemCount = 1 + random.nextInt(3);
        for (int i = 0; i < itemCount; i++) {
            orderItems.add(items[random.nextInt(items.length)]);
        }

        return new Order(orderId, 700 + random.nextInt(100),
                orderItems, random.nextBoolean(), 500 + random.nextDouble() * 1000);
    }
}
