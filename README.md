# M3U8 Encoder 

![Java](https://img.shields.io/badge/Java-17+-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen)
![Maven](https://img.shields.io/badge/Maven-Build-blue)
![MongoDB](https://img.shields.io/badge/MongoDB-6.0+-green)
![FFmpeg](https://img.shields.io/badge/FFmpeg-Required-red)

Un **service avancé d'encodage et de streaming HLS à débit adaptatif (ABR)** qui convertit automatiquement les fichiers vidéo/audio en plusieurs variantes de qualité et les télécharge vers le stockage Cloudflare R2. Construit avec Spring Boot et conçu pour des applications de streaming vidéo évolutives.

## 🚀 Fonctionnalités

### Fonctionnalités principales
- **🎬 Traitement vidéo multi-formats** : Supporte divers formats vidéo en entrée
- **📱 Streaming à débit adaptatif** : Génère automatiquement plusieurs variantes de qualité (360p, 480p, 720p, 1080p)
- **🎵 Contenu audio uniquement** : Détection et traitement intelligents des fichiers audio uniquement
- **☁️ Intégration stockage cloud** : Téléchargement transparent vers Cloudflare R2 (compatible S3)
- **🔄 Traitement parallèle** : Téléchargement concurrent pour des performances améliorées

### Fonctionnalités avancées
- **🔐 Streaming sécurisé** : Authentification basée sur JWT pour l'accès au contenu protégé
- **📊 Gestion du contenu** : Stockage des métadonnées avec MongoDB
- **🎯 Catégories de ressources** : Support pour Films, Podcasts, Séries, Replays et Vidéos
- **🌐 Support CORS** : Partage de ressources entre origines configurable
- **📖 Documentation API** : Documentation Swagger/OpenAPI intégrée
- **🐳 Prêt pour Docker** : Support complet de la conteneurisation
- **⚡ Mises à jour temps réel SSE** : Événements Server-Sent Events pour suivi des jobs en temps réel
- **🎭 Gestion avancée des jobs** : Système de jobs avec états terminaux dédiés et notifications
- **🔒 Téléchargements concurrents sécurisés** : Support multi-utilisateurs sans conflits de fichiers
- **📈 Suivi de progression détaillé** : Progression par variante, estimations de temps, métriques de performance
- **⚡ Optimisations FFmpeg** : Auto-détection GPU (NVENC / QSV / VideoToolbox) avec repli CPU, multi-threading et paramètres équilibrés

## 📋 Prérequis

- **Java 17+**
- **Maven 3.6+**
- **FFmpeg** (doit être installé et accessible dans PATH)
- **MongoDB 6.0+**
- **Compte Cloudflare R2** (ou stockage compatible AWS S3)

## 🛠️ Installation & Configuration

### 1. Cloner le dépôt
```bash
git clone https://github.com/yourusername/m3u8-encoder-v2.git
cd m3u8-encoder-v2
```

### 2. Installer FFmpeg

**Installation de base :**
```bash
# macOS
brew install ffmpeg

# Ubuntu/Debian
sudo apt update && sudo apt install ffmpeg

# CentOS/RHEL
sudo yum install ffmpeg

# Windows
# Télécharger depuis https://ffmpeg.org/download.html
# Ajouter au PATH système
```

> **Note** : FFmpeg doit être compilé avec les accélérations nécessaires si vous voulez le GPU :
> - NVIDIA : support NVENC (`--enable-nvenc`) + driver NVIDIA récent
> - Intel : QSV (`--enable-libmfx`)
> - macOS : VideoToolbox (inclus dans les builds Homebrew)
>
> Par défaut le service détecte un encodeur matériel disponible et bascule dessus ; sinon il utilise libx264 CPU (preset veryfast, threads auto).

### 3. Démarrer MongoDB (avec Docker Compose)
```bash
docker-compose up -d
```
Cela démarrera :
- MongoDB sur le port `27017`
- Mongo Express (interface web) sur le port `8081`

### 4. Configurer les variables d'environnement

Créer un fichier `.env` à la racine du projet :

```bash
# Configuration R2 (Cloudflare R2)
R2_ACCESS_KEY_ID=votre-clé-accès-r2
R2_SECRET_ACCESS_KEY=votre-clé-secrète-r2
R2_ENDPOINT=https://votre-id-compte.r2.cloudflarestorage.com
R2_BUCKET=nom-de-votre-bucket
R2_ACCOUNT_ID=votre-id-compte-cloudflare

# Configuration MongoDB
MONGODB_URI=mongodb://localhost:27017/m3u8

# Configuration de sécurité JWT
JWT_SECRET=votre-très-longue-clé-secrète-pour-production-au-moins-32-caractères
JWT_EXPIRATION_MINUTES=15

# Configuration serveur
SERVER_HOST=localhost:8080
SERVER_PORT=8080

# Configuration CORS
CORS_ALLOWED_ORIGINS=*

# Profil de stockage
SPRING_PROFILES_ACTIVE=r2
```

### 5. Compiler et exécuter
```bash
# Compiler le projet
mvn clean compile

# Exécuter l'application
mvn spring-boot:run
```

L'application démarrera sur `http://localhost:8080`

## 📖 Documentation API

Une fois l'application lancée, accéder à la documentation API interactive :
- **Swagger UI** (publique, lecture seule) : http://localhost:8080/swagger-ui.html
- **Spécification OpenAPI** : http://localhost:8080/v3/api-docs
> Les endpoints API (hors liste blanche) exigent un JWT OAuth2 provenant de `${ISSUER_URI}` avec le rôle `admin` (scope Keycloak via `${RESOURCE_ID}`). Swagger UI reste accessible sans token mais l’appel des opérations protégées nécessite ce rôle.
 - **Page de test Live URL** : http://localhost:8080/live-url-test.html (utilise le proxy `/m3u8-encoder/api/v2/live-url/proxy/{urlId}`)

## 🎯 Utilisation

### Télécharger et traiter vidéo/audio (fichier)

```bash
curl -X POST http://localhost:8080/api/v2/m3u8-encoder/upload \
  -F "file=@votre-video.mp4" \
  -F "title=ma-super-video" \
  -F "resourceType=VIDEO"
```

### Proxy HLS léger (LiveUrl + urlId)

Modèle LiveUrl minimal:
- id (Mongo) — ignoré au JSON
- urlId — identifiant unique
- url — URL M3U8 d'origine
- createdAt / updatedAt

Endpoints CRUD:
- POST `/m3u8-encoder/api/v2/live-url` { urlId, url }
- GET `/m3u8-encoder/api/v2/live-url`
- GET `/m3u8-encoder/api/v2/live-url/{urlId}`
- PUT `/m3u8-encoder/api/v2/live-url/{urlId}` { url }
- DELETE `/m3u8-encoder/api/v2/live-url/{urlId}`

Endpoints proxy LiveUrl:
- Démarrer manifest via urlId: GET `/m3u8-encoder/api/v2/live-url/proxy/{urlId}`
- Chaînage interne: GET `/m3u8-encoder/api/v2/live-url/proxy/{urlId}?u=<URL-ENCODED .m3u8>`
- Segments/keys: GET `/m3u8-encoder/api/v2/live-url/proxy/segment?u=<URL-ENCODED absolu>`

Exemple:
```bash
# 1) Créer un LiveUrl
curl -X POST http://localhost:8080/m3u8-encoder/api/v2/live-url \
  -H "Content-Type: application/json" \
  -d '{
    "urlId": "my-live-1",
    "url": "https://origin.example.com/path/master.m3u8"
  }'

# 2) Lancer la lecture proxifiée
curl -s http://localhost:8080/m3u8-encoder/api/v2/live-url/proxy/my-live-1

# 3) (interne) Récupérer un segment/clé via le proxy
curl -L "http://localhost:8080/m3u8-encoder/api/v2/live-url/proxy/segment?u=$(python3 -c 'import urllib.parse; print(urllib.parse.quote("https://origin.example.com/path/seg_00001.ts", safe=""))')"
```

### Proxy sécurisé (jobId + tokens)

Endpoints (contrôleur historique sécurisé):
- Playlist maître: GET `/m3u8-encoder/api/v2/proxy/{jobId}`
- Playlist variante: GET `/m3u8-encoder/api/v2/proxy/{jobId}/{variant}/index.m3u8`
- Segment sécurisé: GET `/m3u8-encoder/api/v2/proxy/segment?token=...&resource=...`
- Clé de chiffrement: GET `/m3u8-encoder/api/v2/proxy/key/{jobId}?token=...`

Ce proxy s'appuie sur les enregistrements `MasterPlaylistRecord`, génère des tokens courts via `TokenService` et redirige vers des URLs présignées R2.

Réponse (202 Accepted):
```json
{
  "status": "accepted",
  "message": "URL upload job created successfully",
  "jobId": "job-123e4567-e89b-12d3-a456-426614174000",
  "slug": "ma-super-video",
  "title": "ma-super-video",
  "resourceType": "VIDEO",
  "jobStatus": "DOWNLOADING",
  "statusUrl": "/api/v2/m3u8-encoder/status/ma-super-video",
  "jobUrl": "/api/v2/m3u8-encoder/jobs/job-123e4567-e89b-12d3-a456-426614174000"
}
```

Erreurs (400 Bad Request):
- Corps JSON manquant ou invalide
- Champs requis manquants (`title`, `url`, `resourceType`)
- `resourceType` non supporté

```json
{
  "timestamp": "2025-01-01T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation error",
  "details": "url: url is required"
}
```

### Suivre la progression d'un job (Temps réel avec SSE)

```javascript
// Connexion SSE pour suivre un job spécifique
const eventSource = new EventSource('http://localhost:8080/api/v2/m3u8-encoder/jobs/sse?userId=user123&jobId=job-123');

// Mises à jour de progression (jobs actifs)
eventSource.addEventListener('job-update', (event) => {
  const jobs = JSON.parse(event.data);
  console.log('Progression:', jobs);
  // Afficher: encodage, upload, pourcentage, temps restant, etc.
});

// Événement de complétion
eventSource.addEventListener('job-completed', (event) => {
  const job = JSON.parse(event.data);
  console.log('✅ Job terminé:', job);
  // Afficher URL de lecture, rediriger vers le player, etc.
});

// Événement d'échec
eventSource.addEventListener('job-failed', (event) => {
  const job = JSON.parse(event.data);
  console.error('❌ Job échoué:', job.errorMessage);
  // Afficher message d'erreur à l'utilisateur
});

// Événement d'annulation
eventSource.addEventListener('job-cancelled', (event) => {
  const job = JSON.parse(event.data);
  console.log('🚫 Job annulé:', job);
  // Nettoyer l'interface utilisateur
});
```

### Récupérer le statut d'un job (API REST)

```bash
# Obtenir les détails d'un job spécifique
curl http://localhost:8080/api/v2/m3u8-encoder/jobs/job-123e4567-e89b-12d3-a456-426614174000

# Lister tous les jobs actifs (inclut DOWNLOADING)
curl http://localhost:8080/api/v2/m3u8-encoder/jobs/active

# Lister tous les jobs avec pagination
curl "http://localhost:8080/api/v2/m3u8-encoder/jobs?page=0&size=20"
```

### Gérer les jobs

```bash
# Annuler un job en cours
curl -X POST http://localhost:8080/api/v2/m3u8-encoder/jobs/job-123/cancel

# Supprimer un job terminé
curl -X DELETE http://localhost:8080/api/v2/m3u8-encoder/jobs/job-123

# Nettoyer tous les jobs terminés
curl -X DELETE http://localhost:8080/api/v2/m3u8-encoder/jobs/cleanup
```

### Détails de réécriture du proxy

Le proxy LiveUrl gère et réécrit automatiquement:
- Lignes `.m3u8` (même avec `?context=...`) → `/live-url/proxy/{urlId}?u=...`
- Lignes segment `.ts`/binaires → `/live-url/proxy/segment?u=...`
- Tags avec URI:
  - `#EXT-X-KEY: URI="..."` → `/live-url/proxy/segment?u=...`
  - `#EXT-X-MAP: URI="..."` → `/live-url/proxy/segment?u=...`
  - `#EXT-X-I-FRAME-STREAM-INF: URI="..."` → `/live-url/proxy/{urlId}?u=...` ou `/live-url/proxy/segment?u=...`
  - `#EXT-X-MEDIA: URI="..."` (audio/subs) → `/live-url/proxy/{urlId}?u=...` ou `/live-url/proxy/segment?u=...`

Note: des en-têtes par défaut (Accept / User-Agent) sont envoyés à l'origine pour compatibilité CDN.

### Types de ressources

L'API supporte différentes catégories de contenu :
- `AUDIO` - Contenu audio général
- `VIDEO` - Contenu vidéo général

## 🏗️ Architecture

### Stack technique
- **Backend** : Spring Boot 3.5.4, Java 17
- **Base de données** : MongoDB (avec Spring Data)
- **Stockage** : Cloudflare R2 (API compatible S3)
- **Traitement vidéo** : FFmpeg avec encodage CPU optimisé multi-threading
- **Sécurité** : Tokens JWT
- **Documentation** : SpringDoc OpenAPI

### Pipeline de traitement

1. **Téléchargement** → Fichier reçu via upload multipart ou **téléchargé depuis une URL** (reprise supportée)
2. **Création du Job** → Job créé avec statut PENDING ou **DOWNLOADING** (pour upload URL), retour immédiat au client
3. **Traitement Asynchrone** → Job traité en arrière-plan avec suivi en temps réel
4. **Analyse** → FFmpeg détecte les flux vidéo/audio
5. **Encodage** → Génération de plusieurs variantes de qualité avec CPU optimisé multi-threading (avec notifications SSE)
6. **Téléchargement** → Téléchargement parallèle vers stockage R2
7. **Métadonnées** → Stockage des infos de playlist dans MongoDB
8. **Notification** → Événement terminal envoyé aux clients SSE connectés
9. **Nettoyage** → Suppression intelligente des fichiers temporaires

### États du Job

- **DOWNLOADING** → Téléchargement du fichier distant (upload par URL) avec progression (%)
- **PENDING** → Job créé, en attente de démarrage
- **UPLOADING** → Téléversement local en cours (fichiers multiparts)
- **ENCODING** → Encodage FFmpeg en cours (progression par variante)
- **UPLOADING_TO_CLOUD_STORAGE** → Upload vers R2 en cours
- **COMPLETED** → Job terminé avec succès ✅
- **FAILED** → Job échoué avec message d'erreur ❌
- **CANCELLED** → Job annulé par l'utilisateur 🚫

### Variantes de qualité (Vidéo)
- **1080p** : 1920x1080, ~5 Mbps
- **720p** : 1280x720, ~3 Mbps  
- **480p** : 854x480, ~1.5 Mbps
- **360p** : 640x360, ~800 Kbps

### ⚡ Optimisations de performance

Le service détecte l'accélération matérielle et se replie sur le CPU si besoin :

- Auto-détection : NVENC (NVIDIA), QSV (Intel), VideoToolbox (macOS) via `ffmpeg -hwaccels`
- Fallback CPU : `libx264` preset `veryfast`, `-threads 0` (tous les cœurs)
- GOP dynamique aligné sur la durée des segments HLS
- Filtre `scale=...:flags=bicubic` (downscale prévisible, pas d'upscale)
- Paramètres streaming : `+faststart`, `yuv420p`, `-max_muxing_queue_size 4096`

**Gains de performance estimés :**

| Configuration | Temps pour 3h HD | Gain vs CPU minimal |
|--------------|-------------------|---------------------|
| CPU minimal (1-2 cores) | ~40 min | Baseline |
| CPU multi-cores (4-8 cores) | ~20-25 min | 1.5-2x |
| GPU NVENC (entrée 1080p) | ~3-8 min | Jusqu'à ~5-10x* |

\* Selon le modèle de GPU, le driver et la build FFmpeg.

## 🔧 Configuration

### Propriétés de l'application

Options de configuration principales dans `application.properties` :

```properties
# Limites de téléchargement de fichiers
spring.servlet.multipart.max-file-size=6GB
spring.servlet.multipart.max-request-size=6GB

# Connexion MongoDB
spring.data.mongodb.uri=${MONGODB_URI}

# Paramètres de sécurité
security.jwt.secret=${JWT_SECRET}
security.jwt.expiration-minutes=${JWT_EXPIRATION_MINUTES:15}

# Paramètres CORS
security.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:*}
```

### Profils d'environnement

- **`r2`** : Utiliser le stockage Cloudflare R2 (par défaut)
- **`aws`** : Utiliser le stockage AWS S3

## 🖥️ Déploiement sur serveur (recommandé pour GPU)

### ⚠️ Important : Utilisation du GPU intégré du système

**Pour utiliser le GPU intégré du système (Intel iGPU, AMD iGPU, ou GPU dédié), le service doit être déployé directement sur le serveur sans Docker.**

Les conteneurs Docker nécessitent une configuration spécifique pour accéder aux devices GPU (`/dev/dri` pour Intel/AMD, `--gpus all` pour NVIDIA), ce qui complique le déploiement et peut ne pas fonctionner selon l'environnement. Le déploiement direct sur le serveur permet :

- **Accès automatique au GPU** : Le service détecte automatiquement les accélérations disponibles (NVENC, QSV, VAAPI) via FFmpeg
- **Pas de configuration supplémentaire** : Aucun montage de devices ou configuration Docker spécifique nécessaire
- **Fallback automatique** : Si l'accélération matérielle échoue, le service bascule automatiquement sur l'encodage CPU

### Déploiement direct sur serveur

```bash
# 1. Compiler le projet
mvn clean package -DskipTests

# 2. Créer un service systemd (optionnel mais recommandé)
sudo nano /etc/systemd/system/m3u8-encoder.service
```

Exemple de fichier systemd :
```ini
[Unit]
Description=M3U8 Encoder Service
After=network.target mongodb.service

[Service]
Type=simple
User=your-user
WorkingDirectory=/path/to/m3u8-encoder-service
ExecStart=/usr/bin/java -jar /path/to/m3u8-encoder-v2-*.jar
Restart=always
RestartSec=10
Environment="JAVA_OPTS=-Xmx8g -Xms2g"

[Install]
WantedBy=multi-user.target
```

```bash
# 3. Activer et démarrer le service
sudo systemctl daemon-reload
sudo systemctl enable m3u8-encoder
sudo systemctl start m3u8-encoder

# 4. Vérifier les logs
sudo journalctl -u m3u8-encoder -f
```

### Prérequis pour l'accélération GPU

- **FFmpeg compilé avec support GPU** :
  - NVIDIA : `--enable-nvenc` + driver NVIDIA installé
  - Intel : `--enable-libmfx` ou `--enable-vaapi` + driver iHD installé
  - AMD : `--enable-vaapi` + driver amdgpu/Mesa installé
- **Drivers GPU installés sur le système** :
  - Vérifier avec `nvidia-smi` (NVIDIA), `vainfo` (Intel/AMD), ou `ls /dev/dri/` (Intel/AMD)
- **Le service détecte automatiquement** les accélérations disponibles et les utilise, avec repli CPU si nécessaire

## 🐳 Déploiement Docker

### Utiliser Docker Compose
```bash
# Démarrer tous les services
docker-compose up -d

# Voir les logs
docker-compose logs -f

# Arrêter les services  
docker-compose down
```


### Construire le conteneur d'application
```bash
# Construire le fichier JAR
mvn clean package -DskipTests

# Créer Dockerfile (exemple)
FROM openjdk:17-jre-slim
RUN apt-get update && apt-get install -y ffmpeg
COPY target/m3u8-encoder-v2-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## 🔒 Sécurité

### Authentification JWT
- Les endpoints (hors `/`, Swagger, health/info) exigent un JWT OAuth2 (Resource Server) émis par `${ISSUER_URI}` avec le rôle `admin` mappé via `${RESOURCE_ID}`.
- Les tokens HLS générés par le service expirent sur la durée vidéo + buffer (`security.jwt.buffer-minutes`, défaut 30) ou 15 minutes par défaut si la durée est inconnue.
- Les URLs présignées R2 utilisées par le proxy expirent en 10s, indépendamment du JWT métier.
- Swagger UI est accessible sans authentification mais les appels des endpoints protégés nécessitent le rôle `admin`.

### Bonnes pratiques
- Utiliser des secrets JWT forts (32+ caractères)
- Configurer CORS de manière appropriée pour votre domaine
- Utiliser HTTPS en production
- Faire tourner régulièrement les clés d'accès R2
- Nettoyer régulièrement les jobs terminés pour libérer l'espace disque
- Monitorer les connexions SSE actives en production
- Utiliser des noms de fichiers uniques pour éviter les conflits (déjà implémenté)
- Activer/désactiver le chiffrement HLS via `hls.encryption.enabled` (défaut `true`) et s'assurer que les fichiers de clé/IV sont générés et uploadés quand le chiffrement est activé

## 📊 Surveillance & Logs

L'application fournit une journalisation détaillée pour :
- Progression et statut des téléchargements
- Opérations d'encodage FFmpeg (avec pourcentages et estimations de temps)
- Interactions avec le stockage R2
- Gestion des erreurs et débogage
- Événements SSE et notifications temps réel

Voir les logs en temps réel :
```bash
mvn spring-boot:run | grep -E "(INFO|ERROR|WARN)"
```

### Métriques de Job

Chaque job suit les métriques suivantes :
- **Segments** : Total, complétés, échoués, en upload, en attente
- **Progression** : Pourcentage global et par variante
- **Temps** : Écoulé, restant, estimation totale
- **Performance** : Durée d'encodage, durée d'upload, durée totale
- **Variantes** : Variante actuelle, description, progression

### Événements SSE en temps réel

Le système SSE fournit des mises à jour en temps réel avec throttling intelligent :
- **Throttling** : Cooldown de 2 secondes entre les mises à jour
- **Événements dédiés** : Événements séparés pour états terminaux
- **Ciblage flexible** : Notifications par job spécifique ou tous les jobs
- **Optimisation bande passante** : Pas de dispatch redondant après états terminaux

```
job-update       → Mises à jour de progression (jobs actifs uniquement)
job-completed    → Notification de complétion avec URLs de lecture
job-failed       → Notification d'échec avec détails d'erreur
job-cancelled    → Notification d'annulation
connected        → Confirmation de connexion SSE
```

Outre l'encodage et l'upload, la progression **DOWNLOADING** est publiée en temps réel aux clients SEE/SSE via `job-update`.

## 🤝 Contribution

1. Forker le dépôt
2. Créer une branche de fonctionnalité (`git checkout -b feature/fonctionnalite-incroyable`)
3. Committer vos changements (`git commit -m 'Ajouter une fonctionnalité incroyable'`)
4. Pousser vers la branche (`git push origin feature/fonctionnalite-incroyable`)
5. Ouvrir une Pull Request

## 📄 Licence

Ce projet est sous licence MIT - voir le fichier [LICENSE](LICENSE) pour plus de détails.

## 🆘 Support

### Problèmes courants

**FFmpeg introuvable :**
```bash
# Vérifier l'installation de FFmpeg
ffmpeg -version

# Ajouter au PATH si nécessaire
export PATH="/usr/local/bin:$PATH"
```

**GPU non utilisé alors qu'il est disponible :**
- Vérifier que `ffmpeg -hide_banner -hwaccels` liste `cuda` (NVIDIA), `qsv` (Intel) ou `videotoolbox` (macOS)
- Vérifier que `ffmpeg -encoders | grep nvenc` (ou qsv/videotoolbox) retourne les encodeurs matériels
- Installer/mettre à jour le driver GPU et une build FFmpeg avec ces encodeurs activés
- En conteneur, passer le GPU (ex. `--gpus all` avec nvidia-container-toolkit)

**Encodage trop lent :**
- Augmenter le nombre de cores CPU disponibles
- Vérifier que FFmpeg utilise tous les cores (`-threads 0`)
- Augmenter les ressources CPU allouées au conteneur Docker

**Échec de connexion MongoDB :**
```bash
# Vérifier le statut de MongoDB
docker-compose ps mongo

# Redémarrer MongoDB
docker-compose restart mongo
```

**Erreurs de téléchargement R2 :**
- Vérifier les identifiants R2 et l'URL du point de terminaison
- Vérifier les permissions du bucket
- S'assurer que le bucket existe

**Erreur "Le processus ne peut pas accéder au fichier" (Windows) :**
```bash
# Ce problème est maintenant résolu automatiquement !
# Chaque job utilise un nom de fichier unique : jobId_filename
# Exemple : job-abc123_video.mp4, job-xyz789_video.mp4
# Permet les téléchargements concurrents du même fichier ✅
```

**Job bloqué en ENCODING :**
```bash
# Vérifier les logs FFmpeg
docker-compose logs -f m3u8-encoder

# Annuler le job si nécessaire
curl -X POST http://localhost:8080/api/v2/m3u8-encoder/jobs/{jobId}/cancel
```

**Connexion SSE perdue :**
```javascript
// Les connexions SSE se reconnectent automatiquement
// Timeout de reconnexion : 3 secondes
eventSource.onerror = (error) => {
  console.log('Reconnexion SSE en cours...');
  // Le navigateur reconnecte automatiquement
};
```

### Obtenir de l'aide

- 📖 Consulter la [Documentation API](http://localhost:8080/swagger-ui.html)
- 🐛 [Signaler des problèmes](https://github.com/yourusername/m3u8-encoder-v2/issues)
- 💬 [Discussions](https://github.com/yourusername/m3u8-encoder-v2/discussions)

---

**Construit avec ❤️ par Ousseynou Kone**
