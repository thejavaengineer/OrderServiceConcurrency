package main.validator;

import main.Order;

// Concrete specifications for Order validation rules
class PositiveAmountSpecification implements Specification<Order> {
    @Override
    public boolean isSatisfiedBy(Order order) {
        return order.getTotalAmount() > 0;
    }
}