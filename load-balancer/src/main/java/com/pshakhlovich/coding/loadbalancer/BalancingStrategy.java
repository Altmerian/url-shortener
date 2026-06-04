package com.pshakhlovich.coding.loadbalancer;

import com.pshakhlovich.coding.loadbalancer.model.Server;

import java.util.List;
import java.util.Optional;

public interface BalancingStrategy {

    Optional<Server> selectInstance(List<Server> instances);
}
