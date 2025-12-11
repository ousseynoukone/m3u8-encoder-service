package com.xksgroup.m3u8encoderv2.controller;


import com.xksgroup.m3u8encoderv2.model.LiveUrl;
import com.xksgroup.m3u8encoderv2.service.LiveUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/m3u8-encoder/api/v2/live-url")
@RequiredArgsConstructor
@Tag(name = "Gestion des URLs Live", description = "APIs CRUD pour gérer les URLs de streaming live")
public class LiveUrlController {

    private final LiveUrlService liveUrlService;


    @PostMapping
    @Operation(
            summary = "Créer une nouvelle URL live",
            description = "Crée une nouvelle entrée LiveUrl dans la base de données avec l'URL de streaming fournie"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URL live créée avec succès",
                    content = @Content(schema = @Schema(implementation = LiveUrl.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Données invalides",
                    content = @Content
            )
    })
    public ResponseEntity<LiveUrl> createLiveUrl(
            @Parameter(description = "Objet LiveUrl contenant l'URL à créer", required = true)
            @Valid @RequestBody LiveUrl liveUrl) {
        LiveUrl result = liveUrlService.createLiveUrl(liveUrl);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @Operation(
            summary = "Obtenir toutes les URLs live",
            description = "Récupère la liste de toutes les URLs live enregistrées dans la base de données"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Liste des URLs live récupérée avec succès",
            content = @Content(schema = @Schema(implementation = LiveUrl.class))
    )
    public ResponseEntity<java.util.List<LiveUrl>> getAllLiveUrls() {
        return ResponseEntity.ok(liveUrlService.findAllLiveUrls());
    }

    @GetMapping("/{urlId}")
    @Operation(
            summary = "Obtenir une URL live par son ID",
            description = "Récupère une URL live spécifique en utilisant son urlId"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URL live trouvée",
                    content = @Content(schema = @Schema(implementation = LiveUrl.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "URL live non trouvée",
                    content = @Content
            )
    })
    public ResponseEntity<LiveUrl> getLiveUrlByUrlId(
            @Parameter(description = "Identifiant unique de l'URL live", example = "url-123", required = true)
            @PathVariable String urlId) {
        return ResponseEntity.ok(liveUrlService.findLiveUrlByUrlId(urlId));
    }

    @DeleteMapping("/{urlId}")
    @Operation(
            summary = "Supprimer une URL live",
            description = "Supprime une URL live de la base de données en utilisant son urlId"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URL live supprimée avec succès",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "URL live non trouvée",
                    content = @Content
            )
    })
    public ResponseEntity<Map<String, Object>> deleteLiveUrlByUrlId(
            @Parameter(description = "Identifiant unique de l'URL live à supprimer", example = "url-123", required = true)
            @PathVariable String urlId) {
        boolean deleted = liveUrlService.deleteLiveUrlByUrlId(urlId);
        Map<String, Object> body = new HashMap<>();
        body.put("deleted", deleted);
        body.put("urlId", urlId);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{urlId}")
    @Operation(
            summary = "Mettre à jour une URL live",
            description = "Met à jour les informations d'une URL live existante en utilisant son urlId"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URL live mise à jour avec succès",
                    content = @Content(schema = @Schema(implementation = LiveUrl.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "URL live non trouvée",
                    content = @Content
            )
    })
    public ResponseEntity<LiveUrl> updateLiveUrl(
            @Parameter(description = "Identifiant unique de l'URL live à mettre à jour", example = "url-123", required = true)
            @PathVariable String urlId,
            @Parameter(description = "Objet LiveUrl contenant les nouvelles données", required = true)
            @RequestBody LiveUrl updated) {
        LiveUrl result = liveUrlService.updateLiveUrl(urlId, updated);
        return ResponseEntity.ok(result);
    }
}
