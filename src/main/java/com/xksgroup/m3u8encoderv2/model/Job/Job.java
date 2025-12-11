package com.xksgroup.m3u8encoderv2.model.Job;

import com.xksgroup.m3u8encoderv2.model.RequestIssuer;
import com.xksgroup.m3u8encoderv2.model.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "jobs")
public class Job {
    @Id
    private String id;
    
    private String jobId;
    private String slug;
    private String title;
    private ResourceType resourceType;
    private JobStatus status;
    
    // Progress tracking (current variant)
    private int totalSegments;
    private int completedSegments;
    private int failedSegments;
    private int uploadingSegments;
    private int pendingSegments;
    private int variantProgressPercentage;


    // Total progress tracking (all variants)
    private int totalSegmentsAllVariants;
    private int completedSegmentsAllVariants;
    private int failedSegmentsAllVariants;
    private int uploadingSegmentsAllVariants;
    private int pendingSegmentsAllVariants;
    
    // Enhanced progress tracking
    private int progressPercentage;
    private Long elapsedTimeSeconds;
    private Long remainingTimeSeconds;
    private Long estimatedTotalTimeSeconds;
    private LocalDateTime lastProgressUpdate;
    
    // Variant tracking
    private int currentVariant;
    private int totalVariants;
    private String currentVariantName;
    private String currentVariantDescription;
    
    // File information
    private String originalFilename;
    private long fileSize;
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
    private Long encodingDurationSeconds;
    private Long uploadDurationSeconds;
    private Long totalDurationSeconds;

    // Accélération utilisée (ex: GPU NVENC, CPU libx264)
    private String acceleration;
    
    // Variant information
    private Map<String, Object> variants;

    private RequestIssuer userAgent;
    
    // Metadata
    private Map<String, Object> metadata;

}
