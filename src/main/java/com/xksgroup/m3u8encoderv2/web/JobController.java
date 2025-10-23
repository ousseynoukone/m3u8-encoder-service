package com.xksgroup.m3u8encoderv2.web;

import com.xksgroup.m3u8encoderv2.domain.Job.Job;
import com.xksgroup.m3u8encoderv2.domain.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.domain.ResourceType;
import com.xksgroup.m3u8encoderv2.repo.JobRepository;
import com.xksgroup.m3u8encoderv2.repo.MasterPlaylistRecordRepository;
import com.xksgroup.m3u8encoderv2.repo.VariantSegmentRepository;
import com.xksgroup.m3u8encoderv2.service.JobService;
import com.xksgroup.m3u8encoderv2.service.R2StorageService;
import com.xksgroup.m3u8encoderv2.service.helper.CleanDirectory;
import com.xksgroup.m3u8encoderv2.web.dto.JobProgressDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v2/m3u8-encoder/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Management", description = "Manage and monitor video processing jobs")
public class JobController {

    private final JobRepository jobRepository;
    private final MasterPlaylistRecordRepository masterPlaylistRecordRepository;
    private final VariantSegmentRepository variantSegmentRepository;
    private final JobService jobService;
    private final R2StorageService r2StorageService;
    private final CleanDirectory cleanDirectory;

    @GetMapping
    @Operation(
        summary = "List all jobs with pagination and filtering",
        description = "Retrieve a paginated list of all video processing jobs with optional filtering by status and resource type."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Jobs retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Jobs List",
                    value = """
                    {
                        "content": [
                            {
                                "id": "507f1f77bcf86cd799439011",
                                "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                                "slug": "my-awesome-video",
                                "title": "My Awesome Video",
                                "resourceType": "VIDEO",
                                "status": "COMPLETED",
                                "progressPercentage": 100,
                                "elapsedTime": "5m 30s",
                                "remainingTime": "0s",
                                "estimatedTotalTime": "5m 30s",
                                "totalSegments": 150,
                                "completedSegments": 150,
                                "failedSegments": 0,
                                "uploadingSegments": 0,
                                "pendingSegments": 0,
                                "createdAt": "2024-01-01T12:00:00",
                                "completedAt": "2024-01-01T12:05:30",
                                "masterPlaylistUrl": "https://cdn.example.com/movie/my-awesome-video/job-123/master.m3u8",
                                "securePlaybackUrl": "http://localhost:8080/proxy/my-awesome-video"
                            }
                        ],
                        "totalElements": 1,
                        "totalPages": 1,
                        "size": 20,
                        "number": 0
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> listJobs(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Filter by job status", example = "COMPLETED")
            @RequestParam(required = false) JobStatus status,
            
            @Parameter(description = "Filter by resource type", example = "VIDEO")
            @RequestParam(required = false) ResourceType resourceType) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Job> jobsPage;
            
            if (status != null && resourceType != null) {
                jobsPage = jobRepository.findByStatusAndResourceType(status, resourceType, pageable);
            } else if (status != null) {
                jobsPage = jobRepository.findByStatus(status, pageable);
            } else if (resourceType != null) {
                jobsPage = jobRepository.findByResourceType(resourceType, pageable);
            } else {
                jobsPage = jobRepository.findAll(pageable);
            }
            
            // Convert to DTOs for better formatting
            Page<JobProgressDto> dtoPage = jobsPage.map(JobProgressDto::fromJob);
            
            return ResponseEntity.ok(dtoPage);
            
        } catch (Exception e) {
            log.error("Failed to retrieve jobs - Error: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve jobs");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{jobId}")
    @Operation(
        summary = "Get job information by ID",
        description = "Retrieve detailed information about a specific job including progress, timing, and status."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job information retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Job Details",
                    value = """
                    {
                        "id": "507f1f77bcf86cd799439011",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "slug": "my-awesome-video",
                        "title": "My Awesome Video",
                        "resourceType": "VIDEO",
                        "status": "ENCODING",
                        "progressPercentage": 45,
                        "elapsedTime": "2m 15s",
                        "remainingTime": "2m 45s",
                        "estimatedTotalTime": "5m 0s",
                        "totalSegments": 150,
                        "completedSegments": 67,
                        "failedSegments": 0,
                        "uploadingSegments": 0,
                        "pendingSegments": 83,
                        "fileSizeFormatted": "1.07 GB",
                        "contentType": "video/mp4",
                        "createdAt": "2024-01-01T12:00:00",
                        "startedAt": "2024-01-01T12:00:30",
                        "lastProgressUpdate": "2024-01-01T12:02:45"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Not Found",
                    value = """
                    {
                        "error": "Job not found",
                        "message": "No job found with ID: job-123e4567-e89b-12d3-a456-426614174000"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> getJob(@PathVariable String jobId) {
        
        try {
            Optional<Job> jobOpt = jobRepository.findByJobId(jobId);
            
            if (jobOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Job not found");
                errorResponse.put("message", "No job found with ID: " + jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            // Convert to DTO for better formatting
            JobProgressDto jobDto = JobProgressDto.fromJob(jobOpt.get());
            return ResponseEntity.ok(jobDto);
            
        } catch (Exception e) {
            log.error("Failed to retrieve job: {} - Error: {}", jobId, e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve job");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/active")
    @Operation(
        summary = "Get all active jobs",
        description = "Retrieve a list of all jobs that are currently being processed (PENDING, UPLOADING, ENCODING, UPLOADING_TO_CLOUD_STORAGE) with detailed progress information."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Active jobs retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Active Jobs",
                    value = """
                    [
                        {
                            "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                            "slug": "my-awesome-video",
                            "title": "My Awesome Video",
                            "status": "ENCODING",
                            "progressPercentage": 45,
                            "elapsedTime": "2m 15s",
                            "remainingTime": "2m 45s",
                            "estimatedTotalTime": "5m 0s",
                            "totalSegments": 150,
                            "completedSegments": 67,
                            "failedSegments": 0,
                            "uploadingSegments": 0,
                            "pendingSegments": 83,
                            "fileSizeFormatted": "1.07 GB",
                            "contentType": "video/mp4",
                            "createdAt": "2024-01-01T12:00:00",
                            "startedAt": "2024-01-01T12:00:30",
                            "lastProgressUpdate": "2024-01-01T12:02:45"
                        }
                    ]
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> getActiveJobs() {
        try {
            List<Job> activeJobs = jobRepository.findActiveJobs();
            
            // Convert to DTOs for better formatting
            List<JobProgressDto> activeJobDtos = activeJobs.stream()
                    .map(JobProgressDto::fromJob)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(activeJobDtos);
            
        } catch (Exception e) {
            log.error("Failed to retrieve active jobs - Error: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve active jobs");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(
        summary = "Cancel a running job",
        description = "Cancel a job that is currently being processed. This will stop the FFmpeg process and clean up resources while preserving the job record."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job cancelled successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Success",
                    value = """
                    {
                        "message": "Job cancelled successfully",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "cancelledAt": "2024-01-01T12:00:00",
                        "details": "FFmpeg process stopped, all data deleted, local files cleaned up"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Not Found",
                    value = """
                    {
                        "error": "Job not found",
                        "message": "No job found with ID: job-123e4567-e89b-12d3-a456-426614174000"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Cannot cancel job",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Bad Request",
                    value = """
                    {
                        "error": "Cannot cancel job",
                        "message": "Job is not in a cancellable state"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> cancelJob(@PathVariable String jobId) {
        try {
            log.info("Attempting to cancel job: {}", jobId);
            
            // Check if job exists
            Optional<Job> jobOpt = jobRepository.findByJobId(jobId);
            if (jobOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Job not found");
                errorResponse.put("message", "No job found with ID: " + jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            Job job = jobOpt.get();
            
            // Check if job can be cancelled
            if (job.getStatus() != JobStatus.UPLOADING && 
                job.getStatus() != JobStatus.ENCODING && 
                job.getStatus() != JobStatus.UPLOADING_TO_CLOUD_STORAGE) {
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Cannot cancel job");
                errorResponse.put("message", "Job is not in a cancellable state. Current status: " + job.getStatus());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            // Cancel the job
            boolean cancelled = jobService.cancelJob(jobId);
            
            if (cancelled) {
                Map<String, Object> successResponse = new HashMap<>();
                successResponse.put("message", "Job cancelled successfully");
                successResponse.put("jobId", jobId);
                successResponse.put("cancelledAt", LocalDateTime.now());
                successResponse.put("jobTitle", job.getTitle());
                successResponse.put("jobStatus", job.getStatus());
                successResponse.put("details", "FFmpeg process stopped, all data deleted, local files cleaned up");
                
                log.info("Successfully cancelled job: {} (Title: {}, Status: {})", jobId, job.getTitle(), job.getStatus());
                return ResponseEntity.ok(successResponse);
            } else {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Failed to cancel job");
                errorResponse.put("message", "An error occurred while cancelling the job");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
            
        } catch (Exception e) {
            log.error("Failed to cancel job: {} - Error: {}", jobId, e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to cancel job");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @DeleteMapping("/{jobId}")
    @Operation(
        summary = "Delete a specific job",
        description = "Delete a job by ID. This will stop processing if running, clean up resources, and remove the job from the database."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job deleted successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Success",
                    value = """
                    {
                        "message": "Job deleted successfully",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "deletedAt": "2024-01-01T12:00:00"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Not Found",
                    value = """
                    {
                        "error": "Job not found",
                        "message": "No job found with ID: job-123e4567-e89b-12d3-a456-426614174000"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Cannot delete job",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Bad Request",
                    value = """
                    {
                        "error": "Cannot delete job",
                        "message": "Job is currently being processed and cannot be deleted"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> deleteJob(@PathVariable String jobId) {
        try {
            log.info("Attempting to delete job: {}", jobId);
            
            // Check if job exists
            Optional<Job> jobOpt = jobRepository.findByJobId(jobId);
            if (jobOpt.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Job not found");
                errorResponse.put("message", "No job found with ID: " + jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            Job job = jobOpt.get();
            
            // Check if job can be deleted (not currently processing)
            if (job.getStatus() == JobStatus.UPLOADING || 
                job.getStatus() == JobStatus.ENCODING || 
                job.getStatus() == JobStatus.UPLOADING_TO_CLOUD_STORAGE) {
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Cannot delete job");
                errorResponse.put("message", "Job is currently being processed and cannot be deleted. Please cancel it first.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            // Delete the job
            boolean deleted = jobService.deleteJob(jobId);
            
            if (deleted) {
                Map<String, Object> successResponse = new HashMap<>();
                successResponse.put("message", "Job deleted successfully");
                successResponse.put("jobId", jobId);
                successResponse.put("deletedAt", LocalDateTime.now());
                successResponse.put("jobTitle", job.getTitle());
                successResponse.put("jobStatus", job.getStatus());
                
                log.info("Successfully deleted job: {} (Title: {}, Status: {})", jobId, job.getTitle(), job.getStatus());
                return ResponseEntity.ok(successResponse);
            } else {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Failed to delete job");
                errorResponse.put("message", "An error occurred while deleting the job");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
            
        } catch (Exception e) {
            log.error("Failed to delete job: {} - Error: {}", jobId, e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to delete job");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @DeleteMapping
    @Operation(
        summary = "Delete all jobs",
        description = "Delete all jobs from the database. This will stop all running processes, clean up resources, and remove all jobs. Use with caution!"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "All jobs deleted successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Success",
                    value = """
                    {
                        "message": "All jobs deleted successfully",
                        "totalJobsDeleted": 25,
                        "deletedAt": "2024-01-01T12:00:00",
                        "summary": {
                            "completed": 15,
                            "failed": 5,
                            "cancelled": 3,
                            "pending": 2
                        }
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Cannot delete all jobs",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Bad Request",
                    value = """
                    {
                        "error": "Cannot delete all jobs",
                        "message": "There are currently active jobs that cannot be deleted"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> deleteAllJobs(
            @Parameter(description = "Force deletion even if there are active jobs", example = "false")
            @RequestParam(defaultValue = "false") boolean force) {
        
        try {
            log.info("Attempting to delete all jobs (force: {})", force);
            
            // Check if there are active jobs
            List<Job> activeJobs = jobRepository.findActiveJobs();
            if (!activeJobs.isEmpty() && !force) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Cannot delete all jobs");
                errorResponse.put("message", "There are currently " + activeJobs.size() + " active jobs that cannot be deleted");
                errorResponse.put("activeJobsCount", activeJobs.size());
                errorResponse.put("activeJobIds", activeJobs.stream().map(Job::getJobId).toList());
                errorResponse.put("suggestion", "Use force=true parameter to delete all jobs including active ones");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            // Delete all jobs
            Map<String, Object> deletionResult = jobService.deleteAllJobs(force);
            
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("message", "All jobs deleted successfully");
            successResponse.put("totalJobsDeleted", deletionResult.get("totalJobsDeleted"));
            successResponse.put("deletedAt", LocalDateTime.now());
            successResponse.put("summary", deletionResult.get("summary"));
            successResponse.put("force", force);
            
            log.info("Successfully deleted all jobs. Total deleted: {}", deletionResult.get("totalJobsDeleted"));
            return ResponseEntity.ok(successResponse);
            
        } catch (Exception e) {
            log.error("Failed to delete all jobs - Error: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to delete all jobs");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @DeleteMapping("/cleanup")
    @Operation(
        summary = "Clean up completed and failed jobs",
        description = "Delete only completed, failed, and cancelled jobs while preserving active jobs. This is safer than deleting all jobs."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Cleanup completed successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Success",
                    value = """
                    {
                        "message": "Cleanup completed successfully",
                        "jobsDeleted": 20,
                        "deletedAt": "2024-01-01T12:00:00",
                        "summary": {
                            "completed": 15,
                            "failed": 3,
                            "cancelled": 2
                        },
                        "activeJobsPreserved": 5
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> cleanupCompletedJobs() {
        try {
            log.info("Starting cleanup of completed and failed jobs");
            
            // Perform cleanup
            Map<String, Object> cleanupResult = jobService.cleanupCompletedJobs();
            
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("message", "Cleanup completed successfully");
            successResponse.put("jobsDeleted", cleanupResult.get("jobsDeleted"));
            successResponse.put("deletedAt", LocalDateTime.now());
            successResponse.put("summary", cleanupResult.get("summary"));
            successResponse.put("activeJobsPreserved", cleanupResult.get("activeJobsPreserved"));
            
            log.info("Cleanup completed successfully. Jobs deleted: {}", cleanupResult.get("jobsDeleted"));
            return ResponseEntity.ok(successResponse);
            
        } catch (Exception e) {
            log.error("Failed to cleanup completed jobs - Error: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to cleanup completed jobs");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @DeleteMapping("/cleanup-directories")
    @Operation(
        summary = "Clean up all local directories",
        description = "Clean up all hls-v2 and upload-v2 directories. Use this after all jobs are complete to free up disk space."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Directories cleaned successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error during cleanup")
    })
    public ResponseEntity<Map<String, Object>> cleanupDirectories() {
        try {
            log.info("🧹 Starting cleanup of all local directories...");
            
            // Clean up all directories
            jobService.cleanupAllDirectories();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All directories cleaned successfully");
            response.put("cleanedDirectories", new String[]{"hls-v2", "upload-v2"});
            response.put("timestamp", LocalDateTime.now());
            
            log.info("✅ Directory cleanup completed");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error during directory cleanup: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Directory cleanup failed: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/{jobId}/master")
    @Operation(
        summary = "Delete master playlist and all segments by job ID",
        description = "Delete a master playlist and all its associated segments from both R2 cloud storage and MongoDB database using job ID."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Master playlist and segments deleted successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Deletion Success",
                    value = """
                    {
                        "message": "Master playlist and segments deleted successfully",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "details": "Deleted from both R2 storage and MongoDB"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Master playlist not found",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Master Not Found",
                    value = """
                    {
                        "error": "Master playlist not found",
                        "message": "No master playlist found with job ID: job-123e4567-e89b-12d3-a456-426614174000"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error during deletion",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Deletion Error",
                    value = """
                    {
                        "error": "Failed to delete master playlist",
                        "message": "Error details here"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> deleteMasterPlaylist(
            @Parameter(description = "Job ID identifier for the master playlist", required = true, example = "job-123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String jobId) {
        
        try {
            log.info("Request to delete master playlist and segments for job ID: {}", jobId);
            
            // Use comprehensive delete method from JobService
            boolean deleted = jobService.deleteMasterAndSegmentsByJobId(jobId);
            
            if (deleted) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Master playlist and segments deleted successfully");
                response.put("jobId", jobId);
                response.put("details", "Deleted from both R2 storage and MongoDB");
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Master playlist not found");
                errorResponse.put("message", "No master playlist found with job ID: " + jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
        } catch (Exception e) {
            log.error("Failed to delete master playlist for job ID: {} - Error: {}", jobId, e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to delete master playlist");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/debug-r2")
    @Operation(summary = "Debug R2 storage - list all files")
    public ResponseEntity<Object> debugR2() {
        try {
            List<String> files = r2StorageService.listAllFiles();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "R2 debug info");
            response.put("totalFiles", files.size());
            response.put("files", files);
            response.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @DeleteMapping("/all-content")
    @Operation(
        summary = "Delete ALL content from R2 and database", 
        description = "⚠️ DANGEROUS: Deletes ALL jobs, master playlists, segments from both R2 storage and database. This cannot be undone!"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All content deleted successfully"),
        @ApiResponse(responseCode = "500", description = "Error during deletion")
    })
    public ResponseEntity<Object> deleteAllContent(
            @Parameter(description = "Confirmation - must be 'YES_DELETE_EVERYTHING'", required = true)
            @RequestParam String confirm) {
        
        if (!"YES_DELETE_EVERYTHING".equals(confirm)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid confirmation");
            errorResponse.put("message", "Must provide confirm=YES_DELETE_EVERYTHING");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        
        try {
            log.warn("🚨 DELETING ALL CONTENT - Jobs, R2 storage, and database records");
            
            // 1. Delete ALL files from R2 storage first
            boolean r2Deleted = r2StorageService.deleteAllFiles();
            
            // 2. Delete all jobs (this will also clean up associated data)
            Map<String, Object> jobDeletionResult = jobService.deleteAllJobs(true);
            
            // 3. Delete any remaining master playlist records
            long masterPlaylistCount = masterPlaylistRecordRepository.count();
            masterPlaylistRecordRepository.deleteAll();
            
            // 4. Delete any remaining variant segments
            long segmentCount = variantSegmentRepository.count();
            variantSegmentRepository.deleteAll();
            
            // 5. Clean up temporary directories
            cleanDirectory.cleanFileAndDirectory();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "All content deleted successfully");
            response.put("r2StorageDeleted", r2Deleted);
            response.put("jobDeletionResult", jobDeletionResult);
            response.put("masterPlaylistsDeleted", masterPlaylistCount);
            response.put("segmentsDeleted", segmentCount);
            response.put("timestamp", java.time.Instant.now().toString());
            
            log.warn("✅ ALL CONTENT DELETION COMPLETED - R2: {}, Jobs: {}, Playlists: {}, Segments: {}", 
                    r2Deleted, jobDeletionResult.get("totalDeleted"), masterPlaylistCount, segmentCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to delete all content: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to delete all content");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
