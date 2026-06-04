package com.pshakhlovich.coding.loadbalancer.model;

public record Server(String url, int weight) {

    public Server(String url) {
        this(url, 1);
    }
}
