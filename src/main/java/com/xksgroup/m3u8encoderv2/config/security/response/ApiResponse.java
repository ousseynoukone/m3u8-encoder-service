package com.xksgroup.m3u8encoderv2.config.security.response;

/**
 * Représente la structure standard pour toutes les réponses HTTP réussies.
 */
public record ApiResponse(
        boolean success,
        int status,
        String message,
        Object data,
        String timestamp,
        String path
) {}

