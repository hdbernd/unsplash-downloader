package de.dittnet.unsplashDownloader.service;

import de.dittnet.unsplashDownloader.UnsplashDownloader;
import de.dittnet.unsplashDownloader.config.StorageConfig;
import de.dittnet.unsplashDownloader.model.DownloadStatus;
import de.dittnet.unsplashDownloader.model.DownloadProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DownloadService {
    private static final Logger logger = LoggerFactory.getLogger(DownloadService.class);
    
    @Autowired
    private PhotoService photoService;
    
    @Autowired
    private DownloadProgressService progressService;
    
    @Autowired
    private StorageConfig storageConfig;
    
    @Autowired
    private UserSettingsService userSettingsService;
    
    private final ConcurrentHashMap<String, CompletableFuture<Void>> activeDownloads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UnsplashDownloader> activeDownloaders = new ConcurrentHashMap<>();
    private final AtomicReference<DownloadProgress> currentProgress = new AtomicReference<>();
    private final AtomicReference<Boolean> cancellationRequested = new AtomicReference<>(false);
    
    public CompletableFuture<Void> startDownload(String username, String outputPath) {
        String downloadId = generateDownloadId(username);
        
        if (activeDownloads.containsKey(downloadId)) {
            throw new IllegalStateException("Download already in progress for user: " + username);
        }
        
        // Update storage configuration for this download
        userSettingsService.updateLastOutputPath(outputPath);
        
        // Initialize progress
        DownloadProgress progress = new DownloadProgress();
        progress.setUsername(username);
        progress.setOutputPath(outputPath);
        progress.setStatus(DownloadStatus.STARTING);
        progress.setStartTime(LocalDateTime.now());
        progress.setTotalPhotos(0);
        progress.setDownloadedPhotos(0);
        progress.setCurrentPhoto("");
        progress.setMessage("Initializing download...");
        
        currentProgress.set(progress);
        progressService.updateProgress(progress);
        cancellationRequested.set(false);
        
        CompletableFuture<Void> downloadFuture = CompletableFuture.runAsync(() -> {
            try {
                performDownload(username, outputPath, progress, downloadId);
            } catch (InterruptedException e) {
                logger.info("Download interrupted for user: " + username);
                progress.setStatus(DownloadStatus.CANCELLED);
                progress.setMessage("Download cancelled by user");
                progress.setEndTime(LocalDateTime.now());
                progressService.updateProgress(progress);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Download failed for user: " + username, e);
                progress.setStatus(DownloadStatus.FAILED);
                progress.setMessage("Download failed: " + e.getMessage());
                progress.setEndTime(LocalDateTime.now());
                progressService.updateProgress(progress);
            } finally {
                activeDownloads.remove(downloadId);
                activeDownloaders.remove(downloadId);
            }
        });
        
        activeDownloads.put(downloadId, downloadFuture);
        return downloadFuture;
    }
    
    private void performDownload(String username, String outputPath, DownloadProgress progress, String downloadId) throws IOException, InterruptedException {
        // Create user photos directory (within the output path)
        String userPhotosDir = storageConfig.getUserPhotosDirectory(outputPath);
        File photosDir = new File(userPhotosDir);
        if (!photosDir.exists()) {
            photosDir.mkdirs();
        }
        
        // Create the .unsplash-downloader system directory
        File systemDir = new File(outputPath, ".unsplash-downloader");
        if (!systemDir.exists()) {
            systemDir.mkdirs();
        }
        
        progress.setStatus(DownloadStatus.DOWNLOADING);
        progress.setMessage("Starting download from Unsplash...");
        progressService.updateProgress(progress);
        
        // Create custom progress callback
        DownloadProgressCallback callback = new DownloadProgressCallback() {
            @Override
            public void onPhotoStarted(String photoId, String filename, int currentIndex, int totalPhotos) {
                // Check for cancellation
                if (cancellationRequested.get() || Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Download cancelled");
                }
                
                progress.setCurrentPhoto(filename);
                progress.setDownloadedPhotos(currentIndex);
                progress.setTotalPhotos(totalPhotos);
                progress.setMessage("Downloading: " + filename + " (" + (currentIndex + 1) + "/" + totalPhotos + ")");
                progressService.updateProgress(progress);
            }
            
            @Override
            public void onPhotoCompleted(String photoId, String filename, int currentIndex, int totalPhotos) {
                progress.setDownloadedPhotos(currentIndex + 1);
                progress.setMessage("Completed: " + filename + " (" + (currentIndex + 1) + "/" + totalPhotos + ")");
                progressService.updateProgress(progress);
            }
            
            @Override
            public void onTotalPhotosDiscovered(int totalPhotos) {
                progress.setTotalPhotos(totalPhotos);
                progress.setMessage("Found " + totalPhotos + " photos to download");
                progressService.updateProgress(progress);
            }
            
            @Override
            public void onError(String photoId, String error) {
                progress.setMessage("Error downloading " + photoId + ": " + error);
                progressService.updateProgress(progress);
            }
        };
        
        // Create downloader instance with progress callback
        // Pass the photos directory, not the base output path
        UnsplashDownloader downloader = new UnsplashDownloader(userPhotosDir, photoService);
        downloader.setProgressCallback(callback);
        
        // Store the downloader for potential cancellation
        activeDownloaders.put(downloadId, downloader);
        
        // Start the download
        downloader.downloadUserPhotos(username);
        
        // Mark as completed
        progress.setStatus(DownloadStatus.COMPLETED);
        progress.setEndTime(LocalDateTime.now());
        progress.setMessage("Download completed successfully!");
        progressService.updateProgress(progress);
    }
    
    public DownloadProgress getCurrentProgress() {
        return currentProgress.get();
    }
    
    public boolean isDownloadInProgress(String username) {
        return activeDownloads.containsKey(generateDownloadId(username));
    }
    
    public void cancelDownload(String username) {
        String downloadId = generateDownloadId(username);
        
        // Set cancellation flag
        cancellationRequested.set(true);
        
        // Cancel the future
        CompletableFuture<Void> future = activeDownloads.get(downloadId);
        if (future != null) {
            future.cancel(true);
        }
        
        // Get the downloader and interrupt if possible
        UnsplashDownloader downloader = activeDownloaders.get(downloadId);
        if (downloader != null) {
            // You might want to add a cancel method to UnsplashDownloader
            logger.info("Cancelling download for user: {}", username);
        }
        
        // Update progress
        DownloadProgress progress = currentProgress.get();
        if (progress != null && progress.getUsername().equals(username)) {
            progress.setStatus(DownloadStatus.CANCELLED);
            progress.setMessage("Download cancelled by user");
            progress.setEndTime(LocalDateTime.now());
            progressService.updateProgress(progress);
        }
        
        // Clean up
        activeDownloads.remove(downloadId);
        activeDownloaders.remove(downloadId);
    }
    
    private String generateDownloadId(String username) {
        return "download_" + username.toLowerCase();
    }
    
    // Callback interface for progress updates
    public interface DownloadProgressCallback {
        void onPhotoStarted(String photoId, String filename, int currentIndex, int totalPhotos);
        void onPhotoCompleted(String photoId, String filename, int currentIndex, int totalPhotos);
        void onTotalPhotosDiscovered(int totalPhotos);
        void onError(String photoId, String error);
    }
}