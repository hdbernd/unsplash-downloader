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
    private ApiKeyManager apiKeyManager;
    
    @Autowired
    private StorageConfig storageConfig;
    
    public ApiKeyService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }
    
    private void initializeApiKeyManager() {
        try {
            String stateDir = storageConfig.getStateDirectory();
            this.apiKeyManager = new ApiKeyManager(stateDir);
        } catch (IOException e) {
            logger.error("Failed to initialize API key manager", e);
        }
    }
    
    public List<ApiKeyInfo> getAllApiKeys() {
        if (apiKeyManager == null) {
            initializeApiKeyManager();
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
                    info.setUsageCount(usage.getOrDefault(singleKey, 0));
                    info.setDailyLimit(500);
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
                            info.setUsageCount(usage.getOrDefault(key, 0));
                            info.setDailyLimit(500);
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
            return false;
        }
        
        try {
            Request request = new Request.Builder()
                    .url(API_BASE_URL + "/me")
                    .header("Authorization", "Client-ID " + apiKey.trim())
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            logger.error("Failed to validate API key", e);
            return false;
        }
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
        
        // Reinitialize API key manager
        initializeApiKeyManager();
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
        
        // Reinitialize API key manager
        initializeApiKeyManager();
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
        
        for (ApiKeyInfo key : keys) {
            totalUsage += key.getUsageCount();
            totalLimit += key.getDailyLimit();
            if (key.isActive()) {
                activeKeys++;
            }
        }
        
        stats.put("totalUsage", totalUsage);
        stats.put("totalLimit", totalLimit);
        stats.put("activeKeys", activeKeys);
        stats.put("usagePercentage", totalLimit > 0 ? (double) totalUsage / totalLimit * 100 : 0);
        
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
}