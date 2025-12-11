package com.xksgroup.m3u8encoderv2.service.helper;

import com.xksgroup.m3u8encoderv2.service.EventService;
import com.xksgroup.m3u8encoderv2.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper class for managing upload progress tracking and SSE event dispatching
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadProgressHelper {

    private final EventService eventService;
    private JobService jobService;

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
                    "Upload", message,0);

            // Dispatch SSE event immediately
            eventService.dispatchJobProgressionToSEEClients();

            log.info("Upload progress for job {}: {} ({}%)", jobId, message, percentage);

        } catch (Exception e) {
            log.warn("Failed to update upload progress for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Update segment counts and dispatch SSE event immediately
     */
    public void updateSegmentCounts(String jobId, int total, int completed, int failed, int uploading, int pending) {
        if (jobId == null || jobService == null) {
            return;
        }

        try {
            jobService.updateSegmentCounts(jobId, total, completed, failed, uploading, pending);
            eventService.dispatchJobProgressionToSEEClients();
            log.debug("Updated segment counts for job {}: {}/{}/{}/{}/{}", jobId, total, completed, failed, uploading, pending);
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
            log.debug("Updated segment counts with immediate SSE for job {}: {}/{}/{}/{}/{}", jobId, total, completed, failed, uploading, pending);
        } catch (Exception e) {
            log.warn("Failed to update segment counts for job {}: {}", jobId, e.getMessage());
        }
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
