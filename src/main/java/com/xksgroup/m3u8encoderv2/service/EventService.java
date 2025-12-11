package com.xksgroup.m3u8encoderv2.service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xksgroup.m3u8encoderv2.model.Job.Job;
import com.xksgroup.m3u8encoderv2.repo.JobRepository;
import com.xksgroup.m3u8encoderv2.model.dto.ClientFriendlyJobDto;
import com.xksgroup.m3u8encoderv2.model.dto.SEEDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private final Map<String, SEEDto> emitters = new ConcurrentHashMap<>();
    private final JobRepository jobRepository;

    // Throttling fields
    private static final long DISPATCH_COOLDOWN_MS = 2000; // 2 seconds
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean cooldownActive = new AtomicBoolean(false);
    private final AtomicBoolean pendingUpdate = new AtomicBoolean(false);

    /**
     * Client subscribes to SSE stream.
     */
    public SseEmitter subscribe(String userId, String jobId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(userId, SEEDto.builder().sseEmitter(emitter).jobId(jobId).build());

        Runnable cleanup = () -> emitters.remove(userId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());

        // Initial connection event
        safeSend(emitter, "connected", Map.of("ok", true));
        return emitter;
    }

    /**
     * Send a single event to a specific user.
     */
    public void sendEventToUser(String userId, Object payload) {
        SEEDto seed = emitters.get(userId);
        if (seed == null) return;
        safeSend(seed.getSseEmitter(), "job-status", payload);
    }

    /**
     * Send an event to multiple users.
     */
    public void sendEventToMultipleUsers(List<String> userIds, Object payload) {
        for (String userId : userIds) sendEventToUser(userId, payload);
    }

    /**
     * Dispatch a payload to all connected SSE clients.
     */
    public void dispatchAllJobsProgressionToSEEClients(Object payload) {
        emitters.forEach((userId, seed) ->
                safeSend(seed.getSseEmitter(), "job-update", payload));
    }

    /**
     * Smart-throttled dispatch:
     * - Sends immediately if cooldown not active.
     * - Otherwise, marks pending for next round.
     * - When cooldown expires, sends the latest update once.
     */
    public void dispatchJobProgressionToSEEClients() {
        if (cooldownActive.compareAndSet(false, true)) {
            // Run immediately
            doDispatch();

            // Schedule cooldown end
            scheduler.schedule(() -> {
                cooldownActive.set(false);

                if (pendingUpdate.getAndSet(false)) {
                    // A new call arrived during cooldown → send again immediately
                    dispatchJobProgressionToSEEClients();
                }
            }, DISPATCH_COOLDOWN_MS, TimeUnit.MILLISECONDS);
        } else {
            // Cooldown is active → record pending update
            pendingUpdate.set(true);
        }
    }

    /**
     * Actually performs the SSE broadcast with the latest job data.
     */
    private void doDispatch() {
        for (Map.Entry<String, SEEDto> entry : emitters.entrySet()) {
            SEEDto seed = entry.getValue();
            SseEmitter emitter = seed.getSseEmitter();
            String jobId = seed.getJobId();

            List<Job> jobs = (jobId != null && !jobId.isBlank())
                    ? jobRepository.findActiveJobsById(jobId)
                    : jobRepository.findActiveJobs();

            List<ClientFriendlyJobDto> dtos = jobs.stream()
                    .map(ClientFriendlyJobDto::fromJob)
                    .collect(Collectors.toList());

            safeSend(emitter, "job-update", dtos);
        }
    }

    /**
     * Notify clients about a job failure with dedicated event.
     * Ensures clients watching this job receive the failure notification.
     */
    public void notifyJobFailure(String jobId, Job failedJob) {
        ClientFriendlyJobDto dto = ClientFriendlyJobDto.fromJob(failedJob);
        
        // Send to all clients watching this specific job or all jobs
        emitters.forEach((userId, seed) -> {
            if (seed.getJobId() == null || seed.getJobId().isBlank() || jobId.equals(seed.getJobId())) {
                safeSend(seed.getSseEmitter(), "job-failed", dto);
            }
        });
    }

    /**
     * Notify clients about a job completion with dedicated event.
     * Ensures clients watching this job receive the completion notification.
     */
    public void notifyJobCompletion(String jobId, Job completedJob) {
        ClientFriendlyJobDto dto = ClientFriendlyJobDto.fromJob(completedJob);
        
        // Send to all clients watching this specific job or all jobs
        emitters.forEach((userId, seed) -> {
            if (seed.getJobId() == null || seed.getJobId().isBlank() || jobId.equals(seed.getJobId())) {
                safeSend(seed.getSseEmitter(), "job-completed", dto);
            }
        });
    }

    /**
     * Notify clients about a job cancellation with dedicated event.
     * Ensures clients watching this job receive the cancellation notification.
     */
    public void notifyJobCancellation(String jobId, Job cancelledJob) {
        ClientFriendlyJobDto dto = ClientFriendlyJobDto.fromJob(cancelledJob);
        
        // Send to all clients watching this specific job or all jobs
        emitters.forEach((userId, seed) -> {
            if (seed.getJobId() == null || seed.getJobId().isBlank() || jobId.equals(seed.getJobId())) {
                safeSend(seed.getSseEmitter(), "job-cancelled", dto);
            }
        });
    }

    /**
     * Check if a user is connected to SSE.
     */
    public boolean isUserConnected(String userId) {
        return emitters.containsKey(userId);
    }

    /**
     * Safely sends an SSE event, removing the emitter on failure.
     */
    private void safeSend(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(UUID.randomUUID().toString())
                    .name(eventName)
                    .data(data)
                    .reconnectTime(3000)
                    .build());
        } catch (IOException | IllegalStateException e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
            emitters.entrySet().removeIf(en -> en.getValue().getSseEmitter() == emitter);
        }
    }

    /**
     * Send periodic heartbeat events to keep SSE connections alive.
     */
    // @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        emitters.values().forEach(seed ->
                safeSend(seed.getSseEmitter(), "heartbeat",
                        Map.of("ts", System.currentTimeMillis())));
    }

    /**
     * Clean shutdown for scheduler.
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
