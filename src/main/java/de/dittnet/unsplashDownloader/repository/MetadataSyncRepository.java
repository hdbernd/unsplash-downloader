package de.dittnet.unsplashDownloader.repository;

import de.dittnet.unsplashDownloader.entity.MetadataSyncEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetadataSyncRepository extends JpaRepository<MetadataSyncEntity, Long> {
    
    // Find by photo ID
    Optional<MetadataSyncEntity> findByPhotoId(String photoId);
    
    // Find by sync status
    List<MetadataSyncEntity> findBySyncStatus(MetadataSyncEntity.SyncStatus status);
    
    // Find photos that need syncing (pending or failed with retry count < 3)
    @Query("SELECT m FROM MetadataSyncEntity m WHERE " +
           "m.syncStatus = 'PENDING' OR " +
           "(m.syncStatus = 'FAILED' AND m.retryCount < 3)")
    List<MetadataSyncEntity> findPhotosNeedingSync();
    
    // Find photos that need syncing with limit
    @Query("SELECT m FROM MetadataSyncEntity m WHERE " +
           "m.syncStatus = 'PENDING' OR " +
           "(m.syncStatus = 'FAILED' AND m.retryCount < 3) " +
           "ORDER BY m.createdAt ASC")
    List<MetadataSyncEntity> findPhotosNeedingSyncLimited(Pageable pageable);
    
    // Count by status
    long countBySyncStatus(MetadataSyncEntity.SyncStatus status);
    
    // Count photos needing sync
    @Query("SELECT COUNT(m) FROM MetadataSyncEntity m WHERE " +
           "m.syncStatus = 'PENDING' OR " +
           "(m.syncStatus = 'FAILED' AND m.retryCount < 3)")
    long countPhotosNeedingSync();
    
    // Find by file path
    Optional<MetadataSyncEntity> findByFilePath(String filePath);
    
    // Find all with pagination
    Page<MetadataSyncEntity> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    
    // Find by status with pagination
    Page<MetadataSyncEntity> findBySyncStatusOrderByUpdatedAtDesc(MetadataSyncEntity.SyncStatus status, Pageable pageable);
    
    // Check if photo exists and is completed
    @Query("SELECT COUNT(m) > 0 FROM MetadataSyncEntity m WHERE " +
           "m.photoId = :photoId AND m.syncStatus = 'COMPLETED'")
    boolean isPhotoSynced(@Param("photoId") String photoId);
    
    // Get sync statistics
    @Query("SELECT " +
           "COUNT(m) as total, " +
           "SUM(CASE WHEN m.syncStatus = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
           "SUM(CASE WHEN m.syncStatus = 'PENDING' THEN 1 ELSE 0 END) as pending, " +
           "SUM(CASE WHEN m.syncStatus = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
           "SUM(CASE WHEN m.syncStatus = 'ERROR' THEN 1 ELSE 0 END) as error, " +
           "SUM(CASE WHEN m.syncStatus = 'SKIPPED' THEN 1 ELSE 0 END) as skipped " +
           "FROM MetadataSyncEntity m")
    Object[] getSyncStatistics();
    
    // Find photos with high retry count (potential permanent failures)
    @Query("SELECT m FROM MetadataSyncEntity m WHERE " +
           "m.syncStatus = 'FAILED' AND m.retryCount >= 3")
    List<MetadataSyncEntity> findPermanentFailures();
    
    // Delete completed entries older than specified days (for cleanup)
    @Query("DELETE FROM MetadataSyncEntity m WHERE " +
           "m.syncStatus = 'COMPLETED' AND m.syncedAt < :cutoffDate")
    void deleteOldCompletedEntries(@Param("cutoffDate") java.time.LocalDateTime cutoffDate);
}