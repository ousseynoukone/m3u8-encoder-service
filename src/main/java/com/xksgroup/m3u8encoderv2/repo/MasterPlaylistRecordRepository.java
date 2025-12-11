package com.xksgroup.m3u8encoderv2.repo;

import com.xksgroup.m3u8encoderv2.model.MasterPlaylistRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MasterPlaylistRecordRepository extends MongoRepository<MasterPlaylistRecord, String> {
    Optional<MasterPlaylistRecord> findByTitle(String title);
    Optional<MasterPlaylistRecord> findBySlug(String slug);
    Optional<MasterPlaylistRecord> findByJobId(String jobId);
    Page<MasterPlaylistRecord> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}


