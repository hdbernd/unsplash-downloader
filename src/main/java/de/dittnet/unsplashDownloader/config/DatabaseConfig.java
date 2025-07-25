package de.dittnet.unsplashDownloader.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.dittnet.unsplashDownloader.service.DatabaseSyncService;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    
    @Autowired
    private StorageConfig storageConfig;
    
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        String databasePath = storageConfig.getDatabasePath();
        String jdbcUrl;
        
        // Always use local database with network synchronization for reliability
        if (databasePath.startsWith("/Volumes/")) {
            // Initialize local database from network BEFORE creating datasource
            DatabaseSyncService.initializeDatabaseFromNetwork(databasePath);
            
            // Use local database with automatic sync to network drive
            String localDbPath = "./unsplash-data/database/unsplash_photos";
            jdbcUrl = "jdbc:h2:file:" + localDbPath + ";FILE_LOCK=NO";
            logger.info("Network drive detected for data path: {}. Using local database with network synchronization.", databasePath);
            logger.info("Local database: {} -> Network sync: {}", localDbPath, databasePath.replace("/database/unsplash_photos", "/database/"));
            logger.info("DatabaseSyncService will automatically sync local database to network drive every minute and on shutdown.");
        } else {
            // Use file-based database for local drives with no file locking
            jdbcUrl = "jdbc:h2:file:" + databasePath + ";FILE_LOCK=NO";
            logger.info("Local drive detected. Using file-based database: {}", databasePath);
        }
        
        logger.info("Configuring database with URL: {}", jdbcUrl);
        
        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .driverClassName("org.h2.Driver")
                .username("sa")
                .password("")
                .build();
    }
    
    public void updateDatabasePath(String newPath) {
        // This method can be called when the user changes the output directory
        logger.info("Database path update requested to: {}", newPath);
        // Note: Changing database path at runtime would require application restart
        // or more complex datasource switching logic
    }
}