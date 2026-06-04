package com.pshakhlovich.coding.loadbalancer.impl;

import com.pshakhlovich.coding.loadbalancer.LoadBalancer;
import com.pshakhlovich.coding.loadbalancer.TestUtils;
import com.pshakhlovich.coding.loadbalancer.exception.LoadBalancerException;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static com.pshakhlovich.coding.loadbalancer.TestUtils.getTestInstance;
import static com.pshakhlovich.coding.loadbalancer.impl.LoadBalancerImpl.MAX_INSTANCE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.BDDAssertions.thenNoException;

class LoadBalancerImplTest {

    private final LoadBalancer loadBalancer = new LoadBalancerImpl(new RandomBalancingStrategy());

    @Test
    void registerInstance_shouldRegisterServer_whenNotMaxNumber() {
        // given
        var server = getTestInstance("1");

        // when then
        thenNoException().isThrownBy((() -> loadBalancer.registerInstance(server)));
    }

    @Test
    void registerInstance_shouldNotRegisterServer_whenMaxNumber() {
        // given
        IntStream.range(0, MAX_INSTANCE_NUMBER)
            .mapToObj(Integer::toString)
            .map(TestUtils::getTestInstance)
            .forEach(loadBalancer::registerInstance);

        // when then
       assertThatThrownBy((() -> loadBalancer.registerInstance(getTestInstance("11"))))
           .isInstanceOf(LoadBalancerException.class);
    }

    @Test
    void getInstance_shouldReturnInstance_whenItRegistered() {
        // given
        var testInstance = getTestInstance("0");
        loadBalancer.registerInstance(testInstance);

        // when
        var instanceOptional = loadBalancer.getInstance();

        // then
        assertThat(instanceOptional.isPresent()).isTrue();
        assertThat(instanceOptional.get()).isEqualTo(testInstance);
    }
}