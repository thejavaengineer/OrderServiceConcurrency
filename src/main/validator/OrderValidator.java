package main.validator;

import main.Order;

import java.util.Map;

// Separate validator class using the specification pattern
public class OrderValidator {
    private final Specification<Order> validationSpec;

    public OrderValidator() {
        // Compose specifications functionally (can add more rules here)
        this.validationSpec = new PositiveAmountSpecification()
                .and(new NonEmptyItemsSpecification());
        // Example: .and(new AnotherSpecification()) if needed
    }

    public OrderValidator(Map<String, Integer> inventory) {
        // Compose all specifications functionally (including the new inventory check)
        this.validationSpec = new PositiveAmountSpecification()
                .and(new NonEmptyItemsSpecification())
                .and(new SufficientInventorySpecification(inventory));
        // Example: .and(new AnotherSpecification()) if more needed
    }

    public boolean validate(Order order) {
        return validationSpec.isSatisfiedBy(order);
    }
}
