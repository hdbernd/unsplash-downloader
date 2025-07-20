package de.dittnet.unsplashDownloader.service;

import de.dittnet.unsplashDownloader.entity.PhotoEntity;
import de.dittnet.unsplashDownloader.entity.PhotoTagEntity;
import de.dittnet.unsplashDownloader.model.Photo;
import de.dittnet.unsplashDownloader.repository.PhotoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class PhotoService {
    
    @Autowired
    private PhotoRepository photoRepository;
    
    public void savePhoto(Photo photo, String filePath, String username) {
        PhotoEntity entity = new PhotoEntity();
        entity.setId(photo.getId());
        entity.setDescription(photo.getDescription());
        entity.setTitle(photo.getTitle());
        entity.setFilePath(filePath);
        entity.setDownloadDate(LocalDateTime.now());
        
        if (photo.getUser() != null) {
            entity.setPhotographerName(photo.getUser().getName());
            entity.setPhotographerUsername(photo.getUser().getUsername());
        }
        
        if (photo.getUrls() != null) {
            entity.setUnsplashUrl(photo.getUrls().getFull());
        }
        
        entity.setImageWidth(photo.getWidth());
        entity.setImageHeight(photo.getHeight());
        entity.setColor(photo.getColor());
        entity.setLikes(photo.getLikes());
        
        // Save the photo first
        photoRepository.save(entity);
        
        // Save tags
        if (photo.getTags() != null && !photo.getTags().isEmpty()) {
            Set<PhotoTagEntity> tagEntities = new HashSet<>();
            for (Photo.Tag tag : photo.getTags()) {
                PhotoTagEntity tagEntity = new PhotoTagEntity(entity, tag.getTitle(), tag.getType());
                tagEntities.add(tagEntity);
            }
            entity.setTags(tagEntities);
            photoRepository.save(entity);
        }
    }
    
    public Page<PhotoEntity> getAllPhotos(Pageable pageable) {
        return photoRepository.findAll(pageable);
    }
    
    public Page<PhotoEntity> searchPhotos(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return photoRepository.findAll(pageable);
        }
        return photoRepository.searchPhotos(search.trim(), pageable);
    }
    
    public Page<PhotoEntity> getPhotosByPhotographer(String photographer, Pageable pageable) {
        return photoRepository.findByPhotographerNameContainingIgnoreCase(photographer, pageable);
    }
    
    public Page<PhotoEntity> getPhotosByTag(String tag, Pageable pageable) {
        return photoRepository.findByTag(tag, pageable);
    }
    
    public Page<PhotoEntity> getPhotosByColor(String color, Pageable pageable) {
        return photoRepository.findByColorContainingIgnoreCase(color, pageable);
    }
    
    public Page<PhotoEntity> getPhotosByLikes(Pageable pageable) {
        return photoRepository.findAllByOrderByLikesDesc(pageable);
    }
    
    public Optional<PhotoEntity> getPhotoById(String id) {
        return photoRepository.findById(id);
    }
    
    public List<String> getAllPhotographers() {
        return photoRepository.findAllPhotographers();
    }
    
    public List<String> getAllTags() {
        return photoRepository.findAllTags();
    }
    
    public long getTotalPhotosCount() {
        return photoRepository.count();
    }
    
    public long getPhotosCountByPhotographer(String photographer) {
        return photoRepository.countByPhotographerName(photographer);
    }
    
    public boolean photoExists(String id) {
        return photoRepository.existsById(id);
    }
    
    public void deletePhoto(String id) {
        photoRepository.deleteById(id);
    }
    
    public File getPhotoFile(String id) {
        Optional<PhotoEntity> photo = photoRepository.findById(id);
        if (photo.isPresent() && photo.get().getFilePath() != null) {
            File file = new File(photo.get().getFilePath());
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }
}