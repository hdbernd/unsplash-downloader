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
            
            // Get all existing sync records in one query to avoid N+1 problem
            Set<String> existingPhotoIds = metadataSyncRepository.findAll().stream()
                .map(MetadataSyncEntity::getPhotoId)
                .collect(Collectors.toSet());
            logger.info("Found {} existing sync records", existingPhotoIds.size());
            
            List<MetadataSyncEntity> newEntries = new ArrayList<>();
            String userOutputPath = userSettingsService.getLastOutputPath();
            
            int processed = 0;
            for (PhotoEntity photo : allPhotos) {
                // Skip if sync record already exists
                if (!existingPhotoIds.contains(photo.getId())) {
                    // Determine file path
                    String filePath = getPhotoFilePath(photo, userOutputPath);
                    
                    // Create new sync entry - initialize with minimal data for speed
                    MetadataSyncEntity syncEntity = new MetadataSyncEntity(photo.getId(), filePath);
                    
                    // Quick file existence check only - skip expensive hash calculation for now
                    Path file = Paths.get(filePath);
                    if (Files.exists(file)) {
                        try {
                            syncEntity.setFileSize(Files.size(file));
                            syncEntity.setLastModified(LocalDateTime.now());
                            // Skip hash calculation during initialization for performance
                            // Hash will be calculated during actual sync process
                        } catch (Exception e) {
                            logger.warn("Failed to get file info for {}: {}", filePath, e.getMessage());
                        }
                    } else {
                        syncEntity.markAsSkipped("File not found: " + filePath);
                    }
                    
                    newEntries.add(syncEntity);
                }
                
                processed++;
                if (processed % 500 == 0) {
                    logger.info("Processed {}/{} photos for initialization", processed, allPhotos.size());
                }
            }
            
            // Batch save all new entries for much better performance
            if (!newEntries.isEmpty()) {
                logger.info("Saving {} new sync entries to database...", newEntries.size());
                metadataSyncRepository.saveAll(newEntries);
            }
            
            logger.info("Initialized {} new metadata sync entries", newEntries.size());
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
            // Get photo data from database with tags eagerly loaded
            Optional<PhotoEntity> photoOpt = photoService.getPhotoById(syncEntity.getPhotoId());
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
            
            // Calculate or update file hash if not present or file size changed
            boolean needsHashUpdate = syncEntity.getFileHash() == null;
            long currentFileSize = Files.size(filePath);
            if (syncEntity.getFileSize() == null || !syncEntity.getFileSize().equals(currentFileSize)) {
                needsHashUpdate = true;
                syncEntity.setFileSize(currentFileSize);
            }
            
            if (needsHashUpdate) {
                logger.debug("Calculating file hash for photo {}", photo.getId());
                String currentHash = calculateFileHash(filePath);
                if (syncEntity.getFileHash() != null && !syncEntity.getFileHash().equals(currentHash)) {
                    logger.info("File changed for photo {}, updating hash", photo.getId());
                }
                syncEntity.setFileHash(currentHash);
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
     * Get comprehensive sync statistics including overview totals
     */
    public Map<String, Object> getSyncStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Get total photos in database
            long totalPhotosInDatabase = photoService.getTotalPhotosCount();
            stats.put("totalPhotosInDatabase", totalPhotosInDatabase);
            
            // Get sync statistics from repository
            Object[] result = metadataSyncRepository.getSyncStatistics();
            if (result != null && result.length >= 6) {
                long totalInSync = ((Number) result[0]).longValue();
                long completed = ((Number) result[1]).longValue();
                long pending = ((Number) result[2]).longValue();
                long failed = ((Number) result[3]).longValue();
                long error = ((Number) result[4]).longValue();
                long skipped = ((Number) result[5]).longValue();
                
                stats.put("totalInSync", totalInSync);
                stats.put("completed", completed);
                stats.put("pending", pending);
                stats.put("failed", failed);
                stats.put("error", error);
                stats.put("skipped", skipped);
                
                // Calculate completion percentage based on sync entries
                stats.put("completionPercentage", totalInSync > 0 ? (completed * 100.0 / totalInSync) : 0.0);
                
                // Calculate photos not yet in sync system (newly downloaded)
                long photosNotInSync = totalPhotosInDatabase - totalInSync;
                stats.put("photosNotInSync", Math.max(0, photosNotInSync));
                
                // Photos due for syncing (pending + failed + error + not in sync system)
                long photosDueForSync = pending + failed + error + photosNotInSync;
                stats.put("photosDueForSync", photosDueForSync);
                
                // Overall progress (completed vs all photos in database)
                stats.put("overallCompletionPercentage", totalPhotosInDatabase > 0 ? 
                    (completed * 100.0 / totalPhotosInDatabase) : 0.0);
            } else {
                // No sync data available
                stats.put("totalInSync", 0L);
                stats.put("completed", 0L);
                stats.put("pending", 0L);
                stats.put("failed", 0L);
                stats.put("error", 0L);
                stats.put("skipped", 0L);
                stats.put("completionPercentage", 0.0);
                stats.put("photosNotInSync", totalPhotosInDatabase);
                stats.put("photosDueForSync", totalPhotosInDatabase);
                stats.put("overallCompletionPercentage", 0.0);
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
     * Add newly downloaded photos to sync system
     * This should be called after new photos are downloaded
     */
    @Transactional
    public void addNewPhotosToSync() {
        logger.info("Checking for new photos to add to metadata sync system...");
        
        try {
            // Get all photos from database
            List<PhotoEntity> allPhotos = photoService.getAllPhotosForSync();
            
            // Get existing sync record photo IDs
            Set<String> existingPhotoIds = metadataSyncRepository.findAll().stream()
                .map(MetadataSyncEntity::getPhotoId)
                .collect(Collectors.toSet());
            
            // Find photos not in sync system
            List<PhotoEntity> newPhotos = allPhotos.stream()
                .filter(photo -> !existingPhotoIds.contains(photo.getId()))
                .collect(Collectors.toList());
            
            if (newPhotos.isEmpty()) {
                logger.info("No new photos found to add to sync system");
                return;
            }
            
            logger.info("Found {} new photos to add to sync system", newPhotos.size());
            
            List<MetadataSyncEntity> newSyncEntries = new ArrayList<>();
            String userOutputPath = userSettingsService.getLastOutputPath();
            
            for (PhotoEntity photo : newPhotos) {
                String filePath = getPhotoFilePath(photo, userOutputPath);
                MetadataSyncEntity syncEntity = new MetadataSyncEntity(photo.getId(), filePath);
                
                // Quick file existence and size check
                Path file = Paths.get(filePath);
                if (Files.exists(file)) {
                    try {
                        syncEntity.setFileSize(Files.size(file));
                        syncEntity.setLastModified(LocalDateTime.now());
                    } catch (Exception e) {
                        logger.warn("Failed to get file info for {}: {}", filePath, e.getMessage());
                    }
                } else {
                    syncEntity.markAsSkipped("File not found: " + filePath);
                }
                
                newSyncEntries.add(syncEntity);
            }
            
            // Batch save new entries
            metadataSyncRepository.saveAll(newSyncEntries);
            logger.info("Added {} new photos to metadata sync system", newSyncEntries.size());
            
        } catch (Exception e) {
            logger.error("Failed to add new photos to sync system", e);
        }
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
    
    /**
     * Get sync status for a specific photo
     */
    public Map<String, Object> getPhotoSyncStatus(String photoId) {
        Map<String, Object> status = new HashMap<>();
        
        try {
            Optional<MetadataSyncEntity> syncEntity = metadataSyncRepository.findByPhotoId(photoId);
            
            if (syncEntity.isPresent()) {
                MetadataSyncEntity entity = syncEntity.get();
                status.put("photoId", photoId);
                status.put("syncStatus", entity.getSyncStatus().toString());
                status.put("syncedAt", entity.getSyncedAt());
                status.put("errorMessage", entity.getErrorMessage());
                status.put("retryCount", entity.getRetryCount());
                status.put("fileSize", entity.getFileSize());
                status.put("lastModified", entity.getLastModified());
                status.put("hasSyncRecord", true);
            } else {
                status.put("photoId", photoId);
                status.put("syncStatus", "NOT_IN_SYNC_SYSTEM");
                status.put("hasSyncRecord", false);
            }
            
        } catch (Exception e) {
            logger.error("Failed to get sync status for photo {}", photoId, e);
            status.put("error", "Failed to get sync status: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Read EXIF metadata from image file
     */
    public Map<String, Object> readExifData(String photoId) {
        Map<String, Object> exifData = new HashMap<>();
        
        try {
            // Get photo info to find file path
            Optional<PhotoEntity> photoOpt = photoService.getPhotoById(photoId);
            if (!photoOpt.isPresent()) {
                exifData.put("error", "Photo not found in database");
                return exifData;
            }
            
            PhotoEntity photo = photoOpt.get();
            String userOutputPath = userSettingsService.getLastOutputPath();
            String filePath = getPhotoFilePath(photo, userOutputPath);
            
            Path file = Paths.get(filePath);
            if (!Files.exists(file)) {
                exifData.put("error", "Image file not found: " + filePath);
                return exifData;
            }
            
            // Read image metadata
            ImageMetadata metadata = Imaging.getMetadata(file.toFile());
            exifData.put("hasMetadata", metadata != null);
            
            if (metadata instanceof JpegImageMetadata) {
                JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
                TiffImageMetadata exifMetadata = jpegMetadata.getExif();
                
                if (exifMetadata != null) {
                    // Extract common EXIF fields
                    Map<String, String> exifFields = new HashMap<>();
                    
                    // Description
                    try {
                        String[] descriptionArray = exifMetadata.getFieldValue(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
                        if (descriptionArray != null && descriptionArray.length > 0) {
                            exifFields.put("Image Description", descriptionArray[0]);
                        }
                    } catch (Exception e) { /* Field not present */ }
                    
                    // Artist
                    try {
                        String[] artistArray = exifMetadata.getFieldValue(TiffTagConstants.TIFF_TAG_ARTIST);
                        if (artistArray != null && artistArray.length > 0) {
                            exifFields.put("Artist", artistArray[0]);
                        }
                    } catch (Exception e) { /* Field not present */ }
                    
                    // Copyright
                    try {
                        String[] copyrightArray = exifMetadata.getFieldValue(TiffTagConstants.TIFF_TAG_COPYRIGHT);
                        if (copyrightArray != null && copyrightArray.length > 0) {
                            exifFields.put("Copyright", copyrightArray[0]);
                        }
                    } catch (Exception e) { /* Field not present */ }
                    
                    // Software
                    try {
                        String[] softwareArray = exifMetadata.getFieldValue(TiffTagConstants.TIFF_TAG_SOFTWARE);
                        if (softwareArray != null && softwareArray.length > 0) {
                            exifFields.put("Software", softwareArray[0]);
                        }
                    } catch (Exception e) { /* Field not present */ }
                    
                    // User Comment
                    try {
                        Object userCommentValue = exifMetadata.getFieldValue(ExifTagConstants.EXIF_TAG_USER_COMMENT);
                        if (userCommentValue != null) {
                            if (userCommentValue instanceof String[]) {
                                String[] userCommentArray = (String[]) userCommentValue;
                                if (userCommentArray.length > 0) {
                                    exifFields.put("User Comment", userCommentArray[0]);
                                }
                            } else {
                                exifFields.put("User Comment", userCommentValue.toString());
                            }
                        }
                    } catch (Exception e) { /* Field not present */ }
                    
                    exifData.put("exifFields", exifFields);
                    exifData.put("hasExifData", !exifFields.isEmpty());
                } else {
                    exifData.put("hasExifData", false);
                    exifData.put("message", "No EXIF data found in image");
                }
            } else {
                exifData.put("hasExifData", false);
                exifData.put("message", "Not a JPEG image or no metadata available");
            }
            
            // Add file info
            exifData.put("filePath", filePath);
            exifData.put("fileSize", Files.size(file));
            exifData.put("lastModified", Files.getLastModifiedTime(file).toString());
            
        } catch (Exception e) {
            logger.error("Failed to read EXIF data for photo {}", photoId, e);
            exifData.put("error", "Failed to read EXIF data: " + e.getMessage());
        }
        
        return exifData;
    }
}