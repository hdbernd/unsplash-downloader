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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            
            // API key stats - handle gracefully if no keys configured
            try {
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
            } catch (Exception e) {
                logger.warn("Could not load API key statistics: {}", e.getMessage());
                stats.setTotalApiKeys(0);
                stats.setTotalApiUsage(0);
                stats.setTotalApiLimit(0);
            }
            
            // Storage stats - skip for performance, can be calculated separately if needed
            // calculateStorageStats(stats);
            stats.setTotalStorageBytes(0);
            stats.setTotalFiles(0);
            
            // Set last updated
            stats.setLastUpdated(LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Failed to calculate collection stats", e);
            // Return empty stats on error
            stats = new CollectionStats();
        }
        
        return stats;
    }
    
    public Map<String, Object> calculateStorageStatsDetailed() {
        Map<String, Object> storageStats = new HashMap<>();
        
        try {
            // Calculate total storage used with limits for performance
            long totalSizeBytes = 0;
            int totalFiles = 0;
            
            // Get current user output path for accurate stats
            String userOutputPath = userSettingsService.getLastOutputPath();
            
            // Photos directory (in user-defined location) - limit to first 1000 files for performance
            String photosDir = storageConfig.getUserPhotosDirectory(userOutputPath);
            Path photosPath = Paths.get(photosDir);
            if (Files.exists(photosPath)) {
                try {
                    totalSizeBytes += Files.walk(photosPath)
                        .filter(Files::isRegularFile)
                        .limit(1000) // Limit for performance
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    totalFiles += (int) Files.walk(photosPath)
                        .filter(Files::isRegularFile)
                        .limit(1000) // Limit for performance
                        .count();
                } catch (Exception e) {
                    logger.warn("Failed to walk photos directory: {}", e.getMessage());
                }
            }
            
            // Thumbnails directory (usually smaller)
            Path thumbsPath = Paths.get(storageConfig.getThumbnailsDirectory());
            if (Files.exists(thumbsPath)) {
                try {
                    totalSizeBytes += Files.walk(thumbsPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                } catch (Exception e) {
                    logger.warn("Failed to walk thumbnails directory: {}", e.getMessage());
                }
            }
            
            // Database files (small)
            Path dbPath = Paths.get(storageConfig.getDatabaseDirectory());
            if (Files.exists(dbPath)) {
                try {
                    totalSizeBytes += Files.walk(dbPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                } catch (Exception e) {
                    logger.warn("Failed to walk database directory: {}", e.getMessage());
                }
            }
            
            // Also count descriptions.txt in user directory
            if (userOutputPath != null && !userOutputPath.isEmpty()) {
                Path descriptionsPath = Paths.get(userOutputPath, "descriptions.txt");
                if (Files.exists(descriptionsPath)) {
                    totalSizeBytes += getFileSize(descriptionsPath);
                    totalFiles += 1;
                }
            }
            
            storageStats.put("totalSizeBytes", totalSizeBytes);
            storageStats.put("totalFiles", totalFiles);
            storageStats.put("formattedSize", formatStorageSize(totalSizeBytes));
            storageStats.put("isLimited", totalFiles >= 1000); // Indicate if results are limited
            
        } catch (Exception e) {
            logger.warn("Failed to calculate storage stats", e);
            storageStats.put("totalSizeBytes", 0L);
            storageStats.put("totalFiles", 0);
            storageStats.put("formattedSize", "0 B");
            storageStats.put("isLimited", false);
        }
        
        return storageStats;
    }
    
    @Deprecated
    private void calculateStorageStats(CollectionStats stats) {
        // This method is deprecated for performance reasons
        // Use calculateStorageStatsDetailed() instead for on-demand calculation
        stats.setTotalStorageBytes(0);
        stats.setTotalFiles(0);
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