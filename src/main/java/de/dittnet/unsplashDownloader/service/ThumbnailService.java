package de.dittnet.unsplashDownloader.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ThumbnailService {
    
    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    private static final int THUMBNAIL_SIZE = 300;
    
    @Value("${app.photos.thumbnails-path:./data/thumbnails}")
    private String thumbnailsPath;
    
    public File generateThumbnail(String photoId, File originalFile) {
        try {
            // Create thumbnails directory if it doesn't exist
            Path thumbnailDir = Paths.get(thumbnailsPath);
            if (!Files.exists(thumbnailDir)) {
                Files.createDirectories(thumbnailDir);
            }
            
            // Generate thumbnail file path
            File thumbnailFile = new File(thumbnailDir.toFile(), photoId + "_thumb.jpg");
            
            // Check if thumbnail already exists
            if (thumbnailFile.exists()) {
                return thumbnailFile;
            }
            
            // Read original image
            BufferedImage originalImage = ImageIO.read(originalFile);
            if (originalImage == null) {
                logger.error("Failed to read image: {}", originalFile.getPath());
                return null;
            }
            
            // Calculate thumbnail dimensions
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            int thumbnailWidth, thumbnailHeight;
            
            if (originalWidth > originalHeight) {
                thumbnailWidth = THUMBNAIL_SIZE;
                thumbnailHeight = (int) ((double) originalHeight / originalWidth * THUMBNAIL_SIZE);
            } else {
                thumbnailHeight = THUMBNAIL_SIZE;
                thumbnailWidth = (int) ((double) originalWidth / originalHeight * THUMBNAIL_SIZE);
            }
            
            // Create thumbnail image
            BufferedImage thumbnailImage = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = thumbnailImage.createGraphics();
            
            // Set rendering hints for better quality
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw scaled image
            g2d.drawImage(originalImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
            g2d.dispose();
            
            // Save thumbnail
            if (ImageIO.write(thumbnailImage, "jpg", thumbnailFile)) {
                logger.debug("Generated thumbnail for photo {}: {}", photoId, thumbnailFile.getPath());
                return thumbnailFile;
            } else {
                logger.error("Failed to write thumbnail for photo {}", photoId);
                return null;
            }
            
        } catch (IOException e) {
            logger.error("Error generating thumbnail for photo {}: {}", photoId, e.getMessage());
            return null;
        }
    }
    
    public File getThumbnail(String photoId) {
        File thumbnailFile = new File(thumbnailsPath, photoId + "_thumb.jpg");
        return thumbnailFile.exists() ? thumbnailFile : null;
    }
    
    public boolean deleteThumbnail(String photoId) {
        File thumbnailFile = new File(thumbnailsPath, photoId + "_thumb.jpg");
        if (thumbnailFile.exists()) {
            boolean deleted = thumbnailFile.delete();
            if (deleted) {
                logger.debug("Deleted thumbnail for photo {}", photoId);
            } else {
                logger.error("Failed to delete thumbnail for photo {}", photoId);
            }
            return deleted;
        }
        return true; // Consider it successful if file doesn't exist
    }
    
    public void cleanupOrphanedThumbnails() {
        // This method could be called periodically to remove thumbnails
        // for photos that no longer exist in the database
        try {
            Path thumbnailDir = Paths.get(thumbnailsPath);
            if (Files.exists(thumbnailDir)) {
                Files.list(thumbnailDir)
                    .filter(path -> path.toString().endsWith("_thumb.jpg"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String photoId = filename.substring(0, filename.lastIndexOf("_thumb.jpg"));
                        
                        // Note: This would need PhotoService injection to check if photo exists
                        // For now, we'll just log the potential cleanup
                        logger.debug("Found thumbnail for photo ID: {}", photoId);
                    });
            }
        } catch (IOException e) {
            logger.error("Error during thumbnail cleanup: {}", e.getMessage());
        }
    }
}