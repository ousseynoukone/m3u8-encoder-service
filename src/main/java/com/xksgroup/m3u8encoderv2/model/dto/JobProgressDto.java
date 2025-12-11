package com.xksgroup.m3u8encoderv2.model.dto;

import com.xksgroup.m3u8encoderv2.model.Job.Job;
import com.xksgroup.m3u8encoderv2.model.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.model.RequestIssuer;
import com.xksgroup.m3u8encoderv2.model.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobProgressDto {
    
    private String id;
    private String jobId;
    private String slug;
    private String title;
    private ResourceType resourceType;
    private JobStatus status;
    
    // Progress tracking
    private int totalSegments;
    private int completedSegments;
    private int failedSegments;
    private int uploadingSegments;
    private int pendingSegments;
    
    // Enhanced progress tracking
    private int progressPercentage;
    private String remainingTime;
    private String estimatedTotalTime;
    private LocalDateTime lastProgressUpdate;
    
    // Variant tracking
    private int currentVariant;
    private int totalVariants;
    private String currentVariantName;
    private String currentVariantDescription;
    private String variantProgress;
    
    // File information
    private String originalFilename;
    private String fileSizeFormatted;
    private String contentType;
    
    // Processing details
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    
    // Error information
    private String errorMessage;
    private String errorDetails;
    
    // Output information
    private String masterPlaylistUrl;
    private String securePlaybackUrl;
    private String keyPrefix;
    
    // Performance metrics
    private String encodingDuration;
    private String totalDuration;

    // Accélération utilisée
    private String acceleration;
    
    // Variant information
    private Map<String, Object> variants;
    
    // User/Client information
    private RequestIssuer userAgent;
    
    // Metadata
    private Map<String, Object> metadata;
    
    // Helper methods for time formatting
    public static String formatDuration(Long seconds) {
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
    
    public static String formatFileSize(Long bytes) {
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
        
        return String.format("%.1f %s", size, units[unitIndex]);
    }
    
    // Static factory method to create DTO from Job entity
    public static JobProgressDto fromJob(Job job) {
        return JobProgressDto.builder()
                .id(job.getId())
                .jobId(job.getJobId())
                .slug(job.getSlug())
                .title(job.getTitle())
                .resourceType(job.getResourceType())
                .status(job.getStatus())
                
                // Progress tracking - simplified and more intuitive
                .totalSegments(job.getTotalSegments())
                .completedSegments(job.getCompletedSegments())
                .failedSegments(job.getFailedSegments())
                .uploadingSegments(job.getUploadingSegments())
                .pendingSegments(job.getPendingSegments())
                
                // Enhanced progress tracking
                .progressPercentage(job.getProgressPercentage())
                .remainingTime(formatDuration(job.getRemainingTimeSeconds()))
                .estimatedTotalTime(formatDuration(job.getEstimatedTotalTimeSeconds()))
                .lastProgressUpdate(job.getLastProgressUpdate())
                
                // Variant tracking - improved descriptions
                .currentVariant(job.getCurrentVariant())
                .totalVariants(job.getTotalVariants())
                .currentVariantName(job.getCurrentVariantName())
                .currentVariantDescription(generateUserFriendlyDescription(job))
                .variantProgress(job.getCurrentVariant() > 0 && job.getTotalVariants() > 0 ? 
                    String.format("%d/%d", job.getCurrentVariant(), job.getTotalVariants()) : null)
                
                // File information
                .originalFilename(job.getOriginalFilename())
                .fileSizeFormatted(formatFileSize(job.getFileSize()))
                .contentType(job.getContentType())
                
                // Processing details
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .failedAt(job.getFailedAt())
                
                // Error information
                .errorMessage(job.getErrorMessage())
                .errorDetails(job.getErrorDetails())
                
                // Output information
                .masterPlaylistUrl(job.getMasterPlaylistUrl())
                .securePlaybackUrl(job.getSecurePlaybackUrl())
                .keyPrefix(job.getKeyPrefix())
                
                // Performance metrics
                .encodingDuration(formatDuration(job.getEncodingDurationSeconds()))
                .totalDuration(formatDuration(job.getTotalDurationSeconds()))
                .acceleration(job.getAcceleration())
                
                // Variant information
                .variants(job.getVariants())

                .userAgent(job.getUserAgent())
                
                // Metadata
                .metadata(job.getMetadata())
                .build();
    }
    
    /**
     * Generate user-friendly description based on job status and progress
     */
    private static String generateUserFriendlyDescription(Job job) {
        switch (job.getStatus()) {
            case PENDING:
                return "Waiting to start processing...";
                
            case UPLOADING:
                return "Uploading file to server...";
                
            case ENCODING:
                if (job.getCurrentVariant() > 0 && job.getTotalVariants() > 0) {
                    return String.format("Encoding variant %d/%d (%s)", 
                        job.getCurrentVariant(), job.getTotalVariants(), job.getCurrentVariantName());
                }
                return "Converting to streaming format...";
                
            case UPLOADING_TO_CLOUD_STORAGE:
                if (job.getTotalSegments() > 0) {
                    if (job.getCompletedSegments() == job.getTotalSegments()) {
                        return "Upload completed! Finalizing...";
                    } else if (job.getCompletedSegments() > 0) {
                        return String.format("Uploading segments... %d/%d completed", 
                            job.getCompletedSegments(), job.getTotalSegments());
                    } else {
                        return "Preparing segments for upload...";
                    }
                }
                return "Uploading to cloud storage...";
                
            case COMPLETED:
                return "Processing completed successfully!";
                
            case FAILED:
                return "Processing failed: " + (job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error");
                
            case CANCELLED:
                return "Processing was cancelled";
                
            default:
                return "Processing...";
        }
    }
}
