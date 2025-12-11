package com.xksgroup.m3u8encoderv2.config.security.response;

/**
 * Représente la structure standard pour toutes les réponses d'erreur HTTP.
 */
public record ApiError(
        boolean success,
        int status,
        String error,
        String message,
        Object details,
        String timestamp,
        String path
) {}

