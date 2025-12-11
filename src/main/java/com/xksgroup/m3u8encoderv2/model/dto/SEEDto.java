package com.xksgroup.m3u8encoderv2.model.dto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class SEEDto {
    private SseEmitter sseEmitter;
    private String jobId;
}
