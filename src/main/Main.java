package main;

import java.util.Arrays;

public class Main{
    public static void main(String[] args) {
        Order order1 = new Order(1001, 101, Arrays.asList("laptop", "mouse"), true, 1299.99);
        System.out.println("Sample order created");
        System.out.println(order1);
    }
}