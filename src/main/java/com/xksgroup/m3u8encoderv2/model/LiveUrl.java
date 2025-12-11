package com.xksgroup.m3u8encoderv2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
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
@Document(collection = "live_urls")
public class LiveUrl {
    
    @Id
    @JsonIgnore
    private String id;
    
    @NotBlank(message = "urlId is required")
    private String urlId;
    
    @NotBlank(message = "url is required")
    private String url;
    
    private Instant createdAt;
    
    private Instant updatedAt;
}


