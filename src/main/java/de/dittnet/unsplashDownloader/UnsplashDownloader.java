package de.dittnet.unsplashDownloader;

import de.dittnet.unsplashDownloader.model.Photo;
import de.dittnet.unsplashDownloader.model.DownloadState;
import de.dittnet.unsplashDownloader.service.PhotoService;
import de.dittnet.unsplashDownloader.service.DownloadService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UnsplashDownloader {
    private static final Logger logger = LoggerFactory.getLogger(UnsplashDownloader.class);
    private static final String API_BASE_URL = "https://api.unsplash.com";
    private static final int PER_PAGE = 30;
    private static final int MAX_DAILY_REQUESTS = 500; // Adjust based on your API plan
    
    private final ApiKeyManager apiKeyManager;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String outputDir;
    private final String baseOutputDir;
    private final ImageMetadataHandler metadataHandler;
    private final File stateFile;
    private final File descriptionsFile;
    private DownloadState state;
    private final PhotoService photoService;
    private DownloadService.DownloadProgressCallback progressCallback;

    public UnsplashDownloader(String outputDir) throws IOException {
        this(outputDir, null);
    }
    
    public UnsplashDownloader(String outputDir, PhotoService photoService) throws IOException {
        this.outputDir = outputDir; // This is the photos directory
        
        // Extract base output directory (parent of photos directory)
        File photosDir = new File(outputDir);
        this.baseOutputDir = photosDir.getParent() != null ? photosDir.getParent() : outputDir;
        
        // Create system directory for state files
        File systemDir = new File(baseOutputDir, ".unsplash-downloader");
        systemDir.mkdirs();
        
        this.apiKeyManager = new ApiKeyManager(systemDir.getAbsolutePath());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
                
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.metadataHandler = new ImageMetadataHandler();
        this.stateFile = new File(systemDir, "download_state.json");
        this.descriptionsFile = new File(baseOutputDir, "descriptions.txt");
        this.photoService = photoService;
        
        // Create output directory if it doesn't exist
        new File(outputDir).mkdirs();
    }
    
    public void setProgressCallback(DownloadService.DownloadProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void downloadUserPhotos(String username) throws IOException {
        loadOrCreateState(username);
        
        // First, get total number of photos if not already known
        if (state.getTotalPhotos() == 0) {
            int totalPhotos = getTotalPhotos(username);
            state.setTotalPhotos(totalPhotos);
            saveState();
            logger.info("Total photos to download: {}", totalPhotos);
            
            // Notify progress callback
            if (progressCallback != null) {
                progressCallback.onTotalPhotosDiscovered(totalPhotos);
            }
        }

        // Calculate remaining photos
        int remainingPhotos = state.getTotalPhotos() - state.getDownloadedPhotos().size();
        logger.info("Remaining photos to download: {}", remainingPhotos);

        if (remainingPhotos == 0) {
            logger.info("All photos have been downloaded!");
            return;
        }

        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            // Check if we've hit the daily limit
            if (!apiKeyManager.hasAvailableKey()) {
                logger.info("All API keys have reached daily limit. Resuming tomorrow.");
                return;
            }

            List<Photo> photos = fetchPhotoPage(username, page);
            
            if (photos.isEmpty()) {
                hasMore = false;
                continue;
            }

            for (Photo photo : photos) {
                String fileName = String.format("%s_%s.jpg", username, photo.getId());
                File outputFile = new File(outputDir, fileName);
                
                // Check both state and file existence for robust incremental download
                if (state.getDownloadedPhotos().contains(photo.getId())) {
                    logger.debug("Skipping already downloaded photo (in state): {}", photo.getId());
                    continue;
                }
                
                if (outputFile.exists()) {
                    logger.info("Photo file exists but not in state, adding to state: {}", fileName);
                    state.getDownloadedPhotos().add(photo.getId());
                    saveState();
                    continue;
                }

                // Notify progress callback - photo started
                if (progressCallback != null) {
                    progressCallback.onPhotoStarted(photo.getId(), fileName, state.getDownloadedPhotos().size(), state.getTotalPhotos());
                }

                try {
                    downloadPhoto(photo, username);
                    state.getDownloadedPhotos().add(photo.getId());
                    saveState();
                    
                    // Notify progress callback - photo completed
                    if (progressCallback != null) {
                        progressCallback.onPhotoCompleted(photo.getId(), fileName, state.getDownloadedPhotos().size() - 1, state.getTotalPhotos());
                    }
                } catch (Exception e) {
                    logger.error("Failed to download photo: {}", photo.getId(), e);
                    
                    // Notify progress callback - error
                    if (progressCallback != null) {
                        progressCallback.onError(photo.getId(), e.getMessage());
                    }
                }

                logger.info("Progress: {}/{} photos downloaded (Total API usage: {}/{}, Available keys: {})", 
                    state.getDownloadedPhotos().size(), 
                    state.getTotalPhotos(),
                    apiKeyManager.getTotalHourlyUsage(),
                    apiKeyManager.getMaxHourlyLimit(),
                    apiKeyManager.getAvailableKeysCount());

                // Check rate limit after each download
                if (!apiKeyManager.hasAvailableKey()) {
                    logger.info("All API keys have reached hourly limit. Progress saved. Next reset: {}", apiKeyManager.getNextResetTime());
                    return;
                }
            }

            page++;
        }
    }

    private int getTotalPhotos(String username) throws IOException {
        String url = String.format("%s/users/%s", API_BASE_URL, username);
        
        String accessToken = apiKeyManager.getNextAvailableKey();
        if (accessToken == null) {
            throw new IOException("No API keys available - all have reached daily limit");
        }
        
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Client-ID " + accessToken)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get user info: " + response);
            }

            Map<String, Object> userInfo = objectMapper.readValue(
                response.body().string(),
                new TypeReference<Map<String, Object>>() {}
            );

            apiKeyManager.recordUsage(accessToken);

            // The total_photos field is directly in the user info object
            Object totalPhotos = userInfo.get("total_photos");
            if (totalPhotos instanceof Integer) {
                return (Integer) totalPhotos;
            } else {
                throw new IOException("Unexpected total_photos format in API response");
            }
        }
    }

    private List<Photo> fetchPhotoPage(String username, int page) throws IOException {
        String url = String.format("%s/users/%s/photos?page=%d&per_page=%d", 
                API_BASE_URL, username, page, PER_PAGE);

        String accessToken = apiKeyManager.getNextAvailableKey();
        if (accessToken == null) {
            throw new IOException("No API keys available - all have reached daily limit");
        }
        
        // Check for dummy keys at runtime
        if (isDummyKey(accessToken)) {
            throw new IOException("❌ Cannot download with dummy/test API key: '" + accessToken + 
                                "'. Please add your real Unsplash API key through the web interface at http://localhost:8099");
        }
        
        logger.info("Fetching photos with API key (length: {})", accessToken.length());
        
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Client-ID " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 403) {
                    // Mark this key as rate limited
                    apiKeyManager.markKeyRateLimited(accessToken);
                    
                    // Check if other keys are available
                    int availableKeys = apiKeyManager.getAvailableKeysCount();
                    if (availableKeys > 0) {
                        logger.warn("⚠️ Rate limit hit for current API key. Switching to next available key ({} remaining).", availableKeys);
                        // Recursively try with next available key
                        return fetchPhotoPage(username, page);
                    } else {
                        logger.warn("⚠️ All API keys have hit rate limits. Demo apps are limited to 50 requests/hour per key.");
                        logger.warn("⚠️ Next reset time: {}. Consider adding more API keys or waiting.", apiKeyManager.getNextResetTime());
                        throw new IOException("All API keys rate limited (403 Forbidden). Demo apps have 50 requests/hour limit per key. Next reset: " + apiKeyManager.getNextResetTime());
                    }
                } else {
                    logger.error("API request failed: {} - {} for URL: {}", response.code(), response.message(), url);
                    throw new IOException("Failed to fetch photos: " + response);
                }
            }

            apiKeyManager.recordUsage(accessToken);
            
            List<Photo> photos = objectMapper.readValue(
                    response.body().string(),
                    new TypeReference<List<Photo>>() {}
            );

            // Add delay between API calls
            Thread.sleep(1000);
            return photos;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }
    
    private boolean isDummyKey(String apiKey) {
        if (apiKey == null) {
            return true;
        }
        
        String key = apiKey.trim().toLowerCase();
        
        // Check for common dummy/test key patterns
        return key.contains("dummy") || 
               key.contains("test") || 
               key.contains("your_") || 
               key.contains("example") || 
               key.contains("placeholder") ||
               key.contains("sample") ||
               key.equals("your_api_key_here") ||
               key.equals("your_access_token_here") ||
               key.startsWith("dummy_test_key");
    }

    private void downloadPhoto(Photo photo, String username) throws IOException {
        String fileName = String.format("%s_%s.jpg", username, photo.getId());
        File outputFile = new File(outputDir, fileName);

        // Download the photo
        URL photoUrl = new URL(photo.getUrls().getFull());
        try (ReadableByteChannel rbc = Channels.newChannel(photoUrl.openStream());
            FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }

        // Add metadata to the downloaded photo
        try {
            metadataHandler.addMetadata(outputFile, photo);
        } catch (Exception e) {
            logger.error("Failed to add metadata to photo {}: {}", fileName, e.getMessage());
        }

        // Write description to the log file
        writeDescription(photo);
        
        // Save to database if service is available
        if (photoService != null) {
            try {
                photoService.savePhoto(photo, outputFile.getAbsolutePath(), username);
            } catch (Exception e) {
                logger.error("Failed to save photo to database {}: {}", fileName, e.getMessage());
            }
        }
    }

    private void writeDescription(Photo photo) {
        File descFile = descriptionsFile;
        try (PrintWriter writer = new PrintWriter(new FileWriter(descFile, true))) {
            writer.printf("Photo ID: %s%n", photo.getId());
            writer.printf("Description: %s%n", photo.getDescription());
            if (photo.getUser() != null) {
                writer.printf("Photographer: %s%n", photo.getUser().getName());
            }
            if (photo.getTags() != null && !photo.getTags().isEmpty()) {
                writer.printf("Tags: %s%n",
                    photo.getTags().stream()
                        .map(Photo.Tag::getTitle)
                        .collect(Collectors.joining(", "))
                );
            }
            writer.println("-------------------");
        } catch (IOException e) {
            logger.error("Failed to write description for photo {}", photo.getId());
        }
    }
    private void loadOrCreateState(String username) throws IOException {
        if (stateFile.exists()) {
            state = objectMapper.readValue(stateFile, DownloadState.class);
            if (!username.equals(state.getUsername())) {
                // If username changed, create new state
                state = new DownloadState();
                state.setUsername(username);
            }
        } else {
            state = new DownloadState();
            state.setUsername(username);
        }
    }

    private void saveState() throws IOException {
        state.setLastUpdate(LocalDateTime.now());
        objectMapper.writeValue(stateFile, state);
    }

    public ApiKeyManager getApiKeyManager() {
        return apiKeyManager;
    }
}
