package com.xksgroup.m3u8encoderv2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantInfo {
    private String label;
    private String bandwidth;
    private String resolution;
    private String codecs;
    private String playlistKey;
    private String playlistUrl;
    private int segmentCount;
}


