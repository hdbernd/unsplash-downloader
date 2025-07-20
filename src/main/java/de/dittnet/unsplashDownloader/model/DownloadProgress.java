package de.dittnet.unsplashDownloader.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class DownloadProgress {
    private String username;
    private String outputPath;
    private DownloadStatus status;
    private int totalPhotos;
    private int downloadedPhotos;
    private String currentPhoto;
    private String message;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    
    // Constructors
    public DownloadProgress() {}
    
    public DownloadProgress(String username, String outputPath) {
        this.username = username;
        this.outputPath = outputPath;
        this.status = DownloadStatus.PENDING;
        this.totalPhotos = 0;
        this.downloadedPhotos = 0;
        this.currentPhoto = "";
        this.message = "";
    }
    
    // Getters and Setters
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getOutputPath() {
        return outputPath;
    }
    
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }
    
    public DownloadStatus getStatus() {
        return status;
    }
    
    public void setStatus(DownloadStatus status) {
        this.status = status;
    }
    
    public int getTotalPhotos() {
        return totalPhotos;
    }
    
    public void setTotalPhotos(int totalPhotos) {
        this.totalPhotos = totalPhotos;
    }
    
    public int getDownloadedPhotos() {
        return downloadedPhotos;
    }
    
    public void setDownloadedPhotos(int downloadedPhotos) {
        this.downloadedPhotos = downloadedPhotos;
    }
    
    public String getCurrentPhoto() {
        return currentPhoto;
    }
    
    public void setCurrentPhoto(String currentPhoto) {
        this.currentPhoto = currentPhoto;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    // Helper methods
    public double getProgressPercentage() {
        if (totalPhotos == 0) return 0.0;
        return (double) downloadedPhotos / totalPhotos * 100.0;
    }
    
    public boolean isActive() {
        return status == DownloadStatus.STARTING || status == DownloadStatus.DOWNLOADING;
    }
    
    public boolean isCompleted() {
        return status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED;
    }
}