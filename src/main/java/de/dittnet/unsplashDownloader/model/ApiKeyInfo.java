package de.dittnet.unsplashDownloader.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ApiKeyInfo {
    private String id;
    private String keyPreview;
    private String fullKey;
    private String type;
    private boolean active;
    private int usageCount;
    private int hourlyLimit;
    private LocalDateTime lastUsed;
    private LocalDateTime availableAgain;
    
    // Constructors
    public ApiKeyInfo() {}
    
    public ApiKeyInfo(String id, String keyPreview, String fullKey, String type) {
        this.id = id;
        this.keyPreview = keyPreview;
        this.fullKey = fullKey;
        this.type = type;
        this.active = true;
        this.usageCount = 0;
        this.hourlyLimit = 50;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getKeyPreview() {
        return keyPreview;
    }
    
    public void setKeyPreview(String keyPreview) {
        this.keyPreview = keyPreview;
    }
    
    public String getFullKey() {
        return fullKey;
    }
    
    public void setFullKey(String fullKey) {
        this.fullKey = fullKey;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public int getUsageCount() {
        return usageCount;
    }
    
    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }
    
    public int getHourlyLimit() {
        return hourlyLimit;
    }
    
    public void setHourlyLimit(int hourlyLimit) {
        this.hourlyLimit = hourlyLimit;
    }
    
    // Backward compatibility methods
    @Deprecated
    public int getDailyLimit() {
        return hourlyLimit;
    }
    
    @Deprecated
    public void setDailyLimit(int limit) {
        this.hourlyLimit = limit;
    }
    
    public LocalDateTime getLastUsed() {
        return lastUsed;
    }
    
    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }
    
    public LocalDateTime getAvailableAgain() {
        return availableAgain;
    }
    
    public void setAvailableAgain(LocalDateTime availableAgain) {
        this.availableAgain = availableAgain;
    }
    
    // Helper methods
    public double getUsagePercentage() {
        if (hourlyLimit == 0) return 0.0;
        return (double) usageCount / hourlyLimit * 100.0;
    }
    
    public int getRemainingRequests() {
        return Math.max(0, hourlyLimit - usageCount);
    }
    
    public boolean isNearLimit() {
        return getUsagePercentage() > 80.0;
    }
    
    public boolean isAtLimit() {
        return usageCount >= hourlyLimit;
    }
    
    // Formatting helper methods
    public String getFormattedLastUsed() {
        if (lastUsed == null) {
            return "Never used";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return lastUsed.format(formatter);
    }
    
    public String getFormattedAvailableAgain() {
        if (availableAgain == null || !active) {
            return "Available now";
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(availableAgain)) {
            return "Available now";
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return "Available at " + availableAgain.format(formatter);
    }
    
    /**
     * Returns the ISO timestamp for client-side countdown timer
     */
    public String getAvailableAgainTime() {
        if (availableAgain == null) {
            return null;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(availableAgain)) {
            return null;
        }
        
        return availableAgain.toString();
    }
    
    public String getStatusBadgeClass() {
        if (!active) {
            return "bg-danger";
        } else if (isAtLimit()) {
            return "bg-warning";
        } else if (isNearLimit()) {
            return "bg-warning";
        } else {
            return "bg-success";
        }
    }
    
    public String getStatusText() {
        if (!active) {
            return "Rate Limited";
        } else if (isAtLimit()) {
            return "At Limit";
        } else if (isNearLimit()) {
            return "Near Limit";
        } else {
            return "Available";
        }
    }
}