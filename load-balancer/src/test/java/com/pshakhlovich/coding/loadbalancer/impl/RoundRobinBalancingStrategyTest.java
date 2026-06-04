package com.pshakhlovich.coding.loadbalancer.impl;

import com.pshakhlovich.coding.loadbalancer.BalancingStrategy;
import com.pshakhlovich.coding.loadbalancer.model.Server;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.pshakhlovich.coding.loadbalancer.TestUtils.getTestInstances;
import static com.pshakhlovich.coding.loadbalancer.impl.LoadBalancerImpl.MAX_INSTANCE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
class RoundRobinBalancingStrategyTest {

    private final List<Server> instances = getTestInstances(MAX_INSTANCE_NUMBER);

    private final BalancingStrategy balancingStrategy = new RoundRobinBalancingStrategy();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void selectInstance_shouldReturnNextInstanceRoundRobin(int invocationNumber) {
        // given
        var expectedServerUrlPrefix = Integer.toString(invocationNumber);

        // when
        var server = balancingStrategy.selectInstance(instances);

        // then
        assertThat(server.isPresent()).isTrue();
        assertThat(server.get().url()).endsWith(expectedServerUrlPrefix);
    }
}