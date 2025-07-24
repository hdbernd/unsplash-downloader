package de.dittnet.unsplashDownloader.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DatabaseSyncService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSyncService.class);
    
    private final String localDbPath = "./unsplash-data/database/";
    private String networkDbPath;
    private boolean syncEnabled = false;
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    
    @Autowired
    private de.dittnet.unsplashDownloader.config.StorageConfig storageConfig;
    
    /**
     * Static method to initialize database from network BEFORE Spring context starts
     */
    public static boolean initializeDatabaseFromNetwork(String configuredPath) {
        if (!configuredPath.startsWith("/Volumes/")) {
            return false; // Not a network path
        }
        
        String networkDbPath = configuredPath.substring(0, configuredPath.lastIndexOf("/")) + "/";
        String localDbPath = "./unsplash-data/database/";
        
        Logger logger = LoggerFactory.getLogger(DatabaseSyncService.class);
        
        try {
            File networkDir = new File(networkDbPath);
            File localDir = new File(localDbPath);
            
            // Create local directory if it doesn't exist
            localDir.mkdirs();
            
            if (networkDir.exists() && networkDir.isDirectory()) {
                // Look for database files on network
                File[] networkDbFiles = networkDir.listFiles((dir, name) -> 
                    name.startsWith("unsplash_photos") && 
                    (name.endsWith(".mv.db") || name.endsWith(".trace.db"))
                );
                
                if (networkDbFiles != null && networkDbFiles.length > 0) {
                    logger.info("Found {} database files on network drive, copying to local storage for startup...", networkDbFiles.length);
                    
                    for (File networkFile : networkDbFiles) {
                        File localFile = new File(localDir, networkFile.getName());
                        try {
                            Files.copy(networkFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            logger.info("Copied {} -> {}", networkFile.getName(), localFile.getAbsolutePath());
                        } catch (IOException e) {
                            logger.warn("Failed to copy {}: {}", networkFile.getName(), e.getMessage());
                        }
                    }
                    
                    logger.info("Database initialization from network completed successfully");
                    return true;
                } else {
                    logger.info("No existing database files found on network drive - starting fresh");
                }
            } else {
                logger.warn("Network database directory does not exist: {}", networkDbPath);
                networkDir.mkdirs();
                logger.info("Created network database directory: {}", networkDbPath);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize database from network: {}", e.getMessage(), e);
        }
        
        return false;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String configuredPath = storageConfig.getDatabasePath();
        
        if (configuredPath.startsWith("/Volumes/")) {
            networkDbPath = configuredPath.substring(0, configuredPath.lastIndexOf("/")) + "/";
            syncEnabled = true;
            
            logger.info("Database sync service enabled");
            logger.info("Local database: {}", localDbPath);
            logger.info("Network database: {}", networkDbPath);
            
            // Register JVM shutdown hook for CTRL-C and other abrupt shutdowns
            registerShutdownHook();
            
            logger.info("Database sync service is active - periodic sync every minute and sync on shutdown");
            logger.info("Shutdown hook registered to handle CTRL-C interruptions");
        } else {
            logger.info("Database sync service disabled - using local storage only");
        }
    }
    
    /**
     * Register JVM shutdown hook to handle CTRL-C and other abrupt shutdowns
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (syncEnabled && !shutdownInProgress.getAndSet(true)) {
                logger.info("JVM shutdown detected (CTRL-C or SIGTERM) - performing emergency database sync...");
                try {
                    // Use a shorter timeout for emergency sync to avoid hanging
                    emergencySyncToNetwork();
                    logger.info("Emergency database sync completed successfully");
                } catch (Exception e) {
                    logger.error("Emergency database sync failed during shutdown: {}", e.getMessage(), e);
                }
            }
        }, "DatabaseSyncShutdownHook"));
    }
    
    /**
     * Emergency sync with shorter timeout for shutdown scenarios
     */
    private void emergencySyncToNetwork() {
        if (!syncEnabled) return;
        
        try {
            File localDir = new File(localDbPath);
            File networkDir = new File(networkDbPath);
            
            if (!localDir.exists()) {
                logger.debug("No local database to sync during emergency shutdown");
                return;
            }
            
            // Ensure network directory exists
            networkDir.mkdirs();
            
            // Find local database files
            File[] localDbFiles = localDir.listFiles((dir, name) -> 
                name.startsWith("unsplash_photos") && 
                (name.endsWith(".mv.db") || name.endsWith(".trace.db"))
            );
            
            if (localDbFiles != null && localDbFiles.length > 0) {
                int syncedFiles = 0;
                
                for (File localFile : localDbFiles) {
                    File networkFile = new File(networkDir, localFile.getName());
                    
                    // Emergency sync - always copy, don't check timestamps
                    try {
                        Files.copy(localFile.toPath(), networkFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        syncedFiles++;
                        logger.debug("Emergency synced: {}", localFile.getName());
                    } catch (IOException e) {
                        logger.warn("Failed to emergency sync {}: {}", localFile.getName(), e.getMessage());
                    }
                }
                
                logger.info("Emergency sync completed: {} files synced to network", syncedFiles);
            }
        } catch (Exception e) {
            logger.error("Emergency sync failed: {}", e.getMessage());
        }
    }
    
    /**
     * Initialize local database from network database at startup
     */
    public void initializeFromNetwork() {
        if (!syncEnabled) return;
        
        try {
            File networkDir = new File(networkDbPath);
            File localDir = new File(localDbPath);
            
            // Create local directory if it doesn't exist
            localDir.mkdirs();
            
            if (networkDir.exists() && networkDir.isDirectory()) {
                // Look for database files on network
                File[] networkDbFiles = networkDir.listFiles((dir, name) -> 
                    name.startsWith("unsplash_photos") && 
                    (name.endsWith(".mv.db") || name.endsWith(".trace.db"))
                );
                
                if (networkDbFiles != null && networkDbFiles.length > 0) {
                    logger.info("Found {} database files on network drive, copying to local storage...", networkDbFiles.length);
                    
                    for (File networkFile : networkDbFiles) {
                        File localFile = new File(localDir, networkFile.getName());
                        copyFileWithRetry(networkFile.toPath(), localFile.toPath());
                    }
                    
                    logger.info("Database initialization from network completed successfully");
                } else {
                    logger.info("No existing database files found on network drive - starting fresh");
                }
            } else {
                logger.warn("Network database directory does not exist: {}", networkDbPath);
                networkDir.mkdirs();
                logger.info("Created network database directory: {}", networkDbPath);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize database from network: {}", e.getMessage(), e);
            logger.warn("Continuing with local database only - sync will attempt to recover");
        }
    }
    
    /**
     * Shutdown hook to sync database before application closes
     */
    @PreDestroy
    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown() {
        if (syncEnabled) {
            logger.info("Application shutting down - performing final sync to network...");
            syncToNetworkBlocking();
        }
    }
    
    /**
     * Sync local database to network (scheduled every minute)
     */
    @Scheduled(fixedDelay = 60000) // 1 minute
    @Async
    public void syncToNetwork() {
        if (!syncEnabled) return;
        
        try {
            File localDir = new File(localDbPath);
            File networkDir = new File(networkDbPath);
            
            if (!localDir.exists()) {
                logger.debug("No local database to sync");
                return;
            }
            
            // Ensure network directory exists
            networkDir.mkdirs();
            
            // Find local database files
            File[] localDbFiles = localDir.listFiles((dir, name) -> 
                name.startsWith("unsplash_photos") && 
                (name.endsWith(".mv.db") || name.endsWith(".trace.db"))
            );
            
            if (localDbFiles != null && localDbFiles.length > 0) {
                int syncedFiles = 0;
                
                for (File localFile : localDbFiles) {
                    File networkFile = new File(networkDir, localFile.getName());
                    
                    // Only sync if local file is newer or network file doesn't exist
                    if (!networkFile.exists() || localFile.lastModified() > networkFile.lastModified()) {
                        if (copyFileWithRetry(localFile.toPath(), networkFile.toPath())) {
                            syncedFiles++;
                        }
                    }
                }
                
                if (syncedFiles > 0) {
                    logger.info("Synced {} database files to network drive", syncedFiles);
                } else {
                    logger.debug("Database sync: no files needed syncing");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to sync database to network: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Force immediate sync to network
     */
    public void forceSyncToNetwork() {
        if (!syncEnabled) {
            logger.warn("Database sync is not enabled");
            return;
        }
        
        logger.info("Force syncing database to network...");
        syncToNetworkBlocking();
    }
    
    /**
     * Blocking version of sync to network (for shutdown and manual operations)
     */
    private void syncToNetworkBlocking() {
        if (!syncEnabled) return;
        
        try {
            File localDir = new File(localDbPath);
            File networkDir = new File(networkDbPath);
            
            if (!localDir.exists()) {
                logger.debug("No local database to sync");
                return;
            }
            
            // Ensure network directory exists
            networkDir.mkdirs();
            
            // Find local database files
            File[] localDbFiles = localDir.listFiles((dir, name) -> 
                name.startsWith("unsplash_photos") && 
                (name.endsWith(".mv.db") || name.endsWith(".trace.db"))
            );
            
            if (localDbFiles != null && localDbFiles.length > 0) {
                int syncedFiles = 0;
                
                for (File localFile : localDbFiles) {
                    File networkFile = new File(networkDir, localFile.getName());
                    
                    // Always sync on shutdown or force sync
                    if (copyFileWithRetry(localFile.toPath(), networkFile.toPath())) {
                        syncedFiles++;
                    }
                }
                
                logger.info("Synced {} database files to network drive", syncedFiles);
            }
        } catch (Exception e) {
            logger.error("Failed to sync database to network: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Restore local database from network (manual recovery)
     */
    public void restoreFromNetwork() {
        if (!syncEnabled) {
            logger.warn("Database sync is not enabled");
            return;
        }
        
        logger.info("Restoring local database from network...");
        
        try {
            // Backup current local database
            backupLocalDatabase();
            
            // Initialize from network
            initializeFromNetwork();
            
            logger.info("Database restore from network completed");
        } catch (Exception e) {
            logger.error("Failed to restore database from network: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Create backup of local database
     */
    private void backupLocalDatabase() {
        try {
            File localDir = new File(localDbPath);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File backupDir = new File(localDir, "backup_" + timestamp);
            
            if (localDir.exists()) {
                backupDir.mkdirs();
                
                File[] dbFiles = localDir.listFiles((dir, name) -> 
                    name.startsWith("unsplash_photos") && 
                    (name.endsWith(".mv.db") || name.endsWith(".trace.db"))
                );
                
                if (dbFiles != null) {
                    for (File dbFile : dbFiles) {
                        File backupFile = new File(backupDir, dbFile.getName());
                        Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    logger.info("Created database backup in: {}", backupDir.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to backup local database: {}", e.getMessage());
        }
    }
    
    /**
     * Copy file with retry logic for network reliability
     */
    private boolean copyFileWithRetry(Path source, Path target) {
        int maxRetries = 3;
        int retryDelay = 1000; // 1 second
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Copied {} -> {} (attempt {})", source.getFileName(), target.getFileName(), attempt);
                return true;
            } catch (IOException e) {
                if (attempt == maxRetries) {
                    logger.error("Failed to copy {} after {} attempts: {}", source.getFileName(), maxRetries, e.getMessage());
                    return false;
                } else {
                    logger.warn("Copy attempt {} failed for {}: {} - retrying in {}ms", 
                        attempt, source.getFileName(), e.getMessage(), retryDelay);
                    
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Get sync status information
     */
    public SyncStatus getSyncStatus() {
        SyncStatus status = new SyncStatus();
        status.syncEnabled = syncEnabled;
        status.localDbPath = localDbPath;
        status.networkDbPath = networkDbPath;
        
        if (syncEnabled) {
            File localDir = new File(localDbPath);
            File networkDir = new File(networkDbPath);
            
            status.localDbExists = localDir.exists() && localDir.listFiles((dir, name) -> name.endsWith(".mv.db")) != null;
            status.networkDbExists = networkDir.exists() && networkDir.listFiles((dir, name) -> name.endsWith(".mv.db")) != null;
            
            if (status.localDbExists) {
                File[] localFiles = localDir.listFiles((dir, name) -> name.endsWith(".mv.db"));
                if (localFiles != null && localFiles.length > 0) {
                    status.localDbLastModified = LocalDateTime.ofEpochSecond(
                        localFiles[0].lastModified() / 1000, 0, java.time.ZoneOffset.systemDefault().getRules().getOffset(LocalDateTime.now())
                    );
                }
            }
            
            if (status.networkDbExists) {
                File[] networkFiles = networkDir.listFiles((dir, name) -> name.endsWith(".mv.db"));
                if (networkFiles != null && networkFiles.length > 0) {
                    status.networkDbLastModified = LocalDateTime.ofEpochSecond(
                        networkFiles[0].lastModified() / 1000, 0, java.time.ZoneOffset.systemDefault().getRules().getOffset(LocalDateTime.now())
                    );
                }
            }
        }
        
        return status;
    }
    
    /**
     * Reset all storage and logs - DANGEROUS operation that clears everything
     */
    public void resetAllStorage() throws IOException {
        if (!syncEnabled) {
            resetLocalStorageOnly();
            return;
        }
        
        logger.warn("RESET ALL STORAGE: This will delete ALL data from both local and network storage!");
        
        // Get paths
        String configuredPath = storageConfig.getDatabasePath();
        String networkBasePath = configuredPath.substring(0, configuredPath.lastIndexOf("/database")) + "/";
        
        logger.info("Resetting local storage and artifacts");
        logger.info("Resetting network storage: {}", networkBasePath);
        
        // Reset local storage (includes all artifacts)
        clearAllLocalArtifacts();
        
        // Reset network storage thoroughly
        clearAllNetworkArtifacts(networkBasePath);
        
        // Recreate directory structures
        createDirectoryStructure("./unsplash-data/");
        createDirectoryStructure(networkBasePath);
        
        logger.info("Reset complete - all storage cleared and directory structure recreated");
    }
    
    /**
     * Clear all network artifacts including hidden files and state files
     */
    private void clearAllNetworkArtifacts(String networkBasePath) throws IOException {
        File networkBase = new File(networkBasePath);
        if (!networkBase.exists()) {
            logger.info("Network storage directory does not exist: {}", networkBasePath);
            return;
        }
        
        // Get parent directory to also clean any artifacts
        File networkParent = networkBase.getParentFile();
        
        // Delete the entire .unsplash-downloader directory
        deleteDirectoryRecursively(networkBase);
        logger.info("Deleted network storage directory: {}", networkBasePath);
        
        // Look for any other artifacts in the parent directory
        if (networkParent != null && networkParent.exists()) {
            File[] artifacts = networkParent.listFiles((dir, name) -> 
                name.startsWith("unsplash") || name.startsWith("download") || 
                name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".log") ||
                name.endsWith(".properties") || name.contains("temp") || name.contains("cache")
            );
            
            if (artifacts != null) {
                for (File artifact : artifacts) {
                    try {
                        if (artifact.isDirectory()) {
                            deleteDirectoryRecursively(artifact);
                        } else {
                            artifact.delete();
                        }
                        logger.info("Deleted network artifact: {}", artifact.getAbsolutePath());
                    } catch (Exception e) {
                        logger.warn("Could not delete network artifact {}: {}", artifact.getAbsolutePath(), e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Create standard directory structure
     */
    private void createDirectoryStructure(String basePath) {
        new File(basePath + "database").mkdirs();
        new File(basePath + "photos").mkdirs();
        new File(basePath + "thumbnails").mkdirs();
        new File(basePath + "logs").mkdirs();
        new File(basePath + "config").mkdirs();
        new File(basePath + "state").mkdirs();
        logger.info("Created directory structure at: {}", basePath);
    }
    
    /**
     * Reset only local storage (for local-only configurations)
     */
    public void resetLocalStorageOnly() throws IOException {
        String localBasePath = "./unsplash-data/";
        
        logger.warn("RESET LOCAL STORAGE: This will delete ALL local data!");
        logger.info("Resetting local storage: {}", localBasePath);
        
        // Clear all potential local artifacts
        clearAllLocalArtifacts();
        
        // Recreate local directory structure
        new File(localBasePath + "database").mkdirs();
        new File(localBasePath + "photos").mkdirs();
        new File(localBasePath + "thumbnails").mkdirs();
        new File(localBasePath + "logs").mkdirs();
        new File(localBasePath + "config").mkdirs();
        new File(localBasePath + "state").mkdirs();
        
        logger.info("Local reset complete - all local storage and artifacts cleared");
    }
    
    /**
     * Clear all local artifacts including hidden files and config artifacts
     */
    private void clearAllLocalArtifacts() throws IOException {
        // Main unsplash-data directory
        File localBase = new File("./unsplash-data/");
        if (localBase.exists()) {
            deleteDirectoryRecursively(localBase);
            logger.info("Deleted local storage directory: {}", localBase.getAbsolutePath());
        }
        
        // Application logs
        File logsDir = new File("./logs");
        if (logsDir.exists()) {
            deleteDirectoryContents(logsDir);
            logger.info("Cleared application logs directory: {}", logsDir.getAbsolutePath());
        }
        
        // Local config file
        File localConfig = new File("./local-config.properties");
        if (localConfig.exists()) {
            if (localConfig.delete()) {
                logger.info("Deleted local config file: {}", localConfig.getAbsolutePath());
            }
        }
        
        // Any descriptions.txt file
        File descriptionsFile = new File("./descriptions.txt");
        if (descriptionsFile.exists()) {
            if (descriptionsFile.delete()) {
                logger.info("Deleted descriptions file: {}", descriptionsFile.getAbsolutePath());
            }
        }
        
        // Download state files
        File downloadState = new File("./download_state.json");
        if (downloadState.exists()) {
            if (downloadState.delete()) {
                logger.info("Deleted download state file: {}", downloadState.getAbsolutePath());
            }
        }
        
        // API key state files
        File apiKeyState = new File("./api_key_state.json");
        if (apiKeyState.exists()) {
            if (apiKeyState.delete()) {
                logger.info("Deleted API key state file: {}", apiKeyState.getAbsolutePath());
            }
        }
        
        // Config properties file
        File configProps = new File("./config.properties");
        if (configProps.exists()) {
            if (configProps.delete()) {
                logger.info("Deleted config properties file: {}", configProps.getAbsolutePath());
            }
        }
        
        // Any .h2.db files in root
        File currentDir = new File(".");
        File[] h2Files = currentDir.listFiles((dir, name) -> 
            name.endsWith(".mv.db") || name.endsWith(".trace.db") || name.endsWith(".lock.db")
        );
        if (h2Files != null) {
            for (File h2File : h2Files) {
                if (h2File.delete()) {
                    logger.info("Deleted H2 database file: {}", h2File.getAbsolutePath());
                }
            }
        }
        
        // Any thumbnail or temp directories
        File[] tempDirs = currentDir.listFiles((dir, name) -> 
            name.startsWith("temp_") || name.contains("thumbnail") || name.contains("cache")
        );
        if (tempDirs != null) {
            for (File tempDir : tempDirs) {
                if (tempDir.isDirectory()) {
                    deleteDirectoryRecursively(tempDir);
                    logger.info("Deleted temp directory: {}", tempDir.getAbsolutePath());
                } else if (tempDir.delete()) {
                    logger.info("Deleted temp file: {}", tempDir.getAbsolutePath());
                }
            }
        }
    }
    
    /**
     * Recursively delete a directory and all its contents
     */
    private void deleteDirectoryRecursively(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }
        
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
        }
        
        if (!directory.delete()) {
            throw new IOException("Failed to delete: " + directory.getAbsolutePath());
        }
    }
    
    /**
     * Delete contents of directory but keep the directory itself
     */
    private void deleteDirectoryContents(File directory) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteDirectoryRecursively(file);
            }
        }
    }
    
    public static class SyncStatus {
        public boolean syncEnabled;
        public String localDbPath;
        public String networkDbPath;
        public boolean localDbExists;
        public boolean networkDbExists;
        public LocalDateTime localDbLastModified;
        public LocalDateTime networkDbLastModified;
    }
}