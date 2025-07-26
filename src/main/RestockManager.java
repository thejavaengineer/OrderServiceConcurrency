package main;

// RestockManager.java - Simulates restocking
import java.util.Random;

public class RestockManager implements Runnable {
    private final OrderProcessorWithSignaling processor;
    private final Random random = new Random();
    private volatile boolean running = true;

    private final String[] itemsToRestock = {"Laptop", "iPhone", "Keyboard", "Monitor"};

    public RestockManager(OrderProcessorWithSignaling processor) {
        this.processor = processor;
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Wait before restocking
                Thread.sleep(3000 + random.nextInt(2000));

                // Choose random item to restock
                String item = itemsToRestock[random.nextInt(itemsToRestock.length)];
                int quantity = 10 + random.nextInt(20);

                processor.restockItem(item, quantity);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running = false;
    }
}