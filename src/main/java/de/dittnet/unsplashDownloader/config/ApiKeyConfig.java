package de.dittnet.unsplashDownloader.config;

import de.dittnet.unsplashDownloader.ApiKeyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class ApiKeyConfig {
    
    @Autowired
    private StorageConfig storageConfig;
    
    @Bean
    public ApiKeyManager apiKeyManager() throws IOException {
        // Use the base directory to be consistent with StorageConfig
        return new ApiKeyManager(storageConfig.getBaseDirectory());
    }
}