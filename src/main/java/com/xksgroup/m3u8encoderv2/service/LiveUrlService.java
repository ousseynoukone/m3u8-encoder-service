package com.xksgroup.m3u8encoderv2.service;

import com.xksgroup.m3u8encoderv2.model.LiveUrl;
import com.xksgroup.m3u8encoderv2.repo.LiveUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveUrlService {

    private final LiveUrlRepository liveUrlRepository;

    /**
     * Create a new LiveUrl entry
     */
    @Transactional
    public LiveUrl createLiveUrl(LiveUrl liveUrl) {
        // Generate urlId if not provided
        if (liveUrl.getUrlId() == null || liveUrl.getUrlId().trim().isEmpty()) {
            liveUrl.setUrlId("live-" + UUID.randomUUID().toString());
        }
        
        // Check if urlId already exists
        if (liveUrlRepository.existsByUrlId(liveUrl.getUrlId())) {
            throw new IllegalArgumentException("LiveUrl with urlId '" + liveUrl.getUrlId() + "' already exists");
        }
        
        liveUrl.setCreatedAt(Instant.now());
        liveUrl.setUpdatedAt(Instant.now());
        
        log.info("Creating LiveUrl with urlId: {}, url: {}", liveUrl.getUrlId(), liveUrl.getUrl());
        return liveUrlRepository.save(liveUrl);
    }

    /**
     * Find all LiveUrls
     */
    public List<LiveUrl> findAllLiveUrls() {
        return liveUrlRepository.findAll();
    }

    /**
     * Find LiveUrl by urlId
     */
    public LiveUrl findLiveUrlByUrlId(String urlId) {
        return liveUrlRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalArgumentException("LiveUrl not found with urlId: " + urlId));
    }

    /**
     * Delete LiveUrl by urlId
     */
    @Transactional
    public boolean deleteLiveUrlByUrlId(String urlId) {
        if (!liveUrlRepository.existsByUrlId(urlId)) {
            log.warn("Attempted to delete non-existent LiveUrl with urlId: {}", urlId);
            return false;
        }
        
        liveUrlRepository.deleteByUrlId(urlId);
        log.info("Deleted LiveUrl with urlId: {}", urlId);
        return true;
    }

    /**
     * Update LiveUrl
     */
    @Transactional
    public LiveUrl updateLiveUrl(String urlId, LiveUrl updatedLiveUrl) {
        LiveUrl existing = findLiveUrlByUrlId(urlId);
        
        if (updatedLiveUrl.getUrl() != null) {
            existing.setUrl(updatedLiveUrl.getUrl());
        }
        
        existing.setUpdatedAt(Instant.now());
        
        log.info("Updated LiveUrl with urlId: {}", urlId);
        return liveUrlRepository.save(existing);
    }
}


