package main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class Main{
    public static void main(String[] args) throws InterruptedException {
        // Create blocking queue with capacity of 20
        BlockingQueue<Order> orderQueue = new ArrayBlockingQueue<>(20);

        //create order processor
        OrderProcessor processor = new OrderProcessor();

        // Create thread pool for consumers
        int consumerCount = 4;
        //create thread pool
        ExecutorService executorService = Executors.newFixedThreadPool(consumerCount);

        // Start consumers
        OrderConsumer[] consumers = new OrderConsumer[consumerCount];
        for (int i = 0; i < consumerCount; i++) {
            consumers[i] = new OrderConsumer(orderQueue, processor);
            executorService.submit(consumers[i]);
        }

        // Start producer
        ExecutorService producerExecutor = Executors.newSingleThreadExecutor();
        producerExecutor.submit(new OrderProducer(orderQueue, 50));

        // Let it run for a while
        Thread.sleep(10000);

        // Shutdown
        producerExecutor.shutdown();
        for (OrderConsumer consumer : consumers) {
            consumer.stop();
        }

        producerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("System shutdown complete. Orders remaining in queue: " + orderQueue.size());
    }
}