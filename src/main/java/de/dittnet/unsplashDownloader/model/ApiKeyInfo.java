package de.dittnet.unsplashDownloader.model;

public class ApiKeyInfo {
    private String id;
    private String keyPreview;
    private String fullKey;
    private String type;
    private boolean active;
    private int usageCount;
    private int dailyLimit;
    
    // Constructors
    public ApiKeyInfo() {}
    
    public ApiKeyInfo(String id, String keyPreview, String fullKey, String type) {
        this.id = id;
        this.keyPreview = keyPreview;
        this.fullKey = fullKey;
        this.type = type;
        this.active = true;
        this.usageCount = 0;
        this.dailyLimit = 500;
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
    
    public int getDailyLimit() {
        return dailyLimit;
    }
    
    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }
    
    // Helper methods
    public double getUsagePercentage() {
        if (dailyLimit == 0) return 0.0;
        return (double) usageCount / dailyLimit * 100.0;
    }
    
    public int getRemainingRequests() {
        return Math.max(0, dailyLimit - usageCount);
    }
    
    public boolean isNearLimit() {
        return getUsagePercentage() > 80.0;
    }
    
    public boolean isAtLimit() {
        return usageCount >= dailyLimit;
    }
}