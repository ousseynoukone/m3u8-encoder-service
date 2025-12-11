package com.xksgroup.m3u8encoderv2.controller;

import com.xksgroup.m3u8encoderv2.model.Job.Job;
import com.xksgroup.m3u8encoderv2.model.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.model.ResourceType;
import com.xksgroup.m3u8encoderv2.repo.JobRepository;
import com.xksgroup.m3u8encoderv2.repo.MasterPlaylistRecordRepository;
import com.xksgroup.m3u8encoderv2.repo.VariantSegmentRepository;
import com.xksgroup.m3u8encoderv2.service.JobService;
import com.xksgroup.m3u8encoderv2.service.R2StorageService;
import com.xksgroup.m3u8encoderv2.service.helper.CleanDirectory;
import com.xksgroup.m3u8encoderv2.model.dto.JobProgressDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
@RequestMapping("m3u8-encoder/api/v2/jobs")
@RequiredArgsConstructor
@Tag(name = "Gestion des Jobs", description = "Gérer et surveiller les jobs de traitement vidéo")
public class JobController {

    private final JobRepository jobRepository;
    private final MasterPlaylistRecordRepository masterPlaylistRecordRepository;
    private final VariantSegmentRepository variantSegmentRepository;
    private final JobService jobService;
    private final R2StorageService r2StorageService;
    private final CleanDirectory cleanDirectory;

    @GetMapping
    @Operation(
        summary = "Lister tous les jobs avec pagination et filtrage",
        description = "Récupère une liste paginée de tous les jobs de traitement vidéo avec filtrage optionnel par statut et type de ressource."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Jobs récupérés avec succès",
            content = @Content(
                mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = JobProgressDto.class)),
                examples = @ExampleObject(
                    name = "Liste des jobs",
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
            @Parameter(description = "Numéro de page (basé sur 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Taille de la page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Filtrer par statut du job", example = "COMPLETED")
            @RequestParam(required = false) JobStatus status,
            
            @Parameter(description = "Filtrer par type de ressource", example = "VIDEO")
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
        summary = "Obtenir les informations d'un job par ID",
        description = "Récupère les informations détaillées sur un job spécifique incluant la progression, le timing et le statut."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Informations du job récupérées avec succès",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = JobProgressDto.class),
                examples = @ExampleObject(
                    name = "Détails du job",
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
        summary = "Obtenir tous les jobs actifs",
        description = "Récupère une liste de tous les jobs actuellement en cours de traitement (PENDING, UPLOADING, ENCODING, UPLOADING_TO_CLOUD_STORAGE) avec des informations de progression détaillées."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Jobs actifs récupérés avec succès",
            content = @Content(
                mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = JobProgressDto.class)),
                examples = @ExampleObject(
                    name = "Jobs actifs",
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
        summary = "Annuler un job en cours",
        description = "Annule un job actuellement en cours de traitement. Cela arrêtera le processus FFmpeg et nettoiera les ressources tout en préservant l'enregistrement du job."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job annulé avec succès",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Succès",
                    value = """
                    {
                        "message": "Job annulé avec succès",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "cancelledAt": "2024-01-01T12:00:00",
                        "details": "Processus FFmpeg arrêté, toutes les données supprimées, fichiers locaux nettoyés"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Job non trouvé",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Non trouvé",
                    value = """
                    {
                        "error": "Job not found",
                        "message": "Aucun job trouvé avec l'ID : job-123e4567-e89b-12d3-a456-426614174000"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Impossible d'annuler le job",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Requête invalide",
                    value = """
                    {
                        "error": "Cannot cancel job",
                        "message": "Le job n'est pas dans un état annulable"
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
        summary = "Supprimer un job spécifique",
        description = "Supprime un job par ID. Cela arrêtera le traitement s'il est en cours, nettoiera les ressources et supprimera le job de la base de données."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job supprimé avec succès",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Succès",
                    value = """
                    {
                        "message": "Job supprimé avec succès",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "deletedAt": "2024-01-01T12:00:00"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Job non trouvé",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Non trouvé",
                    value = """
                    {
                        "error": "Job not found",
                        "message": "Aucun job trouvé avec l'ID : job-123e4567-e89b-12d3-a456-426614174000"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Impossible de supprimer le job",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Requête invalide",
                    value = """
                    {
                        "error": "Cannot delete job",
                        "message": "Le job est actuellement en cours de traitement et ne peut pas être supprimé"
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
        summary = "Supprimer tous les jobs",
        description = "Supprime tous les jobs de la base de données. Cela arrêtera tous les processus en cours, nettoiera les ressources et supprimera tous les jobs. À utiliser avec précaution !"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Tous les jobs supprimés avec succès",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Succès",
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
            description = "Impossible de supprimer tous les jobs",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Requête invalide",
                    value = """
                    {
                        "error": "Cannot delete all jobs",
                        "message": "Il y a actuellement des jobs actifs qui ne peuvent pas être supprimés"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> deleteAllJobs(
            @Parameter(description = "Forcer la suppression même s'il y a des jobs actifs", example = "false")
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
        summary = "Nettoyer tous les répertoires locaux",
        description = "Nettoie tous les répertoires hls-v2 et upload-v2. Utilisez ceci après que tous les jobs soient terminés pour libérer de l'espace disque."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Répertoires nettoyés avec succès"),
        @ApiResponse(responseCode = "500", description = "Erreur serveur interne lors du nettoyage")
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
        summary = "Supprimer la playlist maître et tous les segments par ID de job",
        description = "Supprime une playlist maître et tous ses segments associés du stockage cloud R2 et de la base de données MongoDB en utilisant l'ID du job."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Playlist maître et segments supprimés avec succès",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Suppression réussie",
                    value = """
                    {
                        "message": "Playlist maître et segments supprimés avec succès",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "details": "Supprimé du stockage R2 et de MongoDB"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Playlist maître non trouvée",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Playlist maître non trouvée",
                    value = """
                    {
                        "error": "Master playlist not found",
                        "message": "Aucune playlist maître trouvée avec l'ID de job : job-123e4567-e89b-12d3-a456-426614174000"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erreur serveur interne lors de la suppression",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Erreur de suppression",
                    value = """
                    {
                        "error": "Failed to delete master playlist",
                        "message": "Détails de l'erreur ici"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> deleteMasterPlaylist(
            @Parameter(description = "Identifiant de l'ID du job pour la playlist maître", required = true, example = "job-123e4567-e89b-12d3-a456-426614174000")
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
    @Operation(summary = "Déboguer le stockage R2 - lister tous les fichiers")
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
        summary = "Supprimer TOUT le contenu de R2 et de la base de données", 
        description = "⚠️ DANGEREUX : Supprime TOUS les jobs, playlists maîtres, segments du stockage R2 et de la base de données. Cette action est irréversible !"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tout le contenu supprimé avec succès"),
        @ApiResponse(responseCode = "500", description = "Erreur lors de la suppression")
    })
    public ResponseEntity<Object> deleteAllContent(
            @Parameter(description = "Confirmation - doit être 'YES_DELETE_EVERYTHING'", required = true)
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
