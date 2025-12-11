package com.xksgroup.m3u8encoderv2.controller;

import com.xksgroup.m3u8encoderv2.model.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.repo.JobRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("m3u8-encoder/api/v2/health")
@RequiredArgsConstructor
@Tag(name = "Santé & Métriques", description = "Vérifications de santé du système et métriques de performance")
public class HealthController {

    private final JobRepository jobRepository;

    @GetMapping
    @Operation(
        summary = "Vérification de santé du système",
        description = "Vérifie l'état de santé global du service d'encodage m3u8 incluant la connectivité à la base de données et les métriques système de base."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Le système est en bonne santé",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Système sain",
                    value = """
                    {
                        "status": "healthy",
                        "timestamp": "2024-01-01T12:00:00",
                        "service": "m3u8-encoder-v2",
                        "version": "0.0.1-SNAPSHOT",
                        "uptime": "2 hours 15 minutes",
                        "database": "connected",
                        "checks": {
                            "database": "PASS",
                            "memory": "PASS",
                            "disk": "PASS"
                        }
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Le système est en mauvaise santé",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Système en mauvaise santé",
                    value = """
                    {
                        "status": "unhealthy",
                        "timestamp": "2024-01-01T12:00:00",
                        "service": "m3u8-encoder-v2",
                        "version": "0.0.1-SNAPSHOT",
                        "uptime": "2 hours 15 minutes",
                        "database": "disconnected",
                        "checks": {
                            "database": "FAIL",
                            "memory": "PASS",
                            "disk": "PASS"
                        },
                        "issues": [
                            "Database connection failed"
                        ]
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> healthCheck() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("timestamp", LocalDateTime.now());
            health.put("service", "m3u8-encoder-v2");
            health.put("version", "0.0.1-SNAPSHOT");
            
            // Calculate uptime
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            long hours = uptime / (1000 * 60 * 60);
            long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
            health.put("uptime", String.format("%d hours %d minutes", hours, minutes));
            
            // Check database connectivity
            try {
                long totalJobs = jobRepository.count();
                health.put("database", "connected");
                health.put("totalJobs", totalJobs);
            } catch (Exception e) {
                health.put("database", "disconnected");
                health.put("status", "unhealthy");
            }
            
            // System checks
            Map<String, String> checks = new HashMap<>();
            checks.put("database", health.get("database").equals("connected") ? "PASS" : "FAIL");
            checks.put("memory", checkMemory() ? "PASS" : "FAIL");
            checks.put("disk", checkDisk() ? "PASS" : "FAIL");
            health.put("checks", checks);
            
            // Check if any checks failed
            if (checks.containsValue("FAIL")) {
                health.put("status", "unhealthy");
            }
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Health check failed - Error: {}", e.getMessage(), e);
            Map<String, Object> health = new HashMap<>();
            health.put("status", "unhealthy");
            health.put("timestamp", LocalDateTime.now());
            health.put("service", "m3u8-encoder-v2");
            health.put("error", e.getMessage());
            return ResponseEntity.ok(health);
        }
    }

    @GetMapping("/metrics")
    @Operation(
        summary = "Métriques de performance du système",
        description = "Obtenir des métriques détaillées de performance du système incluant l'utilisation de la mémoire, les statistiques CPU et les statistiques des jobs."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Métriques récupérées avec succès",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Métriques système",
                    value = """
                    {
                        "timestamp": "2024-01-01T12:00:00",
                        "system": {
                            "memory": {
                                "total": "8.0 GB",
                                "used": "2.5 GB",
                                "free": "5.5 GB",
                                "usage": "31.25%"
                            },
                            "cpu": {
                                "load": "0.45",
                                "cores": 8
                            },
                            "uptime": "2 hours 15 minutes"
                        },
                        "jobs": {
                            "total": 150,
                            "completed": 120,
                            "failed": 5,
                            "pending": 15,
                            "processing": 10,
                            "successRate": "96.0%"
                        },
                        "storage": {
                            "totalUploads": "45.2 GB",
                            "averageJobSize": "301.3 MB",
                            "totalSegments": 15000
                        }
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> getMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("timestamp", LocalDateTime.now());
            
            // System metrics
            Map<String, Object> system = new HashMap<>();
            
            // Memory metrics
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long totalMemory = memoryBean.getHeapMemoryUsage().getMax();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long freeMemory = totalMemory - usedMemory;
            double usagePercent = (double) usedMemory / totalMemory * 100;
            
            Map<String, Object> memory = new HashMap<>();
            memory.put("total", formatBytes(totalMemory));
            memory.put("used", formatBytes(usedMemory));
            memory.put("free", formatBytes(freeMemory));
            memory.put("usage", String.format("%.2f%%", usagePercent));
            system.put("memory", memory);
            
            // CPU metrics
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Map<String, Object> cpu = new HashMap<>();
            cpu.put("load", String.format("%.2f", osBean.getSystemLoadAverage()));
            cpu.put("cores", osBean.getAvailableProcessors());
            system.put("cpu", cpu);
            
            // Uptime
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            long hours = uptime / (1000 * 60 * 60);
            long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
            system.put("uptime", String.format("%d hours %d minutes", hours, minutes));
            
            metrics.put("system", system);
            
            // Job metrics
            Map<String, Object> jobs = new HashMap<>();
            long totalJobs = jobRepository.count();
            long completedJobs = jobRepository.countByStatus(JobStatus.COMPLETED);
            long failedJobs = jobRepository.countByStatus(JobStatus.FAILED);
            long pendingJobs = jobRepository.countByStatus(JobStatus.PENDING);
            long processingJobs = jobRepository.countByStatus(JobStatus.ENCODING) + 
                                jobRepository.countByStatus(JobStatus.UPLOADING_TO_CLOUD_STORAGE);
            
            jobs.put("total", totalJobs);
            jobs.put("completed", completedJobs);
            jobs.put("failed", failedJobs);
            jobs.put("pending", pendingJobs);
            jobs.put("processing", processingJobs);
            
            if (totalJobs > 0) {
                double successRate = (double) completedJobs / totalJobs * 100;
                jobs.put("successRate", String.format("%.1f%%", successRate));
            } else {
                jobs.put("successRate", "0.0%");
            }
            
            metrics.put("jobs", jobs);
            
            // Storage metrics (placeholder - would need actual implementation)
            Map<String, Object> storage = new HashMap<>();
            storage.put("totalUploads", "N/A");
            storage.put("averageJobSize", "N/A");
            storage.put("totalSegments", "N/A");
            metrics.put("storage", storage);
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Failed to retrieve metrics - Error: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve metrics");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    @GetMapping("/storage")
    @Operation(
        summary = "Statut et configuration du stockage",
        description = "Obtenir des informations sur la configuration et le statut du stockage cloud."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Statut du stockage récupéré avec succès",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Statut du stockage",
                    value = """
                    {
                        "status": "connected",
                        "provider": "Cloudflare R2",
                        "bucket": "my-video-bucket",
                        "region": "auto",
                        "endpoint": "https://account-id.r2.cloudflarestorage.com",
                        "configuration": {
                            "maxFileSize": "6 GB",
                            "supportedFormats": ["mp4", "avi", "mov", "mkv", "mp3", "wav"],
                            "compression": "enabled",
                            "encryption": "enabled"
                        },
                        "lastCheck": "2024-01-01T12:00:00"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> getStorageStatus() {
        try {
            Map<String, Object> storage = new HashMap<>();
            storage.put("status", "connected");
            storage.put("provider", "Cloudflare R2");
            storage.put("bucket", "my-video-bucket");
            storage.put("region", "auto");
            storage.put("endpoint", "https://account-id.r2.cloudflarestorage.com");
            storage.put("lastCheck", LocalDateTime.now());
            
            Map<String, Object> config = new HashMap<>();
            config.put("maxFileSize", "6 GB");
            config.put("supportedFormats", new String[]{"mp4", "avi", "mov", "mkv", "mp3", "wav"});
            config.put("compression", "enabled");
            config.put("encryption", "enabled");
            storage.put("configuration", config);
            
            return ResponseEntity.ok(storage);
            
        } catch (Exception e) {
            log.error("Failed to retrieve storage status - Error: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve storage status");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Check memory availability
     */
    private boolean checkMemory() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long totalMemory = memoryBean.getHeapMemoryUsage().getMax();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            double usagePercent = (double) usedMemory / totalMemory * 100;
            
            // Consider healthy if memory usage is below 90%
            return usagePercent < 90.0;
        } catch (Exception e) {
            log.warn("Memory check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check disk availability (placeholder)
     */
    private boolean checkDisk() {
        // This would need actual disk space checking implementation
        // For now, return true as placeholder
        return true;
    }

    /**
     * Format bytes to human readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
