package com.xksgroup.m3u8encoderv2.service;

import com.xksgroup.m3u8encoderv2.model.ResourceType;
import com.xksgroup.m3u8encoderv2.service.helper.FFmpegHelper;
import com.xksgroup.m3u8encoderv2.service.helper.PlaylistHelper;
import com.xksgroup.m3u8encoderv2.service.helper.ProcessHelper;
import com.xksgroup.m3u8encoderv2.service.helper.EncryptionHelper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
public class FFmpegEncoderService {

    @Setter
    private JobService jobService;

    @Value("${hls.encryption.enabled:true}")
    private boolean encryptionEnabled;

    @Value("${server.host:localhost}")
    private String serverHost;


    @Value("${protocol:https}")
    private String protocol;

    private final FFmpegHelper ffmpegHelper;
    private final PlaylistHelper playlistHelper;
    private final ProcessHelper processHelper;
    private final EncryptionHelper encryptionHelper;

    public FFmpegEncoderService(FFmpegHelper ffmpegHelper, PlaylistHelper playlistHelper, 
                               ProcessHelper processHelper, EncryptionHelper encryptionHelper) {
        this.ffmpegHelper = ffmpegHelper;
        this.playlistHelper = playlistHelper;
        this.processHelper = processHelper;
        this.encryptionHelper = encryptionHelper;
    }

    /**
     * Stop a running FFmpeg process for a specific job
     */
    public boolean stopProcess(String jobId) {
        return processHelper.stopProcess(jobId);
    }

    /**
     * Validate that the output directory contains necessary files for upload
     */
    private boolean isOutputDirectoryValid(Path outputDir, ResourceType resourceType) {
        try {
            // Check if master playlist exists
            Path masterPlaylist = outputDir.resolve("master.m3u8");
            if (!Files.exists(masterPlaylist)) {
                log.warn("Master playlist not found in output directory: {}", outputDir);
                return false;
            }
            
            // Determine which variant directories to check based on resource type
            String[] variants;
            if (resourceType == ResourceType.VIDEO) {
                variants = new String[]{"v0", "v1", "v2", "v3"}; // Video variants
            } else {
                variants = new String[]{"a0", "a1", "a2"}; // Audio variants
            }
            
            boolean hasValidVariants = false;
            
            for (String variant : variants) {
                Path variantDir = outputDir.resolve(variant);
                Path variantPlaylist = variantDir.resolve("index.m3u8");
                
                if (Files.exists(variantDir) && Files.exists(variantPlaylist)) {
                    // Check if there are .ts files
                    try (var files = Files.list(variantDir)) {
                        if (files.anyMatch(path -> path.toString().endsWith(".ts"))) {
                            hasValidVariants = true;
                            log.info("Found valid {} variant: {} with {} .ts files", 
                                resourceType == ResourceType.VIDEO ? "video" : "audio",
                                variant, 
                                Files.list(variantDir).filter(p -> p.toString().endsWith(".ts")).count());
                            break;
                        }
                    }
                }
            }
            
            if (!hasValidVariants) {
                log.warn("No valid {} variants found in output directory: {}", 
                    resourceType == ResourceType.VIDEO ? "video" : "audio",
                    outputDir);
                return false;
            }
            
            log.info("Output directory validation passed for {}: {}", resourceType, outputDir);
            return true;
            
        } catch (Exception e) {
            log.warn("Error validating output directory {}: {}", outputDir, e.getMessage());
            return false;
        }
    }

    public Path generateAbrHls(Path inputFile, String slug, Path targetDir, String jobId, ResourceType resourceType) throws Exception {
        log.info("Starting ABR HLS generation - Input file: {}, Slug: {}, Target directory: {}", inputFile, slug, targetDir);

        // Input validation
        validateInputs(inputFile, targetDir, jobId);
        
        log.info("Creating target directory: {}", targetDir);
        Files.createDirectories(targetDir);
        log.info("Successfully created target directory: {}", targetDir);

                // Use resourceType to determine processing type
        log.info("Processing file with resource type: {} for file: {}", resourceType, inputFile);

        try {
        if (resourceType == ResourceType.VIDEO) {
            log.info("Generating video variants for file: {}", inputFile);
                generateVideoVariants(inputFile, targetDir, jobId);
                
                // Check if job was cancelled after variant generation
                if (isJobCancelled(jobId)) {
                    log.info("Job {} was cancelled after variant generation, stopping ABR HLS generation", jobId);
                    return null; // Return null to indicate cancellation
                }
                
            log.info("Successfully completed video variant generation for file: {}", inputFile);
        } else {
            log.info("Generating audio variants for file: {} (ResourceType: {})", inputFile, resourceType);
                generateAudioOnly(inputFile, targetDir, jobId);
                
                // Check if job was cancelled after audio generation
                if (isJobCancelled(jobId)) {
                    log.info("Job {} was cancelled after audio generation, stopping ABR HLS generation", jobId);
                    return null; // Return null to indicate cancellation
                }
                
                log.info("Successfully completed audio variant generation for file: {}", inputFile);
            }
        } catch (Exception e) {
            log.error("Encoding failed for job {}: {}", jobId, e.getMessage());
            // Cleanup on failure
            stopProcess(jobId);
            throw e;
        }

        // Final cancellation check before cleanup
        if (isJobCancelled(jobId)) {
            log.info("Job {} was cancelled before cleanup, stopping ABR HLS generation", jobId);
            return null; // Return null to indicate cancellation
        }

        log.info("Cleaning up input file: {}", inputFile);
        try { 
            Files.deleteIfExists(inputFile); 
            log.info("Successfully deleted input file: {}", inputFile);
        } catch (IOException e) { 
            log.warn("Failed to delete input file: {} - Error: {}", inputFile, e.getMessage());
        }

        // Validate output directory before returning
        if (!isOutputDirectoryValid(targetDir, resourceType)) {
            throw new RuntimeException("Output directory validation failed - missing required files for upload");
        }
        
        log.info("ABR HLS generation completed successfully - Output directory: {}", targetDir);
        return targetDir;
    }

    /**
     * Validate input parameters
     */
    private void validateInputs(Path inputFile, Path targetDir, String jobId) throws Exception {
        if (inputFile == null || !Files.exists(inputFile)) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFile);
        }
        
        if (Files.size(inputFile) == 0) {
            throw new IllegalArgumentException("Input file is empty: " + inputFile);
        }
        
        if (targetDir == null) {
            throw new IllegalArgumentException("Target directory cannot be null");
        }
        
        if (jobId == null || jobId.trim().isEmpty()) {
            throw new IllegalArgumentException("Job ID cannot be null or empty");
        }
        
        // Check if FFmpeg is available
        if (!ffmpegHelper.isFFmpegAvailable()) {
            throw new RuntimeException("FFmpeg is not available in the system PATH. Please install FFmpeg.");
        }
        
        // Check for invalid file types first
        String fileName = inputFile.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".m3u8") || fileName.endsWith(".m3u")) {
            throw new IllegalArgumentException("Cannot process playlist files (.m3u8/.m3u). Please upload the original media file (video/audio) instead of playlist files. Uploaded file: " + inputFile);
        }
        
        if (fileName.endsWith(".txt") || fileName.endsWith(".log") || fileName.endsWith(".json") || fileName.endsWith(".xml")) {
            throw new IllegalArgumentException("Cannot process text files. Please upload a valid media file (video/audio). Uploaded file: " + inputFile);
        }
        
        // Validate input file format
        if (!ffmpegHelper.isValidVideoFile(inputFile)) {
            throw new IllegalArgumentException("Input file is not a valid video/audio file. Please ensure you're uploading a media file (e.g., .mp4, .avi, .mov, .mp3, .wav, .m4a). Uploaded file: " + inputFile);
        }
        
        log.info("Input validation passed for file: {} (size: {} bytes)", 
                inputFile, Files.size(inputFile));
    }

    private void generateVideoVariants(Path inputFile, Path targetDir, String jobId) throws Exception {
        log.info("Starting video variant generation for file: {} in directory: {}", inputFile, targetDir);
        
        // Create variant directories
        log.info("Creating variant directories v0, v1, v2, v3 in: {}", targetDir);
        Files.createDirectories(targetDir.resolve("v0"));
        Files.createDirectories(targetDir.resolve("v1"));
        Files.createDirectories(targetDir.resolve("v2"));
        Files.createDirectories(targetDir.resolve("v3"));
        log.info("Successfully created all variant directories in: {}", targetDir);

        // Generate each variant separately for better reliability
        List<String> failedVariants = new ArrayList<>();
        List<String> successfulVariants = new ArrayList<>();
        
        // Define variants to generate
        String[][] variantConfigs = {
            {"v0", "1080p", "1920x1080", "4000k", "4200k", "8000k", "30", "high", "4.0"},
            {"v1", "720p", "1280x720", "2500k", "2750k", "5000k", "30", "high", "3.1"},
            {"v2", "480p", "854x480", "1000k", "1100k", "2000k", "25", "main", "3.0"},
            {"v3", "360p", "640x360", "500k", "550k", "1000k", "25", "baseline", "3.0"}
        };

        // Setup encryption if enabled
        Path keyInfoFile = null;
        if (encryptionEnabled) {
            keyInfoFile = setupEncryption(targetDir, jobId);
        }
        
        for (int i = 0; i < variantConfigs.length; i++) {
            String[] config = variantConfigs[i];
            int variantNumber = i + 1;
            int totalVariants = variantConfigs.length;
            
            // Check if job was cancelled before processing each variant
            if (isJobCancelled(jobId)) {
                log.info("Job {} was cancelled, stopping variant generation", jobId);
                return; // Exit early - don't generate master playlist
            }
            
            try {


                log.info("Processing variant {}/{} - {} ({})", variantNumber, totalVariants, config[1], config[2]);
                generateVariant(inputFile, targetDir, config[0], config[1], config[2], 
                              config[3], config[4], config[5], config[6], config[7], config[8], 
                              jobId, variantNumber, totalVariants,keyInfoFile);
                successfulVariants.add(config[1]);
                log.info("✓ Completed variant {}/{} - {} successfully", variantNumber, totalVariants, config[1]);
            } catch (Exception e) {
                // Check if this is a cancellation-related error
                if (isJobCancelled(jobId)) {
                    log.info("Job {} was cancelled during {} variant generation, stopping", jobId, config[1]);
                    return; // Exit early - don't generate master playlist
                }
                
                log.error("✗ Failed variant {}/{} - {}: {}", variantNumber, totalVariants, config[1], e.getMessage());
                failedVariants.add(config[1]);
                // Continue with other variants instead of failing completely
            }
        }
        
        // Check if job was cancelled before proceeding
        if (isJobCancelled(jobId)) {
            log.info("Job {} was cancelled, skipping master playlist generation", jobId);
            return; // Exit early - don't generate master playlist
        }
        
        if (successfulVariants.isEmpty()) {
            throw new RuntimeException("All video variants failed to generate. Cannot proceed.");
        }
        
        log.info("Video variant generation completed. Successful: {}, Failed: {}", 
                successfulVariants, failedVariants.isEmpty() ? "none" : failedVariants);

        // Generate master playlist (will only include successful variants)
        playlistHelper.generateMasterPlaylist(targetDir, jobId);
    }

    private void generateVariant(Path inputFile, Path targetDir, String variantDir, String quality, 
                                String resolution, String bitrate, String maxrate, String bufsize, 
                                String framerate, String profile, String level, String jobId, 
                                int variantNumber, int totalVariants, Path keyInfoFile) throws Exception {
        log.info("Generating {} variant ({}) in directory: {}", quality, resolution, variantDir);
        
        // Check if job was cancelled before starting
        if (isJobCancelled(jobId)) {
            log.info("Job {} was cancelled before starting {} variant generation", jobId, quality);
            return; // Exit early - don't generate variant
        }
        
        Path variantPath = targetDir.resolve(variantDir);
        
        // Get input video dimensions to avoid scaling issues
        FFmpegHelper.VideoDimensions inputDimensions = ffmpegHelper.getVideoDimensions(inputFile);
        String adjustedResolution = ffmpegHelper.adjustResolution(resolution, inputDimensions);
        
        if (!adjustedResolution.equals(resolution)) {
            log.info("Adjusted resolution for {} variant from {} to {} (input: {}x{})", 
                    quality, resolution, adjustedResolution, inputDimensions.width, inputDimensions.height);
        }
        
        // Check if audio stream exists
        boolean hasAudio = ffmpegHelper.hasAudioStream(inputFile);
        
        // Build FFmpeg command with CPU encoder (libx264)
        List<String> command = ffmpegHelper.buildVideoVariantCommand(
            inputFile, adjustedResolution, bitrate, maxrate, bufsize, 
            framerate, profile, level, hasAudio, keyInfoFile
        );

        FFmpegHelper.EncoderChoice encoderChoice = ffmpegHelper.getCurrentEncoderChoice();
        if (encoderChoice != null) {
            String accelerationLabel = encoderChoice.hwAccel != null
                    ? String.format("gpu/%s (%s)", encoderChoice.videoCodec, encoderChoice.hwAccel)
                    : String.format("cpu/%s", encoderChoice.videoCodec);
            jobService.updateAcceleration(jobId, accelerationLabel);
        }

        // Run FFmpeg with hardware, fallback to CPU if device init fails
        try {
            processHelper.runFFmpeg(command, quality + " variant", jobId,
                    (percentage, currentTime, totalTime) -> {
                        if (jobService != null) {
                            int baseProgress = ((variantNumber - 1) * 100) / totalVariants;
                            int variantProgress = percentage / totalVariants;
                            int overallProgress = Math.min(100, baseProgress + variantProgress);

                            int variantProgressPercentage = percentage;

                            jobService.updateJobProgressWithVariant(jobId, overallProgress, currentTime, totalTime,
                                    variantNumber, totalVariants, quality, quality + " (" + adjustedResolution + ")", variantProgressPercentage);

                            if (percentage % 10 == 0) {
                                log.info("Variant {}/{} ({}): {}% complete - Overall: {}%",
                                        variantNumber, totalVariants, quality, percentage, overallProgress);
                            }
                        }
                    }, variantPath);
        } catch (RuntimeException ex) {
            // Detect hardware device/init errors and retry in CPU
            if (shouldFallbackToCpu(ex)) {
                log.warn("Hardware acceleration failed for {} ({}). Retrying with CPU libx264.", quality, encoderChoice != null ? encoderChoice.videoCodec : "unknown");
                List<String> cpuCommand = ffmpegHelper.buildCpuFallbackCommand(
                        inputFile, adjustedResolution, bitrate, maxrate, bufsize,
                        framerate, profile, level, hasAudio, keyInfoFile);
                jobService.updateAcceleration(jobId, "cpu/libx264 (fallback)");

                processHelper.runFFmpeg(cpuCommand, quality + " variant (cpu fallback)", jobId,
                        (percentage, currentTime, totalTime) -> {
                            if (jobService != null) {
                                int baseProgress = ((variantNumber - 1) * 100) / totalVariants;
                                int variantProgress = percentage / totalVariants;
                                int overallProgress = Math.min(100, baseProgress + variantProgress);

                                int variantProgressPercentage = percentage;

                                jobService.updateJobProgressWithVariant(jobId, overallProgress, currentTime, totalTime,
                                        variantNumber, totalVariants, quality, quality + " (" + adjustedResolution + ")", variantProgressPercentage);

                                if (percentage % 10 == 0) {
                                    log.info("Variant {}/{} ({}): {}% complete - Overall: {}%",
                                            variantNumber, totalVariants, quality, percentage, overallProgress);
                                }
                            }
                        }, variantPath);
            } else {
                throw ex;
            }
        }
        
        log.info("Successfully generated {} variant using CPU encoding (libx264)", quality);
    }

    private boolean shouldFallbackToCpu(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        return msg.contains("device") || msg.contains("hardware") || msg.contains("failed") || msg.contains("filtergraph");
    }

    private void generateAudioOnly(Path inputFile, Path targetDir, String jobId) throws Exception {
        log.info("Starting audio variant generation for file: {} in directory: {}", inputFile, targetDir);

        // Check if job was cancelled before starting
        if (isJobCancelled(jobId)) {
            log.info("Job {} was cancelled before starting audio variant generation", jobId);
            return; // Exit early - don't generate playlist
        }

        // Create variant directories for audio (similar to video)
        log.info("Creating audio variant directories a0, a1, a2 in: {}", targetDir);
        Files.createDirectories(targetDir.resolve("a0")); // High quality audio
        Files.createDirectories(targetDir.resolve("a1")); // Medium quality audio  
        Files.createDirectories(targetDir.resolve("a2")); // Low quality audio
        log.info("Successfully created all audio variant directories in: {}", targetDir);

        // Setup encryption if enabled
        Path keyInfoFile = null;
        if (encryptionEnabled) {
            keyInfoFile = setupEncryption(targetDir, jobId);
        }

        // Generate audio variants individually
        generateAudioVariant(inputFile, targetDir, "a0", "192k", "48000", 1, 3, jobId,keyInfoFile);
        generateAudioVariant(inputFile, targetDir, "a1", "128k", "48000", 2, 3, jobId,keyInfoFile);
        generateAudioVariant(inputFile, targetDir, "a2", "96k", "44100", 3, 3, jobId,keyInfoFile);
        
        // Check if job was cancelled after encoding
        if (isJobCancelled(jobId)) {
            log.info("Job {} was cancelled after audio encoding, skipping playlist generation", jobId);
            return; // Exit early - don't generate playlist
        }
        
        // Generate audio playlist structure (master.m3u8 and a0/index.m3u8, a1/index.m3u8, a2/index.m3u8)
        playlistHelper.generateAudioVariantsPlaylist(targetDir, jobId);
    }
    
    private void generateAudioVariant(Path inputFile, Path targetDir, String variant, String bitrate, 
                                    String sampleRate, int variantNumber, int totalVariants, String jobId, Path keyInfoFile) throws Exception {
        log.info("Generating audio variant {} ({}) for job: {}", variant, bitrate, jobId);
        

        
        // Build FFmpeg command for single audio variant
        List<String> command = ffmpegHelper.buildSingleAudioVariantCommand(inputFile, variant, bitrate, sampleRate, keyInfoFile);

        // Run FFmpeg for this audio variant with enhanced progress tracking
        processHelper.runFFmpeg(command, variant + " audio variant", jobId, 
            (percentage, currentTime, totalTime) -> {
                // Progress callback - update job progress with variant tracking
                if (jobService != null) {
                    // Calculate overall progress across all variants
                    int baseProgress = ((variantNumber - 1) * 100) / totalVariants;
                    int variantProgress = percentage / totalVariants;
                    int overallProgress = Math.min(100, baseProgress + variantProgress);
                    
                    
                    int variantProgressPercentage = percentage;
                    
                    // Update with variant tracking
                    jobService.updateJobProgressWithVariant(jobId, overallProgress, currentTime, totalTime,
                            variantNumber, totalVariants, variant + " (" + bitrate + ")", 
                            "Audio variant " + variant + " - " + bitrate + " AAC", variantProgressPercentage);
                    
                    // Log variant-specific progress
                    if (percentage % 10 == 0) { // Log every 10%
                        log.info("Audio variant {}/{} ({}): {}% complete - Overall: {}%", 
                                variantNumber, totalVariants, variant, percentage, overallProgress);
                    }
                }
            }, targetDir.resolve(variant));
        
        log.info("Successfully generated audio variant {}", variant);
    }

    /**
     * Check if a job has been cancelled
     */
    private boolean isJobCancelled(String jobId) {
        if (jobId == null || jobService == null) {
            return false;
        }
        
        try {
            return jobService.isJobCancelled(jobId);
        } catch (Exception e) {
            log.warn("Error checking if job {} was cancelled: {}", jobId, e.getMessage());
            return false;
        }
    }

    /**
     * Setup encryption for HLS segments
     */
    private Path setupEncryption(Path targetDir, String jobId) throws Exception {
        try {
            // Generate encryption key and IV
            byte[] encryptionKey = encryptionHelper.generateEncryptionKey();
            byte[] iv = encryptionHelper.generateIV();
            
            // Create key and IV files
            String keyFileName = encryptionHelper.getKeyFileName(jobId);
            String ivFileName = encryptionHelper.getIVFileName(jobId);
            String keyInfoFileName = encryptionHelper.getKeyInfoFileName(jobId);
            
            Path keyFile = targetDir.resolve(keyFileName);
            Path ivFile = targetDir.resolve(ivFileName);
            Path keyInfoFile = targetDir.resolve(keyInfoFileName);
            
            // Write key and IV to files
            encryptionHelper.writeKeyToFile(encryptionKey, keyFile);
            encryptionHelper.writeIVToFile(iv, ivFile);

            // Generate key URI for the proxy endpoint
            String keyUri = encryptionHelper.generateKeyUri(protocol, serverHost, jobId);
            
            // Create key info file for FFmpeg
            encryptionHelper.createKeyInfoFile(keyInfoFile, keyUri, keyFile, iv);
            
            log.info("Setup encryption for job {} - Key: {}, IV: {}, KeyInfo: {}", 
                    jobId, keyFile, ivFile, keyInfoFile);
            
            return keyInfoFile;
            
        } catch (Exception e) {
            log.error("Failed to setup encryption for job {}: {}", jobId, e.getMessage(), e);
            throw e;
        }
    }
}