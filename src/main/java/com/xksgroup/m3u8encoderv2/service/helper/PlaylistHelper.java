package com.xksgroup.m3u8encoderv2.service.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class PlaylistHelper {

    /**
     * Generate master playlist with variant information
     */
    public void generateMasterPlaylist(Path targetDir, String jobId) throws Exception {
        log.info("Generating master playlist in directory: {}", targetDir);
        
        StringBuilder masterContent = new StringBuilder();
        masterContent.append("#EXTM3U\n");
        masterContent.append("#EXT-X-VERSION:3\n");
        
        // Add variant entries - order from lowest to highest bandwidth for proper ABR
        String[] variants = {"v3", "v2", "v1", "v0"};
        String[] qualities = {"360p", "480p", "720p", "1080p"};
        String[] defaultResolutions = {"640x360", "854x480", "1280x720", "1920x1080"};
        String[] bandwidths = {"800000", "1400000", "2800000", "5000000"};
        
        int validVariants = 0;
        for (int i = 0; i < variants.length; i++) {
            Path variantDir = targetDir.resolve(variants[i]);
            Path playlistFile = variantDir.resolve("index.m3u8");
            
            if (Files.exists(variantDir) && Files.exists(playlistFile)) {
                // Try to get actual resolution from the generated content
                String actualResolution = getActualResolution(variantDir, defaultResolutions[i]);
                
                masterContent.append("#EXT-X-STREAM-INF:");
                masterContent.append("BANDWIDTH=").append(bandwidths[i]);
                masterContent.append(",RESOLUTION=").append(actualResolution);
                masterContent.append(",CODECS=\"avc1.4d401f,mp4a.40.2\"\n");
                masterContent.append(variants[i]).append("/index.m3u8\n");
                validVariants++;
                log.debug("Added {} variant to master playlist with resolution {}", qualities[i], actualResolution);
            } else {
                log.warn("Skipping {} variant - directory or playlist not found: {}", qualities[i], variantDir);
            }
        }
        
        if (validVariants == 0) {
            throw new RuntimeException("No valid variants were generated. Cannot create master playlist.");
        }
        
        // Write master playlist
        Path masterPath = targetDir.resolve("master.m3u8");
        Files.write(masterPath, masterContent.toString().getBytes());
        log.info("Successfully generated master playlist with {} variants: {}", validVariants, masterPath);
    }



    /**
     * Get actual resolution from generated content
     */
    private String getActualResolution(Path variantDir, String defaultResolution) {
        try {
            // Look for the first .ts file to get actual dimensions
            try (var files = Files.walk(variantDir, 1)) {
                var tsFile = files
                    .filter(path -> path.toString().endsWith(".ts"))
                    .findFirst();
                
                if (tsFile.isPresent()) {
                    List<String> probeCommand = List.of(
                            "ffprobe", "-v", "quiet", "-select_streams", "v:0",
                            "-show_entries", "stream=width,height", "-of", "csv=p=0",
                            tsFile.get().toAbsolutePath().toString()
                    );
                    
                    ProcessBuilder pb = new ProcessBuilder(probeCommand);
                    Process process = pb.start();
                    
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                        String result = reader.readLine();
                        int exit = process.waitFor();
                        
                        if (exit == 0 && result != null && result.contains(",")) {
                            String[] parts = result.split(",");
                            if (parts.length == 2) {
                                int width = Integer.parseInt(parts[0]);
                                int height = Integer.parseInt(parts[1]);
                                return width + "x" + height;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not determine actual resolution for variant {}, using default: {}", 
                    variantDir.getFileName(), defaultResolution);
        }
        
        return defaultResolution;
    }



    /**
     * Generate audio variants playlist structure (multiple quality variants)
     */
    public void generateAudioVariantsPlaylist(Path targetDir, String jobId) throws Exception {
        log.info("Generating audio variants playlist in directory: {}", targetDir);
        
        // Create audio variant directories (a0, a1, a2)
        Path a0Dir = targetDir.resolve("a0");
        Path a1Dir = targetDir.resolve("a1");
        Path a2Dir = targetDir.resolve("a2");
        Files.createDirectories(a0Dir);
        Files.createDirectories(a1Dir);
        Files.createDirectories(a2Dir);
        
        // Generate master playlist with multiple audio variants
        StringBuilder masterContent = new StringBuilder();
        masterContent.append("#EXTM3U\n");
        masterContent.append("#EXT-X-VERSION:3\n");
        
        // Add a0 variant entry (high quality audio - 192k)
        masterContent.append("#EXT-X-STREAM-INF:");
        masterContent.append("BANDWIDTH=192000"); // 192kbps
        masterContent.append(",RESOLUTION=audio");
        masterContent.append(",CODECS=\"mp4a.40.2\"\n");
        masterContent.append("a0/index.m3u8\n");
        
        // Add a1 variant entry (medium quality audio - 128k)
        masterContent.append("#EXT-X-STREAM-INF:");
        masterContent.append("BANDWIDTH=128000"); // 128kbps
        masterContent.append(",RESOLUTION=audio");
        masterContent.append(",CODECS=\"mp4a.40.2\"\n");
        masterContent.append("a1/index.m3u8\n");
        
        // Add a2 variant entry (low quality audio - 96k)
        masterContent.append("#EXT-X-STREAM-INF:");
        masterContent.append("BANDWIDTH=96000"); // 96kbps
        masterContent.append(",RESOLUTION=audio");
        masterContent.append(",CODECS=\"mp4a.40.2\"\n");
        masterContent.append("a2/index.m3u8\n");
        
        // Write master playlist
        Path masterPath = targetDir.resolve("master.m3u8");
        Files.write(masterPath, masterContent.toString().getBytes());
        log.info("Successfully generated audio variants master playlist: {}", masterPath);
        
        // Note: FFmpeg will generate the variant playlists with actual segments
        // We don't need to create empty playlists here as they would override FFmpeg's output
        
        log.info("Successfully generated audio variants playlist structure for job: {}", jobId);
    }

}
