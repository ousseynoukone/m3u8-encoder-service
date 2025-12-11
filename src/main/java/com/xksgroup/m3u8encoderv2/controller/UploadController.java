package com.xksgroup.m3u8encoderv2.controller;

import com.xksgroup.m3u8encoderv2.model.Job.Job;
import com.xksgroup.m3u8encoderv2.model.RequestIssuer;
import com.xksgroup.m3u8encoderv2.model.ResourceType;
import com.xksgroup.m3u8encoderv2.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;
import com.xksgroup.m3u8encoderv2.service.helper.ResumableDownloader;
import java.util.concurrent.CompletableFuture;
import com.xksgroup.m3u8encoderv2.model.dto.UploadUrlRequest;
import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("m3u8-encoder/api/v2/upload")
@RequiredArgsConstructor
@Tag(name = "Upload", description = "Upload unique qui génère du HLS ABR et téléverse vers le stockage cloud")
public class UploadController {

    private final JobService jobService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Téléverser un fichier vidéo/audio et démarrer la génération HLS ABR",
        description = """
            Téléverse un fichier vidéo ou audio et renvoie immédiatement un ID de job. Le fichier sera traité de manière asynchrone pour générer plusieurs variantes de qualité et téléverser vers le stockage cloud.
            
            ## 🏗️ Architecture : Génération de slug basée sur le titre
            
            **Fonctionnement :**
            1. **Saisie du titre** : Vous fournissez un titre lisible (ex: "Les Avengers : Endgame (2019)")
            2. **Génération automatique du slug** : Le système convertit le titre en slug compatible URL (ex: "les-avengers-endgame-2019")
            3. **ID de job unique** : Chaque téléversement obtient un ID de job unique (ex: "job-abc123") indépendamment du titre
            4. **Regroupement de contenu** : Même titre = Même slug, permettant de regrouper plusieurs épisodes/versions ensemble
            
            **Exemples de scénarios :**
            - **Épisode 1** : Titre "Les Avengers" → Slug "les-avengers" → Job ID "job-abc123"
            - **Épisode 2** : Titre "Les Avengers" → Slug "les-avengers" → Job ID "job-def456"
            - **Version Director's Cut** : Titre "Les Avengers" → Slug "les-avengers" → Job ID "job-ghi789"
            
            **Avantages :**
            • **Découverte de contenu** : Tous les épisodes de "Les Avengers" partagent le slug "les-avengers"
            • **Accès précis** : Chaque épisode accessible via son ID de job unique
            • **Organisation logique** : Chemin de stockage R2 : `movie/les-avengers/job-abc123/`
            • **Pas de slugs dupliqués** : Le système gère gracieusement plusieurs téléversements avec le même titre
            
            **Structure des URLs :**
            - **Playlist maître** : `/proxy/job-abc123` (pas `/proxy/les-avengers`)
            - **Playlists de variantes** : `/proxy/job-abc123/v0/index.m3u8`
            - **Vérification du statut** : `/v2/status/les-avengers` (affiche tous les épisodes)
            
        

            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202", 
            description = "Job accepté et traitement démarré",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UploadResponse.class),
                examples = @ExampleObject(
                    name = "Réponse de succès",
                    value = """
                    {
                        "status": "accepted",
                        "message": "Job de téléversement créé avec succès",
                        "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
                        "slug": "les-avengers-endgame-2019",
                        "title": "Les Avengers : Endgame (2019)",
                        "resourceType": "VIDEO",
                        "jobStatus": "PENDING",
                        "progress": {
                            "totalSegments": 0,
                            "completedSegments": 0,
                            "failedSegments": 0,
                            "uploadingSegments": 0,
                            "pendingSegments": 0
                        },
                        "statusUrl": "/api/v2/m3u8-encoder/status/les-avengers-endgame-2019",
                        "jobUrl": "/api/v2/m3u8-encoder/jobs/job-123e4567-e89b-12d3-a456-426614174000",
                        "estimatedCompletionTime": "5-10 minutes",
                        "note": "Utilisez jobId pour un accès précis au contenu, slug pour la découverte de contenu"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Requête invalide - fichier ou paramètres invalides",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(
                        name = "Fichier vide",
                        value = """
                        {
                            "error": "file is empty",
                            "message": "Le fichier téléversé ne contient aucune donnée"
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Type de fichier invalide - Playlist",
                        value = """
                        {
                            "error": "invalid file type",
                            "message": "Impossible de traiter les fichiers de playlist (.m3u8/.m3u). Veuillez téléverser le fichier média original (vidéo/audio) au lieu des fichiers de playlist.",
                            "uploadedFile": "Memory Reboot_a2.m3u8",
                            "supportedFormats": "Vidéo : .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | Audio : .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma"
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Type de fichier invalide - Texte",
                        value = """
                        {
                            "error": "invalid file type",
                            "message": "Impossible de traiter les fichiers texte. Veuillez téléverser un fichier média valide (vidéo/audio).",
                            "uploadedFile": "config.txt",
                            "supportedFormats": "Vidéo : .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | Audio : .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma"
                        }
                        """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "413", 
            description = "Fichier trop volumineux",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Fichier trop volumineux",
                    value = """
                    {
                        "error": "File size exceeds maximum limit",
                        "message": "Veuillez téléverser un fichier plus petit (max 6GB)"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "Erreur serveur interne",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Erreur serveur",
                    value = """
                    {
                        "error": "Failed to create job",
                        "message": "Une erreur inattendue s'est produite lors du traitement de votre requête"
                    }
                    """
                )
            )
        )
    })
    public ResponseEntity<Object> upload(
            @Parameter(description = "Fichier vidéo ou audio à téléverser", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(
                description = """
                    Titre du contenu. Ce titre sera automatiquement converti en slug compatible URL.
                    
                    **Règles de génération de slug :**
                    • Converti en minuscules
                    • Caractères spéciaux remplacés par des tirets
                    • Espaces/tirets multiples réduits à un seul tiret
                    • Tirets en début/fin supprimés
                    
                    **Exemples :**
                    • "Les Avengers : Endgame (2019)" → slug "les-avengers-endgame-2019"
                    • "Ma Super Vidéo !!!" → slug "ma-super-video"
                    • "Épisode 1 - Le Commencement" → slug "episode-1-le-commencement"
                    
                    **Important :** Même titre = Même slug. Plusieurs téléversements avec le même titre seront regroupés ensemble.
                    """, 
                required = true, 
                example = "Les Avengers : Endgame (2019)"
            )
            @RequestParam("title") String title,
            
            @Parameter(description = "Type de contenu", required = true, example = "VIDEO")
            @RequestParam("resourceType") ResourceType resourceType,
            @AuthenticationPrincipal Jwt principal
            ) {
        
        log.info("Upload request received - Filename: {}, Size: {} bytes, Title: '{}', ResourceType: {}",
                file.getOriginalFilename(), file.getSize(), title, resourceType);

        try {
            // Validate file
            if (file.isEmpty()) {
                log.warn("Upload rejected - File is empty for title: '{}'", title);
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "file is empty");
                errorResponse.put("message", "The uploaded file contains no data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            // Validate file type immediately
            String fileName = file.getOriginalFilename();
            if (fileName != null) {
                String lowerFileName = fileName.toLowerCase();
                
                // Check for invalid file types
                if (lowerFileName.endsWith(".m3u8") || lowerFileName.endsWith(".m3u")) {
                    log.warn("Upload rejected - Playlist file detected: {} for title: '{}'", fileName, title);
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "invalid file type");
                    errorResponse.put("message", "Cannot process playlist files (.m3u8/.m3u). Please upload the original media file (video/audio) instead of playlist files.");
                    errorResponse.put("uploadedFile", fileName);
                    errorResponse.put("supportedFormats", "Video: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | Audio: .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }
                
                if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".log") || 
                    lowerFileName.endsWith(".json") || lowerFileName.endsWith(".xml")) {
                    log.warn("Upload rejected - Text file detected: {} for title: '{}'", fileName, title);
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "invalid file type");
                    errorResponse.put("message", "Cannot process text files. Please upload a valid media file (video/audio).");
                    errorResponse.put("uploadedFile", fileName);
                    errorResponse.put("supportedFormats", "Video: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | Audio: .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }

                String mimeType = file.getContentType();

                boolean isVideoMismatch = mimeType.startsWith("video/") && resourceType != ResourceType.VIDEO;
                boolean isAudioMismatch = mimeType.startsWith("audio/") && resourceType != ResourceType.AUDIO;

                if (isVideoMismatch || isAudioMismatch) {
                    String providedType = mimeType.startsWith("video/") ? "video" : "audio";
                    String expectedType = isVideoMismatch ? "audio" : "video"; // what the system expects

                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Invalid file type");
                    errorResponse.put("message", String.format(
                            "You provided a %s file, but a %s file was expected. Please upload a valid %s file.",
                            providedType, expectedType, expectedType
                    ));
                    errorResponse.put("supportedFormats",
                            "Video: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm, .m4v | " +
                                    "Audio: .mp3, .wav, .m4a, .aac, .flac, .ogg, .wma"
                    );

                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }

            }

            
            // Get user agent info from headers
            RequestIssuer userAgent = RequestIssuer.builder()
                    .email(principal.getClaimAsString("email"))
                    .name(principal.getClaimAsString("name"))
                    .issuerId(principal.getClaimAsString("sub"))
                    .scope(principal.getClaimAsString("scope"))
                    .build();

            Job job = jobService.createJob(title, resourceType, file.getOriginalFilename(),
                                         file.getSize(), file.getContentType(), userAgent);

            log.info("Job created successfully: {} for title: '{}'", job.getJobId(), title);

            // Save file temporarily with unique jobId prefix to avoid collisions
            Path tmpDir = Paths.get("upload-v2");
            Files.createDirectories(tmpDir);

            String originalFilename = file.getOriginalFilename() != null ?
                Path.of(file.getOriginalFilename()).getFileName().toString() : "upload.bin";
            
            // Make filename unique by prefixing with jobId to prevent file locking conflicts
            String uniqueFilename = job.getJobId() + "_" + originalFilename;
            Path src = tmpDir.resolve(uniqueFilename);
            
            Files.copy(file.getInputStream(), src, StandardCopyOption.REPLACE_EXISTING);

            // Start async processing
            jobService.processJobAsync(job, src);

            // Return immediate response

            Map<String, Object> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("message", "Upload job created successfully");
            response.put("jobId", job.getJobId());
            response.put("slug", job.getSlug());
            response.put("title", job.getTitle());
            response.put("resourceType", job.getResourceType().name());
            response.put("jobStatus", job.getStatus().name());
            response.put("statusUrl", "/api/v2/m3u8-encoder/status/" + job.getSlug());
            response.put("jobUrl", "/api/v2/m3u8-encoder/jobs/" + job.getJobId());

            log.info("Upload job accepted - Job: {}, Slug: '{}' - Processing started in background",
                     job.getJobId(), job.getSlug());
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            log.error("Failed to create upload job for file: {} with title: '{}' - Error: {}",
                     file.getOriginalFilename(), title, e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to create job");
            errorResponse.put("message", "An unexpected error occurred while processing your request");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Upload par URL : Télécharge un fichier distant, suit la progression du téléchargement comme étape du job, puis encode.
     */
    @PostMapping(path = "/url", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Téléverser vidéo/audio par URL et démarrer la génération HLS ABR",
        description = """
            ## 📥 Téléversement par URL - Fonctionnement détaillé
            
            Cet endpoint permet de télécharger un fichier média (vidéo ou audio) depuis une URL distante, 
            de suivre la progression du téléchargement, puis de lancer automatiquement l'encodage HLS ABR 
            et le téléversement vers le stockage cloud.
            
            ### 🔄 Processus en plusieurs étapes
            
            **1. Création du Job (Immédiat)**
            - Un job est créé immédiatement avec le statut `DOWNLOADING`
            - Un `jobId` unique est généré (ex: `job-123e4567-e89b-12d3-a456-426614174000`)
            - Un `slug` est généré à partir du titre (ex: "mon-super-film" → slug: `mon-super-film`)
            - La réponse est renvoyée immédiatement avec le `jobId` et les URLs de suivi
            
            **2. Analyse des en-têtes HTTP (HEAD Request)**
            - Le système effectue une requête HEAD vers l'URL pour récupérer les métadonnées :
              - **Nom du fichier** : Extrait de l'en-tête `Content-Disposition` ou du chemin de l'URL
              - **Taille du fichier** : Extrait de l'en-tête `Content-Length`
              - **Type MIME** : Extrait de l'en-tête `Content-Type`
            - Ces informations sont stockées dans le job pour suivi et affichage
            
            **3. Téléchargement asynchrone avec progression**
            - Le téléchargement démarre de manière asynchrone (non-bloquant)
            - Utilise un **téléchargeur résumable** pour gérer les interruptions
            - La progression est mise à jour en temps réel :
              - Pourcentage de téléchargement (0-100%)
              - Octets téléchargés / Total
              - Statut visible via l'endpoint de statut du job
            
            **4. Finalisation du téléchargement**
            - Une fois le téléchargement terminé :
              - Le type MIME réel est détecté via `Files.probeContentType()`
              - La taille finale du fichier est vérifiée
              - Le statut du job passe à `PENDING`
            
            **5. Encodage et téléversement (Automatique)**
            - Le fichier téléchargé est automatiquement traité comme un upload de fichier :
              - Génération des variantes de qualité HLS (ABR)
              - Découpage en segments
              - Génération des playlists M3U8
              - Téléversement vers Cloudflare R2
              - Génération des URLs signées sécurisées
            
            ### 📋 Paramètres requis
            
            - **`url`** (String, requis) : URL complète du fichier média à télécharger
              - Doit être accessible publiquement ou avec authentification
              - Formats supportés : MP4, MKV, AVI, MOV, MP3, AAC, etc.
              - Exemple : `https://example.com/videos/my-video.mp4`
            
            - **`title`** (String, requis) : Titre du contenu
              - Utilisé pour générer le slug (identifiant URL-friendly)
              - **Important** : Les contenus avec le même titre seront regroupés dans la même collection
              - Exemple : `"Mon Super Film (2024)"`
            
            - **`resourceType`** (Enum, requis) : Type de ressource
              - Valeurs possibles : `VIDEO` ou `AUDIO`
              - Détermine les paramètres d'encodage appropriés
            
            ### 🎯 Regroupement par titre
            
            **Comportement important** : Les contenus avec le même titre sont automatiquement regroupés.
            
            **Exemple pratique** :
            - Upload 1 : `title: "Les Aventures"` → `slug: "les-aventures"` → `jobId: "job-abc123"`
            - Upload 2 : `title: "Les Aventures"` → `slug: "les-aventures"` → `jobId: "job-def456"`
            - Upload 3 : `title: "Les Aventures"` → `slug: "les-aventures"` → `jobId: "job-ghi789"`
            
            **Résultat** :
            - Tous les jobs partagent le même slug : `"les-aventures"`
            - L'endpoint `/api/v2/m3u8-encoder/status/les-aventures` affiche tous les jobs
            - Chaque job reste accessible individuellement via son `jobId`
            - Le stockage R2 organise les fichiers : `content/les-aventures/job-abc123/`, `content/les-aventures/job-def456/`, etc.
            
            ### 📊 Suivi de progression
            
            **Statuts du job** :
            1. `DOWNLOADING` : Téléchargement en cours depuis l'URL
            2. `PENDING` : Téléchargement terminé, en attente d'encodage
            3. `PROCESSING` : Encodage HLS en cours
            4. `UPLOADING` : Téléversement vers R2 en cours
            5. `COMPLETED` : Terminé avec succès
            6. `FAILED` : Échec (avec message d'erreur)
            
            **Endpoints de suivi** :
            - **Statut par slug** : `GET /api/v2/m3u8-encoder/status/{slug}`
              - Affiche tous les jobs avec le même titre/slug
            - **Détails du job** : `GET /api/v2/m3u8-encoder/jobs/{jobId}`
              - Informations détaillées sur un job spécifique
              - Progression en temps réel
              - Métadonnées du fichier
            
            ### 🔒 Sécurité et authentification
            
            L'identité utilisateur est extraite du token (JWT) par la gateway ou les filtres en amont. Aucun header `x-auth-*` n'est requis ni consommé directement par ce service.
            
            ### ⚠️ Gestion des erreurs
            
            **Erreurs possibles** :
            - **400 Bad Request** : Paramètres manquants (`url`, `title`, ou `resourceType`)
            - **500 Internal Server Error** : Échec du téléchargement ou de l'encodage
            - Le job passe en statut `FAILED` avec un message d'erreur détaillé
            
            **Résilience** :
            - Le téléchargeur supporte la reprise de téléchargement (résumable)
            - Les erreurs de téléchargement sont capturées et enregistrées dans le job
            - Le statut d'erreur est accessible via l'endpoint de statut
            
            ### 📝 Exemple de requête
            
            ```json
            {
              "url": "https://example.com/videos/my-video.mp4",
              "title": "Mon Super Film (2024)",
              "resourceType": "VIDEO"
            }
            ```
            
            ### 📤 Exemple de réponse
            
            ```json
            {
              "status": "accepted",
              "message": "URL upload job created successfully",
              "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
              "slug": "mon-super-film-2024",
              "title": "Mon Super Film (2024)",
              "resourceType": "VIDEO",
              "jobStatus": "DOWNLOADING",
              "statusUrl": "/api/v2/m3u8-encoder/status/mon-super-film-2024",
              "jobUrl": "/api/v2/m3u8-encoder/jobs/job-123e4567-e89b-12d3-a456-426614174000"
            }
            ```
            
            ### 💡 Bonnes pratiques
            
            1. **Utilisez des titres descriptifs** : Ils génèrent des slugs lisibles
            2. **Vérifiez l'accessibilité de l'URL** : Assurez-vous que l'URL est accessible publiquement
            3. **Surveillez la progression** : Utilisez les endpoints de statut pour suivre l'avancement
            4. **Groupez les contenus similaires** : Utilisez le même titre pour regrouper des épisodes/saisons
            5. **Gérez les erreurs** : Vérifiez régulièrement le statut pour détecter les échecs
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Job accepté et traitement démarré",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UploadResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Requête invalide - Paramètres manquants ou invalides"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erreur serveur - Échec du téléchargement ou de l'encodage"
        )
    })
    public ResponseEntity<Object> uploadUrl(
            @Valid @RequestBody UploadUrlRequest req,
            @AuthenticationPrincipal Jwt principal) {
        String url = req.getUrl();
        String title = req.getTitle();
        ResourceType resourceType = req.getResourceType();
        // Validation de l'url et du title
        if (url == null || title == null || resourceType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing url, title or resourceType"));
        }

        // Recuperation des claims depuis le Authentication JWT

        RequestIssuer userAgent = RequestIssuer.builder()
                .email(principal.getClaimAsString("email"))
                .name(principal.getClaimAsString("name"))
                .issuerId(principal.getClaimAsString("sub"))
                .scope(principal.getClaimAsString("scope"))
                .build();



        // Create job with .DOWNLOADING status initially
        Job job = jobService.createJob(title, resourceType, null, 0L, null, userAgent);
        job.setStatus(com.xksgroup.m3u8encoderv2.model.Job.JobStatus.DOWNLOADING);
        // === Peek headers for filename, fileSize, type BEFORE download ===
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            okhttp3.Request reqHead = new okhttp3.Request.Builder().url(url).head().build();
            try (okhttp3.Response response = client.newCall(reqHead).execute()) {
                String fileName = null;
                String cd = response.header("Content-Disposition");
                if (cd != null && cd.contains("filename=")) {
                    fileName = cd.substring(cd.indexOf("filename=") + 9).replace("\"", "").trim();
                }
                if (fileName == null) {
                    try {
                        fileName = java.nio.file.Paths.get(new java.net.URI(url).getPath()).getFileName().toString();
                    } catch (Exception ignore) {}
                }
                String contentType = response.header("Content-Type");
                long fileSize = response.header("Content-Length") != null ? Long.parseLong(response.header("Content-Length")) : 0L;
                jobService.updateJobFileInfo(job.getJobId(), fileName, (fileSize > 0 ? fileSize : null), contentType);
            }
        } catch (Exception ignored) { }
        jobService.updateJobStatus(job.getJobId(), com.xksgroup.m3u8encoderv2.model.Job.JobStatus.DOWNLOADING);
        // Compose output path
        Path tmpDir = Paths.get("upload-v2");
        try { Files.createDirectories(tmpDir); } catch (Exception ignored) {}
        String ext = (job.getOriginalFilename() != null && job.getOriginalFilename().contains("."))
                ? job.getOriginalFilename().substring(job.getOriginalFilename().lastIndexOf('.'))
                : (resourceType == ResourceType.VIDEO ? ".mp4" : ".mp3");
        String safeFilename = job.getJobId() + "_fromUrl" + ext;
        Path outFile = tmpDir.resolve(safeFilename);
        // Async: Start download and update job progress, then schedule encoding on completion
        CompletableFuture.runAsync(() -> {
            try {
                ResumableDownloader downloader = new ResumableDownloader();
                downloader.download(url, outFile.toString(), (downloaded, total) -> {
                    int percent = (total > 0) ? (int)((downloaded * 100) / total) : 0;
                    jobService.updateJobDownloadProgress(job.getJobId(), percent, total, downloaded);
                });
                // After download: update final file details
                try {
                    String actualContentType = Files.probeContentType(outFile);
                    String finalName = outFile.getFileName().toString();
                    Long finalSize = outFile.toFile().length();
                    jobService.updateJobFileInfo(job.getJobId(), finalName, finalSize, actualContentType);
                } catch (Exception ignoreFinal) {}
                jobService.updateJobStatus(job.getJobId(), com.xksgroup.m3u8encoderv2.model.Job.JobStatus.PENDING);
                jobService.processJobAsync(job, outFile); // keep logic aligned with file upload
            } catch (Exception ex) {
                jobService.updateJobError(job.getJobId(), "Download failed", ex.toString());
            }
        });
        // Respond immediately as with file upload endpoint
        Map<String, Object> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("message", "URL upload job created successfully");
        response.put("jobId", job.getJobId());
        response.put("slug", job.getSlug());
        response.put("title", job.getTitle());
        response.put("resourceType", job.getResourceType().name());
        response.put("jobStatus", job.getStatus().name());
        return ResponseEntity.accepted().body(response);
    }
}

// Schéma de réponse pour la documentation OpenAPI
@Schema(description = "Réponse de téléversement")
class UploadResponse {
    @Schema(description = "Statut de la réponse", example = "accepted")
    private String status;
    
    @Schema(description = "Message de réponse", example = "Job de téléversement créé avec succès")
    private String message;
    
    @Schema(description = "Identifiant unique du job", example = "job-123e4567-e89b-12d3-a456-426614174000")
    private String jobId;
    
    @Schema(description = "Slug du contenu", example = "ma-super-video")
    private String slug;
    
    @Schema(description = "Titre du contenu", example = "Ma Super Vidéo")
    private String title;
    
    @Schema(description = "Type de ressource", example = "VIDEO")
    private String resourceType;
    
    @Schema(description = "Statut actuel du job", example = "PENDING")
    private String jobStatus;
    
    @Schema(description = "Informations de progression")
    private ProgressInfo progress;
    
    @Schema(description = "URL pour vérifier le statut", example = "/api/v2/m3u8-encoder/status/ma-super-video")
    private String statusUrl;
    
    @Schema(description = "URL pour obtenir les détails du job", example = "/api/v2/m3u8-encoder/jobs/job-123e4567-e89b-12d3-a456-426614174000")
    private String jobUrl;
    
    @Schema(description = "Temps de complétion estimé", example = "5-10 minutes")
    private String estimatedCompletionTime;
}

@Schema(description = "Informations de progression")
class ProgressInfo {
    @Schema(description = "Nombre total de segments", example = "0")
    private int totalSegments;
    
    @Schema(description = "Nombre de segments complétés", example = "0")
    private int completedSegments;
    
    @Schema(description = "Nombre de segments échoués", example = "0")
    private int failedSegments;
    
    @Schema(description = "Nombre de segments en cours de téléversement", example = "0")
    private int uploadingSegments;
    
    @Schema(description = "Nombre de segments en attente", example = "0")
    private int pendingSegments;
}
