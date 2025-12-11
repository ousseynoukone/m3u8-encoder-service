package com.xksgroup.m3u8encoderv2.service.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

@Slf4j
@Component
public class ProcessHelper {

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // Track running processes by jobId
    private final ConcurrentHashMap<String, Process> runningProcesses = new ConcurrentHashMap<>();
    
    // Track running tasks for cleanup
    private final ConcurrentHashMap<String, List<Future<?>>> runningTasks = new ConcurrentHashMap<>();

    /**
     * Execute FFmpeg command and monitor progress
     */
    public void runFFmpeg(List<String> command, String description, String jobId, 
                         ProgressCallback progressCallback) throws Exception {
        runFFmpeg(command, description, jobId, progressCallback, null);
    }
    
    /**
     * Execute FFmpeg command and monitor progress with working directory
     */
    public void runFFmpeg(List<String> command, String description, String jobId, 
                         ProgressCallback progressCallback, Path workingDirectory) throws Exception {
        
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workingDirectory != null ? workingDirectory.toFile() : null);

        // Set environment variables to limit memory usage
        pb.environment().put("MALLOC_ARENA_MAX", "2");

        log.info("Starting FFmpeg {} encoding", description);
        log.info("FFmpeg command: {}", String.join(" ", command));

        Process process = pb.start();

        // Track the process for potential cancellation
        if (jobId != null) {
            runningProcesses.put(jobId, process);
        }

        List<Future<?>> tasks = new ArrayList<>();

        // Capture stdout in a separate thread
        Future<?> stdoutTask = executorService.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    log.debug("FFmpeg stdout: {}", line);
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.warn("Error reading FFmpeg stdout: {}", e.getMessage());
                }
            }
        });
        tasks.add(stdoutTask);

        // Capture stderr and monitor progress
        Future<?> stderrTask = executorService.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                double totalDuration = 0.0;
                int lastLoggedProgress = -1;

                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    // Check if process was cancelled
                    if (isProcessCancelled(jobId)) {
                        log.info("Process cancelled for job {}, stopping progress monitoring", jobId);
                        break;
                    }
                    
                    // Log only errors and essential progress info
                    if (line.contains("Error") || line.contains("error") || line.contains("failed")) {
                        log.error("FFmpeg stderr: {}", line);
                    } else {
                        log.debug("FFmpeg stderr: {}", line);
                    }

                    // Parse progress information - support both out_time_ms and time formats
                    double currentTimeSeconds = -1;
                    
                    if (line.startsWith("out_time_ms=")) {
                        try {
                            String timeStr = line.substring(12);
                            if (!"N/A".equals(timeStr)) {
                                double currentTimeMs = Double.parseDouble(timeStr);
                                currentTimeSeconds = currentTimeMs / 1000000.0; // microseconds to seconds
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse out_time_ms from line: {}", line);
                        }
                    } else if (line.startsWith("time=")) {
                        try {
                            String timeStr = line.substring(5);
                            if (!"N/A".equals(timeStr)) {
                                // Parse time format like "00:01:23.45" or just seconds
                                currentTimeSeconds = parseTimeString(timeStr);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse time from line: {}", line);
                        }
                    }
                    
                    // Process progress if we got a valid time
                    if (currentTimeSeconds >= 0) {
                        if (totalDuration > 0) {
                            double progressPercent = Math.min(100.0, (currentTimeSeconds / totalDuration) * 100.0);
                            int progressInt = (int) Math.round(progressPercent);

                            // Log progress less frequently (every 5%)
                            if (progressInt != lastLoggedProgress && progressInt % 5 == 0) {
                                log.info("FFmpeg encoding progress: {}% ({}s / {}s)",
                                        String.format("%.1f", progressPercent),
                                        String.format("%.1f", currentTimeSeconds),
                                        String.format("%.1f", totalDuration));
                                lastLoggedProgress = progressInt;
                            }

                            // Call progress callback if provided
                            if (progressCallback != null) {
                                progressCallback.onProgress(progressInt, currentTimeSeconds, totalDuration);
                            }
                        } else {
                            log.debug("Total duration not yet available for progress calculation: currentTime={}s", 
                                    currentTimeSeconds);
                        }
                    }

                    // Parse total duration - support different formats
                    if (line.startsWith("duration=")) {
                        try {
                            String durationStr = line.substring(9);
                            if (!"N/A".equals(durationStr)) {
                                totalDuration = parseTimeString(durationStr);
                                if (totalDuration > 0) {
                                    log.info("FFmpeg detected total duration: {} seconds", totalDuration);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse duration from line: {}", line);
                        }
                    }
                    
                    // Also try to parse duration from Duration line (alternative format)
                    if (line.trim().startsWith("Duration:")) {
                        try {
                            // Format: "Duration: 00:05:00.40, start: 0.000000, bitrate: 1234 kb/s"
                            String[] parts = line.split(",");
                            if (parts.length > 0) {
                                String durationPart = parts[0].trim();
                                if (durationPart.startsWith("Duration:")) {
                                    String durationStr = durationPart.substring(9).trim();
                                    totalDuration = parseTimeString(durationStr);
                                    if (totalDuration > 0) {
                                        log.info("FFmpeg detected total duration from Duration line: {} seconds", totalDuration);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse Duration line: {}", line);
                        }
                    }
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.warn("Error reading FFmpeg stderr: {}", e.getMessage());
                }
            }
        });
        tasks.add(stderrTask);

        // Track tasks for this job
        if (jobId != null) {
            runningTasks.put(jobId, tasks);
        }

        log.info("Waiting for FFmpeg {} process to complete...", description);

        try {
            int exit = process.waitFor();

            // Wait for output threads to finish with timeout
            try {
                stdoutTask.get(10, TimeUnit.SECONDS);
                stderrTask.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Timeout waiting for output threads to finish for job: {}", jobId);
                stdoutTask.cancel(true);
                stderrTask.cancel(true);
            }

            // Check if this was a cancellation (exit code 137 typically means SIGKILL)
            if (exit == 137) {
                log.info("FFmpeg process was terminated (likely cancelled) for job: {}", jobId);
                return; // Don't throw error for cancellation
            }

            if (exit != 0) {
                log.error("FFmpeg {} failed with exit code: {}", description, exit);
                throw new RuntimeException("FFmpeg " + description + " failed, exit=" + exit);
            }
            log.info("Successfully completed FFmpeg {} encoding with exit code: {}", description, exit);

        } catch (InterruptedException e) {
            log.warn("FFmpeg process interrupted for job: {}", jobId);
            process.destroyForcibly();
            tasks.forEach(task -> task.cancel(true));
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            // Clean up process tracking
            if (jobId != null) {
                runningProcesses.remove(jobId);
                runningTasks.remove(jobId);
            }
        }
    }

    /**
     * Stop a running FFmpeg process for a specific job
     */
    public boolean stopProcess(String jobId) {
        Process process = runningProcesses.get(jobId);
        boolean stopped = false;

        if (process != null && process.isAlive()) {
            log.info("Stopping FFmpeg process for job: {}", jobId);
            process.destroyForcibly();
            runningProcesses.remove(jobId);
            stopped = true;
        }

        // Cancel associated tasks
        List<Future<?>> tasks = runningTasks.remove(jobId);
        if (tasks != null) {
            tasks.forEach(task -> task.cancel(true));
            log.debug("Cancelled {} associated tasks for job: {}", tasks.size(), jobId);
        }

        return stopped;
    }



    /**
     * Check if a process was cancelled
     */
    private boolean isProcessCancelled(String jobId) {
        if (jobId == null) {
            return false;
        }
        
        // Check if the process is no longer in our tracking map
        Process process = runningProcesses.get(jobId);
        return process == null || !process.isAlive();
    }
    
    /**
     * Parse time string in various formats (HH:MM:SS.ss or seconds)
     */
    private double parseTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return -1;
        }
        
        timeStr = timeStr.trim();
        
        try {
            // Try parsing as seconds first (e.g., "123.45")
            if (!timeStr.contains(":")) {
                return Double.parseDouble(timeStr);
            }
            
            // Parse HH:MM:SS.ss format
            String[] parts = timeStr.split(":");
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                
                return hours * 3600 + minutes * 60 + seconds;
            } else if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                double seconds = Double.parseDouble(parts[1]);
                
                return minutes * 60 + seconds;
            }
        } catch (Exception e) {
            log.warn("Failed to parse time string: {}", timeStr);
        }
        
        return -1;
    }

    /**
     * Progress callback interface
     */
    public interface ProgressCallback {
        void onProgress(int percentage, double currentTime, double totalTime);
    }
}
