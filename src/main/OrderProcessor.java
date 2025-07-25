package main;

import main.validator.OrderValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OrderProcessor {
    private final Map<String, Integer> inventory;
    OrderValidator validator = new OrderValidator();
    private final Object inventoryLock = new Object(); // Lock object



    public OrderProcessor(){
        inventory = new HashMap<>();
        inventory.put("Laptop", 50);
        inventory.put("Mouse", 200);
        inventory.put("Keyboard", 150);
        inventory.put("Monitor", 75);
        inventory.put("iPhone", 100);
        inventory.put("AirPods", 120);
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
        synchronized (inventoryLock) {
            System.out.println("Inventory snapshot: " + inventory);
        }    }

    private boolean checkAndUpdateInventory(Order order) {
        synchronized (inventoryLock) {
            // First check if all items are available
            OrderValidator validator1 = new OrderValidator(inventory);
            if (!validator1.validate(order)) {
                System.out.println("Order validation failed for: " + order.getOrderId());
                return false;
            }

            // Update inventory
            Map<String, Long> itemCounts = order.getItems().stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            itemCounts.forEach((item, count) -> {
                int newQuantity = inventory.get(item) - count.intValue();
                inventory.put(item, newQuantity);
            });
            return true;
        }
    }

    private void simulatePaymentProcessing() {
        try {
            Thread.sleep(100); // Simulate payment processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
