package de.dittnet.unsplashDownloader.controller;

import de.dittnet.unsplashDownloader.entity.PhotoEntity;
import de.dittnet.unsplashDownloader.model.CollectionStats;
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

import java.util.List;
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
        
        // Add filter options - limit for performance
        List<String> photographers = photoService.getAllPhotographers().stream()
            .limit(100) // Limit to top 100 photographers for performance
            .collect(Collectors.toList());
        List<String> tags = photoService.getAllTags().stream()
            .limit(200) // Limit to top 200 tags for performance  
            .collect(Collectors.toList());
        
        model.addAttribute("photographers", photographers);
        model.addAttribute("tags", tags);
        
        // Add collection statistics
        CollectionStats stats = statsService.getCollectionStats();
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
        List<String> tags = photoService.getAllTags().stream()
            .limit(100) // Top 100 tags for stats page
            .collect(Collectors.toList());
            
        model.addAttribute("photographers", photographers);
        model.addAttribute("tags", tags);
        
        return "stats";
    }
    
    @GetMapping("/popular")
    public String popular(Model model,
                         @RequestParam(name = "page", defaultValue = "0") int page,
                         @RequestParam(name = "size", defaultValue = "24") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PhotoEntity> photos = photoService.getPhotosByLikes(pageable);
        
        model.addAttribute("photos", photos);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", photos.getTotalPages());
        model.addAttribute("totalElements", photos.getTotalElements());
        model.addAttribute("pageTitle", "Popular Photos");
        
        return "popular";
    }
    
    @GetMapping("/settings")
    public String settings(Model model) {
        UserSettings settings = userSettingsService.getSettings();
        model.addAttribute("settings", settings);
        model.addAttribute("pageTitle", "Settings");
        
        return "settings";
    }
}