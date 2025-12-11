# Guide d'utilisation du proxy HLS avec LiveUrl

Ce guide explique comment utiliser le **LiveUrl Controller** et le **Proxy Controller** ensemble pour proxifier des flux HLS externes via votre serveur.

## 📋 Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Modèle LiveUrl](#modèle-liveurl)
3. [LiveUrl Controller (CRUD)](#liveurl-controller-crud)
4. [Proxy Controller](#proxy-controller)
5. [Workflow complet](#workflow-complet)
6. [Comment fonctionne la réécriture d'URL](#comment-fonctionne-la-réécriture-durl)
7. [Exemples de code](#exemples-de-code)
8. [Dépannage](#dépannage)

---

## 🎯 Vue d'ensemble

Le système fournit un **proxy HLS léger** qui :
- Stocke les URLs de flux HLS externes dans MongoDB (via `LiveUrl`)
- Proxifie les playlists M3U8 et les segments via votre serveur
- Réécrit automatiquement toutes les URLs internes pour router via le proxy
- Supporte CORS pour les requêtes cross-origin des navigateurs
- Fonctionne avec n'importe quel lecteur HLS (HLS.js, Video.js, HTML5 natif, etc.)

**Concept clé** : Vous enregistrez une URL externe avec un `urlId`, puis y accédez via `/live-url/proxy/{urlId}`. Le proxy gère automatiquement toutes les playlists imbriquées, segments et clés.

---

## 📦 Modèle LiveUrl

L'entité `LiveUrl` est minimale et stocke uniquement les informations essentielles :

```json
{
  "urlId": "mon-stream-001",
  "url": "https://origin.example.com/path/master.m3u8",
  "createdAt": "2025-11-03T14:00:00Z",
  "updatedAt": "2025-11-03T14:00:00Z"
}
```

**Champs :**
- `id` - ID du document MongoDB (généré automatiquement, **ignoré dans les réponses JSON**)
- `urlId` - **Requis** - Identifiant unique que vous choisissez (ex: "stream-1", "live-news")
- `url` - **Requis** - L'URL M3U8 externe à proxifier
- `createdAt` - Horodatage (généré automatiquement)
- `updatedAt` - Horodatage (mis à jour automatiquement)

---

## 🔧 LiveUrl Controller (CRUD)

Chemin de base : `/api/v2/m3u8-encoder/live-url`

### 1. Créer un LiveUrl

**POST** `/api/v2/m3u8-encoder/live-url`

**Corps de la requête :**
```json
{
  "urlId": "mon-stream-001",
  "url": "https://origin.example.com/path/master.m3u8"
}
```

**Réponse (200 OK) :**
```json
{
  "urlId": "mon-stream-001",
  "url": "https://origin.example.com/path/master.m3u8",
  "createdAt": "2025-11-03T14:00:00Z",
  "updatedAt": "2025-11-03T14:00:00Z"
}
```

**Exemple :**
```bash
curl -X POST http://localhost:8080/api/v2/m3u8-encoder/live-url \
  -H "Content-Type: application/json" \
  -d '{
    "urlId": "dacast-stream",
    "url": "https://view.dacast.com/3ec2bb25-c530-4821-84d4-dffe895b97a7/3ec2bb25-c530-4821-84d4-dffe895b97a7-video=2728635.m3u8?context=..."
  }'
```

### 2. Obtenir tous les LiveUrls

**GET** `/api/v2/m3u8-encoder/live-url`

**Réponse (200 OK) :**
```json
[
  {
    "urlId": "mon-stream-001",
    "url": "https://origin.example.com/path/master.m3u8",
    "createdAt": "2025-11-03T14:00:00Z",
    "updatedAt": "2025-11-03T14:00:00Z"
  },
  {
    "urlId": "mon-stream-002",
    "url": "https://another-origin.com/stream.m3u8",
    "createdAt": "2025-11-03T14:05:00Z",
    "updatedAt": "2025-11-03T14:05:00Z"
  }
]
```

### 3. Obtenir un LiveUrl par urlId

**GET** `/api/v2/m3u8-encoder/live-url/{urlId}`

**Réponse (200 OK) :**
```json
{
  "urlId": "mon-stream-001",
  "url": "https://origin.example.com/path/master.m3u8",
  "createdAt": "2025-11-03T14:00:00Z",
  "updatedAt": "2025-11-03T14:00:00Z"
}
```

### 4. Mettre à jour un LiveUrl

**PUT** `/api/v2/m3u8-encoder/live-url/{urlId}`

**Corps de la requête :**
```json
{
  "url": "https://new-origin.example.com/new-stream.m3u8"
}
```

**Réponse (200 OK) :**
```json
{
  "urlId": "mon-stream-001",
  "url": "https://new-origin.example.com/new-stream.m3u8",
  "createdAt": "2025-11-03T14:00:00Z",
  "updatedAt": "2025-11-03T14:30:00Z"
}
```

### 5. Supprimer un LiveUrl

**DELETE** `/api/v2/m3u8-encoder/live-url/{urlId}`

**Réponse (200 OK) :**
```json
{
  "deleted": true,
  "urlId": "mon-stream-001"
}
```

---

## 🌐 Proxy Controller

Chemin de base : `/api/v2/m3u8-encoder`

### 1. Proxy M3U8 Playlist (Point d'entrée principal)

**GET** `/api/v2/m3u8-encoder/live-url/proxy/{urlId}`

**Description :**
- Récupère la playlist M3U8 depuis le `LiveUrl` stocké dans la base de données
- Réécrit toutes les URLs dans la playlist pour router via le proxy
- Retourne le contenu M3U8 réécrit

**Paramètres :**
- `urlId` (path) - L'identifiant LiveUrl
- `u` (query, optionnel) - URL de remplacement pour les requêtes de playlist imbriquées (utilisé en interne)

**En-têtes de réponse :**
- `Content-Type: application/vnd.apple.mpegurl`
- `Access-Control-Allow-Origin: *` (ou valeur configurée)
- `Access-Control-Allow-Methods: GET, OPTIONS`
- `Access-Control-Allow-Headers: *`

**Exemple :**
```bash
curl http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/mon-stream-001
```

**Réponse (format M3U8) :**
```
#EXTM3U
#EXT-X-VERSION:7
#EXT-X-STREAM-INF:BANDWIDTH=304000,CODECS="avc1.64001E",RESOLUTION=492x270
http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/mon-stream-001?u=https%3A%2F%2Forigin.example.com%2Fvariant1.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=901000,CODECS="avc1.640020",RESOLUTION=986x540
http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/mon-stream-001?u=https%3A%2F%2Forigin.example.com%2Fvariant2.m3u8
```

### 2. Proxy Segments/Keys (Contenu binaire)

**GET** `/api/v2/m3u8-encoder/live-url/proxy/segment?u={encoded-url}`

**Description :**
- Récupère le contenu binaire (segments vidéo `.ts`, clés de chiffrement, segments d'initialisation)
- Retourne les octets bruts avec le type de contenu approprié

**Paramètres :**
- `u` (query, requis) - URL absolue encodée du segment/clé

**En-têtes de réponse :**
- `Content-Type: application/octet-stream` (ou type détecté)
- `Access-Control-Allow-Origin: *`
- `Access-Control-Allow-Methods: GET, OPTIONS`

**Exemple :**
```bash
# Encoder l'URL du segment d'abord
ENCODED_URL=$(python3 -c "import urllib.parse; print(urllib.parse.quote('https://origin.example.com/seg_00001.ts', safe=''))")

curl "http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/segment?u=$ENCODED_URL"
```

### 3. Prévol CORS (OPTIONS)

Les deux endpoints supportent OPTIONS pour le prévol CORS :

**OPTIONS** `/api/v2/m3u8-encoder/live-url/proxy/{urlId}`
**OPTIONS** `/api/v2/m3u8-encoder/live-url/proxy/segment`

Retourne les en-têtes CORS sans corps.

### Proxy sécurisé (jobId + tokens)

Pour les contenus encodés et stockés via R2, le contrôleur historique fournit un proxy sécurisé basé sur `jobId` :

- Playlist maître : `GET /api/v2/m3u8-encoder/proxy/{jobId}`
- Playlist variante : `GET /api/v2/m3u8-encoder/proxy/{jobId}/{variant}/index.m3u8`
- Segment sécurisé : `GET /api/v2/m3u8-encoder/proxy/segment?token=...&resource=...`
- Clé de chiffrement : `GET /api/v2/m3u8-encoder/proxy/key/{jobId}?token=...`

Ce flux s'appuie sur `MasterPlaylistRecord`, génère des tokens temporaires via `TokenService` et redirige vers des URLs présignées R2.

---

## 🔄 Workflow complet

### Exemple étape par étape

#### Étape 1 : Créer un LiveUrl

```bash
curl -X POST http://localhost:8080/api/v2/m3u8-encoder/live-url \
  -H "Content-Type: application/json" \
  -d '{
    "urlId": "dacast-live",
    "url": "https://view.dacast.com/abc123/master.m3u8?context=xyz"
  }'
```

**Réponse :**
```json
{
  "urlId": "dacast-live",
  "url": "https://view.dacast.com/abc123/master.m3u8?context=xyz",
  "createdAt": "2025-11-03T14:00:00Z",
  "updatedAt": "2025-11-03T14:00:00Z"
}
```

#### Étape 2 : Accéder au flux proxifié

```bash
# Obtenir la playlist maître proxifiée
curl http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/dacast-live
```

**Ce qui se passe :**
1. Le proxy cherche `dacast-live` dans la base de données
2. Récupère `https://view.dacast.com/abc123/master.m3u8?context=xyz` depuis l'origine
3. Réécrit toutes les URLs dans la playlist pour pointer vers le proxy
4. Retourne le M3U8 réécrit

#### Étape 3 : Le lecteur suit automatiquement les URLs réécrites

Lorsqu'un lecteur (HLS.js, Video.js, etc.) charge la playlist proxifiée, il va :
1. Demander les playlists de variantes via `/live-url/proxy/dacast-live?u=...`
2. Demander les segments via `/live-url/proxy/segment?u=...`
3. Demander les clés de chiffrement via `/live-url/proxy/segment?u=...`

Toutes les requêtes passent automatiquement par votre serveur proxy.

---

## 🔀 Comment fonctionne la réécriture d'URL

Le proxy détecte et réécrit automatiquement différents types d'URLs dans les playlists M3U8 :

### 1. Lignes média simples (Segments/Playlists)

**Original :**
```
seg_00001.ts
variant1.m3u8
https://origin.com/path/seg_00002.ts
```

**Réécrit :**
```
http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/segment?u=https%3A%2F%2Forigin.com%2Fpath%2Fseg_00001.ts
http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/dacast-live?u=https%3A%2F%2Forigin.com%2Fpath%2Fvariant1.m3u8
http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/segment?u=https%3A%2F%2Forigin.com%2Fpath%2Fseg_00002.ts
```

**Logique de détection :**
- Si le chemin de l'URL se termine par `.m3u8` (même avec des paramètres de requête comme `?context=...`) → Route vers `/live-url/proxy/{urlId}?u=...`
- Sinon (`.ts`, `.key`, etc.) → Route vers `/live-url/proxy/segment?u=...`

### 2. Tags EXT-X-KEY

**Original :**
```
#EXT-X-KEY:METHOD=AES-128,URI="key.bin"
```

**Réécrit :**
```
#EXT-X-KEY:METHOD=AES-128,URI="http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/segment?u=https%3A%2F%2Forigin.com%2Fkey.bin"
```

### 3. Tags EXT-X-MAP (Segments d'initialisation)

**Original :**
```
#EXT-X-MAP:URI="init.mp4"
```

**Réécrit :**
```
#EXT-X-MAP:URI="http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/segment?u=https%3A%2F%2Forigin.com%2Finit.mp4"
```

### 4. Tags EXT-X-I-FRAME-STREAM-INF

**Original :**
```
#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=38000,URI="keyframes/playlist.m3u8?context=..."
```

**Réécrit :**
```
#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=38000,URI="http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/dacast-live?u=https%3A%2F%2Forigin.com%2Fkeyframes%2Fplaylist.m3u8%3Fcontext%3D..."
```

### 5. Tags EXT-X-MEDIA (Audio/Sous-titres)

**Original :**
```
#EXT-X-MEDIA:TYPE=AUDIO,URI="audio.m3u8"
```

**Réécrit :**
```
#EXT-X-MEDIA:TYPE=AUDIO,URI="http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/dacast-live?u=https%3A%2F%2Forigin.com%2Faudio.m3u8"
```

---

## 💻 Exemples de code

### JavaScript (Navigateur avec HLS.js)

```javascript
// Étape 1 : Créer LiveUrl (si n'existe pas)
async function createLiveUrl(urlId, externalUrl) {
  const response = await fetch('http://localhost:8080/api/v2/m3u8-encoder/live-url', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ urlId, url: externalUrl })
  });
  return await response.json();
}

// Étape 2 : Charger et lire le flux proxifié
function playProxiedStream(urlId) {
  const video = document.getElementById('video');
  const proxyUrl = `http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/${urlId}`;
  
  if (Hls.isSupported()) {
    const hls = new Hls({
      debug: false,
      enableWorker: true
    });
    
    hls.loadSource(proxyUrl);
    hls.attachMedia(video);
    
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play();
    });
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    // Support HLS natif (Safari, iOS)
    video.src = proxyUrl;
  }
}

// Utilisation
createLiveUrl('mon-stream', 'https://origin.com/master.m3u8')
  .then(() => playProxiedStream('mon-stream'))
  .catch(console.error);
```

### Python

```python
import requests
import json

BASE_URL = "http://localhost:8080/api/v2/m3u8-encoder"

# Étape 1 : Créer LiveUrl
def create_live_url(url_id, external_url):
    response = requests.post(
        f"{BASE_URL}/live-url",
        json={"urlId": url_id, "url": external_url},
        headers={"Content-Type": "application/json"}
    )
    response.raise_for_status()
    return response.json()

# Étape 2 : Obtenir la playlist proxifiée
def get_proxied_playlist(url_id):
    response = requests.get(f"{BASE_URL}/live-url/proxy/{url_id}")
    response.raise_for_status()
    return response.text

# Étape 3 : Obtenir un segment proxifié
def get_proxied_segment(segment_url):
    import urllib.parse
    encoded = urllib.parse.quote(segment_url, safe='')
    response = requests.get(f"{BASE_URL}/live-url/proxy/segment?u={encoded}")
    response.raise_for_status()
    return response.content

# Utilisation
live_url = create_live_url("mon-stream", "https://origin.com/master.m3u8")
playlist = get_proxied_playlist("mon-stream")
print(playlist)
```

### Exemples cURL

```bash
# 1. Créer LiveUrl
curl -X POST http://localhost:8080/api/v2/m3u8-encoder/live-url \
  -H "Content-Type: application/json" \
  -d '{"urlId": "test", "url": "https://origin.com/master.m3u8"}'

# 2. Obtenir la playlist maître proxifiée
curl http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/test

# 3. Obtenir un segment proxifié (encoder l'URL d'abord)
SEGMENT_URL="https://origin.com/seg_00001.ts"
ENCODED=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$SEGMENT_URL', safe=''))")
curl "http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/segment?u=$ENCODED" --output segment.ts

# 4. Lister tous les LiveUrls
curl http://localhost:8080/api/v2/m3u8-encoder/live-url

# 5. Supprimer LiveUrl
curl -X DELETE http://localhost:8080/api/v2/m3u8-encoder/live-url/test
```

### Exemple React

```jsx
import { useState, useEffect, useRef } from 'react';
import Hls from 'hls.js';

function HLSPlayer({ urlId, serverUrl = 'http://localhost:8080' }) {
  const [error, setError] = useState(null);
  const videoRef = useRef(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const proxyUrl = `${serverUrl}/api/v2/m3u8-encoder/live-url/proxy/${urlId}`;

    if (Hls.isSupported()) {
      const hls = new Hls();
      hls.loadSource(proxyUrl);
      hls.attachMedia(video);

      hls.on(Hls.Events.ERROR, (event, data) => {
        if (data.fatal) {
          setError(`Erreur HLS: ${data.type}`);
        }
      });

      return () => {
        hls.destroy();
      };
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = proxyUrl;
    } else {
      setError('HLS non supporté dans ce navigateur');
    }
  }, [urlId, serverUrl]);

  return (
    <div>
      {error && <div className="error">{error}</div>}
      <video ref={videoRef} controls />
    </div>
  );
}
```

---

## 🎬 Utiliser la page de test

Une page de test est disponible à : `http://localhost:8080/live-url-test.html`

**Fonctionnalités :**
- Créer des LiveUrls avec un formulaire simple
- Lister tous les LiveUrls existants
- Sélectionner et lire des flux directement
- Supprimer des LiveUrls
- Sauvegarde automatique des entrées dans localStorage

**Comment utiliser :**
1. Ouvrir `http://localhost:8080/live-url-test.html` dans votre navigateur
2. Entrer un `urlId` (ex: "mon-stream")
3. Entrer l'URL externe (ex: `https://origin.com/master.m3u8`)
4. Cliquer sur "Create LiveUrl"
5. Cliquer sur "Load & Play" pour démarrer la lecture

La page de test utilise HLS.js pour la lecture et gère automatiquement toutes les requêtes proxy.

---

## 🔍 Dépannage

### Problème : 404 Not Found lors de l'accès à `/live-url/proxy/{urlId}`

**Cause :** Le `urlId` n'existe pas dans la base de données.

**Solution :**
```bash
# Vérifier si LiveUrl existe
curl http://localhost:8080/api/v2/m3u8-encoder/live-url/{urlId}

# Le créer s'il manque
curl -X POST http://localhost:8080/api/v2/m3u8-encoder/live-url \
  -H "Content-Type: application/json" \
  -d '{"urlId": "votre-urlid", "url": "https://origin.com/master.m3u8"}'
```

### Problème : Erreurs CORS dans le navigateur

**Cause :** Les en-têtes CORS ne sont pas envoyés.

**Solution :** Le proxy inclut automatiquement les en-têtes CORS. Si vous voyez encore des erreurs :
- Vérifier que le serveur est en cours d'exécution
- Vérifier la propriété `security.cors.allowed-origins` dans `application.properties`
- S'assurer d'utiliser le bon chemin d'endpoint

### Problème : Les segments ne se chargent pas

**Cause :** Les URLs réécrites pourraient être incorrectes.

**Solution :**
1. Vérifier le contenu de la playlist proxifiée :
   ```bash
   curl http://localhost:8080/api/v2/m3u8-encoder/live-url/proxy/{urlId}
   ```
2. Vérifier que les URLs de segments sont correctement encodées
3. Vérifier les logs du serveur pour les erreurs de récupération depuis l'origine

### Problème : Les URLs de playlist ne sont pas réécrites

**Cause :** La playlist pourrait avoir un format inhabituel.

**Solution :**
- Vérifier le format de la playlist originale
- S'assurer que les fichiers `.m3u8` sont détectés (même avec des paramètres de requête comme `?context=...`)
- Vérifier que le proxy traite tous les types de tags (`#EXT-X-KEY`, `#EXT-X-MEDIA`, etc.)

### Problème : "Failed to fetch M3U8 content"

**Cause :** Le serveur d'origine est inaccessible ou retourne une erreur.

**Solution :**
1. Tester l'URL d'origine directement :
   ```bash
   curl https://origin.com/master.m3u8
   ```
2. Vérifier si l'origine nécessite des en-têtes spécifiques (User-Agent, etc.)
3. Vérifier la connectivité réseau

---

## 📝 Résumé

**Démarrage rapide :**
1. **Créer** un LiveUrl : `POST /api/v2/m3u8-encoder/live-url` avec `{urlId, url}`
2. **Accéder** au flux proxifié : `GET /api/v2/m3u8-encoder/live-url/proxy/{urlId}`
3. **Le lecteur** suit automatiquement les URLs réécrites pour les segments/clés

**Points clés :**
- ✅ CRUD simple pour gérer les URLs externes
- ✅ Réécriture automatique d'URL pour tous les composants HLS
- ✅ Support CORS pour les lecteurs basés sur navigateur
- ✅ Aucune sécurité/auth requise (proxy léger)
- ✅ Fonctionne avec n'importe quel lecteur HLS (HLS.js, Video.js, natif, etc.)

**Résumé des endpoints :**

| Méthode | Endpoint | Objectif |
|--------|----------|----------|
| POST | `/api/v2/m3u8-encoder/live-url` | Créer LiveUrl |
| GET | `/api/v2/m3u8-encoder/live-url` | Lister tous les LiveUrls |
| GET | `/api/v2/m3u8-encoder/live-url/{urlId}` | Obtenir LiveUrl par urlId |
| PUT | `/api/v2/m3u8-encoder/live-url/{urlId}` | Mettre à jour LiveUrl |
| DELETE | `/api/v2/m3u8-encoder/live-url/{urlId}` | Supprimer LiveUrl |
| GET | `/api/v2/m3u8-encoder/live-url/proxy/{urlId}` | **Proxy playlist M3U8** |
| GET | `/api/v2/m3u8-encoder/live-url/proxy/segment?u=...` | **Proxy segments/clés** |
| OPTIONS | `/api/v2/m3u8-encoder/live-url/proxy/{urlId}` | Prévol CORS |
| OPTIONS | `/api/v2/m3u8-encoder/live-url/proxy/segment` | Prévol CORS |

---

**Pour plus d'informations, voir :**
- README principal : `README.md`
- Documentation API : `http://localhost:8080/swagger-ui.html`
- Page de test : `http://localhost:8080/live-url-test.html`

