package main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main{
    public static void main(String[] args) throws InterruptedException {
        //create order processor
        OrderProcessor processor = new OrderProcessor();

        //create thread pool
        ExecutorService executorService = Executors.newFixedThreadPool(4);

        //create orders
        List<Order> orders = createSampleOrders();

        //process order concurrently
        long startTime = System.currentTimeMillis();
        for(Order order : orders){
            executorService.submit(() -> {
                System.out.println("Thread "+ Thread.currentThread().getName() + " processing order " + order.getOrderId());
                processor.processOrder(order);

            });
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        System.out.println("Total processing time: "+ (endTime - startTime));
    }
    private static List<Order> createSampleOrders() {
        List<Order> orders = new ArrayList<>();
        // Create 20 orders with overlapping items to demonstrate race conditions
        for (int i = 1; i <= 15; i++) {
            boolean isPremium = i % 3 == 0;
            orders.add(new Order(1000 + i, 100 + i,
                    Arrays.asList("Laptop", "Mouse"), isPremium, 1299.99));
        }
        return orders;
    }
}