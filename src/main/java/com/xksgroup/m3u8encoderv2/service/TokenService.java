package com.xksgroup.m3u8encoderv2.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
public class TokenService {

    private final SecretKey secretKey;
    private final int tokenExpirationMinutes;
    private final int bufferMinutes;

    public TokenService(@Value("${security.jwt.secret:my-secure-hls-secret-key-that-is-very-long-and-secure}") String secret,
                       @Value("${security.jwt.expiration-minutes:15}") int expirationMinutes,
                       @Value("${security.jwt.buffer-minutes:30}") int bufferMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.tokenExpirationMinutes = expirationMinutes;
        this.bufferMinutes = bufferMinutes;
        log.info("TokenService initialized with {}min token expiration, {}min buffer for dynamic tokens", 
                 expirationMinutes, bufferMinutes);
    }

    /**
     * Generates a time-limited token for accessing a specific resource
     */
    public String generateToken(String resourceKey, String clientId) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(tokenExpirationMinutes * 60L);
        
        String token = Jwts.builder()
                .subject(resourceKey)
                .claim("clientId", clientId)
                .claim("resourceKey", resourceKey)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
                
        log.debug("Generated token for resource: {} (expires: {})", resourceKey, expiration);
        return token;
    }

    /**
     * Validates a token and extracts the resource key
     */
    public TokenValidationResult validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String resourceKey = claims.get("resourceKey", String.class);
            String clientId = claims.get("clientId", String.class);
            
            log.debug("Token validated successfully for resource: {}", resourceKey);
            return new TokenValidationResult(true, resourceKey, clientId, null);
            
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return new TokenValidationResult(false, null, null, e.getMessage());
        }
    }

    /**
     * Generates a token specifically for HLS segment access
     */
    public String generateSegmentToken(String bucketKey, String userAgent) {
        // Use user agent as a simple client identifier
        String clientId = Integer.toString(Math.abs(userAgent.hashCode()));
        return generateToken(bucketKey, clientId);
    }

    /**
     * Generates a token with dynamic expiration based on video duration
     * Adds a buffer time to the video duration for generous viewing time
     */
    public String generateTokenWithDuration(String resourceKey, String clientId, Long videoDurationSeconds) {
        Instant now = Instant.now();
        
        // Calculate dynamic expiration: video duration + configurable buffer
        long bufferSeconds = bufferMinutes * 60L;
        long totalExpirationSeconds = videoDurationSeconds != null ? 
            videoDurationSeconds + bufferSeconds : 
            tokenExpirationMinutes * 60L; // Fallback to default
            
        Instant expiration = now.plusSeconds(totalExpirationSeconds);
        
        String token = Jwts.builder()
                .subject(resourceKey)
                .claim("clientId", clientId)
                .claim("resourceKey", resourceKey)
                .claim("videoDuration", videoDurationSeconds)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
                
        log.debug("Generated dynamic token for resource: {} (video: {}s, expires: {}, total: {}min)", 
                  resourceKey, videoDurationSeconds, expiration, totalExpirationSeconds / 60);
        return token;
    }

    /**
     * Generates a segment token with dynamic expiration based on video duration
     */
    public String generateSegmentTokenWithDuration(String bucketKey, String userAgent, Long videoDurationSeconds) {
        String clientId = Integer.toString(Math.abs(userAgent.hashCode()));
        return generateTokenWithDuration(bucketKey, clientId, videoDurationSeconds);
    }

    /**
     * Generates a token specifically for encryption key access
     */
    public String generateKeyToken(String keyResource, String userAgent, Long videoDurationSeconds) {
        String clientId = Integer.toString(Math.abs(userAgent.hashCode()));
        if (videoDurationSeconds != null) {
            return generateTokenWithDuration(keyResource, clientId, videoDurationSeconds);
        } else {
            return generateToken(keyResource, clientId);
        }
    }




    /**
     * Result of token validation
     */
    public static class TokenValidationResult {
        private final boolean valid;
        private final String resourceKey;
        private final String clientId;
        private final String error;
        private final String urlId;
        private final String clientIp;

        public TokenValidationResult(boolean valid, String resourceKey, String clientId, String error) {
            this(valid, resourceKey, clientId, error, null, null);
        }

        public TokenValidationResult(boolean valid, String resourceKey, String clientId, String error, String urlId, String clientIp) {
            this.valid = valid;
            this.resourceKey = resourceKey;
            this.clientId = clientId;
            this.error = error;
            this.urlId = urlId;
            this.clientIp = clientIp;
        }

        public boolean isValid() { return valid; }
        public String getResourceKey() { return resourceKey; }
        public String getClientId() { return clientId; }
        public String getError() { return error; }
        public String getUrlId() { return urlId; }
        public String getClientIp() { return clientIp; }
    }
}
