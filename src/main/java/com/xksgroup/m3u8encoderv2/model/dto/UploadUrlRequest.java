package com.xksgroup.m3u8encoderv2.model.dto;

import com.xksgroup.m3u8encoderv2.model.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Requête de téléversement par URL")
public class UploadUrlRequest {
    @NotBlank(message = "title is required")
    @Schema(
        description = "Titre du contenu. Utilisé pour générer le slug (identifiant URL-friendly). " +
                     "Les contenus avec le même titre seront regroupés dans la même collection.",
        example = "Mon Super Film (2024)"
    )
    private String title;

    @NotBlank(message = "url is required")
    @Schema(
        description = "URL complète du fichier média à télécharger. Doit être accessible publiquement. " +
                     "Formats supportés : MP4, MKV, AVI, MOV, MP3, AAC, etc.",
        example = "https://example.com/videos/my-video.mp4"
    )
    private String url;

    @NotNull(message = "resourceType is required")
    @Schema(
        description = "Type de ressource. Détermine les paramètres d'encodage appropriés.",
        example = "VIDEO",
        allowableValues = {"VIDEO", "AUDIO"}
    )
    private ResourceType resourceType;
}

