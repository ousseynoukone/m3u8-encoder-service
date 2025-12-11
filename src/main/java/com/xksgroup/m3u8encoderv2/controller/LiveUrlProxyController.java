package com.xksgroup.m3u8encoderv2.controller;

import com.xksgroup.m3u8encoderv2.model.LiveUrl;
import com.xksgroup.m3u8encoderv2.service.LiveUrlService;
import com.xksgroup.m3u8encoderv2.service.helper.ProxyHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.net.URLDecoder.decode;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/m3u8-encoder/api/v2/live-url/proxy")
@Tag(name = "Proxy URLs Live", description = "Proxy pour les streams HLS depuis des URLs live externes")
public class LiveUrlProxyController {

    private final WebClient webClient;
    private final LiveUrlService liveUrlService;

    @Value("${protocol}")
    private String protocol;

    @Value("${security.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Autowired
    public LiveUrlProxyController(LiveUrlService liveUrlService, WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 16MB limit
                .build();
        this.liveUrlService = liveUrlService;
    }

    private HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", allowedOrigins);
        headers.add("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Expose-Headers", "*");
        headers.add("Access-Control-Max-Age", "3600");
        return headers;
    }

    @GetMapping("/{urlId}")
    @Operation(
            summary = "Proxifier une playlist M3U8 depuis une URL live",
            description = """
                    Récupère une playlist M3U8 depuis une URL live enregistrée et réécrit toutes les URLs 
                    internes pour passer par le proxy. Supporte les playlists maîtres et les variantes.
                    
                    **Paramètres :**
                    - `urlId` : L'identifiant de l'URL live enregistrée dans la base de données
                    - `u` (optionnel) : URL absolue pour les playlists imbriquées (usage interne)
                    
                    **Fonctionnalités :**
                    - Réécriture automatique des URLs de segments (.ts)
                    - Réécriture des URLs de playlists (.m3u8)
                    - Réécriture des clés de chiffrement (#EXT-X-KEY)
                    - Support des tags média (#EXT-X-MEDIA)
                    - Support des streams I-frame (#EXT-X-I-FRAME-STREAM-INF)
                    - Support des tags MAP (#EXT-X-MAP)
                    - Gestion CORS automatique
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Playlist M3U8 proxifiée avec succès",
                    content = @Content(mediaType = "application/vnd.apple.mpegurl")
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Erreur lors du traitement de la playlist",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "URL live non trouvée",
                    content = @Content
            )
    })
    public Mono<ResponseEntity<String>> proxyM3U8(
            @Parameter(description = "Identifiant unique de l'URL live", example = "url-123", required = true)
            @PathVariable String urlId,
            @Parameter(description = "URL absolue pour les playlists imbriquées (usage interne)", required = false)
            @RequestParam(value = "u", required = false) String overrideUrl,
            HttpServletRequest request) {

        try {
            String baseFetchUrl;

            if (overrideUrl != null && !overrideUrl.isBlank()) {
                baseFetchUrl = decode(overrideUrl, StandardCharsets.UTF_8);
                log.info("LiveUrl proxy - nested playlist request - urlId: {}, overrideUrl: {}", urlId, baseFetchUrl);
            } else {
                LiveUrl liveUrl = liveUrlService.findLiveUrlByUrlId(urlId);
                baseFetchUrl = liveUrl.getUrl();
                log.info("LiveUrl proxy - initial request - urlId: {}, baseUrl: {}", urlId, baseFetchUrl);
            }

            String serverUrl = ProxyHelper.buildServerUrl(request,protocol);

            return webClient.get()
                    .uri(baseFetchUrl)
                    .header("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(m3u8Content -> {
                        if (m3u8Content == null || m3u8Content.trim().isEmpty()) {
                            throw new IllegalArgumentException("Failed to fetch M3U8 content");
                        }

                        // Log first few lines of fetched content
                        if (log.isDebugEnabled()) {
                            String[] contentLines = m3u8Content.split("\n", 10);
                            log.debug("Fetched M3U8 content (first {} lines, total {} chars):", contentLines.length, m3u8Content.length());
                            for (int i = 0; i < Math.min(contentLines.length, 8); i++) {
                                log.debug("  Fetched line {}: {}", i + 1, contentLines[i]);
                            }
                        }

                        try {
                            String processedContent = processM3U8Content(m3u8Content, baseFetchUrl, serverUrl, urlId);
                            log.info("LiveUrl proxy - processed content length: {} chars, serverUrl: {}", processedContent.length(), serverUrl);

                            HttpHeaders headers = getCorsHeaders();
                            return ResponseEntity.ok()
                                    .headers(headers)
                                    .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                                    .body(processedContent);
                        } catch (Exception e) {
                            log.error("Error processing M3U8 content: {}", e.getMessage(), e);
                            throw new RuntimeException("Error processing M3U8 content", e);
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("LiveUrl proxy error - urlId: {}, msg: {}", urlId, e.getMessage(), e);
                        return Mono.just(ResponseEntity.badRequest().body("Error: " + e.getMessage()));
                    });

        } catch (Exception e) {
            log.error("LiveUrl proxy error - urlId: {}, msg: {}", urlId, e.getMessage(), e);
            return Mono.just(ResponseEntity.badRequest().body("Error: " + e.getMessage()));
        }
    }

    @RequestMapping(value = "/{urlId}", method = RequestMethod.OPTIONS)
    @Operation(summary = "Prévol pour CORS - Playlist M3U8", description = "Endpoint OPTIONS pour la gestion CORS")
    public ResponseEntity<Void> proxyM3U8Options(@PathVariable String urlId) {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
    }

    @RequestMapping(value = "/segment", method = RequestMethod.OPTIONS)
    @Operation(summary = "Prévol pour CORS - Segment", description = "Endpoint OPTIONS pour la gestion CORS")
    public ResponseEntity<Void> proxySegmentOptions() {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
    }

    @GetMapping("/segment")
    @Operation(
            summary = "Proxifier un segment vidéo/audio",
            description = """
                    Récupère un segment vidéo (.ts) ou audio depuis une URL externe et le renvoie 
                    via le proxy. Utilisé automatiquement par les playlists M3U8 réécrites.
                    
                    **Paramètres :**
                    - `u` : URL encodée du segment à proxifier
                    
                    **Note :** Cet endpoint est principalement utilisé en interne par les playlists 
                    M3U8 réécrites. Les URLs sont automatiquement générées lors du traitement des playlists.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Segment proxifié avec succès",
                    content = @Content(mediaType = "application/octet-stream")
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Erreur lors de la récupération du segment",
                    content = @Content
            )
    })
    public Mono<ResponseEntity<byte[]>> proxySegment(
            @Parameter(description = "URL encodée du segment à proxifier", example = "https%3A%2F%2Fexample.com%2Fsegment.ts", required = true)
            @RequestParam("u") String origin) {
        try {
            String fetchUrl = decode(origin, StandardCharsets.UTF_8);

            return webClient.get()
                    .uri(fetchUrl)
                    .header("Accept", "*/*")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                    .retrieve()
                    .toEntity(byte[].class)
                    .map(response -> {
                        MediaType contentType = response.getHeaders().getContentType();
                        MediaType finalType = contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM;

                        HttpHeaders headers = getCorsHeaders();
                        return ResponseEntity.ok()
                                .headers(headers)
                                .contentType(finalType)
                                .body(response.getBody());
                    })
                    .onErrorResume(e -> {
                        log.error("LiveUrl segment proxy error: {}", e.getMessage(), e);
                        return Mono.just(ResponseEntity.badRequest().body(null));
                    });

        } catch (Exception e) {
            log.error("LiveUrl segment proxy error: {}", e.getMessage(), e);
            return Mono.just(ResponseEntity.badRequest().body(null));
        }
    }

    private String processM3U8Content(String content, String baseUrl, String serverUrl, String urlId) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("M3U8 content is empty or null");
        }

        List<String> newLines = new ArrayList<>();
        String[] lines = content.split("\n");
        URL baseURL = new URL(baseUrl);

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            // Keep empty lines as they may be significant in M3U8 format
            if (line.trim().isEmpty()) {
                newLines.add("");
                continue;
            }

            if (line.startsWith("#")) {
                if (line.startsWith("#EXT-X-KEY:")) {
                    String modifiedLine = processKeyLine(line, baseURL, serverUrl);
                    newLines.add(modifiedLine);
                } else if (line.startsWith("#EXT-X-I-FRAME-STREAM-INF:")) {
                    String modifiedLine = processIFrameStreamInf(line, baseURL, serverUrl, urlId);
                    newLines.add(modifiedLine);
                } else if (line.startsWith("#EXT-X-MEDIA:")) {
                    String modifiedLine = processMediaTag(line, baseURL, serverUrl, urlId);
                    newLines.add(modifiedLine);
                } else if (line.startsWith("#EXT-X-MAP:")) {
                    String modifiedLine = processMapTag(line, baseURL, serverUrl);
                    newLines.add(modifiedLine);
                } else {
                    newLines.add(line);
                }
            } else {
                String modifiedLine = processMediaLine(line, baseURL, serverUrl, urlId);
                newLines.add(modifiedLine);
                // Log first few media lines to debug
                if (newLines.size() <= 15 && log.isDebugEnabled()) {
                    log.debug("Media line rewritten: '{}' -> '{}'", line, modifiedLine);
                }
            }
        }

        String result = String.join("\n", newLines);
        // Log sample of result for debugging
        if (log.isDebugEnabled()) {
            String[] resultLines = result.split("\n", 20);
            log.debug("Processed M3U8 sample (first {} lines):", resultLines.length);
            for (int i = 0; i < Math.min(resultLines.length, 15); i++) {
                log.debug("  Line {}: {}", i + 1, resultLines[i]);
            }
        }
        return result;
    }

    private String processKeyLine(String line, URL baseURL, String serverUrl) throws Exception {
        if (line == null || !line.contains("URI=\"")) {
            return line;
        }

        int urlStart = line.indexOf("URI=\"") + 5;
        int urlEnd = line.indexOf("\"", urlStart);
        if (urlStart > 4 && urlEnd > urlStart) {
            String keyUrl = line.substring(urlStart, urlEnd);
            URL fullUrl = new URL(baseURL, keyUrl);
            String proxyUrl = serverUrl + "/m3u8-encoder/api/v2/live-url/proxy/segment?u=" +
                    URLEncoder.encode(fullUrl.toString(), StandardCharsets.UTF_8);
            return line.substring(0, urlStart) + proxyUrl + line.substring(urlEnd);
        }
        return line;
    }

    private String processIFrameStreamInf(String line, URL baseURL, String serverUrl, String urlId) throws Exception {
        int uriIdx = line.indexOf("URI=\"");
        if (uriIdx < 0) {
            return line;
        }
        int start = uriIdx + 5;
        int end = line.indexOf('"', start);
        if (end <= start) {
            return line;
        }
        String uriValue = line.substring(start, end);
        boolean isPlaylist = uriValue.toLowerCase().contains(".m3u8");
        URL fullUrl = new URL(baseURL, uriValue);
        if (!isPlaylist) {
            isPlaylist = fullUrl.toString().toLowerCase().contains(".m3u8");
        }
        String proxied = isPlaylist
                ? serverUrl + "/m3u8-encoder/api/v2/live-url/proxy/" + urlId + "?u=" +
                URLEncoder.encode(fullUrl.toString(), StandardCharsets.UTF_8)
                : serverUrl + "/m3u8-encoder/api/v2/live-url/proxy/segment?u=" +
                URLEncoder.encode(fullUrl.toString(), StandardCharsets.UTF_8);
        return line.substring(0, start) + proxied + line.substring(end);
    }

    private String processMediaTag(String line, URL baseURL, String serverUrl, String urlId) throws Exception {
        int uriIdx = line.indexOf("URI=\"");
        if (uriIdx < 0) {
            return line;
        }
        int start = uriIdx + 5;
        int end = line.indexOf('"', start);
        if (end <= start) {
            return line;
        }
        String uriValue = line.substring(start, end);
        boolean isPlaylist = uriValue.toLowerCase().contains(".m3u8");
        URL fullUrl = new URL(baseURL, uriValue);
        if (!isPlaylist) {
            isPlaylist = fullUrl.toString().toLowerCase().contains(".m3u8");
        }
        String proxied = isPlaylist
                ? serverUrl + "/m3u8-encoder/api/v2/live-url/proxy/" + urlId + "?u=" +
                URLEncoder.encode(fullUrl.toString(), StandardCharsets.UTF_8)
                : serverUrl + "/m3u8-encoder/api/v2/live-url/proxy/segment?u=" +
                URLEncoder.encode(fullUrl.toString(), StandardCharsets.UTF_8);
        return line.substring(0, start) + proxied + line.substring(end);
    }

    private String processMapTag(String line, URL baseURL, String serverUrl) throws Exception {
        int uriIdx = line.indexOf("URI=\"");
        if (uriIdx < 0) {
            return line;
        }
        int start = uriIdx + 5;
        int end = line.indexOf('"', start);
        if (end <= start) {
            return line;
        }
        String uriValue = line.substring(start, end);
        URL fullUrl = new URL(baseURL, uriValue);
        String proxied = serverUrl + "/m3u8-encoder/api/v2/live-url/proxy/segment?u=" +
                URLEncoder.encode(fullUrl.toString(), StandardCharsets.UTF_8);
        return line.substring(0, start) + proxied + line.substring(end);
    }

    private String processMediaLine(String line, URL baseURL, String serverUrl, String urlId) throws Exception {
        if (line == null || line.trim().isEmpty()) {
            return line;
        }

        String trimmedLine = line.trim();

        // First check the original line for .m3u8 (before URL resolution)
        // This catches cases like "video=285868.m3u8?context=..." where .m3u8 is in the filename
        boolean isPlaylist = trimmedLine.toLowerCase().contains(".m3u8");

        URL segmentUrl;
        try {
            if (trimmedLine.startsWith("http://") || trimmedLine.startsWith("https://")) {
                segmentUrl = new URL(trimmedLine);
            } else {
                // Relative URL - resolve against base URL
                segmentUrl = new URL(baseURL, trimmedLine);
                // Double-check the resolved URL
                if (!isPlaylist) {
                    String resolvedUrl = segmentUrl.toString().toLowerCase();
                    isPlaylist = resolvedUrl.contains(".m3u8");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse URL from line '{}' with base '{}': {}", trimmedLine, baseURL, e.getMessage());
            return line; // Return original line if URL parsing fails
        }

        String result;
        if (isPlaylist) {
            result = serverUrl + "/m3u8-encoder/api/v2/live-url/proxy/" + urlId + "?u=" +
                    URLEncoder.encode(segmentUrl.toString(), StandardCharsets.UTF_8);
            log.debug("Rewritten playlist: '{}' -> '{}'", trimmedLine, result);
        } else {
            result = serverUrl + "/m3u8-encoder/api/v2/live-url/proxy/segment?u=" +
                    URLEncoder.encode(segmentUrl.toString(), StandardCharsets.UTF_8);
        }
        return result;
    }


}