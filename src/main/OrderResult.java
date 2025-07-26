package main;

// OrderResult.java
public class OrderResult {
    private final long orderId;
    private final boolean success;
    private final String message;
    private final long processingTime;

    public OrderResult(long orderId, boolean success, String message) {
        this.orderId = orderId;
        this.success = success;
        this.message = message;
        this.processingTime = System.currentTimeMillis();
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public long getOrderId() { return orderId; }

    @Override
    public String toString() {
        return String.format("OrderResult{orderId=%d, success=%b, message='%s'}",
                orderId, success, message);
    }
}