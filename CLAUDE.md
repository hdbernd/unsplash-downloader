# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java Spring Boot application for downloading and managing Unsplash photos. It provides both a command-line interface for batch downloading and a web interface for browsing and managing downloaded photos.

## Development Commands

### Build and Run
```bash
# Clean build
mvn clean package

# Run web application (port 8099)
mvn spring-boot:run

# Run tests
mvn test

# Build both executable JARs
mvn clean package
```

### Application Modes
```bash
# Web interface mode (default)
java -jar target/unsplash-downloader-1.0-SNAPSHOT.jar

# CLI download mode
java -jar target/unsplash-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar <username> <output_dir>
```

## Architecture

### Layered Architecture Pattern
- **Controllers** (`controller/`): REST API endpoints and web interface
- **Services** (`service/`): Core business logic (PhotoService, DownloadService, ApiKeyService, ThumbnailService)
- **Repositories** (`repository/`): JPA data access layer
- **Entities** (`entity/`): JPA entity mappings

### Dual Entry Points
- `Main.java`: CLI-only mode for batch downloading
- `UnsplashDownloaderApplication.java`: Spring Boot web application

### Key Components
- **API Key Management**: Rotation system using multiple Unsplash API keys
- **Download State Management**: Resumable downloads with JSON state persistence
- **Metadata Handling**: EXIF data embedding using Apache Commons Imaging
- **H2 Database**: Embedded database for photo metadata storage

### Technology Stack
- Java 17, Spring Boot 3.2.1, Spring Data JPA
- H2 Database, Thymeleaf templates
- OkHttp for API calls, Jackson for JSON
- Apache Commons Imaging for EXIF handling

## Data Structure

### Runtime Directory: `./unsplash-data/`
- `database/`: H2 database files
- `photos/`: Downloaded images
- `thumbnails/`: Generated thumbnails
- `config/`: User settings and API configuration
- `logs/`: Application logs

### Configuration Files
- `application.properties`: Spring Boot configuration
- `config.properties`: Unsplash API keys
- `unsplash-data/config/user_settings.json`: Runtime preferences

## Development Notes

### Database Access
- H2 Console available at `http://localhost:8099/h2-console`
- JDBC URL: `jdbc:h2:file:./unsplash-data/database/unsplash_photos`

### API Integration
- Uses Unsplash REST API with rate limiting handling
- Supports multiple API key rotation for increased limits
- WebSocket integration for real-time download progress

### Testing
- JUnit 5 framework (minimal test coverage currently)
- Run with: `mvn test`