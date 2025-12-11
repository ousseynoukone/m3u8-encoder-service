package com.xksgroup.m3u8encoderv2.repo;

import com.xksgroup.m3u8encoderv2.model.VariantSegment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VariantSegmentRepository extends MongoRepository<VariantSegment, String> {
    List<VariantSegment> findAllByMasterIdAndVariantLabelOrderByPositionAsc(String masterId, String variantLabel);

    List<VariantSegment>  findByMasterId(String masterId);

    long countByMasterId(String masterId);
    long countByMasterIdAndUploadStatus(String masterId, VariantSegment.UploadStatus status);
    long countByMasterIdAndVariantLabel(String masterId, String variantLabel);
}


