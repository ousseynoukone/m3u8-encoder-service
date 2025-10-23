package com.xksgroup.m3u8encoderv2.service.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

@Slf4j
@Component
public class FFmpegHelper {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Check if FFmpeg is available in the system PATH
     */
    public boolean isFFmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.warn("FFmpeg availability check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if FFprobe is available in the system PATH
     */
    public boolean isFFprobeAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffprobe", "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.warn("FFprobe availability check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Detect if input file has video stream
     */
    public boolean hasVideoStream(Path inputFile) throws Exception {
        log.info("Running ffprobe to detect video stream in file: {}", inputFile);
        List<String> probeCommand = List.of(
                "ffprobe", "-v", "quiet", "-select_streams", "v:0",
                "-show_entries", "stream=codec_type", "-of", "csv=p=0",
                inputFile.toAbsolutePath().toString()
        );
        log.debug("FFprobe command: {}", String.join(" ", probeCommand));

        ProcessBuilder pb = new ProcessBuilder(probeCommand);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String result = reader.readLine();
            int exit = process.waitFor();
            log.info("FFprobe completed - Exit code: {}, Result: '{}' for file: {}", exit, result, inputFile);
            boolean hasVideo = exit == 0 && "video".equals(result);
            log.info("Video stream detection conclusion: {} for file: {}", hasVideo ? "HAS VIDEO" : "NO VIDEO", inputFile);
            return hasVideo;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.error("Error during video stream detection for file: {} - Error: {}", inputFile, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Detect if input file has audio stream
     */
    public boolean hasAudioStream(Path inputFile) throws Exception {
        try {
            List<String> probeCommand = List.of(
                    "ffprobe", "-v", "quiet", "-select_streams", "a:0",
                    "-show_entries", "stream=codec_type", "-of", "csv=p=0",
                    inputFile.toAbsolutePath().toString()
            );
            
            ProcessBuilder pb = new ProcessBuilder(probeCommand);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                int exit = process.waitFor();
                return exit == 0 && "audio".equals(result);
            }
        } catch (Exception e) {
            log.warn("Error detecting audio stream, assuming no audio: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get video dimensions from input file
     */
    public VideoDimensions getVideoDimensions(Path inputFile) throws Exception {
        try {
            List<String> probeCommand = List.of(
                    "ffprobe", "-v", "quiet", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height", "-of", "csv=p=0",
                    inputFile.toAbsolutePath().toString()
            );
            
            ProcessBuilder pb = new ProcessBuilder(probeCommand);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                int exit = process.waitFor();
                
                if (exit == 0 && result != null && result.contains(",")) {
                    String[] parts = result.split(",");
                    if (parts.length == 2) {
                        int width = Integer.parseInt(parts[0]);
                        int height = Integer.parseInt(parts[1]);
                        return new VideoDimensions(width, height);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get video dimensions: {}", e.getMessage());
        }
        
        // Return default dimensions if detection fails
        return new VideoDimensions(1920, 1080);
    }

    /**
     * Get video duration from input file
     */
    public double getVideoDuration(Path inputFile) throws Exception {
        try {
            List<String> probeCommand = List.of(
                    "ffprobe", "-v", "quiet", "-select_streams", "v:0",
                    "-show_entries", "format=duration", "-of", "csv=p=0",
                    inputFile.toAbsolutePath().toString()
            );
            
            ProcessBuilder pb = new ProcessBuilder(probeCommand);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                int exit = process.waitFor();
                
                if (exit == 0 && result != null && !result.trim().isEmpty()) {
                    return Double.parseDouble(result);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get video duration: {}", e.getMessage());
        }
        
        return 0.0;
    }

    /**
     * Validate input file format
     */
    public boolean isValidVideoFile(Path inputFile) throws Exception {
        try {
            List<String> probeCommand = List.of(
                    "ffprobe", "-v", "quiet", "-select_streams", "v:0",
                    "-show_entries", "stream=codec_type", "-of", "csv=p=0",
                    inputFile.toAbsolutePath().toString()
            );
            
            ProcessBuilder pb = new ProcessBuilder(probeCommand);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                int exit = process.waitFor();
                
                if (exit == 0 && "video".equals(result)) {
                    return true;
                }
                
                // Check if it's an audio-only file
                List<String> audioProbeCommand = List.of(
                        "ffprobe", "-v", "quiet", "-select_streams", "a:0",
                        "-show_entries", "stream=codec_type", "-of", "csv=p=0",
                        inputFile.toAbsolutePath().toString()
                );
                
                ProcessBuilder audioPb = new ProcessBuilder(audioProbeCommand);
                Process audioProcess = audioPb.start();
                
                try (BufferedReader audioReader = new BufferedReader(new InputStreamReader(audioProcess.getInputStream()))) {
                    String audioResult = audioReader.readLine();
                    int audioExit = audioProcess.waitFor();
                    return audioExit == 0 && "audio".equals(audioResult);
                }
            }
        } catch (Exception e) {
            log.warn("File format validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Build FFmpeg command for video variant generation with optional encryption
     */
    public List<String> buildVideoVariantCommand(Path inputFile, String resolution, String bitrate, 
                                               String maxrate, String bufsize, String framerate, 
                                               String profile, String level, boolean hasAudio,
                                               Path keyInfoFile) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("info");
        command.add("-progress");
        command.add("pipe:2");
        command.add("-i");
        command.add(inputFile.toAbsolutePath().toString());

        // Video encoding settings
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("medium");
        command.add("-profile:v");
        command.add(profile);
        command.add("-level");
        command.add(level);
        command.add("-s");
        command.add(resolution);
        command.add("-r");
        command.add(framerate);
        command.add("-b:v");
        command.add(bitrate);
        command.add("-maxrate");
        command.add(maxrate);
        command.add("-bufsize");
        command.add(bufsize);
        command.add("-g");
        command.add("90");

        // Audio encoding (only if audio stream exists)
        if (hasAudio) {
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("128k");
            command.add("-ar");
            command.add("48000");
            command.add("-ac");
            command.add("2");
        } else {
            command.add("-an"); // No audio
        }

        // HLS settings
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add("6");
        command.add("-hls_playlist_type");
        command.add("vod");
        command.add("-hls_flags");
        command.add("independent_segments");
        command.add("-hls_segment_filename");
        command.add("seg_%04d.ts");

        // Add encryption if key info file is provided
        if (keyInfoFile != null) {
            command.add("-hls_key_info_file");
            command.add(keyInfoFile.toAbsolutePath().toString());
        }

        command.add("index.m3u8");

        return command;
    }

    /**
     * Build FFmpeg command for video variant generation (backward compatibility)
     */
    public List<String> buildVideoVariantCommand(Path inputFile, String resolution, String bitrate, 
                                               String maxrate, String bufsize, String framerate, 
                                               String profile, String level, boolean hasAudio) {
        return buildVideoVariantCommand(inputFile, resolution, bitrate, maxrate, bufsize, 
                                      framerate, profile, level, hasAudio, null);
    }

    /**
     * Build FFmpeg command for audio-only generation with optional encryption
     */
    public List<String> buildAudioOnlyCommand(Path inputFile, Path keyInfoFile) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("info");
        command.add("-progress");
        command.add("pipe:2");
        command.add("-i");
        command.add(inputFile.toAbsolutePath().toString());

        // Audio encoding
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-ar");
        command.add("48000");
        command.add("-ac");
        command.add("2");

        // HLS settings
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add("6");
        command.add("-hls_playlist_type");
        command.add("vod");
        command.add("-hls_flags");
        command.add("independent_segments");
        command.add("-hls_segment_filename");
        command.add("seg_%04d.ts");

        // Add encryption if key info file is provided
        if (keyInfoFile != null) {
            command.add("-hls_key_info_file");
            command.add(keyInfoFile.toAbsolutePath().toString());
        }

        command.add("v0/index.m3u8");

        return command;
    }

    /**
     * Build FFmpeg command for audio-only generation (backward compatibility)
     */
    public List<String> buildAudioOnlyCommand(Path inputFile) {
        return buildAudioOnlyCommand(inputFile, null);
    }

    /**
     * Build FFmpeg command for multi-variant audio generation (similar to video variants)
     */
    public List<String> buildAudioVariantsCommand(Path inputFile, Path keyInfoFile) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("info");
        command.add("-progress");
        command.add("pipe:2");
        command.add("-i");
        command.add(inputFile.toAbsolutePath().toString());

        // High quality audio variant (a0) - 192k AAC
        command.add("-map");
        command.add("0:a:0");
        command.add("-c:a:0");
        command.add("aac");
        command.add("-b:a:0");
        command.add("192k");
        command.add("-ar:0");
        command.add("48000");
        command.add("-ac:0");
        command.add("2");

        // Medium quality audio variant (a1) - 128k AAC
        command.add("-map");
        command.add("0:a:0");
        command.add("-c:a:1");
        command.add("aac");
        command.add("-b:a:1");
        command.add("128k");
        command.add("-ar:1");
        command.add("48000");
        command.add("-ac:1");
        command.add("2");

        // Low quality audio variant (a2) - 96k AAC
        command.add("-map");
        command.add("0:a:0");
        command.add("-c:a:2");
        command.add("aac");
        command.add("-b:a:2");
        command.add("96k");
        command.add("-ar:2");
        command.add("44100");
        command.add("-ac:2");
        command.add("2");

        // HLS settings
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add("6");
        command.add("-hls_playlist_type");
        command.add("vod");
        command.add("-hls_flags");
        command.add("independent_segments");
        command.add("-hls_segment_filename");
        command.add("a%v/seg_%04d.ts");
        command.add("-master_pl_name");
        command.add("master.m3u8");

        // Add encryption if key info file is provided
        if (keyInfoFile != null) {
            command.add("-hls_key_info_file");
            command.add(keyInfoFile.toAbsolutePath().toString());
        }

        // Variant stream mapping for audio
        command.add("-var_stream_map");
        command.add("a:0 a:1 a:2");
        command.add("a%v/index.m3u8");

        // Add output file to ensure master playlist is generated
        command.add("master.m3u8");

        return command;
    }

    /**
     * Build FFmpeg command for multi-variant audio generation (backward compatibility)
     */
    public List<String> buildAudioVariantsCommand(Path inputFile) {
        return buildAudioVariantsCommand(inputFile, null);
    }

    /**
     * Build FFmpeg command for single audio variant generation
     */
    public List<String> buildSingleAudioVariantCommand(Path inputFile, String variant, String bitrate, 
                                                      String sampleRate, Path keyInfoFile) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("info");
        command.add("-progress");
        command.add("pipe:2");
        command.add("-i");
        command.add(inputFile.toAbsolutePath().toString());

        // Audio encoding settings
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add(bitrate);
        command.add("-ar");
        command.add(sampleRate);
        command.add("-ac");
        command.add("2");

        // HLS settings
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add("6");
        command.add("-hls_playlist_type");
        command.add("vod");
        command.add("-hls_flags");
        command.add("independent_segments");
        command.add("-hls_segment_filename");
        command.add("seg_%04d.ts");

        // Add encryption if key info file is provided
        if (keyInfoFile != null) {
            command.add("-hls_key_info_file");
            command.add(keyInfoFile.toAbsolutePath().toString());
        }

        // Output file
        command.add("index.m3u8");

        return command;
    }

    /**
     * Adjust resolution based on input dimensions
     */
    public String adjustResolution(String targetResolution, VideoDimensions inputDimensions) {
        try {
            String[] parts = targetResolution.split("x");
            if (parts.length == 2) {
                int targetWidth = Integer.parseInt(parts[0]);
                int targetHeight = Integer.parseInt(parts[1]);
                
                // If target resolution is larger than input, scale down proportionally
                if (targetWidth > inputDimensions.width || targetHeight > inputDimensions.height) {
                    double scaleFactor = Math.min(
                        (double) inputDimensions.width / targetWidth,
                        (double) inputDimensions.height / targetHeight
                    );
                    
                    int newWidth = (int) Math.round(targetWidth * scaleFactor);
                    int newHeight = (int) Math.round(targetHeight * scaleFactor);
                    
                    // Ensure dimensions are even numbers (required by some codecs)
                    newWidth = newWidth - (newWidth % 2);
                    newHeight = newHeight - (newHeight % 2);
                    
                    return newWidth + "x" + newHeight;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to adjust resolution: {}", e.getMessage());
        }
        
        return targetResolution;
    }

    /**
     * Get actual resolution from generated content
     */
    public String getActualResolution(Path variantDir, String defaultResolution) {
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
                    
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
     * Parse duration string in format HH:MM:SS.mmm to seconds
     */
    public double parseDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be null or empty");
        }

        String[] parts = durationStr.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid duration format: " + durationStr);
        }

        try {
            double hours = Double.parseDouble(parts[0]);
            double minutes = Double.parseDouble(parts[1]);
            double seconds = Double.parseDouble(parts[2]);

            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration values in: " + durationStr, e);
        }
    }

    /**
     * Video dimensions data class
     */
    public static class VideoDimensions {
        public final int width;
        public final int height;
        
        public VideoDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }
        
        @Override
        public String toString() {
            return width + "x" + height;
        }
    }
}
