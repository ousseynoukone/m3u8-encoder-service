package com.xksgroup.m3u8encoderv2.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.xksgroup.m3u8encoderv2.model.Job.Job;
import com.xksgroup.m3u8encoderv2.model.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.model.MasterPlaylistRecord;
import com.xksgroup.m3u8encoderv2.model.RequestIssuer;
import com.xksgroup.m3u8encoderv2.model.ResourceType;
import com.xksgroup.m3u8encoderv2.model.VariantSegment;
import com.xksgroup.m3u8encoderv2.repo.JobRepository;
import com.xksgroup.m3u8encoderv2.repo.MasterPlaylistRecordRepository;
import com.xksgroup.m3u8encoderv2.repo.VariantSegmentRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final FFmpegEncoderService encoder;
    private final R2StorageService storage;
    private final MasterPlaylistRecordRepository masterPlaylistRecordRepository;
    private final VariantSegmentRepository variantSegmentRepository;

    private final EventService eventService;


    @Value("${server.host:localhost}")
    private String serverHost;

    @Value("${protocol}")
    private String protocol;

    @Value("${server.port:8080}")
    private int serverPort;


    @PostConstruct
    public void init() {
        // Set circular dependency after construction
        encoder.setJobService(this);
        storage.setJobService(this);
    }

    /**
     * Generate slug from title (no incrementing - same title = same slug)
     */
    private String generateSlug(String title) {
        String slug = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        log.info("Generated slug: {} for title: '{}'", slug, title);
        return slug;
    }

    /**
     * Create a new job and return immediately
     */
    public Job createJob(String title, ResourceType resourceType, String originalFilename, 
                        long fileSize, String contentType, RequestIssuer userAgent) {
        
        String slug = generateSlug(title);
        String jobId = "job-" + UUID.randomUUID().toString();
        
        Job job = Job.builder()
                .jobId(jobId)
                .slug(slug)
                .title(title)
                .resourceType(resourceType)
                .status(JobStatus.PENDING)
                .originalFilename(originalFilename)
                .fileSize(fileSize)
                .contentType(contentType)
                .createdAt(LocalDateTime.now())
                .userAgent(userAgent)
                .totalSegments(0)
                .completedSegments(0)
                .failedSegments(0)
                .uploadingSegments(0)
                .pendingSegments(0)
                .progressPercentage(0)
                .elapsedTimeSeconds(0L)
                .remainingTimeSeconds(0L)
                .estimatedTotalTimeSeconds(0L)
                .currentVariant(0)
                .totalVariants(resourceType == ResourceType.VIDEO ? 4 : 3) // 4 video variants or 3 audio variants
                .currentVariantName("")
                .currentVariantDescription("")
                .encodingDurationSeconds(0L)
                .uploadDurationSeconds(0L)
                .totalDurationSeconds(0L)
                .acceleration("cpu/libx264")
                .build();
        
        Job savedJob = jobRepository.save(job);
        log.info("Created job: {} for title: '{}' with slug: '{}'", jobId, title, slug);
        
        return savedJob;
    }

    /**
     * Update job status
     */
    public void updateJobStatus(String jobId, JobStatus status) {
        Optional<Job> jobOpt = jobRepository.findByJobId(jobId);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            JobStatus previousStatus = job.getStatus();
            job.setStatus(status);
            
            switch (status) {
                case UPLOADING:
                    job.setStartedAt(LocalDateTime.now());
                    break;
                case ENCODING:
                    if (job.getStartedAt() == null) {
                        job.setStartedAt(LocalDateTime.now());
                    }
                    break;
                case DOWNLOADING:
                    // No special timestamps for downloading
                    break;
                case UPLOADING_TO_CLOUD_STORAGE:
                    // No specific timestamp update needed
                    break;
                case COMPLETED:
                    job.setCompletedAt(LocalDateTime.now());
                    break;
                case FAILED:
                    job.setFailedAt(LocalDateTime.now());
                    break;
                case CANCELLED:
                    job.setFailedAt(LocalDateTime.now()); // Reuse failedAt for cancelled timestamp
                    break;
                case PENDING:
                    // No specific timestamp update needed
                    break;
            }
            
            Job savedJob = jobRepository.save(job);
            
            // Handle terminal state events (no need for active job update after terminal state)
            if (status == JobStatus.CANCELLED) {
                eventService.notifyJobCancellation(jobId, savedJob);
            } else {
                // For non-terminal status changes, dispatch active job updates
                eventService.dispatchJobProgressionToSEEClients();
            }
            
            log.info("Updated job {} status from {} to: {}", jobId, previousStatus, status);
        }
    }



    /**
     * Update segment counts for upload tracking (current variant)
     */
    public void updateSegmentCounts(String jobId, int total, int completed, int failed, int uploading, int pending) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setTotalSegments(total);
            job.setCompletedSegments(completed);
            job.setFailedSegments(failed);
            job.setUploadingSegments(uploading);
            job.setPendingSegments(pending);
            
            // Update total counts across all variants
            updateTotalSegmentCounts(job);
            
            // Calculate progress percentage based on completed segments
            if (total > 0) {
                int progressPercentage = (completed * 100) / total;
                job.setProgressPercentage(progressPercentage);
                
                // Update status based on completion
                if (job.getStatus() == JobStatus.UPLOADING_TO_CLOUD_STORAGE) {
                    if (completed == total && failed == 0) {
                        // All segments completed, but keep status as UPLOADING_TO_CLOUD_STORAGE
                        // until the entire upload process is complete
                        log.debug("All segments completed for job {}, keeping upload status", jobId);
                    } else if (failed > 0) {
                        // Some segments failed
                        log.warn("Some segments failed for job {}: {}/{} failed", jobId, failed, total);
                    }
                }
            }
            
            job.setLastProgressUpdate(LocalDateTime.now());
            jobRepository.save(job);
            eventService.dispatchJobProgressionToSEEClients();
            
            log.debug("Updated segment counts for job {}: {}/{}/{}/{}/{} ({}%)", 
                     jobId, total, completed, failed, uploading, pending, job.getProgressPercentage());
        });
    }
    
    /**
     * Update total segment counts across all variants
     */
    private void updateTotalSegmentCounts(Job job) {
        // For now, we'll use the current variant counts as total counts
        // In a more sophisticated implementation, we would track each variant separately
        job.setTotalSegmentsAllVariants(job.getTotalSegments());
        job.setCompletedSegmentsAllVariants(job.getCompletedSegments());
        job.setFailedSegmentsAllVariants(job.getFailedSegments());
        job.setUploadingSegmentsAllVariants(job.getUploadingSegments());
        job.setPendingSegmentsAllVariants(job.getPendingSegments());
    }

    /**
     * Update job progress with variant tracking
     */
    public void updateJobProgressWithVariant(String jobId, int percentage, double currentTime, double totalTime,
                                           int currentVariant, int totalVariants, String variantName, String variantDescription, int variantPercentage) {
        Optional<Job> jobOpt = jobRepository.findByJobId(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("Job not found for progress update: {}", jobId);
            return;
        }
        
        Job job = jobOpt.get();
        job.setProgressPercentage(percentage);
        job.setElapsedTimeSeconds((long) currentTime);
        
        if (totalTime > 0 && currentTime > 0) {
            long estimatedTotal = (long) totalTime;
            long remaining = Math.max(0, estimatedTotal - (long) currentTime);
            job.setEstimatedTotalTimeSeconds(estimatedTotal);
            job.setRemainingTimeSeconds(remaining);
        }
        
        // Update variant tracking
        job.setCurrentVariant(currentVariant);
        job.setTotalVariants(totalVariants);
        job.setCurrentVariantName(variantName);
        job.setCurrentVariantDescription(variantDescription);
        job.setVariantProgressPercentage(variantPercentage);
        
        job.setLastProgressUpdate(LocalDateTime.now());
        jobRepository.save(job);

        eventService.dispatchJobProgressionToSEEClients();
    }

    public void updateAcceleration(String jobId, String acceleration) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setAcceleration(acceleration);
            jobRepository.save(job);
            eventService.dispatchJobProgressionToSEEClients();
        });
    }
    
    /**
     * Mark encoding start time
     */
    public void markEncodingStart(String jobId) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);
        });
    }
    
    /**
     * Mark encoding completion and calculate durations
     */
    public void markEncodingComplete(String jobId) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            LocalDateTime now = LocalDateTime.now();
            job.setCompletedAt(now);
            
            // Calculate encoding duration
            if (job.getStartedAt() != null) {
                long encodingSeconds = java.time.Duration.between(job.getStartedAt(), now).getSeconds();
                job.setEncodingDurationSeconds(encodingSeconds);
            }
            
            // Calculate total duration
            if (job.getCreatedAt() != null) {
                long totalSeconds = java.time.Duration.between(job.getCreatedAt(), now).getSeconds();
                job.setTotalDurationSeconds(totalSeconds);
            }
            
            jobRepository.save(job);
        });
    }
    
    /**
     * Mark upload start time
     */
    public void markUploadStart(String jobId) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            // Store upload start time in metadata for calculation
            if (job.getMetadata() == null) {
                job.setMetadata(new HashMap<>());
            }
            job.getMetadata().put("uploadStartTime", LocalDateTime.now().toString());
            jobRepository.save(job);
        });
    }
    
    /**
     * Mark upload completion and calculate upload duration
     */
    public void markUploadComplete(String jobId) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            LocalDateTime now = LocalDateTime.now();
            
            // Calculate upload duration
            if (job.getMetadata() != null && job.getMetadata().containsKey("uploadStartTime")) {
                LocalDateTime uploadStart = LocalDateTime.parse(job.getMetadata().get("uploadStartTime").toString());
                long uploadSeconds = java.time.Duration.between(uploadStart, now).getSeconds();
                job.setUploadDurationSeconds(uploadSeconds);
            }
            
            // Update total duration
            if (job.getCreatedAt() != null) {
                long totalSeconds = java.time.Duration.between(job.getCreatedAt(), now).getSeconds();
                job.setTotalDurationSeconds(totalSeconds);
            }
            
            jobRepository.save(job);
        });
    }

    /**
     * Update job with error
     */
    public void updateJobError(String jobId, String errorMessage, String errorDetails) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setErrorDetails(errorDetails);
            job.setFailedAt(LocalDateTime.now());
            
            Job savedJob = jobRepository.save(job);
            
            // Send dedicated failure event - this is the terminal state, no need for active job update
            eventService.notifyJobFailure(jobId, savedJob);
            
            log.error("Updated job {} with error: {} - {}", jobId, errorMessage, errorDetails);
        });
    }

    /**
     * Update job with completion details
     */
    public void updateJobCompletion(String jobId, String masterPlaylistUrl, String securePlaybackUrl, 
                                  String keyPrefix, Map<String, Object> variants, Long totalDuration) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.COMPLETED);
            job.setMasterPlaylistUrl(masterPlaylistUrl);
            job.setSecurePlaybackUrl(securePlaybackUrl);
            job.setKeyPrefix(keyPrefix);
            job.setVariants(variants);
            job.setTotalDurationSeconds(totalDuration);
            job.setCompletedAt(LocalDateTime.now());
            
            Job savedJob = jobRepository.save(job);
            
            // Send dedicated completion event - this is the terminal state, no need for active job update
            eventService.notifyJobCompletion(jobId, savedJob);
            
            log.info("Updated job {} completion details", jobId);
        });
    }

    /**
     * Update job file information (originalFilename, size, contentType)
     */
    public void updateJobFileInfo(String jobId, String originalFilename, Long fileSize, String contentType) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            if (originalFilename != null && !originalFilename.isBlank()) {
                job.setOriginalFilename(originalFilename);
            }
            if (fileSize != null && fileSize > 0) {
                job.setFileSize(fileSize);
            }
            if (contentType != null && !contentType.isBlank()) {
                job.setContentType(contentType);
            }
            job.setLastProgressUpdate(java.time.LocalDateTime.now());
            jobRepository.save(job);
            eventService.dispatchJobProgressionToSEEClients();
        });
    }

    /**
     * Process job asynchronously
     */
    @Async("taskExecutor")
    public void processJobAsync(Job job, Path sourceFile) {
        try {
            log.info("Starting async processing for job: {}", job.getJobId());
            
            // Update status to UPLOADING
            updateJobStatus(job.getJobId(), JobStatus.UPLOADING);
            
            // Create output directory
            Path outDir = Paths.get("hls-v2")
                    .resolve(job.getResourceType().name().toLowerCase())
                    .resolve(job.getSlug())
                    .resolve(job.getJobId());
            
            // Update status to ENCODING and mark encoding start
            updateJobStatus(job.getJobId(), JobStatus.ENCODING);
            markEncodingStart(job.getJobId());
            
            // Start FFmpeg encoding
            Path resultDir = encoder.generateAbrHls(sourceFile, job.getSlug(), outDir, job.getJobId(), job.getResourceType());
            
            // Check if encoding was cancelled
            if (resultDir == null) {
                log.info("Job {} encoding was cancelled, stopping processing", job.getJobId());
                return; // Exit early - job was cancelled
            }

            // Mark encoding completion
            markEncodingComplete(job.getJobId());

            // Queue delay: Wait for FFmpeg to fully complete all file operations
            log.info("Job {} encoding completed, queuing for upload in 3 seconds...", job.getJobId());
            try {
                Thread.sleep(3000); // 3 second queue delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Upload queue delay interrupted for job: {}", job.getJobId());
            }

            // Update status to UPLOADING_TO_CLOUD_STORAGE and mark upload start
            updateJobStatus(job.getJobId(), JobStatus.UPLOADING_TO_CLOUD_STORAGE);
            markUploadStart(job.getJobId());
            
            // Upload to cloud storage
            String prefix = job.getResourceType().name().toLowerCase() + "/" + job.getSlug() + "/" + job.getJobId();
            String masterUrl = storage.uploadAbrJob(outDir, prefix, job.getSlug(), job.getTitle(), job.getResourceType().name());

            // Mark upload completion
            markUploadComplete(job.getJobId());
            
            // Generate secure proxy URL
            String secureProxyUrl = String.format("%s://%s/proxy/hls/%s/master.m3u8",protocol,serverHost, prefix);

            // Update job completion
            updateJobCompletion(job.getJobId(), masterUrl, secureProxyUrl, prefix, null, null);
            
            // Clean up only this job's specific directories
            cleanJobDirectories(job.getJobId(), job.getSlug(), job.getResourceType());

            // Check if we should do smart cleanup (when all jobs are done)
            checkAndPerformSmartCleanup();

            log.info("Successfully completed processing for job: {}", job.getJobId());
            
        } catch (Exception e) {
            log.error("Failed to process job: {} - Error: {}", job.getJobId(), e.getMessage(), e);
            
            // Check if this was a cancellation
            if (isJobCancelled(job.getJobId())) {
                log.info("Job {} was cancelled, not marking as failed", job.getJobId());
                return;
            }
            
            // Update job with error
            updateJobError(job.getJobId(), "Processing failed - " + e.getMessage(), e.toString());
            
            // Clean up only this job's specific directories even on failure
            cleanJobDirectories(job.getJobId(), job.getSlug(), job.getResourceType());
        }
    }

    /**
     * Clean up only the specific job's directories (not all jobs)
     */
    private void cleanJobDirectories(String jobId, String slug, ResourceType resourceType) {
        try {
            // Clean up job-specific HLS directory
            Path jobHlsDir = Paths.get("hls-v2", resourceType.name().toLowerCase(), slug, jobId);
            if (Files.exists(jobHlsDir)) {
                deleteDirectoryRecursively(jobHlsDir);
                log.info("Cleaned up job-specific HLS directory: {}", jobHlsDir);
            }
            
            // Clean up job-specific upload directory (if it exists)
            Path jobUploadDir = Paths.get("upload-v2");
            if (Files.exists(jobUploadDir)) {
                // Only clean upload-v2 if it's empty or contains only this job's files
                try (Stream<Path> files = Files.list(jobUploadDir)) {
                    long fileCount = files.count();
                    if (fileCount == 0) {
                        deleteDirectoryRecursively(jobUploadDir);
                        log.info("Cleaned up empty upload directory: {}", jobUploadDir);
                    } else {
                        log.debug("Upload directory not empty ({} files), skipping cleanup", fileCount);
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Error cleaning up job directories for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Smart cleanup: Check if all jobs are done and clean up accordingly
     */
    private void checkAndPerformSmartCleanup() {
        try {
            // Check if there are any active jobs (PENDING, UPLOADING, ENCODING, UPLOADING_TO_CLOUD_STORAGE)
            List<Job> activeJobs = jobRepository.findByStatusIn(List.of(
                JobStatus.PENDING, 
                JobStatus.UPLOADING, 
                JobStatus.ENCODING, 
                JobStatus.UPLOADING_TO_CLOUD_STORAGE
            ));
            
            if (activeJobs.isEmpty()) {
                log.info("🎉 All jobs completed! Performing final cleanup...");
                cleanupAllDirectories();
            } else {
                log.debug("{} active jobs still running, skipping final cleanup", activeJobs.size());
            }
            
        } catch (Exception e) {
            log.error("Error during smart cleanup check: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up all directories after all jobs are complete
     * This should be called when no more jobs are running
     */
    public void cleanupAllDirectories() {
        try {
            log.info("Starting final cleanup of all directories...");
            
            // Clean up hls-v2 directory
            Path hlsRootDir = Paths.get("hls-v2");
            if (Files.exists(hlsRootDir)) {
                deleteDirectoryRecursively(hlsRootDir);
                log.info("✅ Cleaned up hls-v2 directory");
            }
            
            // Clean up upload-v2 directory
            Path uploadRootDir = Paths.get("upload-v2");
            if (Files.exists(uploadRootDir)) {
                deleteDirectoryRecursively(uploadRootDir);
                log.info("✅ Cleaned up upload-v2 directory");
            }
            
            log.info("🎉 Final cleanup completed - all directories cleaned");
            
        } catch (Exception e) {
            log.error("Error during final cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Cancel a job and clean up all resources
     */
    public boolean cancelJob(String jobId) {
        try {
            log.info("Starting cancellation process for job: {}", jobId);

            // Find the job
            Optional<Job> jobOpt = jobRepository.findByJobId(jobId);
            if (jobOpt.isEmpty()) {
                log.warn("Job not found for cancellation: {}", jobId);
                return false;
            }
            
            Job job = jobOpt.get();
            
            // Check if job can be cancelled
            if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
                log.warn("Cannot cancel job {} - already in final state: {}", jobId, job.getStatus());
                return false;
            }
            
            // Stop FFmpeg process first
            encoder.stopProcess(jobId);
            log.info("Stopped FFmpeg process for job: {}", jobId);
            
            // Update job status to CANCELLED
            updateJobStatus(jobId, JobStatus.CANCELLED);
            
            // Clean up local directories
            cleanupJobDirectories(job);

            // Check if we should do smart cleanup (when all remaining jobs are done)
            checkAndPerformSmartCleanup();

            log.info("Successfully cancelled job: {}", jobId);
            return true;

        } catch (Exception e) {
            log.error("Error cancelling job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Clean up directories and files associated with a specific job ONLY
     */
    private void cleanupJobDirectories(Job job) {
        try {
            String jobId = job.getJobId();
            String slug = job.getSlug();
            ResourceType resourceType = job.getResourceType();

            // Clean up ONLY the specific job's HLS directory
            Path jobHlsDir = Paths.get("hls-v2", resourceType.toString().toLowerCase(), slug, jobId);
            if (Files.exists(jobHlsDir)) {
                deleteDirectoryRecursively(jobHlsDir);
                log.info("Cleaned up job-specific HLS directory: {}", jobHlsDir);
            }

            // Clean up ONLY the specific job's upload directory (if it exists)
            Path jobUploadDir = Paths.get("upload-v2", jobId);
            if (Files.exists(jobUploadDir)) {
                deleteDirectoryRecursively(jobUploadDir);
                log.info("Cleaned up job-specific upload directory: {}", jobUploadDir);
            }

            // Clean up empty parent directories only if they're empty
            cleanupEmptyParentDirectories(jobHlsDir);
            cleanupEmptyParentDirectories(jobUploadDir);

        } catch (Exception e) {
            log.error("Error cleaning up directories for job {}: {}", job.getJobId(), e.getMessage());
        }
    }

    /**
     * Clean up empty parent directories recursively
     */
    private void cleanupEmptyParentDirectories(Path startPath) {
        try {
            Path parent = startPath.getParent();
            while (parent != null && Files.exists(parent)) {
                // Only delete if directory is empty
                try (var files = Files.list(parent)) {
                    if (files.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                        log.debug("Cleaned up empty parent directory: {}", parent);
                        parent = parent.getParent();
                    } else {
                        // Directory is not empty, stop here
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error cleaning up parent directories: {}", e.getMessage());
        }
    }

    private void deleteDirectoryRecursively(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files first, then directories
                        .forEach(this::deleteFile);
            }
        } catch (IOException e) {
            log.warn("Error deleting directory {}: {}", directory, e.getMessage());
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Error deleting {}: {}", path, e.getMessage());
        }
    }


    /**
     * Delete a master playlist and all its segments from both R2 and MongoDB by job ID
     */
    public boolean deleteMasterAndSegmentsByJobId(String jobId) {
        try {
            log.info("Starting deletion of master and segments for job ID: {}", jobId);

            // Find the master playlist record by job ID
            Optional<MasterPlaylistRecord> masterOpt = masterPlaylistRecordRepository.findByJobId(jobId);

            if (masterOpt.isEmpty()) {
                log.warn("Master playlist record not found for job ID: {}", jobId);
                return false;
            }

            MasterPlaylistRecord master = masterOpt.get();
            String masterId = master.getId();

            // 1. Delete from R2 cloud storage
            boolean cloudDeleted = false;
            if (master.getMasterKey() != null && !master.getMasterKey().isEmpty()) {
                // Extract prefix from master key (remove /master.m3u8 part)
                String prefix = master.getMasterKey();
                if (prefix.endsWith("/master.m3u8")) {
                    prefix = prefix.substring(0, prefix.lastIndexOf("/master.m3u8"));
                }

                cloudDeleted = storage.deleteFilesByPrefix(prefix);
                log.info("Cloud storage deletion for prefix '{}': {}", prefix, cloudDeleted ? "SUCCESS" : "FAILED");
            } else {
                log.warn("No master key found for job ID: {}, skipping cloud deletion", jobId);
                cloudDeleted = true; // Consider it successful if there's nothing to delete
            }

            // 2. Delete variant segments from MongoDB
            List<VariantSegment> segments = variantSegmentRepository.findByMasterId(masterId);
            if (!segments.isEmpty()) {
                variantSegmentRepository.deleteAll(segments);
                log.info("Deleted {} variant segments from MongoDB for job ID: {}", segments.size(), jobId);
            } else {
                log.info("No variant segments found in MongoDB for job ID: {}", jobId);
            }

            // 3. Delete master playlist record from MongoDB
            masterPlaylistRecordRepository.delete(master);
            log.info("Deleted master playlist record from MongoDB for job ID: {}", jobId);

            // 4. Delete associated job if it exists
            jobRepository.findByJobId(jobId).ifPresent(job -> {
                jobRepository.delete(job);
                log.info("Deleted associated job for job ID: {}", jobId);
            });

            boolean success = cloudDeleted; // Overall success depends on cloud deletion
            log.info("Master and segments deletion for job ID '{}': {}", jobId, success ? "COMPLETED" : "COMPLETED WITH ERRORS");

            return success;

        } catch (Exception e) {
            log.error("Failed to delete master and segments for job ID: {} - Error: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if a job has been cancelled
     */
    public boolean isJobCancelled(String jobId) {
        if (jobId == null) {
            return false;
        }
        
        try {
            Optional<Job> jobOpt = jobRepository.findByJobId(jobId);
            return jobOpt.map(job -> job.getStatus() == JobStatus.CANCELLED).orElse(false);
        } catch (Exception e) {
            log.warn("Error checking if job {} was cancelled: {}", jobId, e.getMessage());
            return false;
        }
    }


    /**
     * Delete a specific job
     */
    public boolean deleteJob(String jobId) {
        try {
            log.info("Starting deletion process for job: {}", jobId);
            
            // Find the job
            Optional<Job> jobOpt = jobRepository.findByJobId(jobId);
            if (jobOpt.isEmpty()) {
                log.warn("Job not found for deletion: {}", jobId);
                return false;
            }
            
            Job job = jobOpt.get();
            
            // Stop FFmpeg process if running
            if (job.getStatus() == JobStatus.UPLOADING || 
                job.getStatus() == JobStatus.ENCODING || 
                job.getStatus() == JobStatus.UPLOADING_TO_CLOUD_STORAGE) {
                encoder.stopProcess(jobId);
                log.info("Stopped FFmpeg process for job: {}", jobId);
            }
            
            // Clean up local directories
            cleanupJobDirectories(job);
            
            // Delete from database
            jobRepository.delete(job);
            
            log.info("Successfully deleted job: {}", jobId);
            return true;
            
        } catch (Exception e) {
            log.error("Error deleting job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Delete all jobs (with optional force parameter)
     */
    public Map<String, Object> deleteAllJobs(boolean force) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Starting deletion of all jobs (force: {})", force);
            
            List<Job> allJobs = jobRepository.findAll();
            int totalJobs = allJobs.size();
            int deletedCount = 0;
            
            Map<String, Integer> summary = new HashMap<>();
            summary.put("total", totalJobs);
            summary.put("deleted", 0);
            summary.put("skipped", 0);
            
            for (Job job : allJobs) {
                try {
                    // Check if job can be deleted
                    if (!force && (job.getStatus() == JobStatus.UPLOADING || 
                                  job.getStatus() == JobStatus.ENCODING || 
                                  job.getStatus() == JobStatus.UPLOADING_TO_CLOUD_STORAGE)) {
                        log.info("Skipping active job: {} (Status: {})", job.getJobId(), job.getStatus());
                        summary.put("skipped", summary.get("skipped") + 1);
                        continue;
                    }
                    
                    // Stop FFmpeg process if running
                    if (job.getStatus() == JobStatus.UPLOADING || 
                        job.getStatus() == JobStatus.ENCODING || 
                        job.getStatus() == JobStatus.UPLOADING_TO_CLOUD_STORAGE) {
                        encoder.stopProcess(job.getJobId());
                    }
                    
                    // Clean up local directories
                    cleanupJobDirectories(job);
                    
                    // Delete from database
                    jobRepository.delete(job);
                    deletedCount++;
                    summary.put("deleted", deletedCount);
                    
                    log.debug("Deleted job: {} (Title: {}, Status: {})", 
                            job.getJobId(), job.getTitle(), job.getStatus());
                    
                } catch (Exception e) {
                    log.error("Error deleting job {}: {}", job.getJobId(), e.getMessage());
                    summary.put("skipped", summary.get("skipped") + 1);
                }
            }
            
            result.put("totalJobsDeleted", deletedCount);
            result.put("summary", summary);
            
            log.info("Completed deletion of all jobs. Total deleted: {}, Skipped: {}", 
                    deletedCount, summary.get("skipped"));
            
        } catch (Exception e) {
            log.error("Error during bulk job deletion: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }
        
        return result;
    }



    /**
     * Clean up completed, failed, and cancelled jobs
     */
    public Map<String, Object> cleanupCompletedJobs() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Starting cleanup of completed and failed jobs");
            
            // Get jobs that can be safely deleted
            List<Job> jobsToDelete = jobRepository.findByStatusIn(
                List.of(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)
            );
            
            int deletedCount = 0;
            
            Map<String, Integer> summary = new HashMap<>();
            summary.put("completed", 0);
            summary.put("failed", 0);
            summary.put("cancelled", 0);
            
            for (Job job : jobsToDelete) {
                try {
                    // Clean up local directories
                    cleanupJobDirectories(job);
                    
                    // Delete from database
                    jobRepository.delete(job);
                    deletedCount++;
                    
                    // Update summary
                    switch (job.getStatus()) {
                        case COMPLETED:
                            summary.put("completed", summary.get("completed") + 1);
                            break;
                        case FAILED:
                            summary.put("failed", summary.get("failed") + 1);
                            break;
                        case CANCELLED:
                            summary.put("cancelled", summary.get("cancelled") + 1);
                            break;
                        case PENDING:
                        case UPLOADING:
                        case ENCODING:
                        case UPLOADING_TO_CLOUD_STORAGE:
                        case DOWNLOADING:
                            // Active jobs, do nothing here
                            log.warn("Unexpected job status {} in cleanup method for job: {}", job.getStatus(), job.getJobId());
                            break;
                    }
                    
                    log.debug("Cleaned up job: {} (Title: {}, Status: {})", 
                            job.getJobId(), job.getTitle(), job.getStatus());
                    
                } catch (Exception e) {
                    log.error("Error cleaning up job {}: {}", job.getJobId(), e.getMessage());
                }
            }
            
            // Get count of active jobs that were preserved
            List<Job> activeJobs = jobRepository.findActiveJobs();
            int activeJobsPreserved = activeJobs.size();
            
            result.put("jobsDeleted", deletedCount);
            result.put("summary", summary);
            result.put("activeJobsPreserved", activeJobsPreserved);
            
            log.info("Cleanup completed. Jobs deleted: {}, Active jobs preserved: {}", 
                    deletedCount, activeJobsPreserved);
            
        } catch (Exception e) {
            log.error("Error during job cleanup: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Update progress percentage and downloaded size for jobs in the DOWNLOADING status
     */
    public void updateJobDownloadProgress(String jobId, int percent, long totalBytes, long downloaded) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setProgressPercentage(percent);
            job.setFileSize(totalBytes);
            // Optionally store downloaded so far (or add a metadata field)
            if (job.getMetadata() == null) job.setMetadata(new java.util.HashMap<>());
            job.getMetadata().put("downloadedBytes", downloaded);
            job.setLastProgressUpdate(java.time.LocalDateTime.now());
            jobRepository.save(job);
            eventService.dispatchJobProgressionToSEEClients();
        });
    }
}
