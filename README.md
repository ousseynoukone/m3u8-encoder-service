# M3U8 Encoder V2

![Java](https://img.shields.io/badge/Java-17+-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen)
![Maven](https://img.shields.io/badge/Maven-Build-blue)
![MongoDB](https://img.shields.io/badge/MongoDB-6.0+-green)
![FFmpeg](https://img.shields.io/badge/FFmpeg-Required-red)

An advanced **Adaptive Bitrate (ABR) HLS encoder and streaming service** that automatically converts video/audio files into multiple quality variants and uploads them to Cloudflare R2 storage. Built with Spring Boot and designed for scalable video streaming applications.

## 🚀 Features

### Core Functionality
- **🎬 Multi-format Video Processing**: Supports various input video formats
- **📱 Adaptive Bitrate Streaming**: Automatically generates multiple quality variants (240p, 360p, 480p, 720p, 1080p)
- **🎵 Audio-only Content**: Intelligent detection and processing of audio-only files
- **☁️ Cloud Storage Integration**: Seamless upload to Cloudflare R2 (S3-compatible)
- **🔄 Parallel Processing**: Concurrent upload for improved performance

### Advanced Features
- **🔐 Secure Streaming**: JWT-based authentication for protected content access
- **📊 Content Management**: MongoDB-backed metadata storage
- **🎯 Resource Categories**: Support for Movies, Podcasts, Series, Replays, and Videos
- **🌐 CORS Support**: Configurable cross-origin resource sharing
- **📖 API Documentation**: Built-in Swagger/OpenAPI documentation
- **🐳 Docker Ready**: Complete containerization support

## 📋 Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **FFmpeg** (must be installed and accessible in PATH)
- **MongoDB 6.0+**
- **Cloudflare R2 Account** (or AWS S3 compatible storage)

## 🛠️ Installation & Setup

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/m3u8-encoder-v2.git
cd m3u8-encoder-v2
```

### 2. Install FFmpeg
```bash
# macOS
brew install ffmpeg

# Ubuntu/Debian
sudo apt update && sudo apt install ffmpeg

# CentOS/RHEL
sudo yum install ffmpeg
```

### 3. Start MongoDB (using Docker Compose)
```bash
docker-compose up -d
```
This will start:
- MongoDB on port `27017`
- Mongo Express (web UI) on port `8081`

### 4. Configure Environment Variables

Create a `.env` file in the project root:

```bash
# R2 Configuration (Cloudflare R2)
R2_ACCESS_KEY_ID=your-r2-access-key-id
R2_SECRET_ACCESS_KEY=your-r2-secret-access-key  
R2_ENDPOINT=https://your-account-id.r2.cloudflarestorage.com
R2_BUCKET=your-bucket-name
R2_ACCOUNT_ID=your-cloudflare-account-id

# MongoDB Configuration
MONGODB_URI=mongodb://localhost:27017/m3u8

# JWT Security Configuration
JWT_SECRET=your-very-long-secure-secret-key-for-production-use-at-least-32-characters
JWT_EXPIRATION_MINUTES=15

# Server Configuration
SERVER_HOST=localhost
SERVER_PORT=8080

# CORS Configuration
CORS_ALLOWED_ORIGINS=*

# Storage Profile
SPRING_PROFILES_ACTIVE=r2
```

### 5. Build and Run
```bash
# Build the project
mvn clean compile

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## 📖 API Documentation

Once the application is running, access the interactive API documentation:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

## 🎯 Usage

### Upload and Process Video/Audio

```bash
curl -X POST http://localhost:8080/v2/upload \
  -F "file=@your-video.mp4" \
  -F "title=my-awesome-video" \
  -F "resourceType=MOVIE"
```

**Response:**
```json
{
  "slug": "my-awesome-video",
  "message": "Upload and encoding completed successfully",
  "playlistUrl": "http://localhost:8080/v2/m3u8/master/my-awesome-video",
  "variants": [
    {
      "resolution": "1920x1080",
      "bandwidth": "5000000",
      "playlistKey": "my-awesome-video/1080p/playlist.m3u8"
    },
    {
      "resolution": "1280x720", 
      "bandwidth": "3000000",
      "playlistKey": "my-awesome-video/720p/playlist.m3u8"
    }
  ]
}
```

### Access Master Playlist

```bash
curl http://localhost:8080/v2/m3u8/master/my-awesome-video
```

**Response (M3U8 format):**
```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
https://r2-presigned-url-for-1080p-playlist

#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720  
https://r2-presigned-url-for-720p-playlist
```

### Resource Types

The API supports different content categories:
- `AUDIO` - General audio content
- `VIDEO` - General video content

## 🏗️ Architecture

### Tech Stack
- **Backend**: Spring Boot 3.5.4, Java 17
- **Database**: MongoDB (with Spring Data)
- **Storage**: Cloudflare R2 (S3-compatible API)
- **Video Processing**: FFmpeg
- **Security**: JWT tokens
- **Documentation**: SpringDoc OpenAPI

### Processing Pipeline

1. **Upload** → File received via multipart upload
2. **Analysis** → FFmpeg detects video/audio streams  
3. **Encoding** → Generate multiple quality variants
4. **Upload** → Parallel upload to R2 storage
5. **Metadata** → Store playlist info in MongoDB
6. **Cleanup** → Remove temporary files

### Quality Variants (Video)
- **1080p**: 1920x1080, ~5 Mbps
- **720p**: 1280x720, ~3 Mbps  
- **480p**: 854x480, ~1.5 Mbps
- **360p**: 640x360, ~800 Kbps
- **240p**: 426x240, ~400 Kbps

## 🔧 Configuration

### Application Properties

Key configuration options in `application.properties`:

```properties
# File upload limits
spring.servlet.multipart.max-file-size=6GB
spring.servlet.multipart.max-request-size=6GB

# MongoDB connection
spring.data.mongodb.uri=${MONGODB_URI}

# Security settings
security.jwt.secret=${JWT_SECRET}
security.jwt.expiration-minutes=${JWT_EXPIRATION_MINUTES:15}

# CORS settings
security.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:*}
```

### Environment Profiles

- **`r2`**: Use Cloudflare R2 storage (default)
- **`aws`**: Use AWS S3 storage

## 🐳 Docker Deployment

### Using Docker Compose
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services  
docker-compose down
```

### Build Application Container
```bash
# Build JAR file
mvn clean package -DskipTests

# Create Dockerfile (example)
FROM openjdk:17-jre-slim
RUN apt-get update && apt-get install -y ffmpeg
COPY target/m3u8-encoder-v2-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## 🔒 Security

### JWT Authentication
- Access tokens expire after 15 minutes (configurable)
- Secure playlist URLs with presigned requests
- Buffer time for token validation

### Best Practices
- Use strong JWT secrets (32+ characters)
- Configure CORS appropriately for your domain
- Use HTTPS in production
- Rotate R2 access keys regularly

## 📊 Monitoring & Logs

The application provides detailed logging for:
- Upload progress and status
- FFmpeg encoding operations  
- R2 storage interactions
- Error handling and debugging

View logs in real-time:
```bash
mvn spring-boot:run | grep -E "(INFO|ERROR|WARN)"
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

### Common Issues

**FFmpeg not found:**
```bash
# Verify FFmpeg installation
ffmpeg -version

# Add to PATH if needed
export PATH="/usr/local/bin:$PATH"
```

**MongoDB connection failed:**
```bash
# Check MongoDB status
docker-compose ps mongo

# Restart MongoDB
docker-compose restart mongo
```

**R2 upload errors:**
- Verify R2 credentials and endpoint URL
- Check bucket permissions
- Ensure bucket exists

### Getting Help

- 📖 Check the [API Documentation](http://localhost:8080/swagger-ui.html)
- 🐛 [Report Issues](https://github.com/yourusername/m3u8-encoder-v2/issues)
- 💬 [Discussions](https://github.com/yourusername/m3u8-encoder-v2/discussions)

---
