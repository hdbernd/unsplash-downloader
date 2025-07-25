package de.dittnet.unsplashDownloader.repository;

import de.dittnet.unsplashDownloader.entity.PhotoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhotoRepository extends JpaRepository<PhotoEntity, String> {
    
    // Find photos by photographer
    Page<PhotoEntity> findByPhotographerNameContainingIgnoreCase(String photographerName, Pageable pageable);
    
    // Find photos by description
    Page<PhotoEntity> findByDescriptionContainingIgnoreCase(String description, Pageable pageable);
    
    // Find photos by title
    Page<PhotoEntity> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    
    // Search across multiple fields
    @Query("SELECT DISTINCT p FROM PhotoEntity p LEFT JOIN p.tags t WHERE " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.photographerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.tagTitle) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<PhotoEntity> searchPhotos(@Param("search") String search, Pageable pageable);
    
    // Find photos by tag
    @Query("SELECT DISTINCT p FROM PhotoEntity p JOIN p.tags t WHERE " +
           "LOWER(t.tagTitle) LIKE LOWER(CONCAT('%', :tag, '%'))")
    Page<PhotoEntity> findByTag(@Param("tag") String tag, Pageable pageable);
    
    // Count photos by photographer
    long countByPhotographerName(String photographerName);
    
    // Get all unique photographers
    @Query("SELECT DISTINCT p.photographerName FROM PhotoEntity p WHERE p.photographerName IS NOT NULL ORDER BY p.photographerName")
    List<String> findAllPhotographers();
    
    // Get all unique tags
    @Query("SELECT DISTINCT t.tagTitle FROM PhotoTagEntity t ORDER BY t.tagTitle")
    List<String> findAllTags();
    
    // Get popular tags with count (top tags by frequency)
    @Query("SELECT t.tagTitle, COUNT(t) as tagCount FROM PhotoTagEntity t GROUP BY t.tagTitle ORDER BY tagCount DESC, t.tagTitle ASC")
    List<Object[]> findPopularTagsWithCount();
    
    // Get photo by ID with tags loaded
    @Query("SELECT p FROM PhotoEntity p LEFT JOIN FETCH p.tags WHERE p.id = :id")
    Optional<PhotoEntity> findByIdWithTags(@Param("id") String id);
    
    // Get photos with color
    Page<PhotoEntity> findByColorContainingIgnoreCase(String color, Pageable pageable);
    
    // Get photos ordered by likes
    Page<PhotoEntity> findAllByOrderByLikesDesc(Pageable pageable);
    
    // Get top photos with likes (non-null and > 0) ordered by likes desc
    @Query("SELECT p FROM PhotoEntity p WHERE p.likes IS NOT NULL AND p.likes > 0 ORDER BY p.likes DESC")
    List<PhotoEntity> findTop100PhotosWithLikes(Limit limit);
    
    // Get photos without any tags
    @Query("SELECT p FROM PhotoEntity p WHERE p.tags IS EMPTY OR SIZE(p.tags) = 0")
    List<PhotoEntity> findPhotosWithoutTags();
}