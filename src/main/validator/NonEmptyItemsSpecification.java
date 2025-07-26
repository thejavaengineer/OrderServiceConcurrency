package main.validator;

import main.Order;

class NonEmptyItemsSpecification implements Specification<Order> {
    @Override
    public boolean isSatisfiedBy(Order order) {
        return !order.getItems().isEmpty();
    }
}