# Docker Setup Guide for M3U8 Encoder V2

This guide will help you set up and run the M3U8 Encoder V2 application using Docker and Docker Compose.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+
- Cloudflare R2 account (or AWS S3 compatible storage)

## Quick Start

### 1. Clone and Setup Environment

```bash
git clone <your-repo-url>
cd m3u8-encoder-v2
```

### 2. Configure Environment Variables

Copy the example environment file and configure your settings:

```bash
cp env.example .env
```

Edit `.env` file with your actual values:

```bash
# Required: R2 Configuration
R2_ACCESS_KEY_ID=your-actual-access-key
R2_SECRET_ACCESS_KEY=your-actual-secret-key
R2_ENDPOINT=https://your-account-id.r2.cloudflarestorage.com
R2_BUCKET=your-bucket-name
R2_ACCOUNT_ID=your-cloudflare-account-id

# Required: JWT Secret (use a strong secret!)
JWT_SECRET=your-very-long-secure-secret-key-for-production-use

# Optional: Customize other settings
SERVER_HOST=localhost
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=*
```

### 3. Build and Run

```bash
# Build and start all services
docker-compose up --build

# Or run in detached mode
docker-compose up --build -d
```

### 4. Verify Installation

- **Application**: http://localhost:8080
- **API Documentation**: http://localhost:8080/swagger-ui.html
- **MongoDB Express**: http://localhost:8081
- **Health Check**: http://localhost:8080/actuator/health

## Services

The Docker Compose setup includes:

### m3u8-encoder
- **Port**: 8080
- **Image**: Built from local Dockerfile
- **Features**: 
  - Java 17 runtime
  - FFmpeg for video processing
  - Spring Boot application
  - Health checks enabled

### mongo
- **Port**: 27017
- **Image**: mongo:6.0
- **Database**: m3u8
- **Persistent storage**: Yes

### mongo-express
- **Port**: 8081
- **Image**: mongo-express:1.0.2-20
- **Purpose**: MongoDB web interface

## Dockerfile Features

The Dockerfile includes:

- **Multi-stage build** for optimized image size
- **Java 17** runtime environment
- **FFmpeg** for video processing
- **Security**: Non-root user execution
- **Health checks** for container monitoring
- **Optimized JVM settings** for containers

## Volume Management

### Persistent Volumes

- `mongo_data`: MongoDB database files
- `uploads_data`: Temporary file uploads
- `processing_data`: Video processing workspace

### Volume Locations

```bash
# View volumes
docker volume ls

# Inspect volume details
docker volume inspect m3u8-encoder-v2_mongo_data
```

## Environment Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `R2_ACCESS_KEY_ID` | Cloudflare R2 access key | `your-access-key` |
| `R2_SECRET_ACCESS_KEY` | Cloudflare R2 secret key | `your-secret-key` |
| `R2_ENDPOINT` | R2 endpoint URL | `https://account.r2.cloudflarestorage.com` |
| `R2_BUCKET` | R2 bucket name | `my-video-bucket` |
| `R2_ACCOUNT_ID` | Cloudflare account ID | `your-account-id` |
| `JWT_SECRET` | JWT signing secret | `very-long-secure-secret` |

### Optional Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_EXPIRATION_MINUTES` | 15 | JWT token expiration time |
| `JWT_BUFFER_MINUTES` | 30 | JWT buffer time |
| `SERVER_HOST` | localhost | Server hostname |
| `SERVER_PORT` | 8080 | Server port |
| `CORS_ALLOWED_ORIGINS` | * | CORS allowed origins |
| `SPRING_PROFILES_ACTIVE` | r2 | Spring profile |
| `HLS_ENCRYPTION_ENABLED` | true | Enable HLS encryption |

## Development Commands

### Build and Run

```bash
# Build only the application
docker-compose build m3u8-encoder

# Run specific service
docker-compose up m3u8-encoder

# View logs
docker-compose logs -f m3u8-encoder

# Restart service
docker-compose restart m3u8-encoder
```

### Debugging

```bash
# Access container shell
docker-compose exec m3u8-encoder bash

# Check FFmpeg installation
docker-compose exec m3u8-encoder ffmpeg -version

# View application logs
docker-compose logs m3u8-encoder

# Check health status
curl http://localhost:8080/actuator/health
```

### Database Management

```bash
# Access MongoDB shell
docker-compose exec mongo mongosh

# Backup database
docker-compose exec mongo mongodump --db m3u8 --out /data/backup

# Access Mongo Express
open http://localhost:8081
```

## Production Considerations

### Security

1. **Change default JWT secret** to a strong, unique value
2. **Restrict CORS origins** to your actual domains
3. **Use HTTPS** in production (update `protocol=https` in application.properties)
4. **Regular security updates** for base images

### Performance

1. **Adjust JVM memory** based on your server capacity
2. **Monitor disk usage** for upload/processing volumes
3. **Configure resource limits** in docker-compose.yml
4. **Use external MongoDB** for production

### Monitoring

1. **Health checks** are enabled by default
2. **Log aggregation** with external tools
3. **Metrics collection** for performance monitoring
4. **Backup strategy** for persistent volumes

## Troubleshooting

### Common Issues

1. **FFmpeg not found**: Ensure the Dockerfile includes FFmpeg installation
2. **MongoDB connection failed**: Check network connectivity between services
3. **R2 upload failed**: Verify credentials and bucket permissions
4. **Out of memory**: Adjust JVM heap size in environment variables

### Logs and Debugging

```bash
# View all service logs
docker-compose logs

# Follow specific service logs
docker-compose logs -f m3u8-encoder

# Check container status
docker-compose ps

# View resource usage
docker stats
```

## Cleanup

```bash
# Stop all services
docker-compose down

# Remove volumes (WARNING: This deletes data!)
docker-compose down -v

# Remove images
docker-compose down --rmi all

# Complete cleanup
docker system prune -a
```

