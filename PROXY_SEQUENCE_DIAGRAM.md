# Diagramme de séquence - Proxy HLS LiveUrl

Ce diagramme explique comment le système de proxy HLS fonctionne, depuis la création d'une URL live jusqu'à la récupération du stream.

## Flux complet

```mermaid
sequenceDiagram
    participant CS as Core Service<br/>(Service TV Principal)
    participant DB as MongoDB<br/>(LiveUrls Collection)
    participant M3U8 as M3U8 Encoder Service<br/>(LiveUrlController)
    participant Proxy as LiveUrlProxyController
    participant Origin as Serveur d'origine<br/>(Stream externe)
    participant Client as Client<br/>(Lecteur vidéo)

    Note over CS: 1. Sauvegarde des informations TV
    CS->>CS: Génère urlUid<br/>(ex: "tv-channel-001")
    
    Note over CS,M3U8: 2. Enregistrement de l'URL live
    CS->>M3U8: POST /api/v2/m3u8-encoder/live-url<br/>{<br/>  "urlId": "tv-channel-001",<br/>  "url": "https://origin.com/master.m3u8"<br/>}
    
    M3U8->>M3U8: LiveUrlService.createLiveUrl()<br/>Vérifie si urlId existe
    M3U8->>DB: Sauvegarde LiveUrl<br/>{urlId, url, createdAt, updatedAt}
    DB-->>M3U8: LiveUrl sauvegardé
    M3U8-->>CS: 200 OK<br/>{<br/>  "urlId": "tv-channel-001",<br/>  "url": "https://origin.com/master.m3u8",<br/>  "createdAt": "...",<br/>  "updatedAt": "..."<br/>}
    
    Note over Client,Proxy: 3. Récupération du stream via proxy
    Client->>Proxy: GET /m3u8-encoder/api/v2/live-url/proxy/tv-channel-001
    
    Proxy->>Proxy: LiveUrlProxyController.proxyM3U8(urlId)
    Proxy->>Proxy: LiveUrlService.findLiveUrlByUrlId(urlId)
    Proxy->>DB: Recherche LiveUrl par urlId
    DB-->>Proxy: LiveUrl trouvé<br/>{urlId: "tv-channel-001", url: "https://origin.com/master.m3u8"}
    
    Note over Proxy,Origin: 4. Récupération du contenu M3U8 depuis l'origine
    Proxy->>Origin: GET https://origin.com/master.m3u8<br/>Headers: Accept, User-Agent
    Origin-->>Proxy: Contenu M3U8 brut<br/>(master playlist)
    
    Note over Proxy: 5. Traitement et réécriture des URLs
    Proxy->>Proxy: processM3U8Content()<br/>Parse le contenu M3U8
    Proxy->>Proxy: Détecte les lignes contenant .m3u8<br/>(variants, segments, clés)
    Proxy->>Proxy: Réécrit chaque URL:<br/>origin.com/variant.m3u8<br/>→<br/>gateway.com/proxy/tv-channel-001?u=origin.com/variant.m3u8
    
    Proxy-->>Client: 200 OK<br/>Content-Type: application/vnd.apple.mpegurl<br/>Contenu M3U8 réécrit<br/>(toutes les URLs pointent vers le proxy)
    
    Note over Client,Origin: 6. Requêtes suivantes (variants, segments)
    Client->>Proxy: GET /proxy/tv-channel-001?u=https%3A%2F%2Forigin.com%2Fvariant.m3u8
    Proxy->>Proxy: Décode le paramètre u
    Proxy->>Origin: GET https://origin.com/variant.m3u8
    Origin-->>Proxy: Contenu variant.m3u8
    Proxy->>Proxy: Réécrit les URLs des segments<br/>segment.ts → proxy/tv-channel-001?u=segment.ts
    Proxy-->>Client: Variant playlist réécrite
    
    Client->>Proxy: GET /proxy/tv-channel-001?u=https%3A%2F%2Forigin.com%2Fsegment001.ts
    Proxy->>Origin: GET https://origin.com/segment001.ts
    Origin-->>Proxy: Segment vidéo binaire
    Proxy-->>Client: Segment vidéo<br/>(sans modification)
```

## Points clés

### 1. Génération de l'identifiant
- Le **Core Service** génère un `urlUid` unique lors de la sauvegarde des informations TV
- Ce `urlUid` devient le `urlId` dans le service M3U8 Encoder
- Le même `urlId` est utilisé pour toutes les opérations (création, récupération, proxy)

### 2. Enregistrement de l'URL
- Le Core Service envoie `urlId` + `url` (URL externe) au service M3U8 Encoder
- Le service sauvegarde cette association dans MongoDB
- L'URL externe peut être une playlist master M3U8 ou n'importe quelle URL de streaming

### 3. Proxy et réécriture d'URLs
- Le client accède au stream via `/proxy/{urlId}`
- Le proxy récupère l'URL externe depuis MongoDB en utilisant le `urlId`
- Le proxy fetch le contenu depuis l'origine
- **Toutes les URLs internes** (variants, segments, clés) sont réécrites pour passer par le proxy
- Le même `urlId` est utilisé dans toutes les URLs réécrites

### 4. Flux de requêtes
- **Requête initiale** : `GET /proxy/{urlId}` → récupère la master playlist
- **Requêtes imbriquées** : `GET /proxy/{urlId}?u={encoded_url}` → récupère variants/segments
- Le paramètre `u` contient l'URL originale encodée en URL
- Le proxy décode `u` et fetch depuis l'origine

## Exemple concret

### Étape 1 : Enregistrement
```json
POST /api/v2/m3u8-encoder/live-url
{
  "urlId": "dacast-stream-001",
  "url": "https://view.dacast.com/.../master.m3u8"
}
```

### Étape 2 : Accès au stream
```
GET /m3u8-encoder/api/v2/live-url/proxy/dacast-stream-001
```

### Étape 3 : URLs réécrites dans la réponse
```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
https://gateway.com/m3u8-encoder/api/v2/live-url/proxy/dacast-stream-001?u=https%3A%2F%2Fview.dacast.com%2F...%2Fvariant_720p.m3u8
```

### Étape 4 : Requête du variant
```
GET /m3u8-encoder/api/v2/live-url/proxy/dacast-stream-001?u=https%3A%2F%2Fview.dacast.com%2F...%2Fvariant_720p.m3u8
```

## Avantages de cette architecture

1. **Sécurité** : L'URL externe n'est jamais exposée directement au client
2. **CORS** : Le proxy gère les en-têtes CORS automatiquement
3. **Traçabilité** : Toutes les requêtes passent par le proxy avec le même `urlId`
4. **Flexibilité** : L'URL externe peut être changée sans affecter le client (via PUT)
5. **Cache** : Possibilité d'ajouter du cache au niveau du proxy

