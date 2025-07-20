package de.dittnet.unsplashDownloader.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {
    private static final Logger logger = LoggerFactory.getLogger(StorageConfig.class);
    
    @Value("${app.base-directory:./unsplash-data}")
    private String fallbackBaseDirectory;
    
    private String currentBaseDirectory;
    private boolean isUserDefined = false;
    
    public String getBaseDirectory() {
        return currentBaseDirectory != null ? currentBaseDirectory : fallbackBaseDirectory;
    }
    
    public void setUserDefinedBaseDirectory(String userDefinedPath) {
        if (userDefinedPath != null && !userDefinedPath.trim().isEmpty()) {
            this.currentBaseDirectory = Paths.get(userDefinedPath, ".unsplash-downloader").toString();
            this.isUserDefined = true;
            logger.info("Set user-defined base directory to: {}", this.currentBaseDirectory);
        }
    }
    
    public void resetToFallbackDirectory() {
        this.currentBaseDirectory = null;
        this.isUserDefined = false;
        logger.info("Reset to fallback directory: {}", fallbackBaseDirectory);
    }
    
    public boolean isUserDefined() {
        return isUserDefined;
    }
    
    public String getUserPhotosDirectory(String userOutputPath) {
        if (userOutputPath != null && !userOutputPath.trim().isEmpty()) {
            return Paths.get(userOutputPath, "photos").toString();
        }
        return getPhotosDirectory();
    }
    
    public String getPhotosDirectory() {
        return Paths.get(getBaseDirectory(), "photos").toString();
    }
    
    public String getThumbnailsDirectory() {
        return Paths.get(getBaseDirectory(), "thumbnails").toString();
    }
    
    public String getDatabaseDirectory() {
        return Paths.get(getBaseDirectory(), "database").toString();
    }
    
    public String getConfigDirectory() {
        return Paths.get(getBaseDirectory(), "config").toString();
    }
    
    public String getLogsDirectory() {
        return Paths.get(getBaseDirectory(), "logs").toString();
    }
    
    public String getStateDirectory() {
        return Paths.get(getBaseDirectory(), "state").toString();
    }
    
    public String getDatabasePath() {
        return Paths.get(getDatabaseDirectory(), "unsplash_photos").toString();
    }
    
    public String getConfigFilePath() {
        return Paths.get(getConfigDirectory(), "config.properties").toString();
    }
    
    public String getUserSettingsPath() {
        return Paths.get(getConfigDirectory(), "user_settings.json").toString();
    }
    
    public String getApiKeyStatePath() {
        return Paths.get(getStateDirectory(), "api_key_state.json").toString();
    }
    
    public String getDownloadStatePath() {
        return Paths.get(getStateDirectory(), "download_state.json").toString();
    }
    
    public void initializeDirectories() throws IOException {
        createDirectoryIfNotExists(getBaseDirectory());
        createDirectoryIfNotExists(getPhotosDirectory());
        createDirectoryIfNotExists(getThumbnailsDirectory());
        createDirectoryIfNotExists(getDatabaseDirectory());
        createDirectoryIfNotExists(getConfigDirectory());
        createDirectoryIfNotExists(getLogsDirectory());
        createDirectoryIfNotExists(getStateDirectory());
    }
    
    private void createDirectoryIfNotExists(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }
    
    public void setBaseDirectory(String baseDirectory) {
        this.currentBaseDirectory = baseDirectory;
    }
    
    public boolean isPortable() {
        // Check if all required files exist in the base directory
        return Files.exists(Paths.get(getDatabasePath() + ".mv.db")) ||
               Files.exists(Paths.get(getDatabasePath() + ".h2.db"));
    }
    
    public void copyDataToNewLocation(String newBaseDirectory) throws IOException {
        Path oldPath = Paths.get(getBaseDirectory());
        Path newPath = Paths.get(newBaseDirectory);
        
        if (Files.exists(oldPath) && !oldPath.equals(newPath)) {
            // This would involve copying all files - implement if needed
            throw new UnsupportedOperationException("Data migration not yet implemented");
        }
    }
    
    public String getSystemConfigDirectory() {
        // Always use fallback for system-wide config that persists across user directories
        return Paths.get(fallbackBaseDirectory, "config").toString();
    }
    
    public String getSystemUserSettingsPath() {
        // System-wide user settings (for bootstrap)
        return Paths.get(getSystemConfigDirectory(), "user_settings.json").toString();
    }
    
    public void initializeUserDirectory(String userOutputPath) throws IOException {
        if (userOutputPath != null && !userOutputPath.trim().isEmpty()) {
            setUserDefinedBaseDirectory(userOutputPath);
            initializeDirectories();
            
            // Also create user photos directory
            String userPhotosDir = getUserPhotosDirectory(userOutputPath);
            createDirectoryIfNotExists(userPhotosDir);
        }
    }
}