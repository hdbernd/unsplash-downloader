package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.model.UserSettings;
import de.dittnet.unsplashDownloader.service.UserSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/settings")
public class SettingsController {
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);
    
    @Autowired
    private UserSettingsService settingsService;
    
    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateFullSettings(
            @RequestParam(required = false) String lastUsername,
            @RequestParam(required = false) String lastOutputPath,
            @RequestParam(required = false) Integer defaultPageSize,
            @RequestParam(required = false) Boolean autoSaveSettings,
            @RequestParam(required = false) Boolean showDetailedProgress,
            @RequestParam(required = false) Boolean confirmBeforeDownload) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            UserSettings settings = settingsService.getSettings();
            
            // Update settings if provided
            if (lastUsername != null) {
                settings.setLastUsername(lastUsername.trim());
            }
            
            if (lastOutputPath != null) {
                settings.setLastOutputPath(lastOutputPath.trim());
            }
            
            if (defaultPageSize != null) {
                settings.setDefaultPageSize(defaultPageSize);
            }
            
            if (autoSaveSettings != null) {
                settings.setAutoSaveSettings(autoSaveSettings);
            }
            
            if (showDetailedProgress != null) {
                settings.setShowDetailedProgress(showDetailedProgress);
            }
            
            if (confirmBeforeDownload != null) {
                settings.setConfirmBeforeDownload(confirmBeforeDownload);
            }
            
            // Save updated settings
            settingsService.updateSettings(settings);
            
            response.put("success", true);
            response.put("message", "Settings updated successfully");
            
            logger.info("Settings updated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to update settings", e);
            response.put("success", false);
            response.put("message", "Failed to update settings: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetSettings() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            settingsService.resetSettings();
            
            response.put("success", true);
            response.put("message", "Settings reset successfully");
            
            logger.info("Settings reset to defaults");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to reset settings", e);
            response.put("success", false);
            response.put("message", "Failed to reset settings: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/clear-history")
    public ResponseEntity<Map<String, Object>> clearHistory() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            settingsService.clearRecentUsernames();
            settingsService.clearRecentOutputPaths();
            
            response.put("success", true);
            response.put("message", "History cleared successfully");
            
            logger.info("Settings history cleared");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to clear history", e);
            response.put("success", false);
            response.put("message", "Failed to clear history: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/remove-recent-username")
    public ResponseEntity<Map<String, Object>> removeRecentUsername(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            UserSettings settings = settingsService.getSettings();
            settings.getRecentUsernames().remove(username);
            settingsService.updateSettings(settings);
            
            response.put("success", true);
            response.put("message", "Username removed from history");
            
            logger.info("Removed username from history: {}", username);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to remove username from history", e);
            response.put("success", false);
            response.put("message", "Failed to remove username: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/remove-recent-path")
    public ResponseEntity<Map<String, Object>> removeRecentPath(@RequestParam String path) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            UserSettings settings = settingsService.getSettings();
            settings.getRecentOutputPaths().remove(path);
            settingsService.updateSettings(settings);
            
            response.put("success", true);
            response.put("message", "Path removed from history");
            
            logger.info("Removed path from history: {}", path);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to remove path from history", e);
            response.put("success", false);
            response.put("message", "Failed to remove path: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}