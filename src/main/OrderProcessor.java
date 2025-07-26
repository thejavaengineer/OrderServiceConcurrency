package main;

import main.validator.OrderValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OrderProcessor {
    private final ConcurrentHashMap<String, AtomicInteger> inventory;
    OrderValidator validator = new OrderValidator();
    private final Object inventoryLock = new Object(); // Lock object



    public OrderProcessor(){
        inventory = new ConcurrentHashMap<>();
        inventory.put("Laptop", new AtomicInteger(50));
        inventory.put("Mouse", new AtomicInteger(200));
        inventory.put("Keyboard", new AtomicInteger(150));
        inventory.put("Monitor", new AtomicInteger(75));
        inventory.put("iPhone", new AtomicInteger(100));
        inventory.put("AirPods", new AtomicInteger(120));
    }

    public void processOrder(Order order){
        System.out.println("Processing order: " + order.getOrderId());

        // Step 1: Validate order
        if (!validator.validate(order)) {
            System.out.println("Order validation failed for: " + order.getOrderId());
            return;
        }

        boolean inventoryUpdated = checkAndUpdateInventory(order);
        if (!inventoryUpdated) {
            System.out.println("Insufficient inventory for order: " + order.getOrderId());
            return;
        }

        // Step 3: Process payment (simulated)
        simulatePaymentProcessing();

        // Step 4: Log success
        System.out.println("Successfully processed order: " + order.getOrderId());
        // Print inventory snapshot
        printInventorySnapshot();
        }

    private boolean checkAndUpdateInventory(Order order) {
        synchronized (inventoryLock) {
            // First check if all items are available
            Map<String, Integer> itemsToReserve = new HashMap<>();
            for (String item : order.getItems()) {
                itemsToReserve.merge(item, 1, Integer::sum);
            }

            // Try to reserve all items
            Map<String, Integer> reserved = new HashMap<>();

            for (Map.Entry<String, Integer> entry : itemsToReserve.entrySet()) {
                String item = entry.getKey();
                int needed = entry.getValue();

                AtomicInteger stock = inventory.get(item);
                if (stock == null) {
                    rollbackReservations(reserved);
                    return false;
                }

                // Try to reserve
                int currentStock = stock.get();
                if (currentStock < needed) {
                    rollbackReservations(reserved);
                    return false;
                }

                // Use compareAndSet for atomic update
                while (true) {
                    int current = stock.get();
                    if (current < needed) {
                        rollbackReservations(reserved);
                        return false;
                    }
                    if (stock.compareAndSet(current, current - needed)) {
                        reserved.put(item, needed);
                        break;
                    }
                    // Retry if another thread modified the value
                }
            }
            return true;
        }
    }
    private void rollbackReservations(Map<String, Integer> reserved) {
        for (Map.Entry<String, Integer> entry : reserved.entrySet()) {
            inventory.get(entry.getKey()).addAndGet(entry.getValue());
        }
    }

    private void simulatePaymentProcessing() {
        try {
            Thread.sleep(100); // Simulate payment processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void printInventorySnapshot() {
        StringBuilder sb = new StringBuilder("Inventory: {");
        inventory.forEach((item, quantity) ->
                sb.append(item).append("=").append(quantity.get()).append(", "));
        if (sb.length() > 12) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        System.out.println(sb.toString());
    }
}
