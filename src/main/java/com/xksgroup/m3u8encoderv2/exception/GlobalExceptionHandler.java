package com.xksgroup.m3u8encoderv2.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
            ex.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument provided: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid request parameter",
            ex.getMessage()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        log.warn("File upload size exceeded: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "File size exceeds maximum limit",
            "Please upload a smaller file (max 6GB)"
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime error occurred", ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "A processing error occurred",
            ex.getMessage()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.error("Runtime error occurred", ex);
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "A processing error occurred",
                ex.getMessage()
        );
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Object> handleSecurityException(SecurityException ex) {
        log.warn("Security violation: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.FORBIDDEN,
            "Access denied",
            "You don't have permission to access this resource"
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Bad request - unreadable/missing body: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid or missing request body",
            ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationErrors(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation error", msg);
    }

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message, String details) {
        Map<String, Object> errorResponse = Map.of(
            "timestamp", LocalDateTime.now(),
            "status", status.value(),
            "error", status.getReasonPhrase(),
            "message", message,
            "details", details != null ? details : "No additional details available"
        );
        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler({
        org.springframework.web.context.request.async.AsyncRequestNotUsableException.class,
        java.io.IOException.class,
        org.apache.catalina.connector.ClientAbortException.class
    })
    public ResponseEntity<Map<String, Object>> handleSseDisconnect(Exception ex) {
        Map<String, Object> body = Map.of(
            "timestamp", java.time.LocalDateTime.now().toString(),
            "status", 499, // client closed request (used by Nginx, more accurate than 500)
            "error", "Client Closed Request",
            "message", "SSE client disconnected or connection was lost",
            "exception", ex.getClass().getSimpleName()
        );

        // Log at INFO level since this is expected behavior
        log.info("SSE client disconnected: {}", ex.getMessage());

        // Return JSON body with explicit content-type (forces JSON serialization)
        return ResponseEntity
                .status(499)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body);
    }

}
