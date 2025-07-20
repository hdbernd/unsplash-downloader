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
            String username = args[0];
            String outputDir = args[1];

            UnsplashDownloader downloader = new UnsplashDownloader(outputDir);
            downloader.downloadUserPhotos(username);
        } catch (Exception e) {
            System.err.println("Error downloading photos: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

}
