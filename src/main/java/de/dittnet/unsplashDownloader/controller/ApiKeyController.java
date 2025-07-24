package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.model.ApiKeyInfo;
import de.dittnet.unsplashDownloader.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api-keys")
public class ApiKeyController {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyController.class);
    
    @Autowired
    private ApiKeyService apiKeyService;
    
    @GetMapping
    public String apiKeysPage(Model model) {
        model.addAttribute("pageTitle", "API Key Management");
        
        // Get all API keys
        List<ApiKeyInfo> apiKeys = apiKeyService.getAllApiKeys();
        model.addAttribute("apiKeys", apiKeys);
        
        // Get statistics
        Map<String, Object> stats = apiKeyService.getApiKeyStatistics();
        model.addAttribute("stats", stats);
        
        return "api-keys";
    }
    
    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addApiKey(@RequestParam("apiKey") String apiKey) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "API key cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }
            
            apiKeyService.addApiKey(apiKey.trim());
            
            response.put("success", true);
            response.put("message", "API key added successfully");
            
            logger.info("API key added successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Failed to add API key", e);
            response.put("success", false);
            response.put("message", "Failed to add API key: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/remove")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeApiKey(@RequestParam("keyId") String keyId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            apiKeyService.removeApiKey(keyId);
            
            response.put("success", true);
            response.put("message", "API key removed successfully");
            
            logger.info("API key removed: {}", keyId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to remove API key", e);
            response.put("success", false);
            response.put("message", "Failed to remove API key: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateApiKey(
            @RequestParam("keyId") String keyId,
            @RequestParam("newApiKey") String newApiKey) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (newApiKey == null || newApiKey.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "New API key cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }
            
            apiKeyService.updateApiKey(keyId, newApiKey.trim());
            
            response.put("success", true);
            response.put("message", "API key updated successfully");
            
            logger.info("API key updated: {}", keyId);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Failed to update API key", e);
            response.put("success", false);
            response.put("message", "Failed to update API key: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateApiKey(@RequestParam("apiKey") String apiKey) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean isValid = apiKeyService.validateApiKey(apiKey);
            
            response.put("success", true);
            response.put("valid", isValid);
            response.put("message", isValid ? "API key is valid" : "API key is invalid");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to validate API key", e);
            response.put("success", false);
            response.put("message", "Failed to validate API key: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = apiKeyService.getApiKeyStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Failed to get API key statistics", e);
            return ResponseEntity.internalServerError().body(new HashMap<>());
        }
    }
    
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<List<ApiKeyInfo>> listApiKeys() {
        try {
            List<ApiKeyInfo> apiKeys = apiKeyService.getAllApiKeys();
            return ResponseEntity.ok(apiKeys);
        } catch (Exception e) {
            logger.error("Failed to list API keys", e);
            return ResponseEntity.internalServerError().body(List.of());
        }
    }
    
    @PostMapping("/test-countdown")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testCountdown(@RequestParam("keyId") String keyId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // This is a test endpoint to simulate a rate-limited key for countdown demo
            apiKeyService.simulateRateLimit(keyId);
            
            response.put("success", true);
            response.put("message", "Key temporarily rate-limited for countdown test");
            
            logger.info("API key temporarily rate-limited for test: {}", keyId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to test countdown", e);
            response.put("success", false);
            response.put("message", "Failed to test countdown: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/check-availability")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkAvailability() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Force refresh of API key states
            List<ApiKeyInfo> apiKeys = apiKeyService.getAllApiKeys();
            
            int availableKeys = (int) apiKeys.stream().filter(key -> !key.isAtLimit()).count();
            int totalKeys = apiKeys.size();
            
            response.put("success", true);
            response.put("availableKeys", availableKeys);
            response.put("totalKeys", totalKeys);
            response.put("message", String.format("%d of %d keys available", availableKeys, totalKeys));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to check key availability", e);
            response.put("success", false);
            response.put("message", "Failed to check availability: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/reset-usage")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetUsage() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            apiKeyService.resetAllUsageCounters();
            
            response.put("success", true);
            response.put("message", "API key usage counters have been reset");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to reset usage counters", e);
            response.put("success", false);
            response.put("message", "Failed to reset usage: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}