package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.entity.MetadataSyncEntity;
import de.dittnet.unsplashDownloader.repository.MetadataSyncRepository;
import de.dittnet.unsplashDownloader.service.MetadataSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/metadata-sync")
public class MetadataSyncController {
    private static final Logger logger = LoggerFactory.getLogger(MetadataSyncController.class);
    
    @Autowired
    private MetadataSyncService metadataSyncService;
    
    @Autowired
    private MetadataSyncRepository metadataSyncRepository;
    
    /**
     * Get metadata sync statistics
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = metadataSyncService.getSyncStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Failed to get metadata sync statistics", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Initialize metadata sync for all photos
     */
    @PostMapping("/initialize")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initialize() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            metadataSyncService.initializeMetadataSync();
            
            response.put("success", true);
            response.put("message", "Metadata sync initialization completed");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to initialize metadata sync", e);
            response.put("success", false);
            response.put("message", "Failed to initialize: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Start metadata sync with specified batch size
     */
    @PostMapping("/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startSync(@RequestParam(defaultValue = "100") int batchSize) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (metadataSyncService.isSyncInProgress()) {
                response.put("success", false);
                response.put("message", "Metadata sync is already in progress");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validate batch size
            if (batchSize < 1 || batchSize > 1000) {
                response.put("success", false);
                response.put("message", "Batch size must be between 1 and 1000");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Start async sync
            CompletableFuture<Void> syncFuture = metadataSyncService.syncMetadataAsync(batchSize);
            
            response.put("success", true);
            response.put("message", "Metadata sync started with batch size: " + batchSize);
            
            logger.info("Started metadata sync with batch size: {}", batchSize);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to start metadata sync", e);
            response.put("success", false);
            response.put("message", "Failed to start sync: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Stop ongoing metadata sync
     */
    @PostMapping("/stop")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> stopSync() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            metadataSyncService.stopSync();
            
            response.put("success", true);
            response.put("message", "Stop signal sent to metadata sync process");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to stop metadata sync", e);
            response.put("success", false);
            response.put("message", "Failed to stop sync: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Reset all sync entries to pending
     */
    @PostMapping("/reset")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetSync() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (metadataSyncService.isSyncInProgress()) {
                response.put("success", false);
                response.put("message", "Cannot reset while sync is in progress. Stop sync first.");
                return ResponseEntity.badRequest().body(response);
            }
            
            metadataSyncService.resetAllSyncEntries();
            
            response.put("success", true);
            response.put("message", "All metadata sync entries have been reset to pending status");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to reset metadata sync", e);
            response.put("success", false);
            response.put("message", "Failed to reset sync: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Add newly downloaded photos to sync system
     */
    @PostMapping("/add-new-photos")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addNewPhotos() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            metadataSyncService.addNewPhotosToSync();
            
            response.put("success", true);
            response.put("message", "New photos have been added to metadata sync system");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to add new photos to sync", e);
            response.put("success", false);
            response.put("message", "Failed to add new photos: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Get sync entries with pagination
     */
    @GetMapping("/entries")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSyncEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<MetadataSyncEntity> entries;
            
            if (status != null && !status.isEmpty()) {
                try {
                    MetadataSyncEntity.SyncStatus syncStatus = MetadataSyncEntity.SyncStatus.valueOf(status.toUpperCase());
                    entries = metadataSyncRepository.findBySyncStatusOrderByUpdatedAtDesc(syncStatus, pageable);
                } catch (IllegalArgumentException e) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Invalid status: " + status);
                    return ResponseEntity.badRequest().body(error);
                }
            } else {
                entries = metadataSyncRepository.findAllByOrderByUpdatedAtDesc(pageable);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("entries", entries.getContent());
            response.put("totalElements", entries.getTotalElements());
            response.put("totalPages", entries.getTotalPages());
            response.put("currentPage", page);
            response.put("size", size);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get sync entries", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load entries: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get photos needing sync
     */
    @GetMapping("/pending")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPendingPhotos(@RequestParam(defaultValue = "20") int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            List<MetadataSyncEntity> pendingPhotos = metadataSyncRepository.findPhotosNeedingSyncLimited(pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("pendingPhotos", pendingPhotos);
            response.put("totalPending", metadataSyncRepository.countPhotosNeedingSync());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get pending photos", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load pending photos: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get sync entry details by photo ID
     */
    @GetMapping("/photo/{photoId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSyncEntryByPhotoId(@PathVariable String photoId) {
        try {
            Optional<MetadataSyncEntity> syncEntry = metadataSyncRepository.findByPhotoId(photoId);
            
            Map<String, Object> response = new HashMap<>();
            if (syncEntry.isPresent()) {
                response.put("syncEntry", syncEntry.get());
                response.put("found", true);
            } else {
                response.put("found", false);
                response.put("message", "No sync entry found for photo: " + photoId);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get sync entry for photo {}", photoId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load sync entry: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Sync metadata for a specific photo
     */
    @PostMapping("/photo/{photoId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncSpecificPhoto(@PathVariable String photoId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<MetadataSyncEntity> syncEntryOpt = metadataSyncRepository.findByPhotoId(photoId);
            
            if (!syncEntryOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "No sync entry found for photo: " + photoId);
                return ResponseEntity.notFound().build();
            }
            
            MetadataSyncEntity syncEntry = syncEntryOpt.get();
            boolean success = metadataSyncService.syncPhotoMetadata(syncEntry);
            
            response.put("success", success);
            response.put("message", success ? "Photo metadata synced successfully" : "Failed to sync photo metadata");
            response.put("syncEntry", syncEntry);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to sync metadata for photo {}", photoId, e);
            response.put("success", false);
            response.put("message", "Failed to sync photo: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Clean up old completed entries
     */
    @PostMapping("/cleanup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cleanupOldEntries(@RequestParam(defaultValue = "30") int daysToKeep) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            metadataSyncService.cleanupOldEntries(daysToKeep);
            
            response.put("success", true);
            response.put("message", "Cleaned up completed entries older than " + daysToKeep + " days");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup old entries", e);
            response.put("success", false);
            response.put("message", "Failed to cleanup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}