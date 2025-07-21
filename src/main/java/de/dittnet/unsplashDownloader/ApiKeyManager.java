package de.dittnet.unsplashDownloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiKeyManager {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyManager.class);
    private static final int DEFAULT_HOURLY_LIMIT_DEMO = 50;
    private static final int DEFAULT_HOURLY_LIMIT_PRODUCTION = 5000;
    
    private final List<String> apiKeys;
    private final Map<String, Integer> hourlyUsage;
    private final Map<String, LocalDateTime> lastUsageHour;
    private final Map<String, Boolean> keyRateLimited;
    private final Map<String, LocalDateTime> rateLimitResetTime;
    private final AtomicInteger currentKeyIndex;
    private final ObjectMapper objectMapper;
    private final File stateFile;
    private final int hourlyLimit;
    
    private final String stateDir;
    
    public ApiKeyManager(String outputDir) throws IOException {
        this.stateDir = outputDir;
        this.hourlyUsage = new HashMap<>();
        this.lastUsageHour = new HashMap<>();
        this.keyRateLimited = new HashMap<>();
        this.rateLimitResetTime = new HashMap<>();
        this.currentKeyIndex = new AtomicInteger(0);
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.stateFile = new File(outputDir, "api_key_state.json");
        this.hourlyLimit = DEFAULT_HOURLY_LIMIT_DEMO; // Default to demo limits
        
        // Try to load API keys, but use empty list if none found
        List<String> loadedKeys;
        try {
            loadedKeys = loadApiKeys();
        } catch (IOException e) {
            logger.warn("No API keys found during initialization: {}", e.getMessage());
            loadedKeys = new ArrayList<>();
        }
        this.apiKeys = loadedKeys;
        
        loadState();
        validateKeys();
    }
    
    private List<String> loadApiKeys() throws IOException {
        List<String> keys = new ArrayList<>();
        
        // Try environment variables first
        String envKeys = System.getenv("UNSPLASH_ACCESS_TOKENS");
        if (envKeys != null && !envKeys.trim().isEmpty()) {
            String[] tokens = envKeys.split(",");
            for (String token : tokens) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    keys.add(trimmed);
                }
            }
        }
        
        // If no environment variables, try properties file
        if (keys.isEmpty()) {
            Properties prop = new Properties();
            String configPath;
            if (stateDir != null) {
                // Use the same path structure as StorageConfig
                configPath = Paths.get(stateDir, "config", "config.properties").toString();
            } else {
                // Fallback to project root config.properties
                configPath = "config.properties";
            }
            
            File configFile = new File(configPath);
            
            logger.info("Looking for API keys in config file: {}", configFile.getAbsolutePath());
            
            if (configFile.exists()) {
                logger.info("Found config file, loading API keys...");
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    prop.load(fis);
                
                // Try single token first
                String singleToken = prop.getProperty("unsplash.access.token");
                if (singleToken != null && !singleToken.trim().isEmpty()) {
                    keys.add(singleToken.trim());
                }
                
                // Try multiple tokens
                String multipleTokens = prop.getProperty("unsplash.access.tokens");
                if (multipleTokens != null && !multipleTokens.trim().isEmpty()) {
                    String[] tokens = multipleTokens.split(",");
                    for (String token : tokens) {
                        String trimmed = token.trim();
                        if (!trimmed.isEmpty() && !keys.contains(trimmed)) {
                            keys.add(trimmed);
                        }
                    }
                }
                } catch (IOException e) {
                    logger.warn("Failed to load config file: {}", e.getMessage());
                }
            } else {
                logger.warn("Config file not found: {}", configFile.getAbsolutePath());
            }
        }
        
        if (keys.isEmpty()) {
            throw new IOException("No API keys found. Please set UNSPLASH_ACCESS_TOKENS environment variable or add tokens to config.properties");
        }
        
        logger.info("Loaded {} API key(s)", keys.size());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            logger.info("API Key {}: length={}, starts with '{}'", i+1, key.length(), key.substring(0, Math.min(8, key.length())));
            
            // Check for dummy/test keys
            if (isDummyKey(key)) {
                logger.warn("⚠️  WARNING: API Key {} appears to be a dummy/test key: '{}'", i+1, key);
                logger.warn("⚠️  Please add your real Unsplash API key through the web interface at http://localhost:8099");
                logger.warn("⚠️  Downloads will fail with dummy keys!");
            }
        }
        return keys;
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
    
    private void validateKeys() {
        if (apiKeys.isEmpty()) {
            logger.warn("No API keys available - downloads will not work until keys are added");
            return;
        }
        
        // Initialize usage tracking for all keys
        for (String key : apiKeys) {
            hourlyUsage.putIfAbsent(key, 0);
            lastUsageHour.putIfAbsent(key, LocalDateTime.now());
            keyRateLimited.putIfAbsent(key, false);
            rateLimitResetTime.putIfAbsent(key, LocalDateTime.now());
        }
    }
    
    public synchronized String getNextAvailableKey() {
        LocalDateTime now = LocalDateTime.now();
        
        // First, reset hourly counters and rate limit flags for keys from previous hour
        for (String key : apiKeys) {
            LocalDateTime lastUsed = lastUsageHour.get(key);
            LocalDateTime resetTime = rateLimitResetTime.get(key);
            
            // Reset hourly usage if we're in a new hour
            if (lastUsed != null && lastUsed.getHour() != now.getHour()) {
                hourlyUsage.put(key, 0);
                lastUsageHour.put(key, now);
                logger.info("Reset hourly usage for API key (starts with: {})", key.substring(0, Math.min(8, key.length())));
            }
            
            // Reset rate limit flag if hour has passed since rate limit
            if (keyRateLimited.get(key) && resetTime != null && now.isAfter(resetTime)) {
                keyRateLimited.put(key, false);
                logger.info("Rate limit reset for API key (starts with: {})", key.substring(0, Math.min(8, key.length())));
            }
        }
        
        // Find a key that hasn't reached its hourly limit and isn't rate limited
        for (int i = 0; i < apiKeys.size(); i++) {
            int index = (currentKeyIndex.get() + i) % apiKeys.size();
            String key = apiKeys.get(index);
            
            if (!keyRateLimited.get(key) && hourlyUsage.get(key) < hourlyLimit) {
                currentKeyIndex.set(index);
                return key;
            }
        }
        
        // All keys have reached their hourly limit or are rate limited
        return null;
    }
    
    public synchronized void recordUsage(String key) {
        if (apiKeys.contains(key)) {
            hourlyUsage.put(key, hourlyUsage.get(key) + 1);
            lastUsageHour.put(key, LocalDateTime.now());
            
            int usage = hourlyUsage.get(key);
            logger.debug("API key usage: {}/{} for key starting with: {}", 
                usage, hourlyLimit, key.substring(0, Math.min(8, key.length())));
            
            try {
                saveState();
            } catch (IOException e) {
                logger.error("Failed to save API key state", e);
            }
        }
    }
    
    public synchronized boolean hasAvailableKey() {
        return getNextAvailableKey() != null;
    }
    
    public synchronized void markKeyRateLimited(String key) {
        if (apiKeys.contains(key)) {
            keyRateLimited.put(key, true);
            rateLimitResetTime.put(key, LocalDateTime.now().plusHours(1));
            logger.warn("Marked API key as rate limited (starts with: {}). Will retry after: {}", 
                key.substring(0, Math.min(8, key.length())), 
                rateLimitResetTime.get(key));
            
            try {
                saveState();
            } catch (IOException e) {
                logger.error("Failed to save API key state", e);
            }
        }
    }
    
    public synchronized int getTotalHourlyUsage() {
        return hourlyUsage.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    public synchronized int getMaxHourlyLimit() {
        return hourlyLimit * apiKeys.size();
    }
    
    public synchronized Map<String, Integer> getCurrentUsage() {
        return new HashMap<>(hourlyUsage);
    }
    
    public synchronized Map<String, Boolean> getRateLimitedKeys() {
        return new HashMap<>(keyRateLimited);
    }
    
    public synchronized int getAvailableKeysCount() {
        LocalDateTime now = LocalDateTime.now();
        int available = 0;
        
        for (String key : apiKeys) {
            boolean rateLimited = keyRateLimited.get(key);
            LocalDateTime resetTime = rateLimitResetTime.get(key);
            
            // Check if rate limit has expired
            if (rateLimited && resetTime != null && now.isAfter(resetTime)) {
                rateLimited = false;
            }
            
            if (!rateLimited && hourlyUsage.get(key) < hourlyLimit) {
                available++;
            }
        }
        
        return available;
    }
    
    public synchronized LocalDateTime getNextResetTime() {
        return rateLimitResetTime.values().stream()
            .filter(Objects::nonNull)
            .min(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now().plusHours(1));
    }
    
    public synchronized LocalDateTime getLastUsageTime(String key) {
        return lastUsageHour.get(key);
    }
    
    public synchronized LocalDateTime getAvailableAgainTime(String key) {
        if (!keyRateLimited.getOrDefault(key, false)) {
            return null; // Available now
        }
        return rateLimitResetTime.get(key);
    }
    
    private void loadState() {
        if (!stateFile.exists()) {
            return;
        }
        
        try {
            ApiKeyState state = objectMapper.readValue(stateFile, ApiKeyState.class);
            if (state != null) {
                if (state.getHourlyUsage() != null) {
                    hourlyUsage.putAll(state.getHourlyUsage());
                }
                if (state.getLastUsageHour() != null) {
                    lastUsageHour.putAll(state.getLastUsageHour());
                }
                if (state.getKeyRateLimited() != null) {
                    keyRateLimited.putAll(state.getKeyRateLimited());
                }
                if (state.getRateLimitResetTime() != null) {
                    rateLimitResetTime.putAll(state.getRateLimitResetTime());
                }
                currentKeyIndex.set(state.getCurrentKeyIndex());
            }
        } catch (IOException e) {
            logger.error("Failed to load API key state", e);
        }
    }
    
    private void saveState() throws IOException {
        ApiKeyState state = new ApiKeyState();
        state.setHourlyUsage(new HashMap<>(hourlyUsage));
        state.setLastUsageHour(new HashMap<>(lastUsageHour));
        state.setKeyRateLimited(new HashMap<>(keyRateLimited));
        state.setRateLimitResetTime(new HashMap<>(rateLimitResetTime));
        state.setCurrentKeyIndex(currentKeyIndex.get());
        
        objectMapper.writeValue(stateFile, state);
    }
    
    public void reloadConfiguration() {
        try {
            List<String> newKeys = loadApiKeys();
            apiKeys.clear();
            apiKeys.addAll(newKeys);
            
            // Initialize tracking maps for all keys
            validateKeys();
            
            logger.info("Reloaded API key configuration: {} keys found", newKeys.size());
        } catch (IOException e) {
            logger.error("Failed to reload API key configuration", e);
        }
    }
    
    private static class ApiKeyState {
        private Map<String, Integer> hourlyUsage = new HashMap<>();
        private Map<String, LocalDateTime> lastUsageHour = new HashMap<>();
        private Map<String, Boolean> keyRateLimited = new HashMap<>();
        private Map<String, LocalDateTime> rateLimitResetTime = new HashMap<>();
        private int currentKeyIndex = 0;
        
        public Map<String, Integer> getHourlyUsage() {
            return hourlyUsage;
        }
        
        public void setHourlyUsage(Map<String, Integer> hourlyUsage) {
            this.hourlyUsage = hourlyUsage;
        }
        
        public Map<String, LocalDateTime> getLastUsageHour() {
            return lastUsageHour;
        }
        
        public void setLastUsageHour(Map<String, LocalDateTime> lastUsageHour) {
            this.lastUsageHour = lastUsageHour;
        }
        
        public Map<String, Boolean> getKeyRateLimited() {
            return keyRateLimited;
        }
        
        public void setKeyRateLimited(Map<String, Boolean> keyRateLimited) {
            this.keyRateLimited = keyRateLimited;
        }
        
        public Map<String, LocalDateTime> getRateLimitResetTime() {
            return rateLimitResetTime;
        }
        
        public void setRateLimitResetTime(Map<String, LocalDateTime> rateLimitResetTime) {
            this.rateLimitResetTime = rateLimitResetTime;
        }
        
        public int getCurrentKeyIndex() {
            return currentKeyIndex;
        }
        
        public void setCurrentKeyIndex(int currentKeyIndex) {
            this.currentKeyIndex = currentKeyIndex;
        }
    }
}