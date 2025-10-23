package com.xksgroup.m3u8encoderv2.web;

import com.xksgroup.m3u8encoderv2.domain.Job.Job;
import com.xksgroup.m3u8encoderv2.domain.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.repo.JobRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v2/m3u8-encoder/status")
@RequiredArgsConstructor
@Tag(name = "Status V2", description = "Upload and segment status endpoints")
public class StatusController {

    private final JobRepository jobRepository;

    @GetMapping("/{slug}")
    @Operation(
        summary = "Get all jobs for a content slug",
        description = "Retrieve a list of all job IDs and their individual stats for a given content slug (e.g., all episodes of a series)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Jobs retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SlugStatusResponse.class),
                examples = @ExampleObject(
                    name = "Success Response",
                    value = """
                    {
                        "slug": "the-avengers",
                        "title": "The Avengers",
                        "totalJobs": 3,
                        "jobs": [
                            {
                                "jobId": "job-abc123",
                                "title": "The Avengers",
                                "resourceType": "VIDEO",
                                "status": "COMPLETED",
                                "progressPercentage": 100,
                                "createdAt": "2024-01-01T12:00:00",
                                "completedAt": "2024-01-01T12:05:30",
                                "fileSizeFormatted": "1.07 GB",
                                "encodingDuration": "5m 30s",
                                "masterPlaylistUrl": "https://cdn.example.com/movie/the-avengers/job-abc123/master.m3u8",
                                "securePlaybackUrl": "http://localhost:8080/proxy/job-abc123"
                            },
                            {
                                "jobId": "job-def456",
                                "title": "The Avengers",
                                "resourceType": "VIDEO",
                                "status": "ENCODING",
                                "progressPercentage": 45,
                                "createdAt": "2024-01-01T13:00:00",
                                "elapsedTime": "2m 15s",
                                "remainingTime": "2m 45s",
                                "fileSizeFormatted": "2.1 GB"
                            }
                        ]
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No jobs found for slug",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Not Found",
                    value = """
                    {
                        "error": "No jobs found",
                        "message": "No jobs found for slug: the-avengers"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> getJobsBySlug(
            @Parameter(description = "Content slug", required = true, example = "the-avengers")
            @PathVariable String slug) {
        
        try {
            log.info("Requesting jobs for slug: {}", slug);
            
            // Find all jobs for this slug
            List<Job> jobs = jobRepository.findAllBySlug(slug);
            
            if (jobs.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "No jobs found");
                errorResponse.put("message", "No jobs found for slug: " + slug);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            // Build response with job details
            Map<String, Object> response = new HashMap<>();
            response.put("slug", slug);
            response.put("title", jobs.get(0).getTitle()); // Use title from first job
            response.put("totalJobs", jobs.size());
            
            List<Map<String, Object>> jobDetails = new ArrayList<>();
            for (Job job : jobs) {
                Map<String, Object> jobInfo = buildJobInfo(job);
                jobDetails.add(jobInfo);
            }
            response.put("jobs", jobDetails);
            
            log.info("Found {} jobs for slug: {}", jobs.size(), slug);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to retrieve jobs for slug: {} - Error: {}", slug, e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve jobs");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Build detailed job information for response
     */
    private Map<String, Object> buildJobInfo(Job job) {
        Map<String, Object> jobInfo = new HashMap<>();
        jobInfo.put("jobId", job.getJobId());
        jobInfo.put("title", job.getTitle());
        jobInfo.put("resourceType", job.getResourceType());
        jobInfo.put("status", job.getStatus());
        jobInfo.put("createdAt", job.getCreatedAt());
        
        // Progress information
        if (job.getProgressPercentage() > 0) {
            jobInfo.put("progressPercentage", job.getProgressPercentage());
        }
        
        // Timing information
        if (job.getElapsedTimeSeconds() != null && job.getElapsedTimeSeconds() > 0) {
            jobInfo.put("elapsedTime", formatDuration(job.getElapsedTimeSeconds()));
        }
        
        if (job.getRemainingTimeSeconds() != null && job.getRemainingTimeSeconds() > 0) {
            jobInfo.put("remainingTime", formatDuration(job.getRemainingTimeSeconds()));
        }
        
        if (job.getCompletedAt() != null) {
            jobInfo.put("completedAt", job.getCompletedAt());
        }
        
        // File information
        if (job.getFileSize() > 0) {
            jobInfo.put("fileSizeFormatted", formatFileSize(job.getFileSize()));
        }
        
        // Duration information
        if (job.getEncodingDurationSeconds() != null && job.getEncodingDurationSeconds() > 0) {
            jobInfo.put("encodingDuration", formatDuration(job.getEncodingDurationSeconds()));
        }
        
        // URLs
        if (job.getMasterPlaylistUrl() != null) {
            jobInfo.put("masterPlaylistUrl", job.getMasterPlaylistUrl());
        }
        
        if (job.getSecurePlaybackUrl() != null) {
            jobInfo.put("securePlaybackUrl", job.getSecurePlaybackUrl());
        }
        
        return jobInfo;
    }
    
    /**
     * Format duration in seconds to human-readable format
     */
    private String formatDuration(Long seconds) {
        if (seconds == null || seconds <= 0) {
            return "0s";
        }
        
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
    
    /**
     * Format file size in bytes to human-readable format
     */
    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024.0 && unitIndex < units.length - 1) {
            size /= 1024.0;
            unitIndex++;
        }
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }
}

// Response schema for OpenAPI documentation
@Schema(description = "Slug status response")
class SlugStatusResponse {
    @Schema(description = "Content slug", example = "the-avengers")
    private String slug;
    
    @Schema(description = "Content title", example = "The Avengers")
    private String title;
    
    @Schema(description = "Total number of jobs for this slug", example = "3")
    private int totalJobs;
    
    @Schema(description = "List of job details")
    private List<JobInfo> jobs;
}

@Schema(description = "Job information")
class JobInfo {
    @Schema(description = "Unique job identifier", example = "job-abc123")
    private String jobId;
    
    @Schema(description = "Job title", example = "The Avengers")
    private String title;
    
    @Schema(description = "Resource type", example = "VIDEO")
    private String resourceType;
    
    @Schema(description = "Job status", example = "COMPLETED")
    private String status;
    
    @Schema(description = "Progress percentage", example = "100")
    private Integer progressPercentage;
    
    @Schema(description = "Job creation time")
    private LocalDateTime createdAt;
    
    @Schema(description = "Job completion time")
    private LocalDateTime completedAt;
    
    @Schema(description = "Formatted file size", example = "1.07 GB")
    private String fileSizeFormatted;
    
    @Schema(description = "Encoding duration", example = "5m 30s")
    private String encodingDuration;
    
    @Schema(description = "Master playlist URL")
    private String masterPlaylistUrl;
    
    @Schema(description = "Secure playback URL")
    private String securePlaybackUrl;
}


