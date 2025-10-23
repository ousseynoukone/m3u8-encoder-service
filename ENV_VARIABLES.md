# Required Environment Variables

Copy these variables to your `.env` file or set them as environment variables:

```bash
# R2 Configuration (Cloudflare R2)
R2_ACCESS_KEY_ID=your-r2-access-key-id
R2_SECRET_ACCESS_KEY=your-r2-secret-access-key  
R2_ENDPOINT=https://your-account-id.r2.cloudflarestorage.com
R2_BUCKET=your-bucket-name
R2_ACCOUNT_ID=your-cloudflare-account-id

# MongoDB Configuration
MONGODB_URI=mongodb://localhost:27017/m3u8

# JWT Security Configuration (IMPORTANT: Use a strong secret in production!)
JWT_SECRET=your-very-long-secure-secret-key-for-production-use-at-least-32-characters
JWT_EXPIRATION_MINUTES=15

# Server Configuration for Secure Proxy
SERVER_HOST=localhost
SERVER_PORT=8080

# CORS Configuration
CORS_ALLOWED_ORIGINS=*

# Spring Profile (use 'r2' for Cloudflare R2, 'aws' for AWS S3)
SPRING_PROFILES_ACTIVE=r2
```

## Running the Application

1. **Create `.env` file** in the project root with the above variables
2. **Start MongoDB** if using local instance
3. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```

## Testing the Configuration

The application should start successfully and you should see logs indicating:
- MongoDB connection established
- TokenService initialized with JWT expiration time
- Spring profile active (r2 or aws)
- Server started on configured port

If you get bean conflicts, ensure `SPRING_PROFILES_ACTIVE=r2` is set properly.

