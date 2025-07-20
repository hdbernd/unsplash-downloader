package de.dittnet.unsplashDownloader.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "photos")
public class PhotoEntity {
    @Id
    private String id;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private String title;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "photographer_name")
    private String photographerName;
    
    @Column(name = "photographer_username")
    private String photographerUsername;
    
    @Column(name = "download_date")
    private LocalDateTime downloadDate;
    
    @Column(name = "unsplash_url")
    private String unsplashUrl;
    
    @Column(name = "image_width")
    private Integer imageWidth;
    
    @Column(name = "image_height")
    private Integer imageHeight;
    
    @Column(name = "color")
    private String color;
    
    @Column(name = "likes")
    private Integer likes;
    
    @OneToMany(mappedBy = "photo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PhotoTagEntity> tags;
    
    // Default constructor
    public PhotoEntity() {}
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getPhotographerName() {
        return photographerName;
    }
    
    public void setPhotographerName(String photographerName) {
        this.photographerName = photographerName;
    }
    
    public String getPhotographerUsername() {
        return photographerUsername;
    }
    
    public void setPhotographerUsername(String photographerUsername) {
        this.photographerUsername = photographerUsername;
    }
    
    public LocalDateTime getDownloadDate() {
        return downloadDate;
    }
    
    public void setDownloadDate(LocalDateTime downloadDate) {
        this.downloadDate = downloadDate;
    }
    
    public String getUnsplashUrl() {
        return unsplashUrl;
    }
    
    public void setUnsplashUrl(String unsplashUrl) {
        this.unsplashUrl = unsplashUrl;
    }
    
    public Integer getImageWidth() {
        return imageWidth;
    }
    
    public void setImageWidth(Integer imageWidth) {
        this.imageWidth = imageWidth;
    }
    
    public Integer getImageHeight() {
        return imageHeight;
    }
    
    public void setImageHeight(Integer imageHeight) {
        this.imageHeight = imageHeight;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }
    
    public Integer getLikes() {
        return likes;
    }
    
    public void setLikes(Integer likes) {
        this.likes = likes;
    }
    
    public Set<PhotoTagEntity> getTags() {
        return tags;
    }
    
    public void setTags(Set<PhotoTagEntity> tags) {
        this.tags = tags;
    }
}