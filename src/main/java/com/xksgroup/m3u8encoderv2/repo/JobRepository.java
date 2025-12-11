package com.xksgroup.m3u8encoderv2.repo;

import com.xksgroup.m3u8encoderv2.model.Job.Job;
import com.xksgroup.m3u8encoderv2.model.Job.JobStatus;
import com.xksgroup.m3u8encoderv2.model.ResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends MongoRepository<Job, String> {
    
    Optional<Job> findByJobId(String jobId);
    Optional<Job> findBySlug(String slug);
    List<Job> findAllBySlug(String slug);
    
    Page<Job> findByStatus(JobStatus status, Pageable pageable);
    Page<Job> findByResourceType(ResourceType resourceType, Pageable pageable);
    Page<Job> findByStatusAndResourceType(JobStatus status, ResourceType resourceType, Pageable pageable);
    Page<Job> findByResourceTypeAndStatus(ResourceType resourceType, JobStatus status, Pageable pageable);
    Page<Job> findByResourceTypeAndStatusNot(ResourceType resourceType, JobStatus status, Pageable pageable);
    Page<Job> findByStatusNot(JobStatus status, Pageable pageable);
    List<Job> findByResourceType(ResourceType resourceType);
    List<Job> findByResourceTypeOrderByCreatedAtDesc(ResourceType resourceType, Pageable pageable);
    List<Job> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    @Query("{'status': {$in: ['DOWNLOADING', 'PENDING', 'UPLOADING', 'ENCODING', 'UPLOADING_TO_CLOUD_STORAGE']}}")
    List<Job> findActiveJobs();

    @Query("{ 'status': { $in: ['DOWNLOADING', 'PENDING','UPLOADING','ENCODING','UPLOADING_TO_CLOUD_STORAGE'] }, 'jobId': ?0 }")
    List<Job> findActiveJobsById(String jobId);


    @Query("{'status': {$in: ?0}}")
    List<Job> findByStatusIn(List<JobStatus> statuses);
    
    @Query("{'createdAt': {$gte: ?0}}")
    List<Job> findJobsCreatedAfter(LocalDateTime date);
    
    @Query("{'title': {$regex: ?0, $options: 'i'}}")
    Page<Job> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    
    @Query("{'status': ?0, 'createdAt': {$gte: ?1}}")
    List<Job> findByStatusAndCreatedAfter(JobStatus status, LocalDateTime date);
    
    long countByStatus(JobStatus status);
    
    @Query("{'status': 'COMPLETED', 'completedAt': {$gte: ?0}}")
    long countCompletedJobsAfter(LocalDateTime date);
}
