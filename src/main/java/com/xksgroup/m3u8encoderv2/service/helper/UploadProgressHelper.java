package com.xksgroup.m3u8encoderv2.service.helper;

import com.xksgroup.m3u8encoderv2.service.EventService;
import com.xksgroup.m3u8encoderv2.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for managing upload progress tracking and SSE event dispatching
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadProgressHelper {

    private final EventService eventService;
    private JobService jobService;

    // Throttling: Track last SSE update time per job (3 seconds minimum)
    private final ConcurrentHashMap<String, LocalDateTime> lastSseUpdate = new ConcurrentHashMap<>();
    private static final int SSE_THROTTLE_SECONDS = 3;

    /**
     * Set JobService reference for circular dependency
     */
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Extract job ID from key prefix (resourceType/slug/jobId)
     */
    public String extractJobIdFromKeyPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            return null;
        }
        String[] parts = keyPrefix.split("/");
        if (parts.length >= 3) {
            return parts[parts.length - 1]; // Last part should be jobId
        }
        return null;
    }

    /**
     * Update upload progress and dispatch SSE event
     */
    private void updateUploadProgress(String jobId, String message, int percentage) {
        if (jobId == null || jobService == null) {
            return;
        }
        
        try {
            // Update job progress in database
            jobService.updateJobProgressWithVariant(jobId, percentage, 0, 0, 0, 0, 
                "Upload", message);
            
            // Dispatch SSE event
            eventService.dispatchJobProgressionToSEEClients();
            
            log.info("Upload progress for job {}: {} ({}%)", jobId, message, percentage);
            
        } catch (Exception e) {
            log.warn("Failed to update upload progress for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     *  Segment counts with smart SSE throttling (max once every 3 seconds)
     */
    public void updateSegmentCounts(String jobId, int total, int completed, int failed, int uploading, int pending) {
        if (jobId == null || jobService == null) {
            return;
        }
        
        try {
            // Always update the database
            jobService.updateSegmentCounts(jobId, total, completed, failed, uploading, pending);
            
            // Send SSE only if throttling allows it
            if (shouldSendSSE(jobId)) {
                eventService.dispatchJobProgressionToSEEClients();
                lastSseUpdate.put(jobId, LocalDateTime.now());
                log.debug("Updated segment counts with SSE for job {}: {}/{}/{}/{}/{}", jobId, total, completed, failed, uploading, pending);
            } else {
                log.debug("Updated segment counts silently for job {}: {}/{}/{}/{}/{}", jobId, total, completed, failed, uploading, pending);
            }
        } catch (Exception e) {
            log.warn("Failed to update segment counts for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Immediate SSE update
     */
    public void updateSegmentCountsImmediate(String jobId, int total, int completed, int failed, int uploading, int pending) {
        if (jobId == null || jobService == null) {
            return;
        }
        
        try {
            jobService.updateSegmentCounts(jobId, total, completed, failed, uploading, pending);
            eventService.dispatchJobProgressionToSEEClients();
            lastSseUpdate.put(jobId, LocalDateTime.now());
            log.debug("Updated segment counts with immediate SSE for job {}: {}/{}/{}/{}/{}", jobId, total, completed, failed, uploading, pending);
        } catch (Exception e) {
            log.warn("Failed to update segment counts for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Check if we should send SSE based on throttling rules
     */
    private boolean shouldSendSSE(String jobId) {
        LocalDateTime lastUpdate = lastSseUpdate.get(jobId);
        if (lastUpdate == null) {
            return true;
        }
        
        LocalDateTime now = LocalDateTime.now();
        return java.time.Duration.between(lastUpdate, now).getSeconds() >= SSE_THROTTLE_SECONDS;
    }


    /**
     * Update progress for variant upload start
     */
    public void updateVariantStart(String jobId, String variantName, int segmentCount) {
        String message = String.format("Téléchargement de %d segments pour la variante %s", segmentCount, variantName);
        updateUploadProgress(jobId, message, 0);
    }


    /**
     * Update progress for upload error
     */
    public void updateUploadError(String jobId, String errorMessage) {
        updateUploadProgress(jobId, "Échec du téléchargement : " + errorMessage, 0);
    }
}