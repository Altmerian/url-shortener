package com.pshakhlovich.coding.loadbalancer;


import com.pshakhlovich.coding.loadbalancer.model.Server;

import java.util.Optional;

public interface LoadBalancer {

    void registerInstance(Server server);

    Optional<Server> getInstance();
}
