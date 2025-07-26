package main;

// OrderConsumer.java
import java.util.concurrent.BlockingQueue;

public class OrderConsumer implements Runnable {
    private final BlockingQueue<Order> orderQueue;
    private final OrderProcessor processor;
    private volatile boolean running = true;

    public OrderConsumer(BlockingQueue<Order> orderQueue, OrderProcessor processor) {
        this.orderQueue = orderQueue;
        this.processor = processor;
    }

    @Override
    public void run() {
        try {
            while (running) {
                Order order = orderQueue.take(); // Blocks if queue is empty
                System.out.println("Consumer " + Thread.currentThread().getName() +
                        " took order: " + order.getOrderId());
                processor.processOrder(order);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Consumer interrupted");
        }
    }

    public void stop() {
        running = false;
    }
}