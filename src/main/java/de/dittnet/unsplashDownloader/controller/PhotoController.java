package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.ApiKeyManager;
import de.dittnet.unsplashDownloader.entity.PhotoEntity;
import de.dittnet.unsplashDownloader.model.Photo;
import de.dittnet.unsplashDownloader.service.PhotoService;
import de.dittnet.unsplashDownloader.service.ThumbnailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    private static final Logger logger = LoggerFactory.getLogger(PhotoController.class);
    private static final String API_BASE_URL = "https://api.unsplash.com";
    
    @Autowired
    private PhotoService photoService;
    
    @Autowired
    private ThumbnailService thumbnailService;
    
    @Autowired
    private ApiKeyManager apiKeyManager;
    
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    
    public PhotoController() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    // Helper method to validate pagination parameters
    private void validatePaginationParams(int[] page, int[] size) {
        if (page[0] < 0) {
            page[0] = 0;
        }
        if (size[0] < 1) {
            size[0] = 20;
        }
    }
    
    @GetMapping
    public ResponseEntity<Page<PhotoEntity>> getAllPhotos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "downloadDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : 
            Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PhotoEntity> photos = photoService.getAllPhotos(pageable);
        
        return ResponseEntity.ok(photos);
    }
    
    @GetMapping("/search")
    public ResponseEntity<Page<PhotoEntity>> searchPhotos(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("downloadDate").descending());
        Page<PhotoEntity> photos = photoService.searchPhotos(query, pageable);
        
        return ResponseEntity.ok(photos);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<PhotoEntity> getPhoto(@PathVariable String id) {
        Optional<PhotoEntity> photo = photoService.getPhotoById(id);
        
        if (photo.isPresent()) {
            return ResponseEntity.ok(photo.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getPhotoImage(@PathVariable String id) {
        File photoFile = photoService.getPhotoFile(id);
        
        if (photoFile != null && photoFile.exists()) {
            try {
                byte[] imageBytes = Files.readAllBytes(photoFile.toPath());
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(imageBytes);
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<byte[]> getPhotoThumbnail(@PathVariable String id) {
        // First try to get existing thumbnail
        File thumbnailFile = thumbnailService.getThumbnail(id);
        
        if (thumbnailFile == null) {
            // Generate thumbnail if it doesn't exist
            File originalFile = photoService.getPhotoFile(id);
            if (originalFile != null) {
                thumbnailFile = thumbnailService.generateThumbnail(id, originalFile);
            }
        }
        
        if (thumbnailFile != null && thumbnailFile.exists()) {
            try {
                byte[] imageBytes = Files.readAllBytes(thumbnailFile.toPath());
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(imageBytes);
            } catch (IOException e) {
                // Fall back to original image if thumbnail fails
                return getPhotoImage(id);
            }
        } else {
            // Fall back to original image if thumbnail doesn't exist
            return getPhotoImage(id);
        }
    }
    
    @GetMapping("/photographer/{photographer}")
    public ResponseEntity<Page<PhotoEntity>> getPhotosByPhotographer(
            @PathVariable String photographer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // Validate pagination parameters
        int[] pageArray = {page};
        int[] sizeArray = {size};
        validatePaginationParams(pageArray, sizeArray);
        page = pageArray[0];
        size = sizeArray[0];
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("downloadDate").descending());
        Page<PhotoEntity> photos = photoService.getPhotosByPhotographer(photographer, pageable);
        
        return ResponseEntity.ok(photos);
    }
    
    @GetMapping("/tag/{tag}")
    public ResponseEntity<Page<PhotoEntity>> getPhotosByTag(
            @PathVariable String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("downloadDate").descending());
        Page<PhotoEntity> photos = photoService.getPhotosByTag(tag, pageable);
        
        return ResponseEntity.ok(photos);
    }
    
    @GetMapping("/color/{color}")
    public ResponseEntity<Page<PhotoEntity>> getPhotosByColor(
            @PathVariable String color,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("downloadDate").descending());
        Page<PhotoEntity> photos = photoService.getPhotosByColor(color, pageable);
        
        return ResponseEntity.ok(photos);
    }
    
    @GetMapping("/popular")
    public ResponseEntity<Page<PhotoEntity>> getPopularPhotos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PhotoEntity> photos = photoService.getPhotosByLikes(pageable);
        
        return ResponseEntity.ok(photos);
    }
    
    @GetMapping("/photographers")
    public ResponseEntity<List<String>> getAllPhotographers() {
        List<String> photographers = photoService.getAllPhotographers();
        return ResponseEntity.ok(photographers);
    }
    
    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags() {
        List<String> tags = photoService.getAllTags();
        return ResponseEntity.ok(tags);
    }
    
    @PostMapping("/{id}/refresh-tags")
    public ResponseEntity<Map<String, Object>> refreshPhotoTags(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if photo exists in our database
            Optional<PhotoEntity> photoOpt = photoService.getPhotoById(id);
            if (!photoOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Photo not found in database");
                return ResponseEntity.notFound().build();
            }
            
            // Fetch fresh photo data from Unsplash API
            Photo photoData = fetchPhotoFromUnsplash(id);
            if (photoData == null) {
                response.put("success", false);
                response.put("message", "Failed to fetch photo data from Unsplash API. Please ensure you have a valid API key configured in the API Keys page.");
                return ResponseEntity.status(500).body(response);
            }
            
            // Update tags in database
            photoService.updatePhotoTags(id, photoData.getTags());
            
            int tagCount = photoData.getTags() != null ? photoData.getTags().size() : 0;
            response.put("success", true);
            response.put("message", "Successfully refreshed " + tagCount + " tags for photo " + id);
            response.put("tagCount", tagCount);
            
            logger.info("Refreshed {} tags for photo {}", tagCount, id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to refresh tags for photo {}", id, e);
            response.put("success", false);
            response.put("message", "Failed to refresh tags: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/refresh-missing-tags")
    public ResponseEntity<Map<String, Object>> refreshMissingTags() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get all photos without tags
            List<PhotoEntity> photosWithoutTags = photoService.getPhotosWithoutTags();
            
            if (photosWithoutTags.isEmpty()) {
                response.put("success", true);
                response.put("message", "All photos already have tags");
                response.put("processedCount", 0);
                return ResponseEntity.ok(response);
            }
            
            // Check if we have any API keys available
            if (!apiKeyManager.hasAvailableKey()) {
                response.put("success", false);
                response.put("message", "No API keys available for fetching photo metadata. Please configure a valid Unsplash API key in the API Keys page.");
                return ResponseEntity.status(500).body(response);
            }
            
            int successCount = 0;
            int errorCount = 0;
            
            for (PhotoEntity photo : photosWithoutTags) {
                try {
                    // Fetch fresh photo data from Unsplash API
                    Photo photoData = fetchPhotoFromUnsplash(photo.getId());
                    if (photoData != null && photoData.getTags() != null && !photoData.getTags().isEmpty()) {
                        photoService.updatePhotoTags(photo.getId(), photoData.getTags());
                        successCount++;
                        logger.info("Refreshed {} tags for photo {}", photoData.getTags().size(), photo.getId());
                    } else {
                        logger.warn("No tags found for photo {} from Unsplash API", photo.getId());
                    }
                    
                    // Add delay to avoid hitting rate limits too quickly
                    Thread.sleep(500);
                    
                } catch (Exception e) {
                    logger.error("Failed to refresh tags for photo {}", photo.getId(), e);
                    errorCount++;
                }
                
                // Check if we should stop due to rate limits
                if (!apiKeyManager.hasAvailableKey()) {
                    logger.warn("Stopping tag refresh due to rate limit. Processed {}/{} photos", 
                               successCount + errorCount, photosWithoutTags.size());
                    break;
                }
            }
            
            response.put("success", true);
            response.put("message", String.format("Processed %d photos. Success: %d, Errors: %d", 
                                                 successCount + errorCount, successCount, errorCount));
            response.put("totalPhotosWithoutTags", photosWithoutTags.size());
            response.put("successCount", successCount);
            response.put("errorCount", errorCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to refresh missing tags", e);
            response.put("success", false);
            response.put("message", "Failed to refresh missing tags: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/stats")
    public ResponseEntity<PhotoStats> getStats() {
        PhotoStats stats = new PhotoStats();
        stats.setTotalPhotos(photoService.getTotalPhotosCount());
        stats.setTotalPhotographers(photoService.getAllPhotographers().size());
        stats.setTotalTags(photoService.getAllTags().size());
        
        return ResponseEntity.ok(stats);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePhoto(@PathVariable String id) {
        if (photoService.photoExists(id)) {
            photoService.deletePhoto(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    public static class PhotoStats {
        private long totalPhotos;
        private long totalPhotographers;
        private long totalTags;
        
        public long getTotalPhotos() {
            return totalPhotos;
        }
        
        public void setTotalPhotos(long totalPhotos) {
            this.totalPhotos = totalPhotos;
        }
        
        public long getTotalPhotographers() {
            return totalPhotographers;
        }
        
        public void setTotalPhotographers(long totalPhotographers) {
            this.totalPhotographers = totalPhotographers;
        }
        
        public long getTotalTags() {
            return totalTags;
        }
        
        public void setTotalTags(long totalTags) {
            this.totalTags = totalTags;
        }
    }
    
    private Photo fetchPhotoFromUnsplash(String photoId) {
        try {
            String url = String.format("%s/photos/%s", API_BASE_URL, photoId);
            
            String accessToken = apiKeyManager.getNextAvailableKey();
            if (accessToken == null) {
                logger.error("No API keys available for fetching photo metadata. Please configure a valid Unsplash API key via the API Keys page at http://localhost:8099/api-keys");
                return null;
            }
            
            logger.info("Fetching photo metadata for {} using API key (length: {}) from URL: {}", 
                       photoId, accessToken.length(), url);
            
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Client-ID " + accessToken)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                logger.info("API response for photo {}: {} - {}", photoId, response.code(), response.message());
                
                if (!response.isSuccessful()) {
                    String responseBody = "";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "";
                    } catch (Exception e) {
                        logger.warn("Could not read response body: {}", e.getMessage());
                    }
                    
                    if (response.code() == 403) {
                        apiKeyManager.markKeyRateLimited(accessToken);
                        logger.warn("Rate limit hit while fetching photo metadata for {}. Response body: {}", photoId, responseBody);
                    } else if (response.code() == 401) {
                        logger.error("401 Unauthorized for photo {} with API key length {}. Response body: {}", 
                                   photoId, accessToken.length(), responseBody);
                        logger.error("Full API key (masked): {}***{}", 
                                   accessToken.substring(0, Math.min(8, accessToken.length())),
                                   accessToken.length() > 8 ? accessToken.substring(accessToken.length() - 4) : "");
                    } else {
                        logger.error("Failed to fetch photo metadata for {}: {} - {}. Response body: {}", 
                                   photoId, response.code(), response.message(), responseBody);
                    }
                    return null;
                }
                
                apiKeyManager.recordUsage(accessToken);
                
                String responseBody = response.body().string();
                Photo photo = objectMapper.readValue(responseBody, Photo.class);
                
                logger.debug("Successfully fetched metadata for photo {} with {} tags", 
                           photoId, photo.getTags() != null ? photo.getTags().size() : 0);
                
                return photo;
            }
        } catch (Exception e) {
            logger.error("Exception while fetching photo metadata for {}", photoId, e);
            return null;
        }
    }
}