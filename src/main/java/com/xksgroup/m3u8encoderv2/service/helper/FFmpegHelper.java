package com.xksgroup.m3u8encoderv2.service.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FFmpegHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Cache simple pour éviter de relancer ffprobe plusieurs fois sur le même fichier
    private final Map<String, ProbeInfo> probeCache = new ConcurrentHashMap<>();
    // Détection d’encodeur matériel faite une seule fois
    private volatile EncoderChoice cachedEncoderChoice;


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
     * Detect if input file has audio stream
     */
    public boolean hasAudioStream(Path inputFile) throws Exception {
        ProbeInfo info = probeMedia(inputFile);
        return info.hasAudio;
    }

    /**
     * Get video dimensions from input file
     */
    public VideoDimensions getVideoDimensions(Path inputFile) throws Exception {
        ProbeInfo info = probeMedia(inputFile);
        return info.dimensions;
    }


    /**
     * Validate input file format
     */
    public boolean isValidVideoFile(Path inputFile) throws Exception {
        ProbeInfo info = probeMedia(inputFile);
        return info.isValidMedia;
    }

    /**
     * Single ffprobe call (JSON) cached per absolute path.
     */
    public ProbeInfo probeMedia(Path inputFile) throws Exception {
        String key = inputFile.toAbsolutePath().normalize().toString();
        ProbeInfo cached = probeCache.get(key);
        if (cached != null) {
            return cached;
        }

        List<String> probeCommand = List.of(
                "ffprobe", "-v", "quiet",
                "-print_format", "json",
                "-show_streams", "-show_format",
                key
        );

        ProcessBuilder pb = new ProcessBuilder(probeCommand);
        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line);
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            log.warn("ffprobe exited with code {} for {}", exit, key);
        }

        ProbeInfo info = parseProbeOutput(stdout.toString());
        probeCache.put(key, info);
        return info;
    }

    private ProbeInfo parseProbeOutput(String json) {
        boolean hasVideo = false;
        boolean hasAudio = false;
        int width = 0;
        int height = 0;

        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode streams = root.path("streams");
            if (streams.isArray()) {
                for (JsonNode stream : streams) {
                    String codecType = stream.path("codec_type").asText("");
                    if ("video".equalsIgnoreCase(codecType)) {
                        hasVideo = true;
                        width = stream.path("width").asInt(width);
                        height = stream.path("height").asInt(height);
                    } else if ("audio".equalsIgnoreCase(codecType)) {
                        hasAudio = true;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse ffprobe output, using defaults: {}", e.getMessage());
        }

        if (width <= 0 || height <= 0) {
            // Preserve previous default behaviour when probing fails
            width = 1920;
            height = 1080;
        }

        boolean validMedia = hasVideo || hasAudio;
        return new ProbeInfo(hasVideo, hasAudio, validMedia, new VideoDimensions(width, height));
    }

    private EncoderChoice selectEncoder() {
        EncoderChoice current = cachedEncoderChoice;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (cachedEncoderChoice != null) {
                return cachedEncoderChoice;
            }
            EncoderChoice detected = detectEncoder();
            cachedEncoderChoice = detected;
            return detected;
        }
    }

    private EncoderChoice detectEncoder() {
        // On interroge ffmpeg pour la liste des accélérations dispo
        List<String> hwaccels = runCommandLines(List.of("ffmpeg", "-hide_banner", "-hwaccels"));
        log.error("Using ---------------------------------------- : " + hwaccels);

        boolean hasCuda = containsAccel(hwaccels, "cuda");
        boolean hasQsv = containsAccel(hwaccels, "qsv");
        boolean hasVideotoolbox = containsAccel(hwaccels, "videotoolbox");

        if (hasCuda) {
            log.info("Using NVIDIA NVENC hardware encoder");
            return new EncoderChoice("h264_nvenc", "p1", "cuda");
        }
        if (hasQsv) {
            log.info("Using Intel QSV hardware encoder");
            return new EncoderChoice("h264_qsv", "veryfast", "qsv");
        }
        if (hasVideotoolbox) {
            log.info("Using Apple VideoToolbox hardware encoder");
            return new EncoderChoice("h264_videotoolbox", null, "videotoolbox");
        }

        log.info("Falling back to CPU encoder (libx264)");
        return new EncoderChoice("libx264", "veryfast", null);
    }

    public EncoderChoice getCurrentEncoderChoice() {
        return selectEncoder();
    }

    private boolean containsAccel(List<String> lines, String needle) {
        for (String line : lines) {
            if (needle.equalsIgnoreCase(line.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> runCommandLines(List<String> command) {
        List<String> lines = new ArrayList<>();
        try {
            Process process = new ProcessBuilder(command).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        lines.add(line.trim());
                    }
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                log.warn("Command {} exited with {}", command, exit);
            }
        } catch (Exception e) {
            log.warn("Failed to run {}: {}", command, e.getMessage());
        }
        return lines;
    }

    // GOP dynamique = fps * durée segment, avec repli 90 si parsing impossible
    private int computeGop(String framerate, int segmentSeconds) {
        try {
            int fps = Integer.parseInt(framerate.trim());
            if (fps > 0 && segmentSeconds > 0) {
                return fps * segmentSeconds;
            }
        } catch (Exception e) {
            log.warn("Failed to parse framerate '{}', using default GOP 90", framerate);
        }
        return 90;
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

        EncoderChoice encoderChoice = selectEncoder();
        if (encoderChoice.hwAccel != null) {
            command.add("-hwaccel");
            command.add(encoderChoice.hwAccel);
        }
        command.add("-i");
        command.add(inputFile.toAbsolutePath().toString());

        // Video encoding settings with hardware detection and CPU fallback
        command.add("-c:v");
        command.add(encoderChoice.videoCodec);
        if (encoderChoice.preset != null && !encoderChoice.preset.isBlank()) {
            command.add("-preset");
            command.add(encoderChoice.preset);
        }
        command.add("-threads");
        command.add("0"); // Use all available CPU cores
        int gopSize = computeGop(framerate, 6);

        command.add("-b:v");
        command.add(bitrate);
        command.add("-maxrate");
        command.add(maxrate);
        command.add("-bufsize");
        command.add(bufsize);
        
        // Common video settings
        command.add("-profile:v");
        command.add(profile);
        command.add("-level");
        command.add(level);
        command.add("-vf");
        command.add("scale=" + resolution + ":flags=bicubic");
        command.add("-r");
        command.add(framerate);
        command.add("-g");
        command.add(String.valueOf(gopSize));
        command.add("-keyint_min");
        command.add(String.valueOf(gopSize)); // Minimum GOP size for better quality
        
        // libx264-specific settings
        command.add("-sc_threshold");
        command.add("0"); // Disable scene change detection for faster encoding
        
        // Common output settings
        command.add("-movflags");
        command.add("+faststart"); 
        command.add("-pix_fmt");
        command.add("yuv420p"); // Ensure compatibility across all devices
        command.add("-max_muxing_queue_size");
        command.add("4096");

        // Audio encoding
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
     * CPU-only fallback command (libx264) when hardware init fails.
     */
    public List<String> buildCpuFallbackCommand(Path inputFile, String resolution, String bitrate,
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

        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-threads");
        command.add("0");

        int gopSize = computeGop(framerate, 6);

        command.add("-b:v");
        command.add(bitrate);
        command.add("-maxrate");
        command.add(maxrate);
        command.add("-bufsize");
        command.add(bufsize);

        command.add("-profile:v");
        command.add(profile);
        command.add("-level");
        command.add(level);
        command.add("-vf");
        command.add("scale=" + resolution + ":flags=bicubic");
        command.add("-r");
        command.add(framerate);
        command.add("-g");
        command.add(String.valueOf(gopSize));
        command.add("-keyint_min");
        command.add(String.valueOf(gopSize));

        command.add("-sc_threshold");
        command.add("0");

        command.add("-movflags");
        command.add("+faststart");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-max_muxing_queue_size");
        command.add("4096");

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
            command.add("-an");
        }

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

        if (keyInfoFile != null) {
            command.add("-hls_key_info_file");
            command.add(keyInfoFile.toAbsolutePath().toString());
        }

        command.add("index.m3u8");
        return command;
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
        command.add("-threads");
        command.add("0"); // Use all available CPU cores
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
    // Empêche l’upscale, conserve le downscale et force des dimensions paires
    public String adjustResolution(String targetResolution, VideoDimensions inputDimensions) {
        try {
            String[] parts = targetResolution.split("x");
            if (parts.length == 2) {
                int targetWidth = Integer.parseInt(parts[0]);
                int targetHeight = Integer.parseInt(parts[1]);

                int outputWidth = targetWidth;
                int outputHeight = targetHeight;

                // Prevent upscaling: clamp to input size when target exceeds input
                if (targetWidth > inputDimensions.width || targetHeight > inputDimensions.height) {
                    double scaleFactor = Math.min(
                            (double) inputDimensions.width / targetWidth,
                            (double) inputDimensions.height / targetHeight
                    );
                    outputWidth = (int) Math.round(targetWidth * scaleFactor);
                    outputHeight = (int) Math.round(targetHeight * scaleFactor);
                }

                // Ensure dimensions are even and non-zero
                outputWidth = Math.max(2, outputWidth - (outputWidth % 2));
                outputHeight = Math.max(2, outputHeight - (outputHeight % 2));

                return outputWidth + "x" + outputHeight;
            }
        } catch (Exception e) {
            log.warn("Failed to adjust resolution: {}", e.getMessage());
        }
        
        return targetResolution;
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

    /**
     * Aggregated probe result to avoid repeated ffprobe calls.
     */
    public static class ProbeInfo {
        public final boolean hasVideo;
        public final boolean hasAudio;
        public final boolean isValidMedia;
        public final VideoDimensions dimensions;

        public ProbeInfo(boolean hasVideo, boolean hasAudio, boolean isValidMedia, VideoDimensions dimensions) {
            this.hasVideo = hasVideo;
            this.hasAudio = hasAudio;
            this.isValidMedia = isValidMedia;
            this.dimensions = dimensions;
        }
    }

    public static class EncoderChoice {
        public final String videoCodec;
        public final String preset;
        public final String hwAccel;

        public EncoderChoice(String videoCodec, String preset, String hwAccel) {
            this.videoCodec = videoCodec;
            this.preset = preset;
            this.hwAccel = hwAccel;
        }
    }
}
