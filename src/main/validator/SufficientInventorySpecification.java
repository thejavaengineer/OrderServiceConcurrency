package main.validator;

import main.Order;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Existing Specification interface and other specs remain the same

// New specification for sufficient inventory check (using functional grouping for correctness with potential duplicate items)
class SufficientInventorySpecification implements Specification<Order> {
    private final Map<String, Integer> inventory;

    public SufficientInventorySpecification(Map<String, Integer> inventory) {
        this.inventory = inventory;
    }

    @Override
    public boolean isSatisfiedBy(Order order) {
        Map<String, Long> itemCounts = order.getItems().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return itemCounts.entrySet().stream()
                .allMatch(entry -> inventory.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }
}
