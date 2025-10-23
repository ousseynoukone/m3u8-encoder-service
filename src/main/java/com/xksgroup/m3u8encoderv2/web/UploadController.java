package com.xksgroup.m3u8encoderv2.web;

import com.xksgroup.m3u8encoderv2.domain.Job.Job;
import com.xksgroup.m3u8encoderv2.domain.RequestIssuer;
import com.xksgroup.m3u8encoderv2.domain.ResourceType;
import com.xksgroup.m3u8encoderv2.service.JobService;
import com.xksgroup.m3u8encoderv2.service.FFmpegEncoderService;
import com.xksgroup.m3u8encoderv2.service.R2StorageService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v2/m3u8-encoder/upload")
@RequiredArgsConstructor
@Tag(name = "Upload V2", description = "Single-source upload that generates ABR HLS and uploads to cloud storage")
public class UploadController {

    private final JobService jobService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload video/audio file and start ABR HLS generation",
        description = """
            Uploads a video or audio file and immediately returns a job ID. The file will be processed asynchronously to generate multiple quality variants and upload to cloud storage.
            
            ## 🏗️ Architecture: Title-Based Slug Generation
            
            **How it works:**
            1. **Title Input**: You provide a human-readable title (e.g., "The Avengers: Endgame (2019)")
            2. **Automatic Slug Generation**: System converts title to URL-friendly slug (e.g., "the-avengers-endgame-2019")
            3. **Unique Job ID**: Each upload gets a unique job ID (e.g., "job-abc123") regardless of title
            4. **Content Grouping**: Same title = Same slug, allowing multiple episodes/versions to be grouped together
            
            **Example Scenarios:**
            - **Episode 1**: Title "The Avengers" → Slug "the-avengers" → Job ID "job-abc123"
            - **Episode 2**: Title "The Avengers" → Slug "the-avengers" → Job ID "job-def456"
            - **Director's Cut**: Title "The Avengers" → Slug "the-avengers" → Job ID "job-ghi789"
            
            **Benefits:**
            • **Content Discovery**: All episodes of "The Avengers" share slug "the-avengers"
            • **Precise Access**: Each episode accessible via unique job ID
            • **Logical Organization**: R2 storage path: `movie/the-avengers/job-abc123/`
            • **No Duplicate Slugs**: System handles multiple uploads with same title gracefully
            
            **URL Structure:**
            - **Master Playlist**: `/proxy/job-abc123` (not `/proxy/the-avengers`)
            - **Variant Playlists**: `/proxy/job-abc123/v0/index.m3u8`
            - **Status Check**: `/v2/status/the-avengers` (shows all episodes)
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202", 
            description = "Job accepted and processing started",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UploadResponse.class),
                examples = @ExampleObject(
                    name = "Success Response",
                    value = """
                    {
                        "status": "accepted",
                        "message": "Upload job created successfully",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "slug": "the-avengers-endgame-2019",
                        "title": "The Avengers: Endgame (2019)",
                        "resourceType": "VIDEO",
                        "jobStatus": "PENDING",
                        "progress": {
                            "totalSegments": 0,
                            "completedSegments": 0,
                            "failedSegments": 0,
                            "uploadingSegments": 0,
                            "pendingSegments": 0
                        },
                        "statusUrl": "/api/v2/m3u8-encoder/status/the-avengers-endgame-2019",
                        "jobUrl": "/api/v2/m3u8-encoder/jobs/job-123e4567-e89b-12d3-a456-426614174000",
                        "estimatedCompletionTime": "5-10 minutes",
                        "note": "Use jobId for precise content access, slug for content discovery"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Bad request - invalid file or parameters",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(
                        name = "Empty File",
                        value = """
                        {
                            "error": "file is empty",
                            "message": "The uploaded file contains no data"
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Invalid File Type - Playlist",
                        value = """
                        {
                            "error": "invalid file type",
                            "message": "Cannot process playlist files (.m3u8/.m3u). Please upload the original media file (video/audio) instead of playlist files.",
                            "uploadedFile": "Memory Reboot_a2.m3u8",
                            "supportedFormats": "Video: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | Audio: .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma"
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Invalid File Type - Text",
                        value = """
                        {
                            "error": "invalid file type",
                            "message": "Cannot process text files. Please upload a valid media file (video/audio).",
                            "uploadedFile": "config.txt",
                            "supportedFormats": "Video: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | Audio: .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma"
                        }
                        """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "413", 
            description = "File too large",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "File Too Large",
                    value = """
                    {
                        "error": "File size exceeds maximum limit",
                        "message": "Please upload a smaller file (max 6GB)"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "Internal server error",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Server Error",
                    value = """
                    {
                        "error": "Failed to create job",
                        "message": "An unexpected error occurred while processing your request"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> upload(
            @Parameter(description = "Video or audio file to upload", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(
                description = """
                    Title of the content. This will be automatically converted to a URL-friendly slug.
                    
                    **Slug Generation Rules:**
                    • Converted to lowercase
                    • Special characters replaced with hyphens
                    • Multiple spaces/hyphens collapsed to single hyphen
                    • Leading/trailing hyphens removed
                    
                    **Examples:**
                    • "The Avengers: Endgame (2019)" → slug "the-avengers-endgame-2019"
                    • "My Awesome Video!!!" → slug "my-awesome-video"
                    • "Episode 1 - The Beginning" → slug "episode-1-the-beginning"
                    
                    **Important:** Same title = Same slug. Multiple uploads with same title will be grouped together.
                    """, 
                required = true, 
                example = "The Avengers: Endgame (2019)"
            )
            @RequestParam("title") String title,
            
            @Parameter(description = "Type of content", required = true, example = "VIDEO")
            @RequestParam("resourceType") ResourceType resourceType,
            
            HttpServletRequest request) {
        
        log.info("Upload request received - Filename: {}, Size: {} bytes, Title: '{}', ResourceType: {}",
                file.getOriginalFilename(), file.getSize(), title, resourceType);
        
        try {
            // Validate file
            if (file.isEmpty()) {
                log.warn("Upload rejected - File is empty for title: '{}'", title);
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "file is empty");
                errorResponse.put("message", "The uploaded file contains no data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            // Validate file type immediately
            String fileName = file.getOriginalFilename();
            if (fileName != null) {
                String lowerFileName = fileName.toLowerCase();
                
                // Check for invalid file types
                if (lowerFileName.endsWith(".m3u8") || lowerFileName.endsWith(".m3u")) {
                    log.warn("Upload rejected - Playlist file detected: {} for title: '{}'", fileName, title);
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "invalid file type");
                    errorResponse.put("message", "Cannot process playlist files (.m3u8/.m3u). Please upload the original media file (video/audio) instead of playlist files.");
                    errorResponse.put("uploadedFile", fileName);
                    errorResponse.put("supportedFormats", "Video: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | Audio: .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }
                
                if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".log") || 
                    lowerFileName.endsWith(".json") || lowerFileName.endsWith(".xml")) {
                    log.warn("Upload rejected - Text file detected: {} for title: '{}'", fileName, title);
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "invalid file type");
                    errorResponse.put("message", "Cannot process text files. Please upload a valid media file (video/audio).");
                    errorResponse.put("uploadedFile", fileName);
                    errorResponse.put("supportedFormats", "Video: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | Audio: .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }

                String mimeType = file.getContentType();

                boolean isVideoMismatch = mimeType.startsWith("video/") && resourceType != ResourceType.VIDEO;
                boolean isAudioMismatch = mimeType.startsWith("audio/") && resourceType != ResourceType.AUDIO;

                if (isVideoMismatch || isAudioMismatch) {
                    String providedType = mimeType.startsWith("video/") ? "video" : "audio";
                    String expectedType = isVideoMismatch ? "audio" : "video"; // what the system expects

                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Invalid file type");
                    errorResponse.put("message", String.format(
                            "You provided a %s file, but a %s file was expected. Please upload a valid %s file.",
                            providedType, expectedType, expectedType
                    ));
                    errorResponse.put("supportedFormats",
                            "Video: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | " +
                                    "Audio: .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma"
                    );

                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }

            }
            
            // Create job 
            String clientId = request.getRemoteAddr();
            
            // Get user agent info from headers
            RequestIssuer userAgent = RequestIssuer.builder()
                .email(request.getHeader("x-auth-email"))
                .name(request.getHeader("x-auth-name"))
                .issuerId(request.getHeader("x-auth-sub"))
                .scope(request.getHeader("x-auth-scope"))
                .build();

            Job job = jobService.createJob(title, resourceType, file.getOriginalFilename(),
                                         file.getSize(), file.getContentType(), clientId, userAgent);

            log.info("Job created successfully: {} for title: '{}'", job.getJobId(), title);

            // Save file temporarily and start async processing
            Path tmpDir = Paths.get("upload-v2");
            Files.createDirectories(tmpDir);

            String safe = file.getOriginalFilename() != null ?
                Path.of(file.getOriginalFilename()).getFileName().toString() : "upload.bin";
            Path src = tmpDir.resolve(safe);
            Files.copy(file.getInputStream(), src, StandardCopyOption.REPLACE_EXISTING);

            // Start async processing
            jobService.processJobAsync(job, src);

            // Return immediate response

            Map<String, Object> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("message", "Upload job created successfully");
            response.put("jobId", job.getJobId());
            response.put("slug", job.getSlug());
            response.put("title", job.getTitle());
            response.put("resourceType", job.getResourceType().name());
            response.put("jobStatus", job.getStatus().name());
            response.put("statusUrl", "/api/v2/m3u8-encoder/status/" + job.getSlug());
            response.put("jobUrl", "/api/v2/m3u8-encoder/jobs/" + job.getJobId());

            log.info("Upload job accepted - Job: {}, Slug: '{}' - Processing started in background",
                     job.getJobId(), job.getSlug());
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            log.error("Failed to create upload job for file: {} with title: '{}' - Error: {}",
                     file.getOriginalFilename(), title, e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to create job");
            errorResponse.put("message", "An unexpected error occurred while processing your request");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

// Response schema for OpenAPI documentation
@Schema(description = "Upload response")
class UploadResponse {
    @Schema(description = "Response status", example = "accepted")
    private String status;
    
    @Schema(description = "Response message", example = "Upload job created successfully")
    private String message;
    
    @Schema(description = "Unique job identifier", example = "job-123e4567-e89b-12d3-a456-426614174000")
    private String jobId;
    
    @Schema(description = "Content slug", example = "my-awesome-video")
    private String slug;
    
    @Schema(description = "Content title", example = "My Awesome Video")
    private String title;
    
    @Schema(description = "Resource type", example = "VIDEO")
    private String resourceType;
    
    @Schema(description = "Current job status", example = "PENDING")
    private String jobStatus;
    
    @Schema(description = "Progress information")
    private ProgressInfo progress;
    
    @Schema(description = "URL to check status", example = "/api/v2/m3u8-encoder/status/my-awesome-video")
    private String statusUrl;
    
    @Schema(description = "URL to get job details", example = "/api/v2/m3u8-encoder/jobs/job-123e4567-e89b-12d3-a456-426614174000")
    private String jobUrl;
    
    @Schema(description = "Estimated completion time", example = "5-10 minutes")
    private String estimatedCompletionTime;
}

@Schema(description = "Progress information")
class ProgressInfo {
    @Schema(description = "Total number of segments", example = "0")
    private int totalSegments;
    
    @Schema(description = "Number of completed segments", example = "0")
    private int completedSegments;
    
    @Schema(description = "Number of failed segments", example = "0")
    private int failedSegments;
    
    @Schema(description = "Number of segments currently uploading", example = "0")
    private int uploadingSegments;
    
    @Schema(description = "Number of pending segments", example = "0")
    private int pendingSegments;
}
