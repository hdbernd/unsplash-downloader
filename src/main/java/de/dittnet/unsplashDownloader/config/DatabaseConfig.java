package de.dittnet.unsplashDownloader.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        String jdbcUrl = "jdbc:h2:file:" + databasePath;
        
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