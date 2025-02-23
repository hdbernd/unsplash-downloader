package de.dittnet.unsplashDownloader;

import de.dittnet.unsplashDownloader.model.Photo;
import de.dittnet.unsplashDownloader.model.DownloadState;
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
    
    private final String accessToken;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String outputDir;
    private final ImageMetadataHandler metadataHandler;
    private final File stateFile;
    private DownloadState state;

    public UnsplashDownloader(String accessToken, String outputDir) {
        this.accessToken = accessToken;
        this.outputDir = outputDir;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
                
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.metadataHandler = new ImageMetadataHandler();
        this.stateFile = new File(outputDir, "download_state.json");
        
        // Create output directory if it doesn't exist
        new File(outputDir).mkdirs();
    }

    public void downloadUserPhotos(String username) throws IOException {
        loadOrCreateState(username);
        
        // First, get total number of photos if not already known
        if (state.getTotalPhotos() == 0) {
            int totalPhotos = getTotalPhotos(username);
            state.setTotalPhotos(totalPhotos);
            saveState();
            logger.info("Total photos to download: {}", totalPhotos);
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
            if (shouldPauseDueToRateLimit()) {
                logger.info("Daily rate limit reached. Resuming tomorrow.");
                return;
            }

            List<Photo> photos = fetchPhotoPage(username, page);
            
            if (photos.isEmpty()) {
                hasMore = false;
                continue;
            }

            for (Photo photo : photos) {
                if (state.getDownloadedPhotos().contains(photo.getId())) {
                    logger.debug("Skipping already downloaded photo: {}", photo.getId());
                    continue;
                }

                downloadPhoto(photo, username);
                state.getDownloadedPhotos().add(photo.getId());
                saveState();

                logger.info("Progress: {}/{} photos downloaded", 
                    state.getDownloadedPhotos().size(), 
                    state.getTotalPhotos());

                // Check rate limit after each download
                if (shouldPauseDueToRateLimit()) {
                    logger.info("Daily rate limit reached. Progress saved. Resuming tomorrow.");
                    return;
                }
            }

            page++;
        }
    }

    private int getTotalPhotos(String username) throws IOException {
        String url = String.format("%s/users/%s", API_BASE_URL, username);
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

            incrementRequestCount();

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

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Client-ID " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch photos: " + response);
            }

            incrementRequestCount();
            
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
    }

    private void writeDescription(Photo photo) {
        File descFile = new File(outputDir, "descriptions.txt");
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

    private void incrementRequestCount() {
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(state.getRequestCountDate().toLocalDate())) {
            // Reset counter for new day
            state.setDailyRequestCount(0);
            state.setRequestCountDate(now);
        }
        state.setDailyRequestCount(state.getDailyRequestCount() + 1);
    }

    private boolean shouldPauseDueToRateLimit() {
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(state.getRequestCountDate().toLocalDate())) {
            // New day, reset counter
            state.setDailyRequestCount(0);
            state.setRequestCountDate(now);
            return false;
        }
        return state.getDailyRequestCount() >= MAX_DAILY_REQUESTS;
    }
}
