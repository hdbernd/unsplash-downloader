package de.dittnet.unsplashDownloader.service;

import de.dittnet.unsplashDownloader.config.StorageConfig;
import de.dittnet.unsplashDownloader.model.CollectionStats;
import de.dittnet.unsplashDownloader.model.DownloadProgress;
import de.dittnet.unsplashDownloader.model.DownloadStatus;
import de.dittnet.unsplashDownloader.model.ApiKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CollectionStatsService {
    private static final Logger logger = LoggerFactory.getLogger(CollectionStatsService.class);
    
    @Autowired
    private PhotoService photoService;
    
    @Autowired
    private DownloadProgressService progressService;
    
    @Autowired
    private ApiKeyService apiKeyService;
    
    @Autowired
    private StorageConfig storageConfig;
    
    @Autowired
    private UserSettingsService userSettingsService;
    
    public CollectionStats getCollectionStats() {
        CollectionStats stats = new CollectionStats();
        
        try {
            // Photo collection stats
            stats.setTotalPhotos((int) photoService.getTotalPhotosCount());
            stats.setTotalPhotographers(photoService.getAllPhotographers().size());
            stats.setTotalTags(photoService.getAllTags().size());
            
            // Download history stats
            List<DownloadProgress> history = progressService.getProgressHistory();
            stats.setTotalDownloads(history.size());
            
            int successfulDownloads = (int) history.stream()
                .filter(p -> p.getStatus() == DownloadStatus.COMPLETED)
                .count();
            stats.setSuccessfulDownloads(successfulDownloads);
            
            int failedDownloads = (int) history.stream()
                .filter(p -> p.getStatus() == DownloadStatus.FAILED)
                .count();
            stats.setFailedDownloads(failedDownloads);
            
            // Recent activity
            DownloadProgress latestProgress = progressService.getLatestProgress();
            stats.setCurrentProgress(latestProgress);
            
            // API key stats
            List<ApiKeyInfo> apiKeys = apiKeyService.getAllApiKeys();
            stats.setTotalApiKeys(apiKeys.size());
            
            int totalApiUsage = apiKeys.stream()
                .mapToInt(ApiKeyInfo::getUsageCount)
                .sum();
            stats.setTotalApiUsage(totalApiUsage);
            
            int totalApiLimit = apiKeys.stream()
                .mapToInt(ApiKeyInfo::getDailyLimit)
                .sum();
            stats.setTotalApiLimit(totalApiLimit);
            
            // Storage stats
            calculateStorageStats(stats);
            
            // Set last updated
            stats.setLastUpdated(LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Failed to calculate collection stats", e);
            // Return empty stats on error
            stats = new CollectionStats();
        }
        
        return stats;
    }
    
    private void calculateStorageStats(CollectionStats stats) {
        try {
            // Calculate total storage used
            long totalSizeBytes = 0;
            int totalFiles = 0;
            
            // Get current user output path for accurate stats
            String userOutputPath = userSettingsService.getLastOutputPath();
            
            // Photos directory (in user-defined location)
            String photosDir = storageConfig.getUserPhotosDirectory(userOutputPath);
            Path photosPath = Paths.get(photosDir);
            if (Files.exists(photosPath)) {
                totalSizeBytes += Files.walk(photosPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(this::getFileSize)
                    .sum();
                
                totalFiles += (int) Files.walk(photosPath)
                    .filter(Files::isRegularFile)
                    .count();
            }
            
            // Thumbnails directory (in user-defined system dir)
            Path thumbsPath = Paths.get(storageConfig.getThumbnailsDirectory());
            if (Files.exists(thumbsPath)) {
                totalSizeBytes += Files.walk(thumbsPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(this::getFileSize)
                    .sum();
            }
            
            // Database files (in user-defined system dir)
            Path dbPath = Paths.get(storageConfig.getDatabaseDirectory());
            if (Files.exists(dbPath)) {
                totalSizeBytes += Files.walk(dbPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(this::getFileSize)
                    .sum();
            }
            
            // Also count descriptions.txt in user directory
            if (userOutputPath != null && !userOutputPath.isEmpty()) {
                Path descriptionsPath = Paths.get(userOutputPath, "descriptions.txt");
                if (Files.exists(descriptionsPath)) {
                    totalSizeBytes += getFileSize(descriptionsPath);
                    totalFiles += 1;
                }
            }
            
            stats.setTotalStorageBytes(totalSizeBytes);
            stats.setTotalFiles(totalFiles);
            
        } catch (Exception e) {
            logger.warn("Failed to calculate storage stats", e);
            stats.setTotalStorageBytes(0);
            stats.setTotalFiles(0);
        }
    }
    
    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return 0;
        }
    }
    
    public String formatStorageSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    public List<DownloadProgress> getRecentDownloads(int limit) {
        return progressService.getProgressHistory().stream()
            .sorted((a, b) -> {
                LocalDateTime timeA = a.getStartTime() != null ? a.getStartTime() : LocalDateTime.MIN;
                LocalDateTime timeB = b.getStartTime() != null ? b.getStartTime() : LocalDateTime.MIN;
                return timeB.compareTo(timeA);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
}