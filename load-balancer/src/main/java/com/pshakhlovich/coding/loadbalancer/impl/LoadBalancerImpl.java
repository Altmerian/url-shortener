package com.pshakhlovich.coding.loadbalancer.impl;

import com.pshakhlovich.coding.loadbalancer.BalancingStrategy;
import com.pshakhlovich.coding.loadbalancer.LoadBalancer;
import com.pshakhlovich.coding.loadbalancer.exception.LoadBalancerException;
import com.pshakhlovich.coding.loadbalancer.model.Server;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class LoadBalancerImpl implements LoadBalancer {

    static final int MAX_INSTANCE_NUMBER = 10;

    private BalancingStrategy balancingStrategy;

    private final Map<String, Server> instances = new ConcurrentHashMap<>() {

    };

    public LoadBalancerImpl(BalancingStrategy balancingStrategy) {
        this.balancingStrategy = balancingStrategy;
    }

    public void setBalancingStrategy(BalancingStrategy balancingStrategy) {
        this.balancingStrategy = balancingStrategy;
    }

    @Override
    public synchronized void registerInstance(Server server) {
        if (instances.size() >= MAX_INSTANCE_NUMBER) {
            throw new LoadBalancerException("Max Instances number reached!");
        }

        instances.put(server.url(), server);
    }

    @Override
    public Optional<Server> getInstance() {
        return balancingStrategy.selectInstance(new ArrayList<>(instances.values()));
    }
}
