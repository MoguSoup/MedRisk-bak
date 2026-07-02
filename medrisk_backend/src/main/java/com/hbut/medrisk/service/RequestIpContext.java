package com.hbut.medrisk.service;

public final class RequestIpContext {
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();

    private RequestIpContext() {
    }

    public static void set(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            CLIENT_IP.remove();
            return;
        }
        CLIENT_IP.set(clientIp);
    }

    public static String get() {
        return CLIENT_IP.get();
    }

    public static void clear() {
        CLIENT_IP.remove();
    }
}
