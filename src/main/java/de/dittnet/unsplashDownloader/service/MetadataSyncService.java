package de.dittnet.unsplashDownloader.service;

import de.dittnet.unsplashDownloader.entity.MetadataSyncEntity;
import de.dittnet.unsplashDownloader.entity.PhotoEntity;
import de.dittnet.unsplashDownloader.entity.PhotoTagEntity;
import de.dittnet.unsplashDownloader.repository.MetadataSyncRepository;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class MetadataSyncService {
    private static final Logger logger = LoggerFactory.getLogger(MetadataSyncService.class);
    
    @Autowired
    private MetadataSyncRepository metadataSyncRepository;
    
    @Autowired
    private PhotoService photoService;
    
    @Autowired
    private UserSettingsService userSettingsService;
    
    private volatile boolean syncInProgress = false;
    private volatile boolean stopRequested = false;
    
    /**
     * Initialize metadata sync entries for all photos that don't have sync records yet
     */
    @Transactional
    public void initializeMetadataSync() {
        logger.info("Initializing metadata sync entries for untracked photos...");
        
        try {
            // Get all photos from database
            List<PhotoEntity> allPhotos = photoService.getAllPhotosForSync();
            logger.info("Found {} total photos in database", allPhotos.size());
            
            int newEntries = 0;
            String userOutputPath = userSettingsService.getLastOutputPath();
            
            for (PhotoEntity photo : allPhotos) {
                // Check if sync record already exists
                if (!metadataSyncRepository.findByPhotoId(photo.getId()).isPresent()) {
                    // Determine file path
                    String filePath = getPhotoFilePath(photo, userOutputPath);
                    
                    // Create new sync entry
                    MetadataSyncEntity syncEntity = new MetadataSyncEntity(photo.getId(), filePath);
                    
                    // Check if file exists and set initial state
                    Path file = Paths.get(filePath);
                    if (Files.exists(file)) {
                        try {
                            syncEntity.setFileSize(Files.size(file));
                            syncEntity.setLastModified(LocalDateTime.now());
                            syncEntity.setFileHash(calculateFileHash(file));
                        } catch (Exception e) {
                            logger.warn("Failed to get file info for {}: {}", filePath, e.getMessage());
                        }
                    } else {
                        syncEntity.markAsSkipped("File not found: " + filePath);
                    }
                    
                    metadataSyncRepository.save(syncEntity);
                    newEntries++;
                }
            }
            
            logger.info("Initialized {} new metadata sync entries", newEntries);
        } catch (Exception e) {
            logger.error("Failed to initialize metadata sync", e);
        }
    }
    
    /**
     * Sync metadata for photos that need updating
     */
    @Async
    public CompletableFuture<Void> syncMetadataAsync(int batchSize) {
        if (syncInProgress) {
            logger.warn("Metadata sync already in progress, skipping");
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            syncInProgress = true;
            stopRequested = false;
            
            logger.info("Starting metadata sync with batch size: {}", batchSize);
            
            List<MetadataSyncEntity> photosToSync = metadataSyncRepository
                .findPhotosNeedingSyncLimited(PageRequest.of(0, batchSize));
            
            logger.info("Found {} photos needing metadata sync", photosToSync.size());
            
            int processed = 0;
            int successful = 0;
            int failed = 0;
            int skipped = 0;
            
            for (MetadataSyncEntity syncEntity : photosToSync) {
                if (stopRequested) {
                    logger.info("Metadata sync stop requested, stopping at photo {}/{}", processed, photosToSync.size());
                    break;
                }
                
                try {
                    // Mark as in progress
                    syncEntity.markAsInProgress();
                    metadataSyncRepository.save(syncEntity);
                    
                    // Sync metadata for this photo
                    boolean result = syncPhotoMetadata(syncEntity);
                    
                    if (result) {
                        successful++;
                    } else {
                        failed++;
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to sync metadata for photo {}: {}", syncEntity.getPhotoId(), e.getMessage());
                    syncEntity.markAsFailed("Unexpected error: " + e.getMessage());
                    metadataSyncRepository.save(syncEntity);
                    failed++;
                }
                
                processed++;
                
                if (processed % 10 == 0) {
                    logger.info("Metadata sync progress: {}/{} processed", processed, photosToSync.size());
                }
            }
            
            logger.info("Metadata sync completed: {} processed, {} successful, {} failed, {} skipped", 
                processed, successful, failed, skipped);
            
        } finally {
            syncInProgress = false;
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Sync metadata for a single photo
     */
    @Transactional
    public boolean syncPhotoMetadata(MetadataSyncEntity syncEntity) {
        try {
            // Get photo data from database
            Optional<PhotoEntity> photoOpt = photoService.getPhotoByIdOptional(syncEntity.getPhotoId());
            if (!photoOpt.isPresent()) {
                syncEntity.markAsError("Photo not found in database");
                metadataSyncRepository.save(syncEntity);
                return false;
            }
            
            PhotoEntity photo = photoOpt.get();
            Path filePath = Paths.get(syncEntity.getFilePath());
            
            // Check if file exists
            if (!Files.exists(filePath)) {
                syncEntity.markAsSkipped("File not found: " + syncEntity.getFilePath());
                metadataSyncRepository.save(syncEntity);
                return false;
            }
            
            // Check if file has changed since last check
            String currentHash = calculateFileHash(filePath);
            if (syncEntity.getFileHash() != null && !syncEntity.getFileHash().equals(currentHash)) {
                logger.info("File changed for photo {}, updating hash", photo.getId());
                syncEntity.setFileHash(currentHash);
                syncEntity.setFileSize(Files.size(filePath));
            }
            
            // Only process JPEG files
            String fileName = filePath.getFileName().toString().toLowerCase();
            if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
                syncEntity.markAsSkipped("Unsupported file format: " + fileName);
                metadataSyncRepository.save(syncEntity);
                return false;
            }
            
            // Write metadata to EXIF
            boolean success = writeMetadataToExif(photo, filePath);
            
            if (success) {
                syncEntity.markAsCompleted();
                logger.debug("Successfully synced metadata for photo: {}", photo.getId());
            } else {
                syncEntity.markAsFailed("Failed to write EXIF metadata");
            }
            
            metadataSyncRepository.save(syncEntity);
            return success;
            
        } catch (Exception e) {
            logger.error("Error syncing metadata for photo {}: {}", syncEntity.getPhotoId(), e.getMessage());
            syncEntity.markAsFailed("Error: " + e.getMessage());
            metadataSyncRepository.save(syncEntity);
            return false;
        }
    }
    
    /**
     * Write photo metadata to EXIF data
     */
    private boolean writeMetadataToExif(PhotoEntity photo, Path filePath) {
        try {
            File imageFile = filePath.toFile();
            
            // Read existing metadata
            ImageMetadata metadata = Imaging.getMetadata(imageFile);
            TiffOutputSet outputSet = null;
            
            // If JPEG, get existing EXIF data
            if (metadata instanceof JpegImageMetadata) {
                JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
                TiffImageMetadata exif = jpegMetadata.getExif();
                if (exif != null) {
                    outputSet = exif.getOutputSet();
                }
            }
            
            // Create new output set if none exists
            if (outputSet == null) {
                outputSet = new TiffOutputSet();
            }
            
            // Get or create EXIF directory
            TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
            
            // Get or create root directory for TIFF tags
            TiffOutputDirectory rootDirectory = outputSet.getOrCreateRootDirectory();
            
            // Remove existing fields we want to overwrite
            rootDirectory.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
            exifDirectory.removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT);
            rootDirectory.removeField(TiffTagConstants.TIFF_TAG_ARTIST);
            rootDirectory.removeField(TiffTagConstants.TIFF_TAG_COPYRIGHT);
            
            // Add description
            if (photo.getDescription() != null && !photo.getDescription().trim().isEmpty()) {
                rootDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, photo.getDescription());
            }
            
            // Add tags as user comment
            if (photo.getTags() != null && !photo.getTags().isEmpty()) {
                String tagsString = photo.getTags().stream()
                    .map(PhotoTagEntity::getTagTitle)
                    .collect(Collectors.joining(", "));
                exifDirectory.add(ExifTagConstants.EXIF_TAG_USER_COMMENT, "Tags: " + tagsString);
            }
            
            // Add photographer
            if (photo.getPhotographerName() != null && !photo.getPhotographerName().trim().isEmpty()) {
                rootDirectory.add(TiffTagConstants.TIFF_TAG_ARTIST, photo.getPhotographerName());
            }
            
            // Add copyright info
            String copyright = "Photo by " + (photo.getPhotographerName() != null ? photo.getPhotographerName() : "Unknown") + 
                              " on Unsplash (https://unsplash.com/photos/" + photo.getId() + ")";
            rootDirectory.add(TiffTagConstants.TIFF_TAG_COPYRIGHT, copyright);
            
            // Create temporary file for writing
            Path tempFile = Files.createTempFile("metadata_sync_", ".jpg");
            
            try {
                // Write the image with new EXIF data
                try (FileOutputStream fos = new FileOutputStream(tempFile.toFile());
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    
                    new ExifRewriter().updateExifMetadataLossless(imageFile, bos, outputSet);
                }
                
                // Replace original file with updated file
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
                
                logger.debug("Successfully updated EXIF metadata for: {}", filePath);
                return true;
                
            } finally {
                // Clean up temp file if it still exists
                Files.deleteIfExists(tempFile);
            }
            
        } catch (ImageReadException e) {
            logger.error("Failed to read image metadata for {}: {}", filePath, e.getMessage());
            return false;
        } catch (ImageWriteException e) {
            logger.error("Failed to write image metadata for {}: {}", filePath, e.getMessage());
            return false;
        } catch (IOException e) {
            logger.error("IO error updating metadata for {}: {}", filePath, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error updating metadata for {}: {}", filePath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get file path for a photo
     */
    private String getPhotoFilePath(PhotoEntity photo, String userOutputPath) {
        // Extract filename from filePath
        String filename = Paths.get(photo.getFilePath()).getFileName().toString();
        
        if (userOutputPath != null && !userOutputPath.trim().isEmpty()) {
            return Paths.get(userOutputPath, "photos", filename).toString();
        }
        return Paths.get("./unsplash-data/photos", filename).toString();
    }
    
    /**
     * Calculate SHA-256 hash of file
     */
    private String calculateFileHash(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = Files.newInputStream(filePath);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            logger.warn("Failed to calculate file hash for {}: {}", filePath, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get sync statistics
     */
    public Map<String, Object> getSyncStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            Object[] result = metadataSyncRepository.getSyncStatistics();
            if (result != null && result.length >= 6) {
                stats.put("total", ((Number) result[0]).longValue());
                stats.put("completed", ((Number) result[1]).longValue());
                stats.put("pending", ((Number) result[2]).longValue());
                stats.put("failed", ((Number) result[3]).longValue());
                stats.put("error", ((Number) result[4]).longValue());
                stats.put("skipped", ((Number) result[5]).longValue());
                
                long total = ((Number) result[0]).longValue();
                long completed = ((Number) result[1]).longValue();
                stats.put("completionPercentage", total > 0 ? (completed * 100.0 / total) : 0.0);
            }
            
            stats.put("syncInProgress", syncInProgress);
            stats.put("needingSync", metadataSyncRepository.countPhotosNeedingSync());
            
        } catch (Exception e) {
            logger.error("Failed to get sync statistics", e);
            stats.put("error", "Failed to load statistics");
        }
        
        return stats;
    }
    
    /**
     * Stop ongoing sync operation
     */
    public void stopSync() {
        stopRequested = true;
        logger.info("Metadata sync stop requested");
    }
    
    /**
     * Check if sync is in progress
     */
    public boolean isSyncInProgress() {
        return syncInProgress;
    }
    
    /**
     * Reset all sync entries (mark as pending for re-sync)
     */
    @Transactional
    public void resetAllSyncEntries() {
        logger.info("Resetting all metadata sync entries to pending status");
        
        List<MetadataSyncEntity> allEntries = metadataSyncRepository.findAll();
        for (MetadataSyncEntity entry : allEntries) {
            entry.setSyncStatus(MetadataSyncEntity.SyncStatus.PENDING);
            entry.setErrorMessage(null);
            entry.setRetryCount(0);
            entry.setSyncedAt(null);
        }
        
        metadataSyncRepository.saveAll(allEntries);
        logger.info("Reset {} sync entries", allEntries.size());
    }
    
    /**
     * Clean up old completed entries
     */
    @Transactional
    public void cleanupOldEntries(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        metadataSyncRepository.deleteOldCompletedEntries(cutoffDate);
        logger.info("Cleaned up completed sync entries older than {} days", daysToKeep);
    }
}