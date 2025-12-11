package com.xksgroup.m3u8encoderv2.service.helper;

import lombok.extern.slf4j.Slf4j;
import com.xksgroup.m3u8encoderv2.model.VariantInfo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public class R2StorageHelper {
    public static Long calculateVideoDuration(Path variantDir) {
        try {
            Path playlistFile = variantDir.resolve("index.m3u8");
            if (!Files.exists(playlistFile)) {
                return 300L; // Default 5 minutes
            }

            String content = Files.readString(playlistFile, StandardCharsets.UTF_8);
            double totalDuration = 0.0;

            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("#EXTINF:")) {
                    String durationStr = line.substring(8);
                    int commaIndex = durationStr.indexOf(',');
                    if (commaIndex > 0) {
                        durationStr = durationStr.substring(0, commaIndex);
                        try {
                            totalDuration += Double.parseDouble(durationStr);
                        } catch (NumberFormatException ignored) {
                            // Skip invalid durations
                        }
                    }
                }
            }

            long durationSeconds = Math.round(totalDuration);
            return durationSeconds > 0 ? durationSeconds : 300L;

        } catch (Exception e) {
            log.error("Failed to calculate duration from {}: {}", variantDir, e.getMessage());
            return 300L;
        }
    }

    public static String rewriteMaster(String content, String prefix, String baseUrl, String bucket, boolean includeBucket, List<Path> variantDirs) {
        String base = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
        Set<String> labels = new LinkedHashSet<>();
        for (Path v : variantDirs) {
            labels.add(v.getFileName().toString());
        }

        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                sb.append(line).append("\n");
                continue;
            }

            String rewritten = line;
            for (String label : labels) {
                if (trimmed.equals(label + "/index.m3u8") || trimmed.equals(label)) {
                    String key = prefix + label + "/index.m3u8";
                    rewritten = includeBucket ? base + "/" + bucket + "/" + key : base + "/" + key;
                    break;
                }
            }
            sb.append(rewritten).append("\n");
        }
        return sb.toString();
    }

    public static String rewriteVariant(String content, String playlistKey, String baseUrl, String bucket, boolean includeBucket) {
        String base = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
        String dir = playlistKey.contains("/") ? playlistKey.substring(0, playlistKey.lastIndexOf('/') + 1) : "";
        StringBuilder sb = new StringBuilder();

        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                sb.append(line).append("\n");
                continue;
            }
            String absolute = includeBucket ? base + "/" + bucket + "/" + dir + trimmed : base + "/" + dir + trimmed;
            sb.append(absolute).append("\n");
        }
        return sb.toString();
    }

    /**
     * Parse FFmpeg-generated master playlist to extract variant information
     */
    public static List<VariantInfo> parseMasterPlaylist(String masterContent, List<Path> variantDirs) {
        List<VariantInfo> variants = new ArrayList<>();
        String[] lines = masterContent.split("\n");
        
        // Create mapping of variant paths to directory names
        Map<String, String> pathToLabel = new HashMap<>();
        for (Path variantDir : variantDirs) {
            String label = variantDir.getFileName().toString();
            pathToLabel.put(label + "/index.m3u8", label);
            pathToLabel.put(label, label);
        }
        
        // Parse EXT-X-STREAM-INF lines
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                String nextLine = lines[i + 1].trim();
                
                // Extract variant label from the URL/path
                String label = extractVariantLabel(nextLine, pathToLabel);
                if (label == null) continue;
                
                // Parse attributes from EXT-X-STREAM-INF line
                VariantInfo variantInfo = parseStreamInfLine(line, label);
                if (variantInfo != null) {
                    variants.add(variantInfo);
                }
            }
        }
        
        // Sort variants by bandwidth (ascending order for better player compatibility)
        variants.sort((a, b) -> {
            try {
                long bandwidthA = Long.parseLong(a.getBandwidth());
                long bandwidthB = Long.parseLong(b.getBandwidth());
                return Long.compare(bandwidthA, bandwidthB);
            } catch (NumberFormatException e) {
                return a.getLabel().compareTo(b.getLabel());
            }
        });
        
        return variants;
    }
    
    private static String extractVariantLabel(String urlLine, Map<String, String> pathToLabel) {
        // Try to match against known variant paths
        for (Map.Entry<String, String> entry : pathToLabel.entrySet()) {
            if (urlLine.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    private static VariantInfo parseStreamInfLine(String streamInfLine, String label) {
        try {
            // Patterns to extract attributes
            Pattern bandwidthPattern = Pattern.compile("BANDWIDTH=(\\d+)");
            Pattern resolutionPattern = Pattern.compile("RESOLUTION=(\\d+x\\d+)");
            Pattern codecsPattern = Pattern.compile("CODECS=\"([^\"]+)\"");
            Pattern avgBandwidthPattern = Pattern.compile("AVERAGE-BANDWIDTH=(\\d+)");
            Pattern frameRatePattern = Pattern.compile("FRAME-RATE=([\\d.]+)");
            
            Matcher bandwidthMatcher = bandwidthPattern.matcher(streamInfLine);
            Matcher resolutionMatcher = resolutionPattern.matcher(streamInfLine);
            Matcher codecsMatcher = codecsPattern.matcher(streamInfLine);
            Matcher avgBandwidthMatcher = avgBandwidthPattern.matcher(streamInfLine);
            Matcher frameRateMatcher = frameRatePattern.matcher(streamInfLine);
            
            String bandwidth = bandwidthMatcher.find() ? bandwidthMatcher.group(1) : "3000000";
            String resolution = resolutionMatcher.find() ? resolutionMatcher.group(1) : "1280x720";
            String codecs = codecsMatcher.find() ? codecsMatcher.group(1) : "avc1.4d401f,mp4a.40.2";
            
            // Build extended attributes for better HLS compatibility
            StringBuilder extendedAttributes = new StringBuilder();
            extendedAttributes.append("BANDWIDTH=").append(bandwidth);
            
            if (avgBandwidthMatcher.find()) {
                extendedAttributes.append(",AVERAGE-BANDWIDTH=").append(avgBandwidthMatcher.group(1));
            }
            
            extendedAttributes.append(",RESOLUTION=").append(resolution);
            extendedAttributes.append(",CODECS=\"").append(codecs).append("\"");
            
            if (frameRateMatcher.find()) {
                extendedAttributes.append(",FRAME-RATE=").append(frameRateMatcher.group(1));
            }
            
            return VariantInfo.builder()
                    .label(label)
                    .bandwidth(bandwidth)
                    .resolution(resolution)
                    .codecs(codecs)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to parse stream info line: {} for label: {}", streamInfLine, label, e);
            return VariantInfo.builder()
                    .label(label)
                    .bandwidth("3000000")
                    .resolution("1280x720")
                    .codecs("avc1.4d401f,mp4a.40.2")
                    .build();
        }
    }


    public static List<Path> findSegmentFiles(Path variantDir) throws Exception {
        try (Stream<Path> ls = Files.list(variantDir)) {
            return ls.sorted()
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".ts"))
                    .toList();
        }
    }

}
