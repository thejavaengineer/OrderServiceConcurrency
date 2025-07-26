package main;

// LoadGenerator.java - Generates variable load to test CPU utilization
import java.util.*;
import java.util.concurrent.*;

public class LoadGenerator {
    private final Random random = new Random();
    private final String[] items = {"Laptop", "Mouse", "Keyboard", "Monitor", "iPhone", "AirPods"};

    public List<Order> generateBurst(int count, int startId) {
        List<Order> orders = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            List<String> orderItems = new ArrayList<>();
            int itemCount = 1 + random.nextInt(4);

            for (int j = 0; j < itemCount; j++) {
                orderItems.add(items[random.nextInt(items.length)]);
            }

            boolean isPremium = random.nextDouble() < 0.3;
            double amount = 100 + random.nextDouble() * 2000;

            orders.add(new Order(startId + i, 1000 + random.nextInt(500),
                    orderItems, isPremium, amount));
        }

        return orders;
    }
}