# Unsplash Photo Downloader

A Java application that downloads photos and their descriptions from Unsplash.com for a specified user. The application handles rate limiting, can resume interrupted downloads, and embeds photo descriptions into the image metadata.

## Features

- Downloads all photos from a specified Unsplash user
- Embeds photo descriptions in EXIF metadata
- Handles API rate limits automatically
- Can resume interrupted downloads
- Tracks download progress
- Maintains a backup description file
- Supports large collections (>16000 photos)

## Prerequisites

- Java 17 or higher
- Maven
- Unsplash API access token (get it from https://unsplash.com/developers)

## Project Setup

1. Clone or download the project
2. Create the following directory structure if it doesn't exist:
```
unsplash-downloader/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── unsplash/
│   │   │               ├── model/
│   │   └── resources/
│   └── test/
│       └── java/
│           └── com/
│               └── example/
│                   └── unsplash/
```

3. Place the files in their correct locations:
   - Root directory: `pom.xml`, `config.properties`
   - `src/main/java/com/example/unsplash/`: Main Java files
   - `src/main/java/com/example/unsplash/model/`: Model Java files
   - `src/main/resources/`: `logback.xml`

## Configuration

You can provide your Unsplash API access token in one of two ways:

1. Environment Variable (Recommended):
   ```bash
   # Windows
   set UNSPLASH_ACCESS_TOKEN=your_api_key_here

   # Linux/Mac
   export UNSPLASH_ACCESS_TOKEN=your_api_key_here
   ```

2. Properties File:
   - Edit `config.properties` in the project root
   - Replace `your_access_token_here` with your actual API key:
     ```properties
     unsplash.access.token=your_api_key_here
     ```

## Building

Build the project using Maven:
```bash
mvn clean package
```

This will create two JAR files in the `target` directory:
- `unsplash-downloader-1.0-SNAPSHOT.jar`
- `unsplash-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar` (use this one)

## Running

Use the following command to run the application:
```bash
java -jar target/unsplash-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar <username> <output_directory>
```

Example:
```bash
java -jar target/unsplash-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar johndoe ./photos
```

## Output

The program creates:
1. Downloaded photos in the specified output directory
2. `descriptions.txt` containing all photo descriptions
3. `download_state.json` tracking download progress
4. Log files in the `logs` directory

Each photo will have:
- EXIF metadata with the photo description
- Photographer information embedded
- Original Unsplash photo ID in the filename

## Rate Limiting

The application respects Unsplash API rate limits:
- Tracks daily API requests
- Automatically pauses when reaching the limit
- Saves progress and resumes the next day
- Configurable daily request limit (default: 50)

## Resuming Downloads

If the download is interrupted or hits the rate limit:
1. The progress is automatically saved
2. Run the same command again to resume
3. Already downloaded photos will be skipped
4. Download continues from the last position

## Logs

Logs are written to:
- Console
- `logs/unsplash-downloader.log`
- Daily rolling log files

## Troubleshooting

1. If you get authorization errors:
   - Check your API key
   - Verify the key is correctly set in environment or config.properties

2. If downloads stop mid-way:
   - This is normal if you hit the rate limit
   - The program will save progress
   - Try again the next day

3. If metadata embedding fails:
   - Check if the downloaded files are valid JPEGs
   - The description will still be saved in descriptions.txt

4. For other issues:
   - Check the log files in the logs directory
   - Verify your internet connection
   - Ensure you have write permissions in the output directory