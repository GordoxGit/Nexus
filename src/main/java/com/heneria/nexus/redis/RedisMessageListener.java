package com.heneria.nexus.redis;

@FunctionalInterface
public interface RedisMessageListener {

    void onMessage(String channel, String message);
}
