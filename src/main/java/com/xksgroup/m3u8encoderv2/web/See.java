package com.xksgroup.m3u8encoderv2.web;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xksgroup.m3u8encoderv2.domain.RequestIssuer;
import com.xksgroup.m3u8encoderv2.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Job Streaming",
        description = "Provides real-time updates about active encoding jobs using Server-Sent Events (SSE)."
)
@RestController
@RequestMapping("/api/v2/m3u8-encoder")
@RequiredArgsConstructor
public class See {

    private final EventService eventService;

    @Operation(
            summary = "Stream active job status updates",
            description = """
                This endpoint uses **Server-Sent Events (SSE)** to continuously stream real-time updates 
                about active encoding or processing jobs.  
                
                Clients can connect and listen for events without needing to poll the server.  
                
                Each event includes:
                - A unique event ID  
                - An event name (`heartbeat` or `job-update`)  
                - The current server time or job progress  
                
                The connection remains open until the client disconnects or a timeout occurs.
                """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "SSE stream started successfully (Content-Type: text/event-stream)",
                            content = @Content(
                                    mediaType = "text/event-stream",
                                    schema = @Schema(implementation = String.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error or stream failure",
                            content = @Content(schema = @Schema(implementation = String.class))
                    )
            }
    )
    @GetMapping(value = "/job-progression-sse", produces = "text/event-stream")
    public SseEmitter streamSseMvc( @RequestHeader Map<String, String> headers, @RequestParam(required = false, defaultValue = "") String jobId) {

        if(!headers.containsKey("x-auth-email") || !headers.containsKey("x-auth-sub")){
            throw new RuntimeException("Unauthorized: Missing authentication headers");
        }
        RequestIssuer issuer = RequestIssuer.builder()
                .email(headers.get("x-auth-email"))
                .name(headers.get("x-auth-name"))
                .issuerId(headers.get("x-auth-sub"))
                .scope(headers.get("x-auth-scopes"))
                .build();

        return eventService.subscribe(issuer.getIssuerId(), jobId);
    }
}
