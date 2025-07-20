package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.model.DownloadProgress;
import de.dittnet.unsplashDownloader.model.UserSettings;
import de.dittnet.unsplashDownloader.service.DownloadService;
import de.dittnet.unsplashDownloader.service.DownloadProgressService;
import de.dittnet.unsplashDownloader.service.UserSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/download")
public class DownloadController {
    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);
    
    @Autowired
    private DownloadService downloadService;
    
    @Autowired
    private DownloadProgressService progressService;
    
    @Autowired
    private UserSettingsService settingsService;
    
    @GetMapping
    public String downloadPage(Model model) {
        model.addAttribute("pageTitle", "Download Manager");
        
        // Get current progress if any
        DownloadProgress currentProgress = downloadService.getCurrentProgress();
        model.addAttribute("currentProgress", currentProgress);
        
        // Get progress history
        List<DownloadProgress> progressHistory = progressService.getProgressHistory();
        model.addAttribute("progressHistory", progressHistory);
        
        // Get user settings
        UserSettings settings = settingsService.getSettings();
        model.addAttribute("settings", settings);
        
        // Get recent usernames and paths
        model.addAttribute("recentUsernames", settings.getRecentUsernames());
        model.addAttribute("recentOutputPaths", settings.getRecentOutputPaths());
        
        // Get default values
        model.addAttribute("defaultPath", settings.getLastOutputPath());
        model.addAttribute("defaultUsername", settings.getLastUsername());
        
        return "download";
    }
    
    @PostMapping("/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startDownload(
            @RequestParam("username") String username,
            @RequestParam("outputPath") String outputPath) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate inputs
            if (username == null || username.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Username is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (outputPath == null || outputPath.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Output path is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Check if download is already in progress
            if (downloadService.isDownloadInProgress(username)) {
                response.put("success", false);
                response.put("message", "Download already in progress for user: " + username);
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create output directory if it doesn't exist
            File outputDir = new File(outputPath);
            if (!outputDir.exists()) {
                if (!outputDir.mkdirs()) {
                    response.put("success", false);
                    response.put("message", "Failed to create output directory: " + outputPath);
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            // Save settings before starting download
            settingsService.updateLastUsername(username);
            settingsService.updateLastOutputPath(outputPath);
            
            // Start download
            CompletableFuture<Void> downloadFuture = downloadService.startDownload(username, outputPath);
            
            response.put("success", true);
            response.put("message", "Download started for user: " + username);
            response.put("username", username);
            response.put("outputPath", outputPath);
            
            logger.info("Started download for user: {} to path: {}", username, outputPath);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to start download", e);
            response.put("success", false);
            response.put("message", "Failed to start download: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelDownload(@RequestParam("username") String username) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            downloadService.cancelDownload(username);
            response.put("success", true);
            response.put("message", "Download cancelled for user: " + username);
            
            logger.info("Cancelled download for user: {}", username);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to cancel download", e);
            response.put("success", false);
            response.put("message", "Failed to cancel download: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/progress")
    @ResponseBody
    public ResponseEntity<DownloadProgress> getProgress() {
        DownloadProgress progress = downloadService.getCurrentProgress();
        return ResponseEntity.ok(progress);
    }
    
    @GetMapping("/history")
    @ResponseBody
    public ResponseEntity<List<DownloadProgress>> getProgressHistory() {
        List<DownloadProgress> history = progressService.getProgressHistory();
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/browse")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> browseDirectory(@RequestParam("path") String path) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            File directory = new File(path);
            
            if (!directory.exists()) {
                response.put("success", false);
                response.put("message", "Directory does not exist: " + path);
                return ResponseEntity.badRequest().body(response);
            }
            
            if (!directory.isDirectory()) {
                response.put("success", false);
                response.put("message", "Path is not a directory: " + path);
                return ResponseEntity.badRequest().body(response);
            }
            
            File[] files = directory.listFiles();
            if (files == null) {
                response.put("success", false);
                response.put("message", "Cannot read directory: " + path);
                return ResponseEntity.badRequest().body(response);
            }
            
            // Filter to show only directories
            java.util.List<Map<String, Object>> directories = new java.util.ArrayList<>();
            for (File file : files) {
                if (file.isDirectory() && !file.isHidden()) {
                    Map<String, Object> dirInfo = new HashMap<>();
                    dirInfo.put("name", file.getName());
                    dirInfo.put("path", file.getAbsolutePath());
                    directories.add(dirInfo);
                }
            }
            
            response.put("success", true);
            response.put("currentPath", directory.getAbsolutePath());
            response.put("parentPath", directory.getParent());
            response.put("directories", directories);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to browse directory", e);
            response.put("success", false);
            response.put("message", "Failed to browse directory: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/settings")
    @ResponseBody
    public ResponseEntity<UserSettings> getSettings() {
        UserSettings settings = settingsService.getSettings();
        return ResponseEntity.ok(settings);
    }
    
    @PostMapping("/settings")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String outputPath) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (username != null && !username.trim().isEmpty()) {
                settingsService.updateLastUsername(username.trim());
            }
            
            if (outputPath != null && !outputPath.trim().isEmpty()) {
                settingsService.updateLastOutputPath(outputPath.trim());
            }
            
            response.put("success", true);
            response.put("message", "Settings updated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to update settings", e);
            response.put("success", false);
            response.put("message", "Failed to update settings: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/create-folder")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFolder(
            @RequestParam("parentPath") String parentPath,
            @RequestParam("folderName") String folderName) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate inputs
            if (parentPath == null || parentPath.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Parent path is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (folderName == null || folderName.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Folder name is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validate folder name (security check)
            if (!folderName.matches("^[a-zA-Z0-9._-]+$")) {
                response.put("success", false);
                response.put("message", "Folder name can only contain letters, numbers, dots, hyphens, and underscores");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create the full path
            File parentDir = new File(parentPath);
            if (!parentDir.exists() || !parentDir.isDirectory()) {
                response.put("success", false);
                response.put("message", "Parent directory does not exist or is not a directory");
                return ResponseEntity.badRequest().body(response);
            }
            
            File newFolder = new File(parentDir, folderName.trim());
            
            // Check if folder already exists
            if (newFolder.exists()) {
                response.put("success", false);
                response.put("message", "Folder already exists");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create the folder
            if (newFolder.mkdirs()) {
                response.put("success", true);
                response.put("message", "Folder created successfully");
                response.put("folderPath", newFolder.getAbsolutePath());
                
                logger.info("Created folder: {} in parent: {}", folderName, parentPath);
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to create folder");
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Failed to create folder", e);
            response.put("success", false);
            response.put("message", "Failed to create folder: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}