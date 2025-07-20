package de.dittnet.unsplashDownloader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dittnet.unsplashDownloader.config.StorageConfig;
import de.dittnet.unsplashDownloader.model.UserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserSettingsService {
    private static final Logger logger = LoggerFactory.getLogger(UserSettingsService.class);
    
    private final ObjectMapper objectMapper;
    private final StorageConfig storageConfig;
    private UserSettings settings;
    private String currentSettingsPath;
    
    @Autowired
    public UserSettingsService(StorageConfig storageConfig) {
        this.objectMapper = new ObjectMapper();
        this.storageConfig = storageConfig;
        
        try {
            // Initialize fallback directories first
            storageConfig.resetToFallbackDirectory();
            storageConfig.initializeDirectories();
        } catch (IOException e) {
            logger.error("Failed to initialize storage directories", e);
        }
        
        loadSettings();
    }
    
    public UserSettings getSettings() {
        if (settings == null) {
            settings = new UserSettings();
        }
        return settings;
    }
    
    public void updateLastUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        
        UserSettings currentSettings = getSettings();
        currentSettings.setLastUsername(username.trim());
        
        // Add to recent usernames (keep last 10)
        Set<String> recentSet = new LinkedHashSet<>(currentSettings.getRecentUsernames());
        recentSet.add(username.trim());
        
        List<String> recentList = new ArrayList<>(recentSet);
        if (recentList.size() > 10) {
            recentList = recentList.subList(recentList.size() - 10, recentList.size());
        }
        
        currentSettings.setRecentUsernames(recentList);
        saveSettings();
    }
    
    public void updateLastOutputPath(String outputPath) {
        if (outputPath == null || outputPath.trim().isEmpty()) {
            return;
        }
        
        UserSettings currentSettings = getSettings();
        String trimmedPath = outputPath.trim();
        
        // If this is a new output path, switch to user-defined directory
        if (!trimmedPath.equals(currentSettings.getLastOutputPath())) {
            switchToUserDefinedDirectory(trimmedPath);
        }
        
        currentSettings.setLastOutputPath(trimmedPath);
        
        // Add to recent paths (keep last 10)
        Set<String> recentSet = new LinkedHashSet<>(currentSettings.getRecentOutputPaths());
        recentSet.add(trimmedPath);
        
        List<String> recentList = new ArrayList<>(recentSet);
        if (recentList.size() > 10) {
            recentList = recentList.subList(recentList.size() - 10, recentList.size());
        }
        
        currentSettings.setRecentOutputPaths(recentList);
        saveSettings();
    }
    
    public void updateSettings(UserSettings newSettings) {
        this.settings = newSettings;
        saveSettings();
    }
    
    public void addRecentUsername(String username) {
        updateLastUsername(username);
    }
    
    public void addRecentOutputPath(String outputPath) {
        updateLastOutputPath(outputPath);
    }
    
    public List<String> getRecentUsernames() {
        return getSettings().getRecentUsernames();
    }
    
    public List<String> getRecentOutputPaths() {
        return getSettings().getRecentOutputPaths();
    }
    
    public String getLastUsername() {
        return getSettings().getLastUsername();
    }
    
    public String getLastOutputPath() {
        return getSettings().getLastOutputPath();
    }
    
    private void loadSettings() {
        // Try to load from system-wide settings first (for bootstrap)
        try {
            File systemSettingsFile = new File(storageConfig.getSystemUserSettingsPath());
            if (systemSettingsFile.exists()) {
                settings = objectMapper.readValue(systemSettingsFile, UserSettings.class);
                currentSettingsPath = systemSettingsFile.getAbsolutePath();
                logger.info("Loaded system user settings from {}", currentSettingsPath);
                
                // If we have a last output path, switch to user-defined directory
                if (settings.getLastOutputPath() != null && !settings.getLastOutputPath().isEmpty()) {
                    switchToUserDefinedDirectory(settings.getLastOutputPath());
                }
                return;
            }
        } catch (IOException e) {
            logger.warn("Failed to load system user settings: {}", e.getMessage());
        }
        
        // Fall back to current directory settings
        try {
            File settingsFile = new File(storageConfig.getUserSettingsPath());
            if (settingsFile.exists()) {
                settings = objectMapper.readValue(settingsFile, UserSettings.class);
                currentSettingsPath = settingsFile.getAbsolutePath();
                logger.info("Loaded user settings from {}", currentSettingsPath);
            } else {
                settings = new UserSettings();
                settings.setLastOutputPath(storageConfig.getPhotosDirectory());
                currentSettingsPath = settingsFile.getAbsolutePath();
                logger.info("Created new user settings");
            }
        } catch (IOException e) {
            logger.error("Failed to load user settings, using defaults", e);
            settings = new UserSettings();
            settings.setLastOutputPath(storageConfig.getPhotosDirectory());
            currentSettingsPath = storageConfig.getUserSettingsPath();
        }
    }
    
    private void saveSettings() {
        try {
            File settingsFile = new File(currentSettingsPath != null ? currentSettingsPath : storageConfig.getUserSettingsPath());
            
            // Ensure parent directory exists
            File parentDir = settingsFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile, settings);
            logger.debug("Saved user settings to {}", settingsFile.getAbsolutePath());
            
            // Also save to system-wide location for bootstrap
            try {
                File systemSettingsFile = new File(storageConfig.getSystemUserSettingsPath());
                File systemParentDir = systemSettingsFile.getParentFile();
                if (systemParentDir != null && !systemParentDir.exists()) {
                    systemParentDir.mkdirs();
                }
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(systemSettingsFile, settings);
                logger.debug("Saved system user settings to {}", systemSettingsFile.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("Failed to save system user settings: {}", e.getMessage());
            }
        } catch (IOException e) {
            logger.error("Failed to save user settings", e);
        }
    }
    
    public void clearRecentUsernames() {
        getSettings().getRecentUsernames().clear();
        saveSettings();
    }
    
    public void clearRecentOutputPaths() {
        getSettings().getRecentOutputPaths().clear();
        saveSettings();
    }
    
    public void resetSettings() {
        settings = new UserSettings();
        saveSettings();
    }
    
    public void switchToUserDefinedDirectory(String userOutputPath) {
        if (userOutputPath != null && !userOutputPath.trim().isEmpty()) {
            try {
                // Initialize user-defined directory structure
                storageConfig.initializeUserDirectory(userOutputPath);
                
                // Update current settings path to user-defined location
                currentSettingsPath = storageConfig.getUserSettingsPath();
                
                // Save settings to new location
                saveSettings();
                
                logger.info("Switched to user-defined directory: {}", userOutputPath);
            } catch (IOException e) {
                logger.error("Failed to switch to user-defined directory: {}", userOutputPath, e);
            }
        }
    }
}