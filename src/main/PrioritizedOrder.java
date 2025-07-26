package main;

// PrioritizedOrder.java
public class PrioritizedOrder implements Comparable<PrioritizedOrder> {
    private final Order order;
    private final long timestamp;

    public PrioritizedOrder(Order order) {
        this.order = order;
        this.timestamp = System.nanoTime();
    }

    public Order getOrder() {
        return order;
    }

    @Override
    public int compareTo(PrioritizedOrder other) {
        // Premium orders have higher priority (come first)
        if (this.order.isPremiumUser() && !other.order.isPremiumUser()) {
            return -1;
        } else if (!this.order.isPremiumUser() && other.order.isPremiumUser()) {
            return 1;
        }

        // If both are same type, use FIFO based on timestamp
        return Long.compare(this.timestamp, other.timestamp);
    }
}
