# Guide de Configuration Docker pour M3U8 Encoder V2

Ce guide vous aidera à configurer et exécuter l'application M3U8 Encoder V2 en utilisant Docker et Docker Compose.

## Prérequis

- Docker Engine 20.10+
- Docker Compose 2.0+
- Compte Cloudflare R2 (ou stockage compatible AWS S3)

## Démarrage Rapide

### 1. Cloner et Configurer l'Environnement

```bash
git clone <url-de-votre-repo>
cd m3u8-encoder-v2
```

### 2. Configurer les Variables d'Environnement

Copier le fichier d'exemple d'environnement et configurer vos paramètres :

```bash
cp env.example .env
```

Éditer le fichier `.env` avec vos valeurs réelles :

```bash
# Requis : Configuration R2
R2_ACCESS_KEY_ID=votre-clé-accès-actuelle
R2_SECRET_ACCESS_KEY=votre-clé-secrète-actuelle
R2_ENDPOINT=https://votre-id-compte.r2.cloudflarestorage.com
R2_BUCKET=nom-de-votre-bucket
R2_ACCOUNT_ID=votre-id-compte-cloudflare

# Requis : Secret JWT (utiliser un secret fort !)
JWT_SECRET=votre-très-longue-clé-secrète-pour-production

# Optionnel : Personnaliser d'autres paramètres
SERVER_HOST=localhost:8080
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=*
```

### 3. Construire et Exécuter

```bash
# Construire et démarrer tous les services
docker-compose up --build

# Ou exécuter en mode détaché
docker-compose up --build -d
```

### 4. Vérifier l'Installation

- **Application** : http://localhost:8080
- **Documentation API** : http://localhost:8080/swagger-ui.html
- **MongoDB Express** : http://localhost:8081
- **Vérification Santé** : http://localhost:8080/actuator/health

## Services

La configuration Docker Compose inclut :

### m3u8-encoder
- **Port** : 8080
- **Image** : Construite depuis le Dockerfile local
- **Fonctionnalités** : 
  - Runtime Java 17
  - FFmpeg pour traitement vidéo
  - Application Spring Boot
  - Vérifications de santé activées

### mongo
- **Port** : 27017
- **Image** : mongo:6.0
- **Base de données** : m3u8
- **Stockage persistant** : Oui

### mongo-express
- **Port** : 8081
- **Image** : mongo-express:1.0.2-20
- **Objectif** : Interface web MongoDB

## Fonctionnalités du Dockerfile

Le Dockerfile inclut :

- **Construction multi-étapes** pour taille d'image optimisée
- Environnement d'exécution **Java 17**
- **FFmpeg** pour traitement vidéo
- **Sécurité** : Exécution utilisateur non-root
- **Vérifications de santé** pour monitoring de conteneur
- **Paramètres JVM optimisés** pour conteneurs

## Gestion des Volumes

### Volumes Persistants

- `mongo_data` : Fichiers de base de données MongoDB
- `uploads_data` : Téléchargements temporaires de fichiers
- `processing_data` : Espace de travail traitement vidéo

### Emplacements des Volumes

```bash
# Voir les volumes
docker volume ls

# Inspecter les détails du volume
docker volume inspect m3u8-encoder-v2_mongo_data
```

## Variables d'Environnement

### Variables Requises

| Variable | Description | Exemple |
|----------|-------------|---------|
| `R2_ACCESS_KEY_ID` | Clé d'accès Cloudflare R2 | `votre-clé-accès` |
| `R2_SECRET_ACCESS_KEY` | Clé secrète Cloudflare R2 | `votre-clé-secrète` |
| `R2_ENDPOINT` | URL de point de terminaison R2 | `https://compte.r2.cloudflarestorage.com` |
| `R2_BUCKET` | Nom du bucket R2 | `mon-bucket-video` |
| `R2_ACCOUNT_ID` | ID de compte Cloudflare | `votre-id-compte` |
| `JWT_SECRET` | Secret de signature JWT | `secret-très-long-et-sécurisé` |

### Variables Optionnelles

| Variable | Défaut | Description |
|----------|--------|-------------|
| `JWT_EXPIRATION_MINUTES` | 15 | Temps d'expiration token JWT |
| `JWT_BUFFER_MINUTES` | 30 | Temps tampon JWT |
| `SERVER_HOST` | localhost:8080 | Nom d'hôte serveur (sans chemin) |
| `SERVER_PORT` | 8080 | Port serveur |
| `CORS_ALLOWED_ORIGINS` | * | Origines CORS autorisées |
| `SPRING_PROFILES_ACTIVE` | r2 | Profil Spring |
| `HLS_ENCRYPTION_ENABLED` | true | Activer chiffrement HLS |

## Commandes de Développement

### Construire et Exécuter

```bash
# Construire uniquement l'application
docker-compose build m3u8-encoder

# Exécuter service spécifique
docker-compose up m3u8-encoder

# Voir les logs
docker-compose logs -f m3u8-encoder

# Redémarrer le service
docker-compose restart m3u8-encoder
```

### Débogage

```bash
# Accéder au shell du conteneur
docker-compose exec m3u8-encoder bash

# Vérifier l'installation de FFmpeg
docker-compose exec m3u8-encoder ffmpeg -version

# Voir les logs de l'application
docker-compose logs m3u8-encoder

# Vérifier l'état de santé
curl http://localhost:8080/actuator/health
```

### Gestion de Base de Données

```bash
# Accéder au shell MongoDB
docker-compose exec mongo mongosh

# Sauvegarder la base de données
docker-compose exec mongo mongodump --db m3u8 --out /data/backup

# Accéder à Mongo Express
open http://localhost:8081
```

## Considérations de Production

### Sécurité

1. **Changer le secret JWT par défaut** vers une valeur forte et unique
2. **Restreindre les origines CORS** à vos domaines réels
3. **Utiliser HTTPS** en production (mettre à jour `protocol=https` dans application.properties)
4. **Mises à jour de sécurité régulières** pour les images de base

### Performance

1. **Ajuster la mémoire JVM** selon la capacité de votre serveur
2. **Surveiller l'utilisation disque** pour volumes upload/processing
3. **Configurer les limites de ressources** dans docker-compose.yml
4. **Utiliser MongoDB externe** pour la production

### Surveillance

1. **Vérifications de santé** activées par défaut
2. **Agrégation de logs** avec outils externes
3. **Collection de métriques** pour surveillance performance
4. **Stratégie de sauvegarde** pour volumes persistants

## Dépannage

### Problèmes Courants

1. **FFmpeg introuvable** : Assurez-vous que le Dockerfile inclut l'installation de FFmpeg
2. **Échec connexion MongoDB** : Vérifier la connectivité réseau entre services
3. **Échec téléchargement R2** : Vérifier identifiants et permissions bucket
4. **Manque de mémoire** : Ajuster la taille heap JVM dans variables d'environnement

### Logs et Débogage

```bash
# Voir tous les logs des services
docker-compose logs

# Suivre les logs d'un service spécifique
docker-compose logs -f m3u8-encoder

# Vérifier le statut des conteneurs
docker-compose ps

# Voir l'utilisation des ressources
docker stats
```

## Nettoyage

```bash
# Arrêter tous les services
docker-compose down

# Supprimer les volumes (ATTENTION : Cela supprime les données !)
docker-compose down -v

# Supprimer les images
docker-compose down --rmi all

# Nettoyage complet
docker system prune -a
```

## Configuration Avancée

### Ajuster les Ressources du Conteneur

Éditez `docker-compose.yml` pour définir les limites de ressources :

```yaml
services:
  m3u8-encoder:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G
        reservations:
          cpus: '1.0'
          memory: 2G
```

### Configuration Réseau Personnalisée

```yaml
networks:
  app-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

### Variables d'Environnement Personnalisées pour la JVM

```yaml
environment:
  - JAVA_OPTS=-Xms512m -Xmx2048m -XX:+UseG1GC
```

### Montages de Volumes pour Développement

```yaml
volumes:
  - ./src:/app/src:ro
  - ./target:/app/target
```

## Déploiement Multi-Environnements

### Environnement de Développement

```bash
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
```

### Environnement de Production

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Intégration CI/CD

### Exemple GitHub Actions

```yaml
name: Build et Deploy

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Construire l'image Docker
        run: docker-compose build
      - name: Exécuter les tests
        run: docker-compose run m3u8-encoder mvn test
      - name: Déployer
        run: |
          docker-compose push
          ssh user@server 'cd /app && docker-compose pull && docker-compose up -d'
```

## Sauvegardes et Restauration

### Sauvegarde MongoDB

```bash
# Sauvegarde manuelle
docker-compose exec mongo mongodump --db m3u8 --out /data/backup

# Sauvegarde automatisée (cron)
0 2 * * * docker-compose exec mongo mongodump --db m3u8 --out /data/backup/$(date +\%Y\%m\%d)
```

### Restauration MongoDB

```bash
# Restaurer depuis une sauvegarde
docker-compose exec mongo mongorestore --db m3u8 /data/backup/20251024/m3u8
```

## Support et Ressources

- 📖 [Documentation Docker](https://docs.docker.com/)
- 📖 [Documentation Docker Compose](https://docs.docker.com/compose/)
- 🐛 [Signaler un problème](https://github.com/yourusername/m3u8-encoder-v2/issues)
- 💬 [Discussions](https://github.com/yourusername/m3u8-encoder-v2/discussions)

---

**Construit avec ❤️ par XKS Group**
