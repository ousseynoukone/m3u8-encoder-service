package com.xksgroup.m3u8encoderv2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "master_playlists")
public class MasterPlaylistRecord {
    @Id
    private String id;
    private String jobId; // Add jobId to link to specific job
    private String title;
    private String slug;
    private String resourceType;
    private String sourceKey;
    private String masterKey;
    private String masterUrl;
    private List<VariantInfo> variants;
    private Long durationSeconds; // Video duration in seconds for token expiration calculation
    private String status; // QUEUED, ENCODING, UPLOADING, COMPLETED, FAILED
    private Instant createdAt;
    private Instant updatedAt;
}


