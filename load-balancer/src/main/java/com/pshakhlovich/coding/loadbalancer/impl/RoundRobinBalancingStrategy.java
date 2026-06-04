package com.pshakhlovich.coding.loadbalancer.impl;

import com.pshakhlovich.coding.loadbalancer.BalancingStrategy;
import com.pshakhlovich.coding.loadbalancer.model.Server;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinBalancingStrategy implements BalancingStrategy {

    private final AtomicInteger pointer = new AtomicInteger();

    @Override
    public Optional<Server> selectInstance(List<Server> instances) {
        if (instances.isEmpty()) {
            return Optional.empty();
        }

        int index = pointer.getAndIncrement() % instances.size();
        return Optional.of(instances.get(index));
    }
}
