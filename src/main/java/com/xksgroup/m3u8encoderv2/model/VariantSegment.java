package com.xksgroup.m3u8encoderv2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "variant_segments")
public class VariantSegment {

    @Id
    private String id;

    private String masterId;      // reference to MasterPlaylistRecord
    private String variantLabel;  // e.g., 1080p or folder name

    private int position;         // order/index in playlist
    private double duration;      // seconds (if known)

    private String key;           // object key in storage (R2/S3)

    public enum UploadStatus { PENDING, UPLOADING, COMPLETED, FAILED }
    private UploadStatus uploadStatus;

    private Instant uploadedAt;
    private String uploadError;
}


