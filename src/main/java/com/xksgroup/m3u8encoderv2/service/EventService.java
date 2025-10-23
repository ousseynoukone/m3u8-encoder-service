package com.xksgroup.m3u8encoderv2.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xksgroup.m3u8encoderv2.domain.Job.Job;
import com.xksgroup.m3u8encoderv2.repo.JobRepository;
import com.xksgroup.m3u8encoderv2.web.dto.ClientFriendlyJobDto;
import com.xksgroup.m3u8encoderv2.web.dto.SEEDto;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class EventService {
    private final Map<String, SEEDto> emitters = new ConcurrentHashMap<>();
    private final JobRepository jobRepository;

    public SseEmitter subscribe(String userId, String jobId) {
        SseEmitter emitter = new SseEmitter(0L); 
        emitters.put(userId, SEEDto.builder().sseEmitter(emitter).jobId(jobId).build());

        Runnable cleanup = () -> emitters.remove(userId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());

        // (Optional) initial event so the client knows it's connected
        safeSend(emitter, "connected", Map.of("ok", true));

        return emitter;
    }

    public void sendEventToUser(String userId, Object payload) {
        SEEDto seed = emitters.get(userId);
        if (seed == null) return;
        safeSend(seed.getSseEmitter(), "job-status", payload);
    }

    public void sendEventToMultipleUsers(List<String> userIds, Object payload) {
        for (String userId : userIds) sendEventToUser(userId, payload);
    }

    public void dispatchAllJobsProgressionToSEEClients(Object payload) {
        emitters.forEach((userId, seed) -> safeSend(seed.getSseEmitter(), "job-update", payload));
    }

    public void dispatchJobProgressionToSEEClients() {
        for (Map.Entry<String, SEEDto> entry : emitters.entrySet()) {
            String userId = entry.getKey();
            SEEDto seed = entry.getValue();
            SseEmitter emitter = seed.getSseEmitter();
            String jobId = seed.getJobId();

            List<Job> jobs;
            if (jobId != null && !jobId.isBlank()) {
                jobs = jobRepository.findActiveJobsById(jobId);
            } else {
                jobs = jobRepository.findActiveJobs();
            }

            List<ClientFriendlyJobDto> dtos = jobs.stream()
                    .map(ClientFriendlyJobDto::fromJob)
                    .collect(Collectors.toList());

            safeSend(emitter, "job-update", dtos);
        }
    }

    public boolean isUserConnected(String userId) {
        return emitters.containsKey(userId);
    }

    private void safeSend(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(UUID.randomUUID().toString())
                    .name(eventName)
                    .data(data)          
                    .reconnectTime(3000)
                    .build());
        } catch (IOException | IllegalStateException e) {
            // Client likely disconnected or emitter already completed
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
            // Find and remove from the map
            emitters.entrySet().removeIf(en -> en.getValue().getSseEmitter() == emitter);
        }
    }

    // Schedule with @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        emitters.values().forEach(seed -> safeSend(seed.getSseEmitter(), "heartbeat", Map.of("ts", System.currentTimeMillis())));
    }
}
