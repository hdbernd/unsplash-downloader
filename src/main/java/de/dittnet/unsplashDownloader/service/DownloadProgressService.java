package de.dittnet.unsplashDownloader.service;

import de.dittnet.unsplashDownloader.model.DownloadProgress;
import de.dittnet.unsplashDownloader.model.DownloadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class DownloadProgressService {
    private static final Logger logger = LoggerFactory.getLogger(DownloadProgressService.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ConcurrentLinkedQueue<DownloadProgress> completedDownloads;
    private final ConcurrentHashMap<String, DownloadProgress> activeDownloads;
    
    public DownloadProgressService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.completedDownloads = new ConcurrentLinkedQueue<>();
        this.activeDownloads = new ConcurrentHashMap<>();
    }
    
    public void updateProgress(DownloadProgress progress) {
        String downloadKey = generateDownloadKey(progress);
        
        // Update active downloads
        activeDownloads.put(downloadKey, progress);
        
        // If download is completed, failed, or cancelled, move to history
        if (progress.getStatus() == DownloadStatus.COMPLETED || 
            progress.getStatus() == DownloadStatus.FAILED || 
            progress.getStatus() == DownloadStatus.CANCELLED) {
            
            // Create a copy for history to avoid reference issues
            DownloadProgress historicalProgress = createHistoricalCopy(progress);
            completedDownloads.offer(historicalProgress);
            
            // Remove from active downloads
            activeDownloads.remove(downloadKey);
            
            // Keep only last 50 completed downloads
            while (completedDownloads.size() > 50) {
                completedDownloads.poll();
            }
            
            // Send history update via WebSocket
            try {
                messagingTemplate.convertAndSend("/topic/download-history", getProgressHistory());
                logger.debug("Sent history update");
            } catch (Exception e) {
                logger.error("Failed to send history update", e);
            }
        }
        
        // Send progress update via WebSocket
        try {
            messagingTemplate.convertAndSend("/topic/download-progress", progress);
            logger.debug("Sent progress update: {}", progress.getMessage());
        } catch (Exception e) {
            logger.error("Failed to send progress update", e);
        }
    }
    
    public List<DownloadProgress> getProgressHistory() {
        return new ArrayList<>(completedDownloads);
    }
    
    public DownloadProgress getLatestProgress() {
        // Return the latest from active downloads if any, otherwise from completed
        if (!activeDownloads.isEmpty()) {
            return activeDownloads.values().stream()
                   .reduce((first, second) -> second)
                   .orElse(null);
        }
        
        return completedDownloads.isEmpty() ? null : 
               completedDownloads.stream()
                   .reduce((first, second) -> second)
                   .orElse(null);
    }
    
    private String generateDownloadKey(DownloadProgress progress) {
        return progress.getUsername() + "_" + 
               (progress.getStartTime() != null ? progress.getStartTime().toString() : "unknown");
    }
    
    private DownloadProgress createHistoricalCopy(DownloadProgress original) {
        DownloadProgress copy = new DownloadProgress();
        copy.setUsername(original.getUsername());
        copy.setOutputPath(original.getOutputPath());
        copy.setStatus(original.getStatus());
        copy.setTotalPhotos(original.getTotalPhotos());
        copy.setDownloadedPhotos(original.getDownloadedPhotos());
        copy.setCurrentPhoto(original.getCurrentPhoto());
        copy.setMessage(original.getMessage());
        copy.setStartTime(original.getStartTime());
        copy.setEndTime(original.getEndTime());
        return copy;
    }
}