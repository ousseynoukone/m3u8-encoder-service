package com.xksgroup.m3u8encoderv2.service;

import com.xksgroup.m3u8encoderv2.model.MasterPlaylistRecord;
import com.xksgroup.m3u8encoderv2.model.VariantInfo;
import com.xksgroup.m3u8encoderv2.model.VariantSegment;
import com.xksgroup.m3u8encoderv2.repo.MasterPlaylistRecordRepository;
import com.xksgroup.m3u8encoderv2.repo.VariantSegmentRepository;
import com.xksgroup.m3u8encoderv2.service.helper.R2StorageHelper;
import com.xksgroup.m3u8encoderv2.service.helper.UploadProgressHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.xksgroup.m3u8encoderv2.service.helper.R2StorageHelper.findSegmentFiles;

@Slf4j
@Service
public class R2StorageService {

    private final S3Client s3;
    private final S3Presigner s3Presigner;
    private final MasterPlaylistRecordRepository masterRepo;
    private final VariantSegmentRepository segmentRepo;
    private final UploadProgressHelper progressHelper;

    @Value("${r2.bucket}")
    private String bucket;

    @Value("${r2.accountId}")
    private String accountId;

    @Value("${cdn.baseUrl:}")
    private String cdnBaseUrl;

    @Value("${upload.parallel.enabled:true}")
    private boolean parallelUploadEnabled;

    @Value("${upload.parallel.poolSize:20}")
    private int uploadPoolSize;

    @Value("${upload.retry.maxAttempts:3}")
    private int maxRetryAttempts;

    @Value("${upload.retry.delayMs:1000}")
    private long retryDelayMs;

    private ExecutorService executor;

    JobService jobService;



    public R2StorageService(@Qualifier("r2Client") S3Client s3,
                            @Qualifier("r2Presigner") S3Presigner s3Presigner,
                            MasterPlaylistRecordRepository masterRepo,
                            VariantSegmentRepository segmentRepo,
                            UploadProgressHelper progressHelper) {
        this.s3 = s3;
        this.s3Presigner = s3Presigner;
        this.masterRepo = masterRepo;
        this.segmentRepo = segmentRepo;
        this.progressHelper = progressHelper;
    }

    // Setter for circular dependency
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
        this.progressHelper.setJobService(jobService);
    }

    @Transactional
    public String uploadAbrJob(Path jobDir, String keyPrefix, String fileSlug, String title, String resourceType) throws Exception {
        log.info("Starting ABR job upload - Job: {}, Variants: {}", fileSlug, countVariantDirs(jobDir));
        UploadTransaction transaction = new UploadTransaction(keyPrefix);

        try {
            initializeExecutor();
            validateMasterPlaylist(jobDir);

            List<Path> variantDirs = findVariantDirs(jobDir);
            String baseUrl = buildBaseUrl();
            boolean includeBucketInUrl = baseUrl.contains("cloudflarestorage.com");
            String prefix = keyPrefix.endsWith("/") ? keyPrefix : keyPrefix + "/";


            // Parse master playlist to extract proper variant attributes
            Path masterPath = jobDir.resolve("master.m3u8");
            String masterContent = Files.readString(masterPath, StandardCharsets.UTF_8);
            List<VariantInfo> parsedVariants = R2StorageHelper.parseMasterPlaylist(masterContent, variantDirs);
            
            log.info("Parsed {} variants from master playlist with proper attributes", parsedVariants.size());

            // Upload encryption keys if they exist
            uploadEncryptionKeys(jobDir, prefix, transaction);

            // Upload master playlist
            String masterUrl = uploadMasterPlaylist(jobDir, prefix, baseUrl, includeBucketInUrl, variantDirs, transaction);

            // Create mapping of variant directory names to parsed variant info
            Map<String, VariantInfo> variantInfoMap = new HashMap<>();
            for (VariantInfo variant : parsedVariants) {
                variantInfoMap.put(variant.getLabel(), variant);
            }

            // Upload variants and segments with parsed attributes
            List<VariantInfo> variants = new ArrayList<>();
            List<VariantSegment> stagedSegments = new ArrayList<>();

            for (Path variantDir : variantDirs) {
                String dirName = variantDir.getFileName().toString();
                VariantInfo parsedVariantInfo = variantInfoMap.get(dirName);
                
                if (parsedVariantInfo == null) {
                    log.warn("No parsed variant info found for directory: {}, using defaults", dirName);
                    parsedVariantInfo = VariantInfo.builder()
                            .label(dirName)
                            .bandwidth("3000000")
                            .resolution("1280x720")
                            .codecs("avc1.4d401f,mp4a.40.2")
                            .segmentCount(0)
                            .build();

                }
                
                VariantUploadResult result = uploadVariant(variantDir, prefix, baseUrl, includeBucketInUrl, transaction, parsedVariantInfo);
                variants.add(result.variantInfo);
                stagedSegments.addAll(result.segments);
            }

            // Verify all uploads succeeded before committing to database
            verifyAllUploadsSuccessful(stagedSegments);

            // Save to database (this will commit the transaction)
            saveToDatabase(title, fileSlug, resourceType, masterUrl,
                    prefix + "master.m3u8", variants, stagedSegments,
                    R2StorageHelper.calculateVideoDuration(variantDirs.get(0)));

            log.info("ABR upload completed successfully - Master: {}, Variants: {}, Segments: {}",
                    masterUrl, variants.size(), stagedSegments.size());

            // Mark upload completion to calculate duration
            if (jobService != null) {
                String jobId = extractJobIdFromKeyPrefix(keyPrefix);
                if (jobId != null) {
                    jobService.markUploadComplete(jobId);
                }
            }

            return masterUrl;

        } catch (Exception e) {
            log.error("ABR upload failed for job: {} - initiating rollback", fileSlug, e);
            rollbackUpload(transaction);
            throw e;
        }
    }

    private void verifyAllUploadsSuccessful(List<VariantSegment> segments) throws Exception {
        List<VariantSegment> failedSegments = segments.stream()
                .filter(s -> s.getUploadStatus() == VariantSegment.UploadStatus.FAILED)
                .toList();

        if (!failedSegments.isEmpty()) {
            throw new Exception("Upload failed: " + failedSegments.size() + " segments failed to upload");
        }
    }

    private void rollbackUpload(UploadTransaction transaction) {
        log.warn("Starting rollback for upload transaction - {} keys to delete", transaction.getUploadedKeys().size());

        try {
            // Delete all uploaded objects from R2
            deleteUploadedObjects(transaction.getUploadedKeys());

            // Delete any database records that might have been created
            rollbackDatabaseRecords(transaction);

            log.info("Rollback completed successfully - {} objects deleted", transaction.getUploadedKeys().size());

        } catch (Exception e) {
            log.error("Rollback failed - manual cleanup may be required for keys: {}",
                    transaction.getUploadedKeys(), e);
        }
    }

    private void deleteUploadedObjects(Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }

        // Use batch delete for efficiency
        List<String> keyList = new ArrayList<>(keys);
        int batchSize = 1000; // S3 delete limit

        for (int i = 0; i < keyList.size(); i += batchSize) {
            List<String> batch = keyList.subList(i, Math.min(i + batchSize, keyList.size()));
            deleteBatch(batch);
        }
    }

    private void deleteBatch(List<String> keys) {
        try {
            List<ObjectIdentifier> objectsToDelete = keys.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

            Delete delete = Delete.builder()
                    .objects(objectsToDelete)
                    .build();

            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(delete)
                    .build();

            DeleteObjectsResponse response = s3.deleteObjects(deleteRequest);

            if (!response.errors().isEmpty()) {
                log.warn("Some objects failed to delete: {}", response.errors());
            }

            log.debug("Deleted batch of {} objects", keys.size());

        } catch (Exception e) {
            log.error("Failed to delete batch of objects: {}", keys, e);
            // Continue with individual deletes as fallback
            deleteIndividually(keys);
        }
    }

    private void deleteIndividually(List<String> keys) {
        for (String key : keys) {
            try {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build();

                s3.deleteObject(deleteRequest);
                log.debug("Deleted object: {}", key);

            } catch (Exception e) {
                log.error("Failed to delete object: {}", key, e);
            }
        }
    }

    private void rollbackDatabaseRecords(UploadTransaction transaction) {
        // Note: If this method is called within a @Transactional method that throws an exception,
        // Spring will automatically roll back any database changes.
        // This method is here for explicit cleanup if needed in the future.
        log.debug("Database rollback handled by Spring transaction management");
    }

    private void initializeExecutor() {
        if (executor == null) {
            executor = Executors.newFixedThreadPool(Math.max(1, uploadPoolSize));
        }
    }

    private void validateMasterPlaylist(Path jobDir) throws IllegalStateException {
        Path master = jobDir.resolve("master.m3u8");
        if (!Files.exists(master)) {
            throw new IllegalStateException("master.m3u8 missing in " + jobDir);
        }
    }

    private List<Path> findVariantDirs(Path jobDir) throws Exception {
        try (Stream<Path> stream = Files.list(jobDir)) {
            List<Path> variantDirs = stream.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("index.m3u8")))
                    .toList();

            if (variantDirs.isEmpty()) {
                throw new IllegalStateException("No variant directories found in " + jobDir);
            }

            return variantDirs;
        }
    }

    private int countVariantDirs(Path jobDir) {
        try (Stream<Path> stream = Files.list(jobDir)) {
            return (int) stream.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("index.m3u8")))
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    private String uploadMasterPlaylist(Path jobDir, String prefix, String baseUrl,
                                        boolean includeBucketInUrl, List<Path> variantDirs,
                                        UploadTransaction transaction) throws Exception {
        Path master = jobDir.resolve("master.m3u8");
        String masterKey = prefix + "master.m3u8";

        String masterContent = Files.readString(master, StandardCharsets.UTF_8);
        String rewrittenMaster = R2StorageHelper.rewriteMaster(masterContent, prefix, baseUrl, bucket, includeBucketInUrl, variantDirs);

        uploadWithRetry(masterKey, RequestBody.fromString(rewrittenMaster, StandardCharsets.UTF_8),
                "application/vnd.apple.mpegurl", transaction);

        return includeBucketInUrl ? baseUrl + "/" + bucket + "/" + masterKey : baseUrl + "/" + masterKey;
    }

    /**
     * Upload encryption keys and related files
     */
    private void uploadEncryptionKeys(Path jobDir, String prefix, UploadTransaction transaction) throws Exception {
        try {
            // Look for encryption key files (*.key, *.txt, keyinfo_*.txt)
            try (Stream<Path> files = Files.walk(jobDir, 1)) {
                List<Path> encryptionFiles = files
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.endsWith(".key") || 
                                   fileName.startsWith("iv_") && fileName.endsWith(".txt") ||
                                   fileName.startsWith("keyinfo_") && fileName.endsWith(".txt");
                        })
                        .toList();

                if (encryptionFiles.isEmpty()) {
                    log.debug("No encryption files found in job directory: {}", jobDir);
                    return;
                }

                log.info("Found {} encryption files to upload", encryptionFiles.size());

                for (Path encryptionFile : encryptionFiles) {
                    String fileName = encryptionFile.getFileName().toString();
                    String keyPath = prefix + fileName;

                    // Determine content type
                    String contentType = fileName.endsWith(".key") ? "application/octet-stream" : "text/plain";

                    uploadWithRetry(keyPath, RequestBody.fromFile(encryptionFile), contentType, transaction);
                    log.debug("Uploaded encryption file: {} -> {}", fileName, keyPath);
                }

                log.info("Successfully uploaded {} encryption files", encryptionFiles.size());
            }
        } catch (Exception e) {
            log.error("Failed to upload encryption keys: {}", e.getMessage(), e);
            throw e;
        }
    }

    private VariantUploadResult uploadVariant(Path variantDir, String prefix, String baseUrl,
                                              boolean includeBucketInUrl, UploadTransaction transaction,
                                              VariantInfo parsedVariantInfo) throws Exception {
        String label = variantDir.getFileName().toString();
        String variantKey = prefix + label + "/index.m3u8";

        // Upload variant playlist
        Path index = variantDir.resolve("index.m3u8");
        String indexContent = Files.readString(index, StandardCharsets.UTF_8);
        String rewrittenVariant = R2StorageHelper.rewriteVariant(indexContent, variantKey, baseUrl, bucket, includeBucketInUrl);

        uploadWithRetry(variantKey, RequestBody.fromString(rewrittenVariant, StandardCharsets.UTF_8),
                "application/vnd.apple.mpegurl", transaction);

        String variantUrl = includeBucketInUrl ? baseUrl + "/" + bucket + "/" + variantKey : baseUrl + "/" + variantKey;

        // Upload segments
        List<Path> segmentFiles = findSegmentFiles(variantDir);
        List<VariantSegment> segments = uploadSegments(variantDir, segmentFiles, prefix, label, transaction);

        // Use parsed variant info with proper attributes
        VariantInfo variantInfo = VariantInfo.builder()
                .label(label)
                .bandwidth(parsedVariantInfo.getBandwidth())
                .resolution(parsedVariantInfo.getResolution())
                .codecs(parsedVariantInfo.getCodecs())
                .playlistKey(variantKey)
                .playlistUrl(variantUrl)
                .segmentCount(segmentFiles.size())
                .build();

        return new VariantUploadResult(variantInfo, segments);
    }



    private List<VariantSegment> uploadSegments(Path variantDir, List<Path> segmentFiles,
                                                String prefix, String label, UploadTransaction transaction) throws Exception {
        if (segmentFiles.isEmpty()) {
            return new ArrayList<>();
        }

        if (parallelUploadEnabled) {
            return uploadSegmentsParallel(segmentFiles, prefix, label, transaction);
        } else {
            return uploadSegmentsSequential(segmentFiles, prefix, label, transaction);
        }
    }

    private List<VariantSegment> uploadSegmentsParallel(List<Path> segmentFiles, String prefix,
                                                        String label, UploadTransaction transaction) throws Exception {
        List<CompletableFuture<SegmentUploadResult>> futures = new ArrayList<>();
        String jobId = progressHelper.extractJobIdFromKeyPrefix(prefix);
        int totalSegments = segmentFiles.size();
        
        // Initialize segment counts with SSE
        progressHelper.updateSegmentCountsImmediate(jobId, totalSegments, 0, 0, 0, totalSegments);
        
        // Update progress: Starting segment uploads for this variant
        progressHelper.updateVariantStart(jobId, label, totalSegments);

        // Atomic counters for thread-safe updates
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        for (int i = 0; i < segmentFiles.size(); i++) {
            Path file = segmentFiles.get(i);
            String segKey = prefix + label + "/" + file.getFileName();
            int position = i;

            futures.add(CompletableFuture.supplyAsync(() -> {
                SegmentUploadResult result = uploadSegmentWithRetry(file, segKey, position, transaction);
                
                // Update counts after completion
                if (result.success) {
                    int completed = completedCount.incrementAndGet();
                    // Use throttled SSE updates (max once every 3 seconds)
                    progressHelper.updateSegmentCounts(jobId, totalSegments, completed, failedCount.get(), 0, totalSegments - completed - failedCount.get());
                } else {
                    int failed = failedCount.incrementAndGet();
                    progressHelper.updateSegmentCounts(jobId, totalSegments, completedCount.get(), failed, 0, totalSegments - completedCount.get() - failed);
                }
                
                return result;
            }, executor));
        }

        // Wait for all uploads and collect results
        List<SegmentUploadResult> results = new ArrayList<>();
        CompletableFuture<Void> allUploads = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            allUploads.join();
            for (CompletableFuture<SegmentUploadResult> future : futures) {
                results.add(future.get());
            }
        } catch (Exception e) {
            throw new Exception("Parallel segment upload failed for variant: " + label, e);
        }

        // Check if any uploads failed - if so, throw exception to trigger rollback
        List<SegmentUploadResult> failedUploads = results.stream()
                .filter(r -> !r.success)
                .toList();

        if (!failedUploads.isEmpty()) {
            throw new Exception("Failed to upload " + failedUploads.size() + " segments for variant: " + label);
        }

        // Convert to VariantSegment objects
        List<VariantSegment> segments = new ArrayList<>();
        for (SegmentUploadResult result : results) {
            segments.add(VariantSegment.builder()
                    .variantLabel(label)
                    .position(result.position)
                    .duration(0)
                    .key(result.key)
                    .uploadStatus(VariantSegment.UploadStatus.COMPLETED)
                    .uploadedAt(Instant.now())
                    .build());
        }

        // Force final SSE update for variant completion
        progressHelper.updateSegmentCountsImmediate(jobId, totalSegments, totalSegments, 0, 0, 0);
        
        log.info("Parallel upload completed for variant '{}': {}/{} segments successful",
                label, segments.size(), segmentFiles.size());

        return segments;
    }

    private List<VariantSegment> uploadSegmentsSequential(List<Path> segmentFiles, String prefix,
                                                          String label, UploadTransaction transaction) throws Exception {
        List<VariantSegment> segments = new ArrayList<>();
        String jobId = progressHelper.extractJobIdFromKeyPrefix(prefix);
        int totalSegments = segmentFiles.size();
        
        // Initialize segment counts with SSE
        progressHelper.updateSegmentCountsImmediate(jobId, totalSegments, 0, 0, 0, totalSegments);
        
        // Update progress: Starting segment uploads for this variant
        progressHelper.updateVariantStart(jobId, label, totalSegments);

        for (int i = 0; i < segmentFiles.size(); i++) {
            Path file = segmentFiles.get(i);
            String segKey = prefix + label + "/" + file.getFileName();

            SegmentUploadResult result = uploadSegmentWithRetry(file, segKey, i, transaction);

            if (!result.success) {
                String errorMessage = "Failed to upload segment: " + file.getFileName() + " for variant: " + label;
                progressHelper.updateUploadError(jobId, errorMessage);
                throw new Exception(errorMessage, result.exception);
            }

            // Update completed segments count - use throttled SSE updates (max once every 3 seconds)
            int completed = i + 1;
            progressHelper.updateSegmentCounts(jobId, totalSegments, completed, 0, 0, totalSegments - completed);

            segments.add(VariantSegment.builder()
                    .variantLabel(label)
                    .position(i)
                    .duration(0)
                    .key(segKey)
                    .uploadStatus(VariantSegment.UploadStatus.COMPLETED)
                    .uploadedAt(Instant.now())
                    .build());
        }

        // Force final SSE update for variant completion
        progressHelper.updateSegmentCountsImmediate(jobId, totalSegments, totalSegments, 0, 0, 0);
        
        log.info("Sequential upload completed for variant '{}': all {} segments successful",
                label, segmentFiles.size());

        return segments;
    }

    private SegmentUploadResult uploadSegmentWithRetry(Path file, String key, int position, UploadTransaction transaction) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                uploadWithRetry(key, RequestBody.fromFile(file), "video/mp2t", transaction);
                return new SegmentUploadResult(key, position, true, null);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(retryDelayMs * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Failed to upload segment after {} attempts: {} -> {}", maxRetryAttempts, file.getFileName(), key, lastException);
        return new SegmentUploadResult(key, position, false, lastException);
    }

    private void uploadWithRetry(String key, RequestBody body, String contentType, UploadTransaction transaction) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(cache(key))
                        .build(), body);

                // Track successful upload for potential rollback
                transaction.addUploadedKey(key);
                return;

            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetryAttempts) {
                    log.warn("Upload attempt {} failed for key: {} - retrying in {}ms", attempt, key, retryDelayMs * attempt);
                    try {
                        Thread.sleep(retryDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Upload interrupted", ie);
                    }
                }
            }
        }

        throw new Exception("Failed to upload after " + maxRetryAttempts + " attempts: " + key, lastException);
    }

    private String saveToDatabase(String title, String fileSlug, String resourceType, String masterUrl,
                                  String masterKey, List<VariantInfo> variants, List<VariantSegment> segments,
                                  Long durationSeconds) throws Exception {

        // Extract jobId from masterKey (format: resourceType/slug/jobId/master.m3u8)
        String jobId = null;
        if (masterKey != null && masterKey.contains("/")) {
            String[] parts = masterKey.split("/");
            if (parts.length >= 3) {
                jobId = parts[2]; // resourceType/slug/jobId/master.m3u8
            }
        }

        MasterPlaylistRecord masterRec = MasterPlaylistRecord.builder()
                .jobId(jobId)
                .title(title)
                .slug(fileSlug)
                .resourceType(resourceType)
                .masterKey(masterKey)
                .masterUrl(masterUrl)
                .variants(variants)
                .durationSeconds(durationSeconds)
                .status("COMPLETED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        masterRec = masterRepo.save(masterRec);

        // Set masterId for all segments and save
        String masterId = masterRec.getId();
        segments.forEach(s -> s.setMasterId(masterId));

        if (!segments.isEmpty()) {
            segmentRepo.saveAll(segments);
        }

        return masterId;
    }

    private String buildBaseUrl() {
        if (cdnBaseUrl != null && !cdnBaseUrl.isBlank()) {
            return cdnBaseUrl.replaceAll("/+$", "");
        }
        return "https://" + accountId + ".r2.cloudflarestorage.com";
    }

    private static String cache(String key) {
        return key.endsWith(".m3u8")
                ? "public, max-age=15, s-maxage=15, must-revalidate"
                : "public, max-age=3600, s-maxage=3600, immutable";
    }




    public String getPlaylistContent(String key) throws Exception {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            byte[] contentBytes = s3.getObject(getRequest).readAllBytes();
            return new String(contentBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new Exception("Failed to fetch playlist: " + key, e);
        }
    }

    public String generatePresignedUrl(String key, int expirationSeconds) throws Exception {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();

        } catch (Exception e) {
            throw new Exception("Failed to generate presigned URL: " + key, e);
        }
    }

    /**
     * Get encryption key data from storage
     */
    public byte[] getKeyData(String key) throws Exception {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            byte[] keyData = s3.getObject(getRequest).readAllBytes();
            log.debug("Retrieved encryption key data: {} bytes from key: {}", keyData.length, key);
            return keyData;

        } catch (Exception e) {
            throw new Exception("Failed to fetch encryption key: " + key, e);
        }
    }



    /**
     * Extract job ID from key prefix (resourceType/slug/jobId)
     */
    private String extractJobIdFromKeyPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            return null;
        }
        String[] parts = keyPrefix.split("/");
        if (parts.length >= 3) {
            return parts[parts.length - 1]; // Last part should be jobId
        }
        return null;
    }

    // Helper classes
    private static class UploadTransaction {
        private final Set<String> uploadedKeys = ConcurrentHashMap.newKeySet();

        UploadTransaction(String keyPrefix) {
            // keyPrefix not used in this implementation
        }

        void addUploadedKey(String key) {
            uploadedKeys.add(key);
        }

        Set<String> getUploadedKeys() {
            return new HashSet<>(uploadedKeys);
        }

    }

    private static class VariantUploadResult {
        final VariantInfo variantInfo;
        final List<VariantSegment> segments;

        VariantUploadResult(VariantInfo variantInfo, List<VariantSegment> segments) {
            this.variantInfo = variantInfo;
            this.segments = segments;
        }
    }

    private static class SegmentUploadResult {
        final String key;
        final int position;
        final boolean success;
        final Exception exception;

        SegmentUploadResult(String key, int position, boolean success, Exception exception) {
            this.key = key;
            this.position = position;
            this.success = success;
            this.exception = exception;
        }
    }

    /**
     * List all files in R2 bucket for debugging
     */
    public List<String> listAllFiles() {
        try {
            log.info("Listing all files in R2 bucket: {}", bucket);
            
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .build();
            
            ListObjectsV2Response listResponse = s3.listObjectsV2(listRequest);
            List<S3Object> objects = listResponse.contents();
            
            List<String> fileNames = objects.stream()
                    .map(S3Object::key)
                    .toList();
            
            log.info("Found {} files in bucket {}", fileNames.size(), bucket);
            return fileNames;
            
        } catch (Exception e) {
            log.error("Failed to list files in R2 bucket {}: {}", bucket, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Delete ALL files from R2 storage (DANGEROUS - deletes entire bucket content)
     */
    public boolean deleteAllFiles() {
        try {
            log.warn("🚨 DELETING ALL FILES FROM R2 BUCKET: {}", bucket);
            
            // List all objects in the bucket
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .build();
            
            ListObjectsV2Response listResponse = s3.listObjectsV2(listRequest);
            List<S3Object> objects = listResponse.contents();
            
            if (objects.isEmpty()) {
                log.info("No files found in bucket: {}", bucket);
                return true;
            }
            
            log.warn("Found {} files to delete from entire bucket", objects.size());
            
            // Delete objects in batches (max 1000 per batch)
            int batchSize = 1000;
            int totalDeleted = 0;
            
            for (int i = 0; i < objects.size(); i += batchSize) {
                List<S3Object> batch = objects.subList(i, Math.min(i + batchSize, objects.size()));
                
                List<ObjectIdentifier> objectsToDelete = batch.stream()
                        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                        .toList();
                
                Delete delete = Delete.builder()
                        .objects(objectsToDelete)
                        .build();
                
                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(delete)
                        .build();
                
                DeleteObjectsResponse deleteResponse = s3.deleteObjects(deleteRequest);
                totalDeleted += deleteResponse.deleted().size();
                
                log.info("Deleted batch of {} files (total so far: {})", batch.size(), totalDeleted);
            }
            
            log.warn("✅ Successfully deleted ALL {} files from R2 bucket: {}", totalDeleted, bucket);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to delete all files from R2 bucket {}: {}", bucket, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Delete all files with a specific prefix from R2 storage
     */
    public boolean deleteFilesByPrefix(String prefix) {
        try {
            log.info("Starting deletion of files with prefix: {}", prefix);
            
            // List all objects with the prefix
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();
            
            ListObjectsV2Response listResponse = s3.listObjectsV2(listRequest);
            List<S3Object> objects = listResponse.contents();
            
            if (objects.isEmpty()) {
                log.info("No files found with prefix: {}", prefix);
                return true;
            }
            
            log.info("Found {} files to delete with prefix: {}", objects.size(), prefix);
            
            // Delete objects in batches
            List<ObjectIdentifier> objectsToDelete = objects.stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();
            
            Delete delete = Delete.builder()
                    .objects(objectsToDelete)
                    .build();
            
            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(delete)
                    .build();
            
            DeleteObjectsResponse deleteResponse = s3.deleteObjects(deleteRequest);
            
            log.info("Successfully deleted {} files with prefix: {}", 
                    deleteResponse.deleted().size(), prefix);
            
            if (!deleteResponse.errors().isEmpty()) {
                log.warn("Some files failed to delete: {}", deleteResponse.errors());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to delete files with prefix: {} - Error: {}", prefix, e.getMessage(), e);
            return false;
        }
    }
}