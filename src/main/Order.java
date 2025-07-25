package main;

import lombok.*;

import java.util.List;

@RequiredArgsConstructor
@Data
public class Order{
    private final long orderId;
    private final long userId;
    private final List<String> items;
    private final boolean isPremiumUser;
    private final double totalAmount;
}