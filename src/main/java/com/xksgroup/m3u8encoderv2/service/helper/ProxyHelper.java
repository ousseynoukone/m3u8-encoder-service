package com.xksgroup.m3u8encoderv2.service.helper;

import jakarta.servlet.http.HttpServletRequest;

public class ProxyHelper {
    public static String buildServerUrl(HttpServletRequest request, String protocol) {
        String host = request.getHeader("Host");

        if (host == null || host.isBlank()) {
            // No Host header = direct access to localhost
            host = "localhost:" + request.getServerPort();
        }

        return protocol + "://" + host;
    }
}
