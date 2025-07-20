package de.dittnet.unsplashDownloader.model;

public class ApiKeyInfo {
    private String id;
    private String keyPreview;
    private String fullKey;
    private String type;
    private boolean active;
    private int usageCount;
    private int hourlyLimit;
    
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
}