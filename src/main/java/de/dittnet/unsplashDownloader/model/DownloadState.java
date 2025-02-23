package de.dittnet.unsplashDownloader.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DownloadState {
    private String username;
    private int totalPhotos;
    private Set<String> downloadedPhotos;
    private LocalDateTime lastUpdate;
    private int dailyRequestCount;
    private LocalDateTime requestCountDate;

    public DownloadState() {
        this.downloadedPhotos = new HashSet<>();
        this.lastUpdate = LocalDateTime.now();
        this.requestCountDate = LocalDateTime.now();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getTotalPhotos() {
        return totalPhotos;
    }

    public void setTotalPhotos(int totalPhotos) {
        this.totalPhotos = totalPhotos;
    }

    public Set<String> getDownloadedPhotos() {
        return downloadedPhotos;
    }

    public void setDownloadedPhotos(Set<String> downloadedPhotos) {
        this.downloadedPhotos = downloadedPhotos;
    }

    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public int getDailyRequestCount() {
        return dailyRequestCount;
    }

    public void setDailyRequestCount(int dailyRequestCount) {
        this.dailyRequestCount = dailyRequestCount;
    }

    public LocalDateTime getRequestCountDate() {
        return requestCountDate;
    }

    public void setRequestCountDate(LocalDateTime requestCountDate) {
        this.requestCountDate = requestCountDate;
    }
}
