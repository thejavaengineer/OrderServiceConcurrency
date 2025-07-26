package main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main{
    public static void main(String[] args) {
        OrderProcessor processor = new OrderProcessor();
        List<Order> orders = new ArrayList<>();

        orders.add(new Order(1001, 101, Arrays.asList("Laptop", "Mouse"), true, 1299.99));
        orders.add(new Order(1002, 102, Arrays.asList("Keyboard", "Monitor"), false, 450.00));
        orders.add(new Order(1003, 103, Arrays.asList("iPhone", "AirPods"), true, 1199.00));
        orders.add(new Order(1004, 104, Arrays.asList("Laptop", "Keyboard"), false, 1199.00));
        orders.add(new Order(1005, 105, Arrays.asList("Mouse", "Monitor"), true, 350.00));

        long startTime = System.currentTimeMillis();
        for(Order order : orders){
            processor.processOrder(order);
            System.out.println("---");
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Total processing time: "+ (endTime - startTime));
    }
}