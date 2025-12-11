package com.xksgroup.m3u8encoderv2.model.dto;

import com.xksgroup.m3u8encoderv2.model.Job.Job;
import com.xksgroup.m3u8encoderv2.model.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.model.RequestIssuer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Client-friendly job DTO with simplified, intuitive structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientFriendlyJobDto {
    
    // Basic Info
    private String id;
    private String jobId;
    private String title;
    private String slug;
    private String resourceType;
    
    // Status & Progress (simplified)
    private String status;
    private String statusMessage;
    private int progressPercentage;
    private String progressDescription;
    
    // Upload Progress (when applicable)
    private UploadProgress uploadProgress;
    
    // Encoding Progress (when applicable)
    private EncodingProgress encodingProgress;
    
    // File Info
    private String originalFilename;
    private String fileSize;
    private String contentType;
    
    // Timing (simplified - only actual durations)
    private String lastUpdate;
    
    // Results (when completed)
    private String masterPlaylistUrl;
    private String securePlaybackUrl;
    private String keyPrefix;
    
    // Error Info (when failed)
    private String errorMessage;
    private String errorDetails;
    
    // Performance Metrics
    private String encodingDuration;
    private String totalDuration;
    private String acceleration;
    
    // User Info
    private RequestIssuer userAgent;
    
    // Metadata
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadProgress {
        private int total;
        private int completed;
        private int failed;
        private int uploading;
        private int pending;
        private String description;
        private String currentVariant;
        private int totalVariants;
        private String variantDescription;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EncodingProgress {
        private int currentVariant;
        private int totalVariants;
        private String currentVariantName;
        private String currentVariantDescription;
        private String quality;
        private String resolution;
        private String codecs;
        private int progressPercentage;
        private String description;
    }
    
    /**
     * Create client-friendly DTO from Job entity
     */
    public static ClientFriendlyJobDto fromJob(Job job) {
        return ClientFriendlyJobDto.builder()
                .id(job.getId())
                .jobId(job.getJobId())
                .title(job.getTitle())
                .slug(job.getSlug())
                .resourceType(job.getResourceType() != null ? job.getResourceType().name() : null)
                
                .status(mapStatusToClientFriendly(job.getStatus()))
                .statusMessage(generateStatusMessage(job))
                .progressPercentage(job.getProgressPercentage())
                .progressDescription(generateProgressDescription(job))
                
                .uploadProgress(createUploadProgress(job))
                .encodingProgress(createEncodingProgress(job))
                
                .originalFilename(job.getOriginalFilename())
                .fileSize(formatFileSize(job.getFileSize()))
                .contentType(job.getContentType())
                
                .lastUpdate(formatDateTime(job.getLastProgressUpdate()))
                
                .masterPlaylistUrl(job.getMasterPlaylistUrl())
                .securePlaybackUrl(job.getSecurePlaybackUrl())
                .keyPrefix(job.getKeyPrefix())
                
                .errorMessage(job.getErrorMessage())
                .errorDetails(job.getErrorDetails())
                
                .encodingDuration(formatDuration(job.getEncodingDurationSeconds()))
                .totalDuration(formatDuration(job.getTotalDurationSeconds()))
                .acceleration(job.getAcceleration())
                .userAgent(job.getUserAgent())
                .metadata(job.getMetadata())
                .build();
    }
    
    private static String mapStatusToClientFriendly(JobStatus status) {
        if (status == null) return "unknown";
        
        return switch (status) {
            case PENDING -> "waiting";
            case UPLOADING -> "uploading";
            case DOWNLOADING -> "downloading";
            case ENCODING -> "processing";
            case UPLOADING_TO_CLOUD_STORAGE -> "uploading";
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case CANCELLED -> "cancelled";
        };
    }
    
    private static String generateStatusMessage(Job job) {
        if (job.getStatus() == null) return "Statut inconnu";
        
        return switch (job.getStatus()) {
            case PENDING -> "En attente de traitement...";
            case UPLOADING -> "Téléchargement du fichier vers le serveur...";
            case DOWNLOADING -> "Téléchargement du fichier distant...";
            case ENCODING -> "Conversion au format de streaming...";
            case UPLOADING_TO_CLOUD_STORAGE -> {
                if (job.getTotalSegments() > 0) {
                    if (job.getCompletedSegments() == job.getTotalSegments()) {
                        yield "Téléchargement terminé ! Finalisation...";
                    } else if (job.getCompletedSegments() > 0) {
                        yield String.format("Téléchargement des segments... %d/%d terminés", 
                            job.getCompletedSegments(), job.getTotalSegments());
                    } else {
                        yield "Préparation des segments pour le téléchargement...";
                    }
                }
                yield "Téléchargement vers le stockage cloud...";
            }
            case COMPLETED -> "Traitement terminé avec succès !";
            case FAILED -> "Échec du traitement : " + (job.getErrorMessage() != null ? job.getErrorMessage() : "Erreur inconnue");
            case CANCELLED -> "Traitement annulé";
        };
    }
    
    private static String generateProgressDescription(Job job) {
        if (job.getStatus() == JobStatus.UPLOADING_TO_CLOUD_STORAGE && job.getTotalSegments() > 0) {
            if (job.getCompletedSegments() == job.getTotalSegments()) {
                return String.format("Téléchargement terminé : %d/%d segments", 
                    job.getCompletedSegments(), job.getTotalSegments());
            } else if (job.getCompletedSegments() > 0) {
                return String.format("Téléchargement des segments %d-%d sur %d...", 
                    job.getCompletedSegments() + 1, job.getTotalSegments(), job.getTotalSegments());
            } else {
                return String.format("Préparation de %d segments pour le téléchargement...", job.getTotalSegments());
            }
        }
        
        if (job.getStatus() == JobStatus.ENCODING && job.getCurrentVariant() > 0 && job.getTotalVariants() > 0) {
            return String.format("Encodage de la variante %d/%d (%s)", 
                job.getCurrentVariant(), job.getTotalVariants(), job.getCurrentVariantName());
        }
        
        return job.getCurrentVariantDescription();
    }
    
    private static UploadProgress createUploadProgress(Job job) {
        if (job.getTotalSegments() == 0) {
            return null;
        }
        
        String description;
        String variantDescription = "";
        
        // Use total segments across all variants if available
        int totalSegments = job.getTotalSegmentsAllVariants() > 0 ? job.getTotalSegmentsAllVariants() : job.getTotalSegments();
        int completedSegments = job.getCompletedSegmentsAllVariants() > 0 ? job.getCompletedSegmentsAllVariants() : job.getCompletedSegments();
        int failedSegments = job.getFailedSegmentsAllVariants() > 0 ? job.getFailedSegmentsAllVariants() : job.getFailedSegments();
        int uploadingSegments = job.getUploadingSegmentsAllVariants() > 0 ? job.getUploadingSegmentsAllVariants() : job.getUploadingSegments();
        int pendingSegments = job.getPendingSegmentsAllVariants() > 0 ? job.getPendingSegmentsAllVariants() : job.getPendingSegments();
        
        if (completedSegments == totalSegments) {
            description = String.format("%d/%d segments téléchargés", 
                completedSegments, totalSegments);
        } else if (completedSegments > 0) {
            description = String.format("Segments %d-%d sur %d en cours...", 
                completedSegments + 1, totalSegments, totalSegments);
        } else {
            description = String.format("Préparation de %d segments...", totalSegments);
        }
        
        // Add variant information if available
        if (job.getCurrentVariantName() != null && !job.getCurrentVariantName().isEmpty()) {
            variantDescription = String.format("Variante %s (%d/%d)", 
                job.getCurrentVariantName(), 
                job.getCurrentVariant(), 
                job.getTotalVariants());
        }
        
        return UploadProgress.builder()
                .total(totalSegments)
                .completed(completedSegments)
                .failed(failedSegments)
                .uploading(uploadingSegments)
                .pending(pendingSegments)
                .description(description)
                .currentVariant(job.getCurrentVariantName())
                .totalVariants(job.getTotalVariants())
                .variantDescription(variantDescription)
                .build();
    }
    
    private static EncodingProgress createEncodingProgress(Job job) {
        if (job.getStatus() != JobStatus.ENCODING || job.getCurrentVariant() == 0) {
            return null;
        }
        
        String description = String.format("Encodage de la variante %d/%d (%s)", 
            job.getCurrentVariant(), 
            job.getTotalVariants(), 
            job.getCurrentVariantName());
        
        // Extract quality info from variant name if available
        String quality = extractQualityFromVariantName(job.getCurrentVariantName());
        String resolution = extractResolutionFromVariantName(job.getCurrentVariantName());
        
        return EncodingProgress.builder()
                .currentVariant(job.getCurrentVariant())
                .totalVariants(job.getTotalVariants())
                .currentVariantName(job.getCurrentVariantName())
                .currentVariantDescription(job.getCurrentVariantDescription())
                .quality(quality)
                .resolution(resolution)
                .codecs("H.264, AAC") // Default codecs
                .progressPercentage(job.getVariantProgressPercentage())
                .description(description)
                .build();
    }
    
    private static String extractQualityFromVariantName(String variantName) {
        if (variantName == null) return "Unknown";
        
        if (variantName.contains("1080p")) return "1080p";
        if (variantName.contains("720p")) return "720p";
        if (variantName.contains("480p")) return "480p";
        if (variantName.contains("360p")) return "360p";

        return variantName; // Return as-is if no quality detected
    }
    
    private static String extractResolutionFromVariantName(String variantName) {
        if (variantName == null) return "Unknown";
        
        if (variantName.contains("1080p")) return "1920x1080";
        if (variantName.contains("720p")) return "1280x720";
        if (variantName.contains("480p")) return "854x480";
        if (variantName.contains("360p")) return "640x360";

        return "Unknown";
    }
    
    private static String formatDuration(Long seconds) {
        if (seconds == null || seconds <= 0) return "0s";
        
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
    
    private static String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024.0 && unitIndex < units.length - 1) {
            size /= 1024.0;
            unitIndex++;
        }
        
        return String.format("%.1f %s", size, units[unitIndex]);
    }
    
    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
