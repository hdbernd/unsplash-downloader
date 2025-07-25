package de.dittnet.unsplashDownloader.service;

import de.dittnet.unsplashDownloader.entity.PhotoEntity;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ExifViewerService {
    private static final Logger logger = LoggerFactory.getLogger(ExifViewerService.class);
    
    @Autowired
    private PhotoService photoService;
    
    @Autowired
    private UserSettingsService userSettingsService;
    
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
                    
                    // Camera and technical details
                    try {
                        String[] makeArray = exifMetadata.getFieldValue(TiffTagConstants.TIFF_TAG_MAKE);
                        if (makeArray != null && makeArray.length > 0) {
                            exifFields.put("Camera Make", makeArray[0]);
                        }
                    } catch (Exception e) { /* Field not present */ }
                    
                    try {
                        String[] modelArray = exifMetadata.getFieldValue(TiffTagConstants.TIFF_TAG_MODEL);
                        if (modelArray != null && modelArray.length > 0) {
                            exifFields.put("Camera Model", modelArray[0]);
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
}