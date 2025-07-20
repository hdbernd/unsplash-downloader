package de.dittnet.unsplashDownloader.model;

import java.time.LocalDateTime;

public class CollectionStats {
    private int totalPhotos;
    private int totalPhotographers;
    private int totalTags;
    private int totalDownloads;
    private int successfulDownloads;
    private int failedDownloads;
    private int totalApiKeys;
    private int totalApiUsage;
    private int totalApiLimit;
    private long totalStorageBytes;
    private int totalFiles;
    private LocalDateTime lastUpdated;
    private DownloadProgress currentProgress;
    
    // Constructors
    public CollectionStats() {
        this.totalPhotos = 0;
        this.totalPhotographers = 0;
        this.totalTags = 0;
        this.totalDownloads = 0;
        this.successfulDownloads = 0;
        this.failedDownloads = 0;
        this.totalApiKeys = 0;
        this.totalApiUsage = 0;
        this.totalApiLimit = 0;
        this.totalStorageBytes = 0;
        this.totalFiles = 0;
        this.lastUpdated = LocalDateTime.now();
    }
    
    // Getters and Setters
    public int getTotalPhotos() {
        return totalPhotos;
    }
    
    public void setTotalPhotos(int totalPhotos) {
        this.totalPhotos = totalPhotos;
    }
    
    public int getTotalPhotographers() {
        return totalPhotographers;
    }
    
    public void setTotalPhotographers(int totalPhotographers) {
        this.totalPhotographers = totalPhotographers;
    }
    
    public int getTotalTags() {
        return totalTags;
    }
    
    public void setTotalTags(int totalTags) {
        this.totalTags = totalTags;
    }
    
    public int getTotalDownloads() {
        return totalDownloads;
    }
    
    public void setTotalDownloads(int totalDownloads) {
        this.totalDownloads = totalDownloads;
    }
    
    public int getSuccessfulDownloads() {
        return successfulDownloads;
    }
    
    public void setSuccessfulDownloads(int successfulDownloads) {
        this.successfulDownloads = successfulDownloads;
    }
    
    public int getFailedDownloads() {
        return failedDownloads;
    }
    
    public void setFailedDownloads(int failedDownloads) {
        this.failedDownloads = failedDownloads;
    }
    
    public int getTotalApiKeys() {
        return totalApiKeys;
    }
    
    public void setTotalApiKeys(int totalApiKeys) {
        this.totalApiKeys = totalApiKeys;
    }
    
    public int getTotalApiUsage() {
        return totalApiUsage;
    }
    
    public void setTotalApiUsage(int totalApiUsage) {
        this.totalApiUsage = totalApiUsage;
    }
    
    public int getTotalApiLimit() {
        return totalApiLimit;
    }
    
    public void setTotalApiLimit(int totalApiLimit) {
        this.totalApiLimit = totalApiLimit;
    }
    
    public long getTotalStorageBytes() {
        return totalStorageBytes;
    }
    
    public void setTotalStorageBytes(long totalStorageBytes) {
        this.totalStorageBytes = totalStorageBytes;
    }
    
    public int getTotalFiles() {
        return totalFiles;
    }
    
    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public DownloadProgress getCurrentProgress() {
        return currentProgress;
    }
    
    public void setCurrentProgress(DownloadProgress currentProgress) {
        this.currentProgress = currentProgress;
    }
    
    // Helper methods
    public double getSuccessRate() {
        if (totalDownloads == 0) return 0.0;
        return (double) successfulDownloads / totalDownloads * 100.0;
    }
    
    public double getApiUsagePercentage() {
        if (totalApiLimit == 0) return 0.0;
        return (double) totalApiUsage / totalApiLimit * 100.0;
    }
    
    public boolean hasActiveDownload() {
        return currentProgress != null && currentProgress.isActive();
    }
    
    public String getFormattedStorageSize() {
        if (totalStorageBytes < 1024) return totalStorageBytes + " B";
        if (totalStorageBytes < 1024 * 1024) return String.format("%.1f KB", totalStorageBytes / 1024.0);
        if (totalStorageBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", totalStorageBytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", totalStorageBytes / (1024.0 * 1024.0 * 1024.0));
    }
}