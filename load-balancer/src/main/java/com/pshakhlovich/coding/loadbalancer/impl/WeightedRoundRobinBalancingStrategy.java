package com.pshakhlovich.coding.loadbalancer.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.pshakhlovich.coding.loadbalancer.model.Server;

public class WeightedRoundRobinBalancingStrategy extends RoundRobinBalancingStrategy {

    @Override
    public Optional<Server> selectInstance(List<Server> instances) {
        if (instances.isEmpty()) {
            return Optional.empty();
        }

        var servers = instances.stream()
                .flatMap(server -> IntStream.range(0, server.weight())
                        .mapToObj(i -> server))
                .toList();

        return super.selectInstance(servers);
    }
}
