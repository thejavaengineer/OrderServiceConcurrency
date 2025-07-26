package main;

// PriorityOrderProducer.java
import java.util.concurrent.PriorityBlockingQueue;
import java.util.Random;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class PriorityOrderProducer implements Runnable {
    private final PriorityBlockingQueue<PrioritizedOrder> orderQueue;
    private final int numberOfOrders;
    private final Random random = new Random();

    private final String[] items = {"Laptop", "Mouse", "Keyboard", "Monitor", "iPhone", "AirPods"};

    public PriorityOrderProducer(PriorityBlockingQueue<PrioritizedOrder> orderQueue, int numberOfOrders) {
        this.orderQueue = orderQueue;
        this.numberOfOrders = numberOfOrders;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < numberOfOrders; i++) {
                Order order = generateRandomOrder(i);
                PrioritizedOrder prioritizedOrder = new PrioritizedOrder(order);

                System.out.println("Producer adding " +
                        (order.isPremiumUser() ? "PREMIUM" : "REGULAR") +
                        " order: " + order.getOrderId());

                orderQueue.put(prioritizedOrder);
                Thread.sleep(random.nextInt(50));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Order generateRandomOrder(int index) {
        long orderId = 3000 + index;
        long userId = 300 + random.nextInt(50);
        // 30% chance of premium user
        boolean isPremium = random.nextDouble() < 0.3;

        int itemCount = 1 + random.nextInt(3);
        List<String> orderItems = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            orderItems.add(items[random.nextInt(items.length)]);
        }

        double amount = 100 + random.nextDouble() * 1900;

        return new Order(orderId, userId, orderItems, isPremium, amount);
    }
}