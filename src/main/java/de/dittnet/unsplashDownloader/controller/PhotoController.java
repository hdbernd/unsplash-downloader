package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.entity.PhotoEntity;
import de.dittnet.unsplashDownloader.service.PhotoService;
import de.dittnet.unsplashDownloader.service.ThumbnailService;
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
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    
    @Autowired
    private PhotoService photoService;
    
    @Autowired
    private ThumbnailService thumbnailService;
    
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
}