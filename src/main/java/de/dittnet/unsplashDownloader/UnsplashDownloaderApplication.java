package de.dittnet.unsplashDownloader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories
@EnableAsync
@EnableScheduling
public class UnsplashDownloaderApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(UnsplashDownloaderApplication.class, args);
    }
    
    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            if (args.length == 2) {
                // Run in CLI mode for downloading
                System.out.println("Starting CLI download mode...");
                String username = args[0];
                String outputDir = args[1];
                
                try {
                    UnsplashDownloader downloader = new UnsplashDownloader(outputDir);
                    downloader.downloadUserPhotos(username);
                } catch (Exception e) {
                    System.err.println("Error downloading photos: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Run in web mode
                System.out.println("Starting web interface...");
                System.out.println("Access the application at: http://localhost:8099");
            }
        };
    }
}