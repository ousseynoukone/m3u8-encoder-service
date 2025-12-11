package com.xksgroup.m3u8encoderv2.controller;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xksgroup.m3u8encoderv2.model.RequestIssuer;
import com.xksgroup.m3u8encoderv2.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Streaming de Jobs",
        description = "Fournit des mises à jour en temps réel sur les jobs d'encodage actifs en utilisant Server-Sent Events (SSE)."
)
@RestController
@RequestMapping("m3u8-encoder/api/v2")
@RequiredArgsConstructor
public class See {

    private final EventService eventService;

    @Operation(
            summary = "Diffuser les mises à jour de statut des jobs actifs",
            description = """
                Cet endpoint utilise **Server-Sent Events (SSE)** pour diffuser en continu des mises à jour en temps réel 
                sur les jobs d'encodage ou de traitement actifs.  
                
                Les clients peuvent se connecter et écouter les événements sans avoir besoin d'interroger le serveur.  
                
                Chaque événement inclut :
                - Un ID d'événement unique  
                - Un nom d'événement (`heartbeat` ou `job-update`)  
                - L'heure actuelle du serveur ou la progression du job  
                
                La connexion reste ouverte jusqu'à ce que le client se déconnecte ou qu'un timeout se produise.
                """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Flux SSE démarré avec succès (Content-Type: text/event-stream)",
                            content = @Content(
                                    mediaType = "text/event-stream",
                                    schema = @Schema(implementation = String.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erreur serveur interne ou échec du flux",
                            content = @Content(schema = @Schema(implementation = String.class))
                    )
            }
    )
    @GetMapping(value = "/job-progression-sse", produces = "text/event-stream")
    public SseEmitter streamSseMvc(@AuthenticationPrincipal Jwt principal, @RequestParam(required = false, defaultValue = "") String jobId) {
        // Get user agent info from headers
        RequestIssuer issuer = RequestIssuer.builder()
                .email(principal.getClaimAsString("email"))
                .name(principal.getClaimAsString("name"))
                .issuerId(principal.getClaimAsString("sub"))
                .scope(principal.getClaimAsString("scope"))
                .build();

        return eventService.subscribe(issuer.getIssuerId(), jobId);
    }
}
