package main;

// OrderProcessorWithSignaling.java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

public class OrderProcessorWithSignaling {
    private final ConcurrentHashMap<String, AtomicInteger> inventory;
    private final ReentrantLock inventoryLock;
    private final Condition inventoryRestocked;
    private final Map<String, Integer> lowStockThreshold;

    // Track waiting orders
    private final Map<String, Integer> waitingOrders;

    public OrderProcessorWithSignaling() {
        inventory = new ConcurrentHashMap<>();
        inventory.put("Laptop", new AtomicInteger(10)); // Low initial stock
        inventory.put("Mouse", new AtomicInteger(50));
        inventory.put("Keyboard", new AtomicInteger(20));
        inventory.put("Monitor", new AtomicInteger(15));
        inventory.put("iPhone", new AtomicInteger(5)); // Low stock
        inventory.put("AirPods", new AtomicInteger(30));

        inventoryLock = new ReentrantLock();
        inventoryRestocked = inventoryLock.newCondition();

        // Set low stock thresholds
        lowStockThreshold = new HashMap<>();
        lowStockThreshold.put("Laptop", 5);
        lowStockThreshold.put("iPhone", 3);
        lowStockThreshold.put("Keyboard", 10);
        lowStockThreshold.put("Monitor", 5);

        waitingOrders = new ConcurrentHashMap<>();
    }

    public void processOrder(Order order) {
        System.out.println("Thread " + Thread.currentThread().getName() +
                " processing order: " + order.getOrderId());

        if (!validateOrder(order)) {
            System.out.println("Order validation failed: " + order.getOrderId());
            return;
        }

        boolean inventoryUpdated = checkAndUpdateInventoryWithWait(order);
        if (!inventoryUpdated) {
            System.out.println("Order " + order.getOrderId() + " cancelled after timeout");
            return;
        }

        simulatePaymentProcessing();
        System.out.println("Successfully processed order: " + order.getOrderId());
    }

    private boolean checkAndUpdateInventoryWithWait(Order order) {
        inventoryLock.lock();
        try {
            Map<String, Integer> itemsNeeded = new HashMap<>();
            for (String item : order.getItems()) {
                itemsNeeded.merge(item, 1, Integer::sum);
            }

            // Try up to 3 times with waiting
            for (int attempt = 0; attempt < 3; attempt++) {
                boolean allAvailable = true;

                // Check availability
                for (Map.Entry<String, Integer> entry : itemsNeeded.entrySet()) {
                    String item = entry.getKey();
                    int needed = entry.getValue();
                    AtomicInteger stock = inventory.get(item);

                    if (stock == null || stock.get() < needed) {
                        allAvailable = false;
                        // Track waiting orders
                        waitingOrders.merge(item, needed, Integer::sum);
                        System.out.println("Order " + order.getOrderId() +
                                " waiting for " + item + " (need " + needed +
                                ", have " + (stock != null ? stock.get() : 0) + ")");
                        break;
                    }
                }

                if (allAvailable) {
                    // Update inventory
                    for (Map.Entry<String, Integer> entry : itemsNeeded.entrySet()) {
                        String item = entry.getKey();
                        int needed = entry.getValue();
                        inventory.get(item).addAndGet(-needed);

                        // Check if we need to trigger low stock alert
                        checkLowStock(item);
                    }
                    return true;
                }

                // Wait for restock signal
                try {
                    System.out.println("Order " + order.getOrderId() + " waiting for restock...");
                    if (!inventoryRestocked.await(5, TimeUnit.SECONDS)) {
                        System.out.println("Order " + order.getOrderId() + " timed out waiting");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            return false;
        } finally {
            inventoryLock.unlock();
        }
    }

    private void checkLowStock(String item) {
        Integer threshold = lowStockThreshold.get(item);
        if (threshold != null && inventory.get(item).get() <= threshold) {
            System.out.println("⚠️  LOW STOCK ALERT: " + item + " = " + inventory.get(item).get());
        }
    }

    public void restockItem(String item, int quantity) {
        inventoryLock.lock();
        try {
            AtomicInteger stock = inventory.get(item);
            if (stock != null) {
                int oldValue = stock.get();
                stock.addAndGet(quantity);
                System.out.println("📦 RESTOCKED: " + item + " from " + oldValue +
                        " to " + stock.get());

                // Signal all waiting threads
                inventoryRestocked.signalAll();

                // Clear waiting orders for this item
                waitingOrders.remove(item);
            }
        } finally {
            inventoryLock.unlock();
        }
    }

    private boolean validateOrder(Order order) {
        return order.getTotalAmount() > 0 && !order.getItems().isEmpty();
    }

    private void simulatePaymentProcessing() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void printInventoryStatus() {
        System.out.println("\n=== Inventory Status ===");
        inventory.forEach((item, quantity) ->
                System.out.println(item + ": " + quantity.get()));
        System.out.println("Waiting orders: " + waitingOrders);
        System.out.println("=====================\n");
    }
}