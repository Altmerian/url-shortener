package com.pshakhlovich.coding.loadbalancer.impl;

import com.pshakhlovich.coding.loadbalancer.BalancingStrategy;
import com.pshakhlovich.coding.loadbalancer.model.Server;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class RandomBalancingStrategy implements BalancingStrategy {

    @Override
    public Optional<Server> selectInstance(List<Server> instances) {
        if (instances.isEmpty()) {
            return Optional.empty();
        }
        var index = new Random().nextInt(instances.size());
        return Optional.of(instances.get(index));
    }
}
