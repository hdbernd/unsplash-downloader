package de.dittnet.unsplashDownloader.model;

public enum DownloadStatus {
    PENDING("Pending"),
    STARTING("Starting"),
    DOWNLOADING("Downloading"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");
    
    private final String displayName;
    
    DownloadStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}