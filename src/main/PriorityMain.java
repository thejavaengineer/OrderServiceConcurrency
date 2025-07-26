package main;

// PriorityMain.java - Main class for Story 7
import java.util.concurrent.*;

public class PriorityMain {
    public static void main(String[] args) throws InterruptedException {
        // Create priority blocking queue
        PriorityBlockingQueue<PrioritizedOrder> orderQueue = new PriorityBlockingQueue<>();

        OrderProcessor processor = new OrderProcessor();

        // Create consumers
        int consumerCount = 3;
        ExecutorService consumerExecutor = Executors.newFixedThreadPool(consumerCount);
        PriorityOrderConsumer[] consumers = new PriorityOrderConsumer[consumerCount];

        for (int i = 0; i < consumerCount; i++) {
            consumers[i] = new PriorityOrderConsumer(orderQueue, processor);
            consumerExecutor.submit(consumers[i]);
        }

        // Create multiple producers to simulate rush
        ExecutorService producerExecutor = Executors.newFixedThreadPool(2);
        producerExecutor.submit(new PriorityOrderProducer(orderQueue, 30));
        producerExecutor.submit(new PriorityOrderProducer(orderQueue, 30));

        // Let it run
        Thread.sleep(15000);

        // Shutdown
        producerExecutor.shutdown();
        for (PriorityOrderConsumer consumer : consumers) {
            consumer.stop();
        }

        producerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        consumerExecutor.shutdown();
        consumerExecutor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Priority system shutdown. Remaining orders: " + orderQueue.size());
    }
}