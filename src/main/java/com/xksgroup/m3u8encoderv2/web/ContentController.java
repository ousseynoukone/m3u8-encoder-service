package com.xksgroup.m3u8encoderv2.web;

import com.xksgroup.m3u8encoderv2.domain.MasterPlaylistRecord;
import com.xksgroup.m3u8encoderv2.domain.Job.Job;
import com.xksgroup.m3u8encoderv2.domain.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.repo.MasterPlaylistRecordRepository;
import com.xksgroup.m3u8encoderv2.repo.JobRepository;
import com.xksgroup.m3u8encoderv2.service.R2StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v2/m3u8-encoder/content")
@RequiredArgsConstructor
@Tag(name = "Content Management", description = "APIs for fetching existing content")
public class ContentController {

    private final MasterPlaylistRecordRepository masterPlaylistRecordRepository;
    private final JobRepository jobRepository;
    private final R2StorageService r2StorageService;

    @Operation(
        summary = "Get content by slug",
        description = "Retrieve existing content information by its slug identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Content found successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "success": true,
                        "data": {
                            "id": "507f1f77bcf86cd799439011",
                            "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                            "slug": "never-fade-away",
                            "title": "Never Fade Away - Cyberpunk 2077",
                            "resourceType": "VIDEO",
                            "status": "COMPLETED",
                            "masterUrl": "https://pub-1ded4ae339574fff8c5101b0a0c5662d.r2.dev/video/never-fade-away/job-123/master.m3u8",
                            "securePlaybackUrl": "http://localhost:8080/proxy/hls/video/never-fade-away/job-123/master.m3u8",
                            "durationSeconds": 300,
                            "variants": [
                                {
                                    "label": "v0",
                                    "bandwidth": "4000000",
                                    "resolution": "1920x1080",
                                    "codecs": "avc1.4d401f,mp4a.40.2",
                                    "playlistUrl": "https://pub-1ded4ae339574fff8c5101b0a0c5662d.r2.dev/video/never-fade-away/job-123/v0/index.m3u8"
                                }
                            ],
                            "createdAt": "2025-09-03T14:00:00Z"
                        }
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Content not found",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "success": false,
                        "error": "Content not found",
                        "message": "No content found with slug: never-fade-away"
                    }
                    """
                )
            )
        )
    })
    @GetMapping("/slug/{slug}")
    public ResponseEntity<Map<String, Object>> getContentBySlug(
            @Parameter(description = "Content slug identifier", example = "never-fade-away")
            @PathVariable String slug) {
        
        try {
            log.info("Fetching content by slug: {}", slug);
            
            Optional<MasterPlaylistRecord> masterOpt = masterPlaylistRecordRepository.findBySlug(slug);
            
            if (masterOpt.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Content not found");
                response.put("message", "No content found with slug: " + slug);
                return ResponseEntity.notFound().build();
            }
            
            MasterPlaylistRecord master = masterOpt.get();
            Map<String, Object> contentData = buildContentResponse(master);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", contentData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching content by slug {}: {}", slug, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Internal server error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @Operation(
        summary = "Get content by job ID",
        description = "Retrieve existing content information by its job ID"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Content found successfully"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Content not found"
        )
    })
    @GetMapping("/job/{jobId}")
    public ResponseEntity<Map<String, Object>> getContentByJobId(
            @Parameter(description = "Job ID identifier", example = "job-123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String jobId) {
        
        try {
            log.info("Fetching content by job ID: {}", jobId);
            
            Optional<MasterPlaylistRecord> masterOpt = masterPlaylistRecordRepository.findByJobId(jobId);
            
            if (masterOpt.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Content not found");
                response.put("message", "No content found with job ID: " + jobId);
                return ResponseEntity.notFound().build();
            }
            
            MasterPlaylistRecord master = masterOpt.get();
            Map<String, Object> contentData = buildContentResponse(master);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", contentData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching content by job ID {}: {}", jobId, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Internal server error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @Operation(
        summary = "List all existing content",
        description = "Retrieve a paginated list of all existing content"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Content list retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "success": true,
                        "data": {
                            "content": [
                                {
                                    "id": "507f1f77bcf86cd799439011",
                                    "slug": "never-fade-away",
                                    "title": "Never Fade Away - Cyberpunk 2077",
                                    "resourceType": "VIDEO",
                                    "status": "COMPLETED",
                                    "masterUrl": "https://pub-1ded4ae339574fff8c5101b0a0c5662d.r2.dev/video/never-fade-away/job-123/master.m3u8",
                                    "securePlaybackUrl": "http://localhost:8080/proxy/hls/video/never-fade-away/job-123/master.m3u8",
                                    "durationSeconds": 300,
                                    "createdAt": "2025-09-03T14:00:00Z"
                                }
                            ],
                            "pagination": {
                                "page": 0,
                                "size": 20,
                                "totalElements": 1,
                                "totalPages": 1
                            }
                        }
                    }
                    """
                )
            )
        )
    })
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listAllContent(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            log.info("Listing all content - Page: {}, Size: {}", page, size);
            
            Pageable pageable = PageRequest.of(page, size);
            Page<MasterPlaylistRecord> mastersPage = masterPlaylistRecordRepository.findAll(pageable);
            
            List<Map<String, Object>> contentList = mastersPage.getContent().stream()
                    .map(this::buildContentSummary)
                    .toList();
            
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", mastersPage.getNumber());
            pagination.put("size", mastersPage.getSize());
            pagination.put("totalElements", mastersPage.getTotalElements());
            pagination.put("totalPages", mastersPage.getTotalPages());
            
            Map<String, Object> data = new HashMap<>();
            data.put("content", contentList);
            data.put("pagination", pagination);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error listing content: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Internal server error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @Operation(
        summary = "Search content by title",
        description = "Search existing content by title (case-insensitive partial match)"
    )
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchContent(
            @Parameter(description = "Search query", example = "cyberpunk")
            @RequestParam String q,
            
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            log.info("Searching content with query: '{}' - Page: {}, Size: {}", q, page, size);
            
            Pageable pageable = PageRequest.of(page, size);
            Page<MasterPlaylistRecord> mastersPage = masterPlaylistRecordRepository.findByTitleContainingIgnoreCase(q, pageable);
            
            List<Map<String, Object>> contentList = mastersPage.getContent().stream()
                    .map(this::buildContentSummary)
                    .toList();
            
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", mastersPage.getNumber());
            pagination.put("size", mastersPage.getSize());
            pagination.put("totalElements", mastersPage.getTotalElements());
            pagination.put("totalPages", mastersPage.getTotalPages());
            
            Map<String, Object> data = new HashMap<>();
            data.put("content", contentList);
            data.put("pagination", pagination);
            data.put("query", q);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error searching content with query '{}': {}", q, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Internal server error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Build detailed content response
     */
    private Map<String, Object> buildContentResponse(MasterPlaylistRecord master) {
        Map<String, Object> content = new HashMap<>();
        content.put("id", master.getId());
        content.put("jobId", master.getJobId());
        content.put("slug", master.getSlug());
        content.put("title", master.getTitle());
        content.put("resourceType", master.getResourceType());
        content.put("status", master.getStatus());
        content.put("masterUrl", master.getMasterUrl());
        content.put("durationSeconds", master.getDurationSeconds());
        content.put("variants", master.getVariants());
        content.put("createdAt", master.getCreatedAt());
        
        // Generate secure proxy URL
        if (master.getMasterKey() != null) {
            String secureProxyUrl = String.format("http://localhost:8080/proxy/hls/%s/master.m3u8", 
                    master.getMasterKey().replace("/master.m3u8", ""));
            content.put("securePlaybackUrl", secureProxyUrl);
        }
        
        return content;
    }

    /**
     * Build summary content response (for lists)
     */
    private Map<String, Object> buildContentSummary(MasterPlaylistRecord master) {
        Map<String, Object> content = new HashMap<>();
        content.put("id", master.getId());
        content.put("slug", master.getSlug());
        content.put("title", master.getTitle());
        content.put("resourceType", master.getResourceType());
        content.put("status", master.getStatus());
        content.put("masterUrl", master.getMasterUrl());
        content.put("durationSeconds", master.getDurationSeconds());
        content.put("createdAt", master.getCreatedAt());
        
        // Generate secure proxy URL
        if (master.getMasterKey() != null) {
            String secureProxyUrl = String.format("http://localhost:8080/proxy/hls/%s/master.m3u8", 
                    master.getMasterKey().replace("/master.m3u8", ""));
            content.put("securePlaybackUrl", secureProxyUrl);
        }
        
        return content;
    }
}

