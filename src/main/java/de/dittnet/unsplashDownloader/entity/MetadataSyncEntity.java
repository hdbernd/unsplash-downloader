package de.dittnet.unsplashDownloader.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "metadata_sync", 
       indexes = {
           @Index(name = "idx_photo_id", columnList = "photo_id"),
           @Index(name = "idx_file_path", columnList = "file_path"),
           @Index(name = "idx_sync_status", columnList = "sync_status"),
           @Index(name = "idx_last_modified", columnList = "last_modified")
       })
public class MetadataSyncEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "photo_id", nullable = false, unique = true)
    private String photoId;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "file_hash")
    private String fileHash; // To detect if file has changed
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private SyncStatus syncStatus;
    
    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
    
    @Column(name = "last_modified")
    private LocalDateTime lastModified;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum SyncStatus {
        PENDING,        // Not yet processed
        IN_PROGRESS,    // Currently being processed
        COMPLETED,      // Successfully synced
        FAILED,         // Failed to sync (will retry)
        SKIPPED,        // Skipped (file not found, unsupported format, etc.)
        ERROR           // Permanent error (won't retry)
    }
    
    // Constructors
    public MetadataSyncEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.syncStatus = SyncStatus.PENDING;
    }
    
    public MetadataSyncEntity(String photoId, String filePath) {
        this();
        this.photoId = photoId;
        this.filePath = filePath;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPhotoId() {
        return photoId;
    }
    
    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getFileHash() {
        return fileHash;
    }
    
    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }
    
    public SyncStatus getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }
    
    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }
    
    public LocalDateTime getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Integer getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Helper methods
    public void markAsCompleted() {
        this.syncStatus = SyncStatus.COMPLETED;
        this.syncedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = null;
    }
    
    public void markAsFailed(String errorMessage) {
        this.syncStatus = SyncStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount = (this.retryCount != null) ? this.retryCount + 1 : 1;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void markAsError(String errorMessage) {
        this.syncStatus = SyncStatus.ERROR;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void markAsSkipped(String reason) {
        this.syncStatus = SyncStatus.SKIPPED;
        this.errorMessage = reason;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void markAsInProgress() {
        this.syncStatus = SyncStatus.IN_PROGRESS;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean needsSync() {
        return this.syncStatus == SyncStatus.PENDING || 
               (this.syncStatus == SyncStatus.FAILED && this.retryCount < 3);
    }
    
    @Override
    public String toString() {
        return "MetadataSyncEntity{" +
                "id=" + id +
                ", photoId='" + photoId + '\'' +
                ", filePath='" + filePath + '\'' +
                ", syncStatus=" + syncStatus +
                ", syncedAt=" + syncedAt +
                ", retryCount=" + retryCount +
                '}';
    }
}