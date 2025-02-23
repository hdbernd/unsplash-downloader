package de.dittnet.unsplashDownloader;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java Main <username> <output_directory>");
            System.exit(1);
        }

        try {
            String accessToken = loadAccessToken();
            String username = args[0];
            String outputDir = args[1];

            UnsplashDownloader downloader = new UnsplashDownloader(accessToken, outputDir);
            downloader.downloadUserPhotos(username);
        } catch (Exception e) {
            System.err.println("Error downloading photos: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String loadAccessToken() throws IOException {
        // First try environment variable
        String accessToken = System.getenv("UNSPLASH_ACCESS_TOKEN");
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            return accessToken;
        }

        // If not found, try properties file
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            prop.load(fis);
            accessToken = prop.getProperty("unsplash.access.token");
            if (accessToken != null && !accessToken.trim().isEmpty()) {
                return accessToken;
            }
        }

        throw new IOException("No access token found. Please set UNSPLASH_ACCESS_TOKEN environment variable or add it to config.properties");
    }
}
