package de.dittnet.unsplashDownloader.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSettings {
    private String lastUsername;
    private String lastOutputPath;
    private List<String> recentUsernames;
    private List<String> recentOutputPaths;
    private int defaultPageSize;
    private boolean autoSaveSettings;
    private boolean showDetailedProgress;
    private boolean confirmBeforeDownload;
    
    // Constructors
    public UserSettings() {
        this.recentUsernames = new ArrayList<>();
        this.recentOutputPaths = new ArrayList<>();
        this.defaultPageSize = 24;
        this.autoSaveSettings = true;
        this.showDetailedProgress = true;
        this.confirmBeforeDownload = false;
        
        // Set default output path (will be updated by service)
        this.lastOutputPath = "./unsplash-data/photos";
    }
    
    // Getters and Setters
    public String getLastUsername() {
        return lastUsername;
    }
    
    public void setLastUsername(String lastUsername) {
        this.lastUsername = lastUsername;
    }
    
    public String getLastOutputPath() {
        return lastOutputPath;
    }
    
    public void setLastOutputPath(String lastOutputPath) {
        this.lastOutputPath = lastOutputPath;
    }
    
    public List<String> getRecentUsernames() {
        if (recentUsernames == null) {
            recentUsernames = new ArrayList<>();
        }
        return recentUsernames;
    }
    
    public void setRecentUsernames(List<String> recentUsernames) {
        this.recentUsernames = recentUsernames;
    }
    
    public List<String> getRecentOutputPaths() {
        if (recentOutputPaths == null) {
            recentOutputPaths = new ArrayList<>();
        }
        return recentOutputPaths;
    }
    
    public void setRecentOutputPaths(List<String> recentOutputPaths) {
        this.recentOutputPaths = recentOutputPaths;
    }
    
    public int getDefaultPageSize() {
        return defaultPageSize;
    }
    
    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }
    
    public boolean isAutoSaveSettings() {
        return autoSaveSettings;
    }
    
    public void setAutoSaveSettings(boolean autoSaveSettings) {
        this.autoSaveSettings = autoSaveSettings;
    }
    
    public boolean isShowDetailedProgress() {
        return showDetailedProgress;
    }
    
    public void setShowDetailedProgress(boolean showDetailedProgress) {
        this.showDetailedProgress = showDetailedProgress;
    }
    
    public boolean isConfirmBeforeDownload() {
        return confirmBeforeDownload;
    }
    
    public void setConfirmBeforeDownload(boolean confirmBeforeDownload) {
        this.confirmBeforeDownload = confirmBeforeDownload;
    }
    
    // Helper methods
    public void addRecentUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        
        getRecentUsernames().remove(username.trim());
        getRecentUsernames().add(0, username.trim());
        
        // Keep only last 10
        if (getRecentUsernames().size() > 10) {
            setRecentUsernames(getRecentUsernames().subList(0, 10));
        }
    }
    
    public void addRecentOutputPath(String outputPath) {
        if (outputPath == null || outputPath.trim().isEmpty()) {
            return;
        }
        
        getRecentOutputPaths().remove(outputPath.trim());
        getRecentOutputPaths().add(0, outputPath.trim());
        
        // Keep only last 10
        if (getRecentOutputPaths().size() > 10) {
            setRecentOutputPaths(getRecentOutputPaths().subList(0, 10));
        }
    }
}