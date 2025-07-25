package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.entity.PhotoEntity;
import de.dittnet.unsplashDownloader.model.CollectionStats;
import de.dittnet.unsplashDownloader.model.TagStats;
import de.dittnet.unsplashDownloader.model.UserSettings;
import de.dittnet.unsplashDownloader.service.PhotoService;
import de.dittnet.unsplashDownloader.service.CollectionStatsService;
import de.dittnet.unsplashDownloader.service.UserSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class WebController {
    
    @Autowired
    private PhotoService photoService;
    
    @Autowired
    private CollectionStatsService statsService;
    
    @Autowired
    private UserSettingsService userSettingsService;
    
    @Autowired
    private de.dittnet.unsplashDownloader.service.ApiKeyService apiKeyService;
    
    @GetMapping("/")
    public String index(Model model,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "24") int size,
                       @RequestParam(name = "search", required = false) String search,
                       @RequestParam(name = "photographer", required = false) String photographer,
                       @RequestParam(name = "tag", required = false) String tag,
                       @RequestParam(name = "color", required = false) String color) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("downloadDate").descending());
        Page<PhotoEntity> photos;
        
        if (search != null && !search.trim().isEmpty()) {
            photos = photoService.searchPhotos(search, pageable);
            model.addAttribute("searchQuery", search);
        } else if (photographer != null && !photographer.trim().isEmpty()) {
            photos = photoService.getPhotosByPhotographer(photographer, pageable);
            model.addAttribute("selectedPhotographer", photographer);
        } else if (tag != null && !tag.trim().isEmpty()) {
            photos = photoService.getPhotosByTag(tag, pageable);
            model.addAttribute("selectedTag", tag);
        } else if (color != null && !color.trim().isEmpty()) {
            photos = photoService.getPhotosByColor(color, pageable);
            model.addAttribute("selectedColor", color);
        } else {
            photos = photoService.getAllPhotos(pageable);
        }
        
        model.addAttribute("photos", photos);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", photos.getTotalPages());
        model.addAttribute("totalElements", photos.getTotalElements());
        
        // Add filter options - only load if needed for performance
        if (photographer == null && tag == null && search == null) {
            // Only load dropdown data for the main page, not for filtered views
            List<String> photographers = photoService.getAllPhotographers().stream()
                .limit(50) // Reduced limit for better performance
                .collect(Collectors.toList());
            List<String> tags = photoService.getAllTags().stream()
                .limit(100) // Reduced limit for better performance  
                .collect(Collectors.toList());
            
            model.addAttribute("photographers", photographers);
            model.addAttribute("tags", tags);
        } else {
            // For filtered views, provide empty lists to avoid expensive queries
            model.addAttribute("photographers", Collections.emptyList());
            model.addAttribute("tags", Collections.emptyList());
        }
        
        // Add collection statistics - use cached/lightweight version
        CollectionStats stats = getOptimizedCollectionStats();
        model.addAttribute("collectionStats", stats);
        
        return "index";
    }
    
    @GetMapping("/photo/{id}")
    public String photoDetail(@PathVariable("id") String id, Model model) {
        Optional<PhotoEntity> photo = photoService.getPhotoById(id);
        
        if (photo.isPresent()) {
            model.addAttribute("photo", photo.get());
            return "photo-detail";
        } else {
            return "redirect:/";
        }
    }
    
    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("totalPhotos", photoService.getTotalPhotosCount());
        
        // Limit for performance - full lists can be very large
        List<String> photographers = photoService.getAllPhotographers().stream()
            .limit(50) // Top 50 photographers for stats page
            .collect(Collectors.toList());
        
        // Get popular tags with counts for better display
        List<TagStats> popularTags = photoService.getPopularTags(100);
        
        // Also provide regular tags list for backward compatibility
        List<String> tags = popularTags.stream()
            .map(TagStats::getTagName)
            .collect(Collectors.toList());
            
        model.addAttribute("photographers", photographers);
        model.addAttribute("popularTags", popularTags);
        model.addAttribute("tags", tags);
        
        return "stats";
    }
    
    @GetMapping("/popular")
    public String popular(Model model) {
        
        List<PhotoEntity> photos = photoService.getTop100PhotosWithLikes();
        
        model.addAttribute("photos", photos);
        model.addAttribute("totalPhotos", photos.size());
        model.addAttribute("pageTitle", "Popular Photos - Top 100");
        
        return "popular";
    }
    
    @GetMapping("/settings")
    public String settings(Model model) {
        UserSettings settings = userSettingsService.getSettings();
        model.addAttribute("settings", settings);
        model.addAttribute("pageTitle", "Settings");
        
        return "settings";
    }
    
    @GetMapping("/api/collection-stats")
    @ResponseBody
    public CollectionStats getCollectionStats() {
        return statsService.getCollectionStats();
    }
    
    @GetMapping("/api/storage-details")
    @ResponseBody
    public Map<String, Object> getStorageDetails() {
        return statsService.calculateStorageStatsDetailed();
    }
    
    /**
     * Get optimized collection stats for home page - skips expensive storage calculation
     */
    private CollectionStats getOptimizedCollectionStats() {
        CollectionStats stats = new CollectionStats();
        
        try {
            // Fast operations only
            stats.setTotalPhotos((int) photoService.getTotalPhotosCount());
            stats.setTotalPhotographers(photoService.getAllPhotographers().stream()
                .limit(50) // Use the same limited list
                .collect(Collectors.toList()).size());
            stats.setTotalTags(photoService.getAllTags().stream()
                .limit(100) // Use the same limited list
                .collect(Collectors.toList()).size());
            
            // Skip expensive storage calculation - show placeholder
            stats.setTotalStorageBytes(0);
            stats.setTotalFiles(0);
            
            // Fast API key stats
            try {
                List<de.dittnet.unsplashDownloader.model.ApiKeyInfo> apiKeys = apiKeyService.getAllApiKeys();
                stats.setTotalApiKeys(apiKeys.size());
                
                int totalApiUsage = apiKeys.stream()
                    .mapToInt(de.dittnet.unsplashDownloader.model.ApiKeyInfo::getUsageCount)
                    .sum();
                stats.setTotalApiUsage(totalApiUsage);
                
                int totalApiLimit = apiKeys.stream()
                    .mapToInt(de.dittnet.unsplashDownloader.model.ApiKeyInfo::getHourlyLimit)
                    .sum();
                stats.setTotalApiLimit(totalApiLimit);
            } catch (Exception e) {
                // Fallback to basic stats
                stats.setTotalApiKeys(0);
                stats.setTotalApiUsage(0);
                stats.setTotalApiLimit(0);
            }
            
            stats.setLastUpdated(java.time.LocalDateTime.now());
            
        } catch (Exception e) {
            // Return empty stats on error
            stats = new CollectionStats();
        }
        
        return stats;
    }
}