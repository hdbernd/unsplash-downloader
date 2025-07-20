package de.dittnet.unsplashDownloader.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "photo_tags")
public class PhotoTagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id")
    private PhotoEntity photo;
    
    @Column(name = "tag_title")
    private String tagTitle;
    
    @Column(name = "tag_type")
    private String tagType;
    
    // Default constructor
    public PhotoTagEntity() {}
    
    public PhotoTagEntity(PhotoEntity photo, String tagTitle, String tagType) {
        this.photo = photo;
        this.tagTitle = tagTitle;
        this.tagType = tagType;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public PhotoEntity getPhoto() {
        return photo;
    }
    
    public void setPhoto(PhotoEntity photo) {
        this.photo = photo;
    }
    
    public String getTagTitle() {
        return tagTitle;
    }
    
    public void setTagTitle(String tagTitle) {
        this.tagTitle = tagTitle;
    }
    
    public String getTagType() {
        return tagType;
    }
    
    public void setTagType(String tagType) {
        this.tagType = tagType;
    }
}