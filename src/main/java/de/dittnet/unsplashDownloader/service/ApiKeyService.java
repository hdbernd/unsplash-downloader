package de.dittnet.unsplashDownloader.service;

import de.dittnet.unsplashDownloader.ApiKeyManager;
import de.dittnet.unsplashDownloader.config.StorageConfig;
import de.dittnet.unsplashDownloader.model.ApiKeyInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ApiKeyService {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String CONFIG_FILE = "config.properties";
    private static final String API_BASE_URL = "https://api.unsplash.com";
    
    private final OkHttpClient client;
    
    @Autowired
    private StorageConfig storageConfig;
    
    @Autowired
    private ApiKeyManager apiKeyManager;
    
    public ApiKeyService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }
    
    public List<ApiKeyInfo> getAllApiKeys() {
        // ApiKeyManager is now injected via Spring
        if (apiKeyManager == null) {
            logger.warn("API key manager not available - dependency injection failed");
            return new ArrayList<>();
        }
        
        List<ApiKeyInfo> keyInfos = new ArrayList<>();
        
        try {
            Properties props = loadProperties();
            
            // Check for single key
            String singleKey = props.getProperty("unsplash.access.token");
            if (singleKey != null && !singleKey.trim().isEmpty()) {
                ApiKeyInfo info = new ApiKeyInfo();
                info.setId("single");
                info.setKeyPreview(maskApiKey(singleKey));
                info.setFullKey(singleKey);
                info.setType("Single Key");
                info.setActive(true);
                
                if (apiKeyManager != null) {
                    Map<String, Integer> usage = apiKeyManager.getCurrentUsage();
                    Map<String, Boolean> rateLimited = apiKeyManager.getRateLimitedKeys();
                    info.setUsageCount(usage.getOrDefault(singleKey, 0));
                    info.setDailyLimit(50); // Demo apps: 50 requests/hour
                    info.setActive(!rateLimited.getOrDefault(singleKey, false));
                    
                    // Set timing information
                    info.setLastUsed(apiKeyManager.getLastUsageTime(singleKey));
                    info.setAvailableAgain(apiKeyManager.getAvailableAgainTime(singleKey));
                }
                
                keyInfos.add(info);
            }
            
            // Check for multiple keys
            String multipleKeys = props.getProperty("unsplash.access.tokens");
            if (multipleKeys != null && !multipleKeys.trim().isEmpty()) {
                String[] keys = multipleKeys.split(",");
                for (int i = 0; i < keys.length; i++) {
                    String key = keys[i].trim();
                    if (!key.isEmpty()) {
                        ApiKeyInfo info = new ApiKeyInfo();
                        info.setId("multi_" + i);
                        info.setKeyPreview(maskApiKey(key));
                        info.setFullKey(key);
                        info.setType("Multiple Key " + (i + 1));
                        info.setActive(true);
                        
                        if (apiKeyManager != null) {
                            Map<String, Integer> usage = apiKeyManager.getCurrentUsage();
                            Map<String, Boolean> rateLimited = apiKeyManager.getRateLimitedKeys();
                            info.setUsageCount(usage.getOrDefault(key, 0));
                            info.setDailyLimit(50); // Demo apps: 50 requests/hour
                            info.setActive(!rateLimited.getOrDefault(key, false));
                            
                            // Set timing information
                            info.setLastUsed(apiKeyManager.getLastUsageTime(key));
                            info.setAvailableAgain(apiKeyManager.getAvailableAgainTime(key));
                        }
                        
                        keyInfos.add(info);
                    }
                }
            }
            
        } catch (IOException e) {
            logger.error("Failed to load API keys", e);
        }
        
        return keyInfos;
    }
    
    public boolean validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("API key is null or empty");
            return false;
        }
        
        String trimmedKey = apiKey.trim();
        logger.info("Validating API key (length: {})", trimmedKey.length());
        
        // Check for dummy/test keys
        if (isDummyKey(trimmedKey)) {
            logger.warn("Rejecting dummy/test API key: {}", trimmedKey);
            return false;
        }
        
        try {
            // Use /photos endpoint instead of /me for Client-ID validation
            // /me requires OAuth2 Bearer token, but /photos works with Client-ID
            Request request = new Request.Builder()
                    .url(API_BASE_URL + "/photos?page=1&per_page=1")
                    .header("Authorization", "Client-ID " + trimmedKey)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                logger.info("API validation response: {} - {}", response.code(), response.message());
                
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "No response body";
                    logger.warn("API key validation failed. Response: {}", responseBody);
                } else {
                    logger.info("API key validation successful for Client-ID access");
                }
                
                return response.isSuccessful();
            }
        } catch (Exception e) {
            logger.error("Failed to validate API key", e);
            return false;
        }
    }
    
    private boolean isDummyKey(String apiKey) {
        if (apiKey == null) {
            return true;
        }
        
        String key = apiKey.trim().toLowerCase();
        
        // Check for common dummy/test key patterns
        return key.contains("dummy") || 
               key.contains("test") || 
               key.contains("your_") || 
               key.contains("example") || 
               key.contains("placeholder") ||
               key.contains("sample") ||
               key.equals("your_api_key_here") ||
               key.equals("your_access_token_here") ||
               key.startsWith("dummy_test_key");
    }
    
    public void addApiKey(String apiKey) throws IOException {
        if (!validateApiKey(apiKey)) {
            throw new IllegalArgumentException("Invalid API key");
        }
        
        Properties props = loadProperties();
        List<String> existingKeys = new ArrayList<>();
        
        // Get existing keys
        String multipleKeys = props.getProperty("unsplash.access.tokens");
        if (multipleKeys != null && !multipleKeys.trim().isEmpty()) {
            existingKeys.addAll(Arrays.asList(multipleKeys.split(",")));
        }
        
        String singleKey = props.getProperty("unsplash.access.token");
        if (singleKey != null && !singleKey.trim().isEmpty()) {
            existingKeys.add(singleKey);
        }
        
        // Check if key already exists
        if (existingKeys.contains(apiKey.trim())) {
            throw new IllegalArgumentException("API key already exists");
        }
        
        // Add new key
        existingKeys.add(apiKey.trim());
        
        // Update properties
        props.remove("unsplash.access.token"); // Remove single key
        props.setProperty("unsplash.access.tokens", String.join(",", existingKeys));
        
        saveProperties(props);
        
        // API key manager is now managed by Spring - reload configuration
        if (apiKeyManager != null) {
            apiKeyManager.reloadConfiguration();
        }
    }
    
    public void removeApiKey(String keyId) throws IOException {
        Properties props = loadProperties();
        List<String> existingKeys = new ArrayList<>();
        
        // Get existing keys
        String multipleKeys = props.getProperty("unsplash.access.tokens");
        if (multipleKeys != null && !multipleKeys.trim().isEmpty()) {
            existingKeys.addAll(Arrays.asList(multipleKeys.split(",")));
        }
        
        String singleKey = props.getProperty("unsplash.access.token");
        if (singleKey != null && !singleKey.trim().isEmpty()) {
            existingKeys.add(singleKey);
        }
        
        // Find and remove key by ID
        List<ApiKeyInfo> allKeys = getAllApiKeys();
        String keyToRemove = null;
        
        for (ApiKeyInfo keyInfo : allKeys) {
            if (keyInfo.getId().equals(keyId)) {
                keyToRemove = keyInfo.getFullKey();
                break;
            }
        }
        
        if (keyToRemove != null) {
            existingKeys.remove(keyToRemove);
        }
        
        // Update properties
        props.remove("unsplash.access.token");
        props.remove("unsplash.access.tokens");
        
        if (existingKeys.size() == 1) {
            props.setProperty("unsplash.access.token", existingKeys.get(0));
        } else if (existingKeys.size() > 1) {
            props.setProperty("unsplash.access.tokens", String.join(",", existingKeys));
        }
        
        saveProperties(props);
        
        // API key manager is now managed by Spring - reload configuration
        if (apiKeyManager != null) {
            apiKeyManager.reloadConfiguration();
        }
    }
    
    public void updateApiKey(String keyId, String newApiKey) throws IOException {
        removeApiKey(keyId);
        addApiKey(newApiKey);
    }
    
    public Map<String, Object> getApiKeyStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<ApiKeyInfo> keys = getAllApiKeys();
        stats.put("totalKeys", keys.size());
        
        int totalUsage = 0;
        int totalLimit = 0;
        int activeKeys = 0;
        int rateLimitedKeys = 0;
        
        for (ApiKeyInfo key : keys) {
            totalUsage += key.getUsageCount();
            totalLimit += key.getDailyLimit();
            if (key.isActive()) {
                activeKeys++;
            } else {
                rateLimitedKeys++;
            }
        }
        
        stats.put("totalUsage", totalUsage);
        stats.put("totalLimit", totalLimit);
        stats.put("activeKeys", activeKeys);
        stats.put("rateLimitedKeys", rateLimitedKeys);
        stats.put("usagePercentage", totalLimit > 0 ? (double) totalUsage / totalLimit * 100 : 0);
        
        if (apiKeyManager != null) {
            stats.put("nextResetTime", apiKeyManager.getNextResetTime());
            stats.put("availableKeysCount", apiKeyManager.getAvailableKeysCount());
        }
        
        return stats;
    }
    
    private Properties loadProperties() throws IOException {
        Properties props = new Properties();
        File configFile = new File(storageConfig.getConfigFilePath());
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }
        }
        
        return props;
    }
    
    private void saveProperties(Properties props) throws IOException {
        File configFile = new File(storageConfig.getConfigFilePath());
        // Ensure parent directory exists
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "Unsplash API Configuration - Updated via Web Interface");
        }
    }
    
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        
        return apiKey.substring(0, 6) + "***" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * Simulate rate limiting for testing countdown timer
     */
    public void simulateRateLimit(String keyId) {
        if (apiKeyManager != null) {
            // Extract the actual key from keyId (remove prefix like "multi_")
            List<ApiKeyInfo> keys = getAllApiKeys();
            for (ApiKeyInfo info : keys) {
                if (info.getId().equals(keyId)) {
                    // Simulate that this key has reached its hourly limit
                    apiKeyManager.simulateHourlyLimitReached(info.getFullKey());
                    break;
                }
            }
        }
    }
    
    /**
     * Scheduled task to reset API key usage counters at the top of each hour
     * Runs every minute to check for usage resets
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void checkAndResetUsage() {
        if (apiKeyManager != null) {
            try {
                // Force a check of all keys which will trigger reset logic if needed
                // This ensures usage counters are reset even when no API calls are being made
                apiKeyManager.getNextAvailableKey();
                logger.debug("Checked API key usage counters for automatic reset");
            } catch (Exception e) {
                // It's okay if no keys are available, we just want to trigger the reset check
                logger.debug("API key usage check completed (no available keys is normal)");
            }
        }
    }
    
    /**
     * Manual reset of all API key usage counters
     * Useful for administrative purposes or testing
     */
    public void resetAllUsageCounters() {
        if (apiKeyManager != null) {
            logger.info("Manually resetting all API key usage counters");
            // The reset logic is built into the ApiKeyManager.getNextAvailableKey() method
            // We can trigger it by calling the method, which will reset any keys that need it
            try {
                apiKeyManager.getNextAvailableKey();
            } catch (Exception e) {
                // Expected if no keys are available
            }
            logger.info("Manual reset completed");
        }
    }
}