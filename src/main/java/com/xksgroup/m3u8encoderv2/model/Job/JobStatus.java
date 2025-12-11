package com.xksgroup.m3u8encoderv2.model.Job;

public enum JobStatus {
    PENDING,        // Job created, waiting to start
    UPLOADING,      // File upload in progress
    DOWNLOADING,   // Downloading file remotely (for URL-uploaded jobs)
    ENCODING,       // FFmpeg encoding in progress
    UPLOADING_TO_CLOUD_STORAGE, // Uploading to cloud storage
    COMPLETED,      // Job completed successfully
    FAILED,         // Job failed
    CANCELLED       // Job was cancelled
}
