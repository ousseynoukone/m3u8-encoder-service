package com.xksgroup.m3u8encoderv2.repo;

import com.xksgroup.m3u8encoderv2.model.LiveUrl;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LiveUrlRepository extends MongoRepository<LiveUrl, String> {
    Optional<LiveUrl> findByUrlId(String urlId);
    boolean existsByUrlId(String urlId);
    void deleteByUrlId(String urlId);
}


