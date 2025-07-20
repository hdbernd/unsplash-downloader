package de.dittnet.unsplashDownloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiKeyManager {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyManager.class);
    private static final int DEFAULT_DAILY_LIMIT = 500;
    
    private final List<String> apiKeys;
    private final Map<String, Integer> dailyUsage;
    private final Map<String, LocalDateTime> lastUsageDate;
    private final AtomicInteger currentKeyIndex;
    private final ObjectMapper objectMapper;
    private final File stateFile;
    private final int dailyLimit;
    
    public ApiKeyManager(String outputDir) throws IOException {
        this.apiKeys = loadApiKeys();
        this.dailyUsage = new HashMap<>();
        this.lastUsageDate = new HashMap<>();
        this.currentKeyIndex = new AtomicInteger(0);
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.stateFile = new File(outputDir, "api_key_state.json");
        this.dailyLimit = DEFAULT_DAILY_LIMIT;
        
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
            try (FileInputStream fis = new FileInputStream("config.properties")) {
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
            }
        }
        
        if (keys.isEmpty()) {
            throw new IOException("No API keys found. Please set UNSPLASH_ACCESS_TOKENS environment variable or add tokens to config.properties");
        }
        
        logger.info("Loaded {} API key(s)", keys.size());
        return keys;
    }
    
    private void validateKeys() {
        if (apiKeys.isEmpty()) {
            throw new IllegalStateException("No API keys available");
        }
        
        // Initialize usage tracking for all keys
        for (String key : apiKeys) {
            dailyUsage.putIfAbsent(key, 0);
            lastUsageDate.putIfAbsent(key, LocalDateTime.now());
        }
    }
    
    public synchronized String getNextAvailableKey() {
        LocalDateTime now = LocalDateTime.now();
        
        // First, reset daily counters for keys that haven't been used today
        for (String key : apiKeys) {
            LocalDateTime lastUsed = lastUsageDate.get(key);
            if (lastUsed != null && !now.toLocalDate().equals(lastUsed.toLocalDate())) {
                dailyUsage.put(key, 0);
                lastUsageDate.put(key, now);
            }
        }
        
        // Find a key that hasn't reached its daily limit
        for (int i = 0; i < apiKeys.size(); i++) {
            int index = (currentKeyIndex.get() + i) % apiKeys.size();
            String key = apiKeys.get(index);
            
            if (dailyUsage.get(key) < dailyLimit) {
                currentKeyIndex.set(index);
                return key;
            }
        }
        
        // All keys have reached their daily limit
        return null;
    }
    
    public synchronized void recordUsage(String key) {
        if (apiKeys.contains(key)) {
            dailyUsage.put(key, dailyUsage.get(key) + 1);
            lastUsageDate.put(key, LocalDateTime.now());
            
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
    
    public synchronized int getTotalDailyUsage() {
        return dailyUsage.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    public synchronized int getMaxDailyLimit() {
        return dailyLimit * apiKeys.size();
    }
    
    public synchronized Map<String, Integer> getCurrentUsage() {
        return new HashMap<>(dailyUsage);
    }
    
    private void loadState() {
        if (!stateFile.exists()) {
            return;
        }
        
        try {
            ApiKeyState state = objectMapper.readValue(stateFile, ApiKeyState.class);
            if (state != null) {
                dailyUsage.putAll(state.getDailyUsage());
                lastUsageDate.putAll(state.getLastUsageDate());
                currentKeyIndex.set(state.getCurrentKeyIndex());
            }
        } catch (IOException e) {
            logger.error("Failed to load API key state", e);
        }
    }
    
    private void saveState() throws IOException {
        ApiKeyState state = new ApiKeyState();
        state.setDailyUsage(new HashMap<>(dailyUsage));
        state.setLastUsageDate(new HashMap<>(lastUsageDate));
        state.setCurrentKeyIndex(currentKeyIndex.get());
        
        objectMapper.writeValue(stateFile, state);
    }
    
    private static class ApiKeyState {
        private Map<String, Integer> dailyUsage = new HashMap<>();
        private Map<String, LocalDateTime> lastUsageDate = new HashMap<>();
        private int currentKeyIndex = 0;
        
        public Map<String, Integer> getDailyUsage() {
            return dailyUsage;
        }
        
        public void setDailyUsage(Map<String, Integer> dailyUsage) {
            this.dailyUsage = dailyUsage;
        }
        
        public Map<String, LocalDateTime> getLastUsageDate() {
            return lastUsageDate;
        }
        
        public void setLastUsageDate(Map<String, LocalDateTime> lastUsageDate) {
            this.lastUsageDate = lastUsageDate;
        }
        
        public int getCurrentKeyIndex() {
            return currentKeyIndex;
        }
        
        public void setCurrentKeyIndex(int currentKeyIndex) {
            this.currentKeyIndex = currentKeyIndex;
        }
    }
}