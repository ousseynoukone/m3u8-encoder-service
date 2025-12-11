package com.xksgroup.m3u8encoderv2.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xksgroup.m3u8encoderv2.config.security.response.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler personnalisé pour gérer les erreurs d'authentification (401).
 * Intercepte les erreurs liées aux tokens JWT (manquant, invalide, expiré).
 */
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, 
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {
        
        log.warn("Erreur d'authentification: {} - Path: {}", authException.getMessage(), request.getRequestURI());
        
        // Déterminer le type d'erreur et le message
        String errorType = "AuthenticationException";
        String message = "Authentification requise";
        Map<String, Object> details = new HashMap<>();
        
        String exceptionMessage = authException.getMessage();
        String exceptionClass = authException.getClass().getSimpleName();
        
        // Vérifier le type d'exception et le message pour une détection plus précise
        if (exceptionMessage != null) {
            String lowerMessage = exceptionMessage.toLowerCase();
            
            // Détection des tokens expirés (plusieurs variantes possibles)
            if (lowerMessage.contains("expired") 
                    || lowerMessage.contains("jwt expired")
                    || lowerMessage.contains("token expired")
                    || lowerMessage.contains("exp")
                    || exceptionClass.contains("Expired")
                    || (lowerMessage.contains("invalid") && lowerMessage.contains("exp"))) {
                errorType = "TokenExpired";
                message = "Le token d'authentification a expiré";
                details.put("reason", "Token expiré. Veuillez vous reconnecter ou rafraîchir votre token.");
                details.put("solution", "Utilisez l'endpoint /api/v2/auth/refresh-token pour obtenir un nouveau token, ou reconnectez-vous via /api/v2/auth/login");
            } 
            // Détection des tokens malformés
            else if (lowerMessage.contains("malformed") 
                    || lowerMessage.contains("invalid format")
                    || lowerMessage.contains("parse")
                    || exceptionClass.contains("Malformed")) {
                errorType = "TokenMalformed";
                message = "Token d'authentification invalide";
                details.put("reason", "Le format du token est invalide.");
            } 
            // Détection des erreurs de signature
            else if (lowerMessage.contains("signature") 
                    || lowerMessage.contains("signature verification")
                    || lowerMessage.contains("invalid signature")
                    || exceptionClass.contains("Signature")) {
                errorType = "TokenSignatureInvalid";
                message = "Signature du token invalide";
                details.put("reason", "La signature du token ne peut pas être vérifiée.");
            } 
            // Détection des tokens manquants
            else if (lowerMessage.contains("bearer") 
                    || lowerMessage.contains("token")
                    || lowerMessage.contains("missing")
                    || lowerMessage.contains("empty")
                    || exceptionClass.contains("Missing")) {
                errorType = "TokenMissing";
                message = "Token d'authentification manquant";
                details.put("reason", "Aucun token d'authentification fourni. Veuillez inclure un token Bearer dans l'en-tête Authorization.");
                details.put("format", "Authorization: Bearer <votre-token>");
            } 
            // Détection des erreurs de validation JWT
            else if (lowerMessage.contains("invalid") 
                    || lowerMessage.contains("validation")
                    || lowerMessage.contains("rejected")) {
                errorType = "TokenInvalid";
                message = "Token d'authentification invalide";
                // Traduire les messages d'erreur courants en français
                String translatedReason = translateErrorMessage(exceptionMessage);
                details.put("reason", translatedReason);
            } 
            // Cas par défaut
            else {
                // Traduire le message d'exception en français si possible
                String translatedReason = translateErrorMessage(exceptionMessage);
                details.put("reason", translatedReason);
                details.put("exceptionType", exceptionClass);
            }
        } else {
            details.put("reason", "Erreur d'authentification non spécifiée");
            details.put("exceptionType", exceptionClass);
        }
        
        details.put("path", getCurrentPath(request));
        
        // Créer la réponse d'erreur standardisée
        ApiError apiError = new ApiError(
                false,
                HttpServletResponse.SC_UNAUTHORIZED,
                errorType,
                message,
                details,
                OffsetDateTime.now().toString(),
                getCurrentPath(request)
        );
        
        // Configurer la réponse HTTP
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // Écrire la réponse JSON
        objectMapper.writeValue(response.getWriter(), apiError);
    }
    
    private String getCurrentPath(HttpServletRequest request) {
        try {
            return ServletUriComponentsBuilder.fromRequestUri(request)
                    .build()
                    .getPath();
        } catch (Exception e) {
            return request.getRequestURI();
        }
    }
    
    /**
     * Traduit les messages d'erreur courants de l'anglais vers le français
     */
    private String translateErrorMessage(String englishMessage) {
        if (englishMessage == null) {
            return "Erreur d'authentification non spécifiée";
        }
        
        String lowerMessage = englishMessage.toLowerCase();
        
        // Traductions courantes
        if (lowerMessage.contains("jwt expired") || lowerMessage.contains("token expired")) {
            return "Le token JWT a expiré";
        }
        if (lowerMessage.contains("invalid token") || lowerMessage.contains("token invalid")) {
            return "Token invalide";
        }
        if (lowerMessage.contains("malformed jwt") || lowerMessage.contains("malformed token")) {
            return "Format de token invalide";
        }
        if (lowerMessage.contains("signature verification failed")) {
            return "Échec de la vérification de la signature";
        }
        if (lowerMessage.contains("invalid signature")) {
            return "Signature invalide";
        }
        if (lowerMessage.contains("token not found") || lowerMessage.contains("missing token")) {
            return "Token manquant";
        }
        if (lowerMessage.contains("unauthorized") || lowerMessage.contains("not authenticated")) {
            return "Non authentifié";
        }
        if (lowerMessage.contains("access denied") || lowerMessage.contains("forbidden")) {
            return "Accès refusé";
        }
        if (lowerMessage.contains("invalid request")) {
            return "Requête invalide";
        }
        if (lowerMessage.contains("authentication failed")) {
            return "Échec de l'authentification";
        }
        
        // Si aucun pattern ne correspond, retourner le message original
        // mais essayer de le rendre plus lisible
        return englishMessage;
    }
}

