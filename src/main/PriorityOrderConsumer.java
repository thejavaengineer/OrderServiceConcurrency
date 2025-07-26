package main;
// PriorityOrderConsumer.java
import java.util.concurrent.PriorityBlockingQueue;

public class PriorityOrderConsumer implements Runnable {
    private final PriorityBlockingQueue<PrioritizedOrder> orderQueue;
    private final OrderProcessor processor;
    private volatile boolean running = true;

    public PriorityOrderConsumer(PriorityBlockingQueue<PrioritizedOrder> orderQueue, OrderProcessor processor) {
        this.orderQueue = orderQueue;
        this.processor = processor;
    }

    @Override
    public void run() {
        // Set thread priority based on consumer type
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);

        try {
            while (running) {
                PrioritizedOrder prioritizedOrder = orderQueue.take();
                Order order = prioritizedOrder.getOrder();

                // Adjust thread priority dynamically based on order type
                if (order.isPremiumUser()) {
                    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                } else {
                    Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
                }

                System.out.println("Consumer " + Thread.currentThread().getName() +
                        " processing " + (order.isPremiumUser() ? "PREMIUM" : "REGULAR") +
                        " order: " + order.getOrderId());

                processor.processOrder(order);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        running = false;
    }
}