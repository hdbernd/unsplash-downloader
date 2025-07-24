package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.service.DatabaseSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/database-sync")
public class DatabaseSyncController {
    
    @Autowired
    private DatabaseSyncService databaseSyncService;
    
    /**
     * Get database synchronization status
     */
    @GetMapping("/status")
    public ResponseEntity<DatabaseSyncService.SyncStatus> getSyncStatus() {
        return ResponseEntity.ok(databaseSyncService.getSyncStatus());
    }
    
    /**
     * Force immediate sync to network
     */
    @PostMapping("/sync-to-network")
    public ResponseEntity<Map<String, Object>> forceSyncToNetwork() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            databaseSyncService.forceSyncToNetwork();
            response.put("success", true);
            response.put("message", "Database sync to network initiated successfully");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to sync to network: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Restore local database from network
     */
    @PostMapping("/restore-from-network")
    public ResponseEntity<Map<String, Object>> restoreFromNetwork() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            databaseSyncService.restoreFromNetwork();
            response.put("success", true);
            response.put("message", "Database restored from network successfully");
            response.put("warning", "Application restart recommended to use restored database");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to restore from network: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reset all storage and logs - DANGEROUS operation
     */
    @PostMapping("/reset-all-storage")
    public ResponseEntity<Map<String, Object>> resetAllStorage() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            databaseSyncService.resetAllStorage();
            response.put("success", true);
            response.put("message", "All storage (local and network) has been reset successfully");
            response.put("warning", "Application restart required. All photos, database, logs, and settings have been deleted.");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to reset all storage: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reset only local storage
     */
    @PostMapping("/reset-local-storage")
    public ResponseEntity<Map<String, Object>> resetLocalStorage() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            databaseSyncService.resetLocalStorageOnly();
            response.put("success", true);
            response.put("message", "Local storage has been reset successfully");
            response.put("warning", "Application restart required. All local data and logs have been deleted.");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to reset local storage: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}