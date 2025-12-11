package com.xksgroup.m3u8encoderv2.model.Job;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UploadingJobProgress {
    private int totalSegments;
    private int completedSegments;
    private int failedSegments;
    private int uploadingSegments;
    private int pendingSegments;
    private int totalIndex;
    private int completedIndex;
    private int failedIndex;
    private int uploadingIndex;
    private int pendingIndex;

}
