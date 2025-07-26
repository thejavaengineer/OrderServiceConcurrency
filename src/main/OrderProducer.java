package main;

// OrderProducer.java
import java.util.concurrent.BlockingQueue;
import java.util.Random;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class OrderProducer implements Runnable {
    private final BlockingQueue<Order> orderQueue;
    private final int numberOfOrders;
    private final Random random = new Random();

    private final String[] items = {"Laptop", "Mouse", "Keyboard", "Monitor", "iPhone", "AirPods"};

    public OrderProducer(BlockingQueue<Order> orderQueue, int numberOfOrders) {
        this.orderQueue = orderQueue;
        this.numberOfOrders = numberOfOrders;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < numberOfOrders; i++) {
                Order order = generateRandomOrder(i);
                System.out.println("Producer adding order: " + order.getOrderId());
                orderQueue.put(order); // Blocks if queue is full
                Thread.sleep(random.nextInt(100)); // Simulate order arrival rate
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Producer interrupted");
        }
    }

    private Order generateRandomOrder(int index) {
        long orderId = 2000 + index;
        long userId = 200 + random.nextInt(50);
        boolean isPremium = random.nextBoolean();

        // Random 1-3 items
        int itemCount = 1 + random.nextInt(3);
        List<String> orderItems = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            orderItems.add(items[random.nextInt(items.length)]);
        }

        double amount = 100 + random.nextDouble() * 1900;

        return new Order(orderId, userId, orderItems, isPremium, amount);
    }
}