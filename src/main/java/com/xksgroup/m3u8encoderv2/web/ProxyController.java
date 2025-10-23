package com.xksgroup.m3u8encoderv2.web;

import com.xksgroup.m3u8encoderv2.service.R2StorageService;
import com.xksgroup.m3u8encoderv2.service.TokenService;
import com.xksgroup.m3u8encoderv2.repo.MasterPlaylistRecordRepository;
import com.xksgroup.m3u8encoderv2.domain.MasterPlaylistRecord;
import com.xksgroup.m3u8encoderv2.service.helper.EncryptionHelper;
import java.util.Optional;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


@Slf4j
@RestController
@RequestMapping("/api/v2/m3u8-encoder/proxy")
@Tag(name = "Secure HLS Proxy", description = "Token-based secure HLS streaming proxy")
public class ProxyController {

    private final R2StorageService storageService;
    private final TokenService tokenService;
    private final MasterPlaylistRecordRepository masterRepo;
    private final EncryptionHelper encryptionHelper;

    public ProxyController(R2StorageService storageService, TokenService tokenService, 
                          MasterPlaylistRecordRepository masterRepo, EncryptionHelper encryptionHelper) {
        this.storageService = storageService;
        this.tokenService = tokenService;
        this.masterRepo = masterRepo;
        this.encryptionHelper = encryptionHelper;
    }

    @Value("${server.host:localhost}")
    private String serverHost;


    @Value("${security.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${protocol:https}")
    private String protocol;



    /**
     * Master playlist endpoint - just /proxy/{jobId}
     */
    @GetMapping("/{jobId}")
    @Operation(summary = "Get master playlist using job ID")
    public ResponseEntity<String> getMasterPlaylist(
            @Parameter(description = "Job ID", example = "job-123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String jobId,
            HttpServletRequest request) {

        String userAgent = request.getHeader("User-Agent");
        log.info(" master playlist request - JobId: {}, User-Agent: {}", jobId, userAgent);

        try {
            // Look up the record by job ID
            Optional<MasterPlaylistRecord> recordOpt = masterRepo.findByJobId(jobId);
            if (recordOpt.isEmpty()) {
                log.warn("No record found for job ID: {}", jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Content not found");
            }

            MasterPlaylistRecord record = recordOpt.get();
            String keyPrefix = extractKeyPrefix(record.getMasterKey());

            // Fetch original master playlist from R2
            String originalPlaylist = storageService.getPlaylistContent(record.getMasterKey());

            // Rewrite variant playlist URLs to go through our proxy with tokens
            String rewrittenPlaylist = rewriteMasterPlaylist(originalPlaylist, keyPrefix, userAgent, record.getTitle());

            log.info("Served  master playlist for job ID: {} (length: {} chars)", jobId, rewrittenPlaylist.length());

            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Access-Control-Allow-Origin", allowedOrigins)
                    .header("Access-Control-Allow-Headers", "Range, Content-Type")
                    .header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Disposition", "inline; filename=\"" + record.getTitle() + ".m3u8\"")
                    .body(rewrittenPlaylist);

        } catch (Exception e) {
            log.error("Failed to serve  master playlist for job ID: {} - Error: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Playlist not found");
        }
    }

    /**
     * CORS preflight for variant playlist
     */
    @RequestMapping(value = "/{jobId}/{variant}/index.m3u8", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> variantPlaylistOptions(@PathVariable String jobId, @PathVariable String variant) {
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", allowedOrigins)
                .header("Access-Control-Allow-Headers", "Range, Content-Type")
                .header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
                .header("Access-Control-Max-Age", "86400")
                .build();
    }

    /**
     * Variant playlist endpoint - /proxy/{jobId}/{variant}/index.m3u8
     */
    @GetMapping("/{jobId}/{variant}/index.m3u8")
    @Operation(summary = "Get variant playlist using job ID")
    public ResponseEntity<String> getVariantPlaylist(
            @PathVariable String jobId,
            @PathVariable String variant,
            HttpServletRequest request) {

        String userAgent = request.getHeader("User-Agent");
        log.info(" variant playlist request - JobId: {}, Variant: {}, User-Agent: {}", jobId, variant, userAgent);

        try {
            // Look up the record by job ID
            Optional<MasterPlaylistRecord> recordOpt = masterRepo.findByJobId(jobId);
            if (recordOpt.isEmpty()) {
                log.warn("No record found for job ID: {}", jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Content not found");
            }

            MasterPlaylistRecord record = recordOpt.get();
            String keyPrefix = extractKeyPrefix(record.getMasterKey());
            String variantKey = keyPrefix + "/" + variant + "/index.m3u8";

            // Fetch original variant playlist from R2
            String originalPlaylist = storageService.getPlaylistContent(variantKey);

            // Rewrite segment URLs to go through our proxy with tokens (with dynamic duration)
            String rewrittenPlaylist = rewriteVariantPlaylist(originalPlaylist, keyPrefix, variant, userAgent, record.getDurationSeconds(), record.getTitle());

            log.info("Served  variant playlist for job ID: {}, variant: {} (length: {} chars)",
                    jobId, variant, rewrittenPlaylist.length());

            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Access-Control-Allow-Origin", allowedOrigins)
                    .header("Access-Control-Allow-Headers", "Range, Content-Type")
                    .header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Disposition", "inline; filename=\"" + record.getTitle() + "_" + variant + ".m3u8\"")
                    .body(rewrittenPlaylist);

        } catch (Exception e) {
            log.error("Failed to serve variant playlist for job ID: {}, variant: {} - Error: {}",
                    jobId, variant, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Playlist not found");
        }
    }

    /**
     * Handles segment requests - validates token and redirects to presigned URL
     */
    @GetMapping("/segment")
    @Operation(summary = "Get video segment with token validation")
    public ResponseEntity<Void> getSegment(
            @Parameter(description = "Access token", required = true)
            @RequestParam("token") String token,
            @Parameter(description = "Segment resource key", required = true)
            @RequestParam("resource") String resourceKey,
            HttpServletRequest request) {

        String userAgent = request.getHeader("User-Agent");
        log.info("Segment request - Resource: {}, User-Agent: {}", resourceKey, userAgent);

        try {
            // Validate the token
            TokenService.TokenValidationResult validation = tokenService.validateToken(token);
            
            if (!validation.isValid()) {
                log.warn("Invalid token for segment: {} - Error: {}", resourceKey, validation.getError());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Verify token is for the requested resource
            if (!resourceKey.equals(validation.getResourceKey())) {
                log.warn("Token resource mismatch - Token: {}, Requested: {}", validation.getResourceKey(), resourceKey);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Generate presigned URL for the actual segment
            String presignedUrl = storageService.generatePresignedUrl(resourceKey, 10);
            
            log.info("Redirecting to presigned URL for segment: {}", resourceKey);
            
            // Redirect to the presigned URL
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, presignedUrl)
                    .header("Cache-Control", "private, no-cache, no-store, must-revalidate")
                    .header("Access-Control-Allow-Origin", allowedOrigins)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to serve segment: {} - Error: {}", resourceKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Handles encryption key requests - validates token and serves the encryption key
     */
    @GetMapping("/key/{jobId}")
    @Operation(summary = "Get encryption key with token validation")
    public ResponseEntity<byte[]> getEncryptionKey(
            @Parameter(description = "Job ID", required = true)
            @PathVariable String jobId,
            @Parameter(description = "Access token", required = false)
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest request) {

        String userAgent = request.getHeader("User-Agent");
        log.info("Encryption key request - JobId: {}, User-Agent: {}", jobId, userAgent);

        try {
            // Check if token is provided
            if (token == null || token.trim().isEmpty()) {
                log.warn("Missing token for encryption key request: {}", jobId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "text/plain")
                        .body("Token required for key access".getBytes());
            }
            
            // Validate the token
            TokenService.TokenValidationResult validation = tokenService.validateToken(token);
            
            if (!validation.isValid()) {
                log.warn("Invalid token for encryption key: {} - Error: {}", jobId, validation.getError());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Verify token is for a key resource
            String expectedResourceKey = "key/" + jobId;
            if (!expectedResourceKey.equals(validation.getResourceKey())) {
                log.warn("Token resource mismatch for key - Token: {}, Expected: {}", 
                        validation.getResourceKey(), expectedResourceKey);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Look up the master playlist record to get the correct keyPrefix
            Optional<MasterPlaylistRecord> recordOpt = masterRepo.findAll().stream()
                    .filter(record -> record.getMasterKey().contains(jobId))
                    .findFirst();
                    
            if (recordOpt.isEmpty()) {
                log.error("No master playlist record found containing jobId: {}", jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            
            MasterPlaylistRecord record = recordOpt.get();
            String keyPrefix = extractKeyPrefix(record.getMasterKey());
            
            // Get the encryption key from storage
            String keyFileName = encryptionHelper.getKeyFileName(jobId);
            String keyPath = keyPrefix + "/" + keyFileName;
            
            try {
                byte[] keyData = storageService.getKeyData(keyPath);
                
                log.info("Served encryption key for jobId: {}", jobId);
                
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Cache-Control", "private, no-cache, no-store, must-revalidate")
                        .header("Access-Control-Allow-Origin", allowedOrigins)
                        .body(keyData);
                        
            } catch (Exception e) {
                log.error("Failed to retrieve encryption key from storage: {} - Error: {}", keyPath, e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
                    
        } catch (Exception e) {
            log.error("Failed to serve encryption key: {} - Error: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



    /**
     * Rewrites variant playlist to point segments to our proxy with tokens and add encryption key
     */
    private String rewriteVariantPlaylist(String content, String keyPrefix, String variant, String userAgent, Long videoDurationSeconds, String title) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n");
        boolean keyTagAdded = false;
        boolean headerAdded = false;
        
        for (String line : lines) {
            // Add title metadata after the #EXTM3U line
            if (line.startsWith("#EXTM3U") && !headerAdded) {
                result.append(line).append("\n");
                result.append("# Title: ").append(title).append(" (").append(variant.toUpperCase()).append(")\n");
                result.append("# Variant: ").append(variant).append("\n");
                headerAdded = true;
                continue;
            }
            
            // Skip any existing EXT-X-KEY tags to avoid duplicates
            if (line.trim().startsWith("#EXT-X-KEY:")) {
                log.debug("Skipping existing EXT-X-KEY tag: {}", line.trim());
                continue;
            }
            
            if (line.trim().startsWith("#EXT-X-VERSION") && !keyTagAdded) {
                result.append(line).append("\n");
                
                // Add encryption key tag right after version
                String jobId = extractJobIdFromKeyPrefix(keyPrefix);
                if (jobId != null) {
                    String keyToken = tokenService.generateKeyToken("key/" + jobId, userAgent, videoDurationSeconds);
                    String  keyUrl = String.format("%s://%s/proxy/key/%s?token=%s",
                                protocol, serverHost, jobId, keyToken);

                    result.append("#EXT-X-KEY:METHOD=AES-128,URI=\"").append(keyUrl).append("\"\n");
                    keyTagAdded = true;
                    log.debug("Added encryption key tag for jobId: {}", jobId);
                }
            } else if (line.trim().endsWith(".ts")) {
                // Extract just the segment filename from the full URL or relative path
                String segmentFilename = extractSegmentFilename(line.trim());
                
                // Generate token for this segment with dynamic duration
                String segmentKey = keyPrefix + "/" + variant + "/" + segmentFilename;
                String token = videoDurationSeconds != null ? 
                    tokenService.generateSegmentTokenWithDuration(segmentKey, userAgent, videoDurationSeconds) :
                    tokenService.generateSegmentToken(segmentKey, userAgent);
                
                // Create tokenized proxy URL
                String encodedResource = URLEncoder.encode(segmentKey, StandardCharsets.UTF_8);
                String proxyUrl = String.format("%s://%s/proxy/segment?token=%s&resource=%s",
                            protocol, serverHost, token, encodedResource);


                        
                result.append(proxyUrl).append("\n");
                
                log.debug("Rewritten segment: {} -> proxy URL with token", line.trim());
            } else {
                result.append(line).append("\n");
            }
        }
        
        return result.toString();
    }



    /**
     * Extract key prefix from master key (resourceType/slug/jobId)
     */
    private String extractKeyPrefix(String masterKey) {
        if (masterKey.endsWith("/master.m3u8")) {
            return masterKey.substring(0, masterKey.length() - "/master.m3u8".length());
        }
        return masterKey;
    }

    /**
     * Rewrites master playlist for  URLs
     */
    private String rewriteMasterPlaylist(String content, String keyPrefix, String userAgent, String title) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n");
        boolean headerAdded = false;
        
        // Extract jobId from keyPrefix (resourceType/slug/jobId)
        String[] parts = keyPrefix.split("/");
        String jobId = parts.length >= 3 ? parts[2] : "unknown";
        
        for (String line : lines) {
            // Add title metadata after the #EXTM3U line
            if (line.startsWith("#EXTM3U") && !headerAdded) {
                result.append(line).append("\n");
                result.append("# Title: ").append(title).append("\n");
                result.append("# Generated by HLS Encoder Service\n");
                headerAdded = true;
                continue;
            }
            
            if (line.trim().endsWith("/index.m3u8")) {
                // Extract variant name from the full URL or relative path
                String variant = extractVariantFromUrl(line.trim());
                
                // Rewrite to proxy URL using jobId
                String proxyUrl = String.format("%s://%s/proxy/%s/%s/index.m3u8",
                            protocol, serverHost, jobId, variant);


                result.append(proxyUrl).append("\n");
                
                log.debug("Rewritten master playlist variant: {} -> {}", line.trim(), proxyUrl);
            } else {
                result.append(line).append("\n");
            }
        }
        
        return result.toString();
    }


    /**
     * Extract variant name from URL (handles both full URLs and relative paths)
     * Examples:
     * - "https://domain.com/path/v0/index.m3u8" -> "v0"
     * - "v1/index.m3u8" -> "v1"
     */
    private String extractVariantFromUrl(String url) {
        // Remove /index.m3u8 from the end
        String withoutIndex = url.replace("/index.m3u8", "");
        
        // Extract the last path segment (the variant name like v0, v1, v2)
        String[] segments = withoutIndex.split("/");
        return segments[segments.length - 1];
    }

    /**
     * Extract segment filename from URL (handles both full URLs and relative paths)
     * Examples:
     * - "https://domain.com/path/v0/seg_0000.ts" -> "seg_0000.ts"
     * - "seg_0001.ts" -> "seg_0001.ts"
     */
    private String extractSegmentFilename(String url) {
        // Extract the last path segment (the filename like seg_0000.ts)
        String[] segments = url.split("/");
        return segments[segments.length - 1];
    }



    /**
     * Extract job ID from key prefix (resourceType/slug/jobId)
     */
    private String extractJobIdFromKeyPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            return null;
        }
        String[] parts = keyPrefix.split("/");
        if (parts.length >= 3) {
            return parts[parts.length - 1]; // Last part should be jobId
        }
        return null;
    }
}
