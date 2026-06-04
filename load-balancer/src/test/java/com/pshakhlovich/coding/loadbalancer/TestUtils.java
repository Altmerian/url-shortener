package com.pshakhlovich.coding.loadbalancer;

import com.pshakhlovich.coding.loadbalancer.model.Server;

import java.util.List;
import java.util.stream.IntStream;


public final class TestUtils {

    private static final String BASE_URL = "baseUrl/";

    private TestUtils() {
    }

    public static List<Server> getTestInstances(int number) {
        return IntStream.range(0, number)
            .mapToObj(i -> getTestInstance(String.valueOf(i)))
            .toList();
    }

    public static Server getTestInstance(String urlSuffix) {
        return new Server(BASE_URL + urlSuffix);
    }
}
