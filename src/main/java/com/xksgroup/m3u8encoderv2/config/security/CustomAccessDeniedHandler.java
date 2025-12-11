package com.xksgroup.m3u8encoderv2.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xksgroup.m3u8encoderv2.config.security.response.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler personnalisé pour gérer les erreurs d'autorisation (403).
 * Intercepte les erreurs d'accès refusé lorsque l'utilisateur n'a pas les permissions nécessaires.
 */
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                      HttpServletResponse response,
                      AccessDeniedException accessDeniedException) throws IOException, ServletException {
        
        log.warn("Accès refusé: {} - Path: {} - User: {}", 
                accessDeniedException.getMessage(), 
                request.getRequestURI(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous");
        
        // Préparer les détails de l'erreur
        Map<String, Object> details = new HashMap<>();
        details.put("reason", "Vous n'avez pas les permissions nécessaires pour accéder à cette ressource.");
        details.put("requiredRoles", "Vérifiez que vous avez les rôles appropriés (ROLE_USER, ROLE_ADMIN, etc.)");
        details.put("path", getCurrentPath(request));
        
        if (request.getUserPrincipal() != null) {
            details.put("authenticatedUser", request.getUserPrincipal().getName());
        }
        
        // Créer la réponse d'erreur standardisée
        ApiError apiError = new ApiError(
                false,
                HttpServletResponse.SC_FORBIDDEN,
                "AccessDenied",
                "Accès refusé",
                details,
                OffsetDateTime.now().toString(),
                getCurrentPath(request)
        );
        
        // Configurer la réponse HTTP
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
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
}

