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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
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
    
    // Cache for storage stats to avoid expensive recalculations
    private final AtomicLong cachedStorageSize = new AtomicLong(0);
    private final AtomicInteger cachedFileCount = new AtomicInteger(0);
    private volatile LocalDateTime lastStorageCalculation = LocalDateTime.MIN;
    
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
            
            // Storage stats - skip expensive calculation, use cached value or placeholder
            // This is now done on-demand via separate endpoint to avoid slowing down home page
            stats.setTotalStorageBytes(getCachedStorageSize());
            stats.setTotalFiles(getCachedFileCount());
            
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
            // Separate breakdown for different storage types
            long photosSize = 0;
            int photosCount = 0;
            long thumbnailsSize = 0;
            int thumbnailsCount = 0;
            long databaseSize = 0;
            int databaseCount = 0;
            long otherSize = 0;
            int otherCount = 0;
            
            // Get current user output path for accurate stats
            String userOutputPath = userSettingsService.getLastOutputPath();
            
            // Photos directory (in user-defined location)
            String photosDir = storageConfig.getUserPhotosDirectory(userOutputPath);
            Path photosPath = Paths.get(photosDir);
            if (Files.exists(photosPath)) {
                try {
                    photosSize = Files.walk(photosPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    photosCount = (int) Files.walk(photosPath)
                        .filter(Files::isRegularFile)
                        .count();
                } catch (Exception e) {
                    logger.warn("Failed to walk photos directory: {}", e.getMessage());
                }
            }
            
            // Thumbnails directory (check both network and local)
            // First check network location
            Path thumbsPath = Paths.get(storageConfig.getThumbnailsDirectory());
            if (Files.exists(thumbsPath)) {
                try {
                    thumbnailsSize = Files.walk(thumbsPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    thumbnailsCount = (int) Files.walk(thumbsPath)
                        .filter(Files::isRegularFile)
                        .count();
                } catch (Exception e) {
                    logger.warn("Failed to walk network thumbnails directory: {}", e.getMessage());
                }
            }
            
            // Also check local thumbnails (generated for UI performance)
            Path localThumbsPath = Paths.get("./unsplash-data/thumbnails");
            if (Files.exists(localThumbsPath)) {
                try {
                    long localThumbsSize = Files.walk(localThumbsPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    int localThumbsCount = (int) Files.walk(localThumbsPath)
                        .filter(Files::isRegularFile)
                        .count();
                    
                    thumbnailsSize += localThumbsSize;
                    thumbnailsCount += localThumbsCount;
                } catch (Exception e) {
                    logger.warn("Failed to walk local thumbnails directory: {}", e.getMessage());
                }
            }
            
            // Database files (check both network and local)
            // First check network location
            Path dbPath = Paths.get(storageConfig.getDatabaseDirectory());
            if (Files.exists(dbPath)) {
                try {
                    databaseSize = Files.walk(dbPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    databaseCount = (int) Files.walk(dbPath)
                        .filter(Files::isRegularFile)
                        .count();
                } catch (Exception e) {
                    logger.warn("Failed to walk network database directory: {}", e.getMessage());
                }
            }
            
            // Also check local database (working copy)
            Path localDbPath = Paths.get("./unsplash-data/database");
            if (Files.exists(localDbPath)) {
                try {
                    long localDbSize = Files.walk(localDbPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    int localDbCount = (int) Files.walk(localDbPath)
                        .filter(Files::isRegularFile)
                        .count();
                    
                    databaseSize += localDbSize;
                    databaseCount += localDbCount;
                } catch (Exception e) {
                    logger.warn("Failed to walk local database directory: {}", e.getMessage());
                }
            }
            
            // Other files (descriptions.txt, logs, config, etc.)
            if (userOutputPath != null && !userOutputPath.isEmpty()) {
                Path descriptionsPath = Paths.get(userOutputPath, "descriptions.txt");
                if (Files.exists(descriptionsPath)) {
                    otherSize += getFileSize(descriptionsPath);
                    otherCount += 1;
                }
            }
            
            // Logs directory (check both network and local)
            Path logsPath = Paths.get(storageConfig.getLogsDirectory());
            if (Files.exists(logsPath)) {
                try {
                    otherSize += Files.walk(logsPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    otherCount += (int) Files.walk(logsPath)
                        .filter(Files::isRegularFile)
                        .count();
                } catch (Exception e) {
                    logger.warn("Failed to walk network logs directory: {}", e.getMessage());
                }
            }
            
            // Local logs
            Path localLogsPath = Paths.get("./unsplash-data/logs");
            if (Files.exists(localLogsPath)) {
                try {
                    otherSize += Files.walk(localLogsPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    otherCount += (int) Files.walk(localLogsPath)
                        .filter(Files::isRegularFile)
                        .count();
                } catch (Exception e) {
                    logger.warn("Failed to walk local logs directory: {}", e.getMessage());
                }
            }
            
            // Local config files
            Path localConfigPath = Paths.get("./unsplash-data/config");
            if (Files.exists(localConfigPath)) {
                try {
                    otherSize += Files.walk(localConfigPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    otherCount += (int) Files.walk(localConfigPath)
                        .filter(Files::isRegularFile)
                        .count();
                } catch (Exception e) {
                    logger.warn("Failed to walk local config directory: {}", e.getMessage());
                }
            }
            
            // Local state files
            Path localStatePath = Paths.get("./unsplash-data/state");
            if (Files.exists(localStatePath)) {
                try {
                    otherSize += Files.walk(localStatePath)
                        .filter(Files::isRegularFile)
                        .mapToLong(this::getFileSize)
                        .sum();
                    
                    otherCount += (int) Files.walk(localStatePath)
                        .filter(Files::isRegularFile)
                        .count();
                } catch (Exception e) {
                    logger.warn("Failed to walk local state directory: {}", e.getMessage());
                }
            }
            
            // Project-level config files
            Path projectConfigFile = Paths.get("./local-config.properties");
            if (Files.exists(projectConfigFile)) {
                otherSize += getFileSize(projectConfigFile);
                otherCount += 1;
            }
            
            long totalSizeBytes = photosSize + thumbnailsSize + databaseSize + otherSize;
            int totalFiles = photosCount + thumbnailsCount + databaseCount + otherCount;
            
            // Total stats
            storageStats.put("totalSizeBytes", totalSizeBytes);
            storageStats.put("totalFiles", totalFiles);
            storageStats.put("formattedSize", formatStorageSize(totalSizeBytes));
            
            // Breakdown by category
            Map<String, Object> breakdown = new HashMap<>();
            breakdown.put("photos", Map.of(
                "sizeBytes", photosSize,
                "formattedSize", formatStorageSize(photosSize),
                "files", photosCount,
                "percentage", totalSizeBytes > 0 ? (photosSize * 100.0 / totalSizeBytes) : 0.0
            ));
            breakdown.put("thumbnails", Map.of(
                "sizeBytes", thumbnailsSize,
                "formattedSize", formatStorageSize(thumbnailsSize),
                "files", thumbnailsCount,
                "percentage", totalSizeBytes > 0 ? (thumbnailsSize * 100.0 / totalSizeBytes) : 0.0
            ));
            breakdown.put("database", Map.of(
                "sizeBytes", databaseSize,
                "formattedSize", formatStorageSize(databaseSize),
                "files", databaseCount,
                "percentage", totalSizeBytes > 0 ? (databaseSize * 100.0 / totalSizeBytes) : 0.0
            ));
            breakdown.put("other", Map.of(
                "sizeBytes", otherSize,
                "formattedSize", formatStorageSize(otherSize),
                "files", otherCount,
                "percentage", totalSizeBytes > 0 ? (otherSize * 100.0 / totalSizeBytes) : 0.0
            ));
            
            storageStats.put("breakdown", breakdown);
            
        } catch (Exception e) {
            logger.warn("Failed to calculate storage stats", e);
            storageStats.put("totalSizeBytes", 0L);
            storageStats.put("totalFiles", 0);
            storageStats.put("formattedSize", "0 B");
            storageStats.put("breakdown", new HashMap<>());
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
    
    /**
     * Get cached storage size or calculate if cache is stale (older than 5 minutes)
     */
    private long getCachedStorageSize() {
        LocalDateTime now = LocalDateTime.now();
        
        // Recalculate if cache is older than 5 minutes or never calculated
        if (lastStorageCalculation.plusMinutes(5).isBefore(now)) {
            try {
                logger.info("Calculating storage stats (cache expired or first run)...");
                Map<String, Object> storageStats = calculateStorageStatsDetailed();
                cachedStorageSize.set((Long) storageStats.get("totalSizeBytes"));
                cachedFileCount.set((Integer) storageStats.get("totalFiles"));
                lastStorageCalculation = now;
                logger.info("Updated storage cache: {} bytes ({}), {} files", 
                    cachedStorageSize.get(), formatStorageSize(cachedStorageSize.get()), cachedFileCount.get());
            } catch (Exception e) {
                logger.warn("Failed to update storage cache: {}", e.getMessage());
                // If calculation fails, try to return something meaningful based on photo count
                if (cachedStorageSize.get() == 0) {
                    long photoCount = photoService.getTotalPhotosCount();
                    cachedStorageSize.set(photoCount * 2_000_000L); // Estimate 2MB per photo
                    cachedFileCount.set((int) photoCount);
                    logger.info("Using estimated storage: {} photos * 2MB = {} bytes", 
                        photoCount, cachedStorageSize.get());
                }
            }
        }
        
        return cachedStorageSize.get();
    }
    
    /**
     * Get cached file count
     */
    private int getCachedFileCount() {
        // Trigger cache update if needed (getCachedStorageSize will update both values)
        getCachedStorageSize();
        return cachedFileCount.get();
    }
    
    /**
     * Force refresh of storage cache - call this after downloads complete
     */
    public void refreshStorageCache() {
        lastStorageCalculation = LocalDateTime.MIN; // Force recalculation
        getCachedStorageSize(); // Trigger immediate recalculation
    }
}