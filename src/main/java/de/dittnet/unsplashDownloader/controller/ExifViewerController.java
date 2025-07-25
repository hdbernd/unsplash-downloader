package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.service.ExifViewerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/exif")
public class ExifViewerController {
    private static final Logger logger = LoggerFactory.getLogger(ExifViewerController.class);
    
    @Autowired
    private ExifViewerService exifViewerService;
    
    /**
     * Get EXIF data for specific photo
     */
    @GetMapping("/photo/{photoId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPhotoExifData(@PathVariable String photoId) {
        try {
            Map<String, Object> exifData = exifViewerService.readExifData(photoId);
            return ResponseEntity.ok(exifData);
            
        } catch (Exception e) {
            logger.error("Failed to get EXIF data for photo {}", photoId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to read EXIF data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}