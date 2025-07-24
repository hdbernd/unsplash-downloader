# Unsplash Photo Downloader

A comprehensive Java Spring Boot application for downloading and managing Unsplash photos. Features both command-line interface for batch downloading and a modern web interface for browsing, searching, and managing your photo collection.

## Features

### Core Download Features
- **Batch Downloads**: Download all photos from any Unsplash user
- **Incremental Downloads**: Automatically skip already downloaded photos
- **Robust State Management**: Resume interrupted downloads seamlessly
- **Multiple API Key Support**: Rotate between multiple API keys for higher rate limits
- **Rate Limit Handling**: Smart handling of demo (50/hour) and production (5000/hour) limits

### Metadata & Organization  
- **Complete Tag Retrieval**: Automatically fetches all tags from Unsplash API for each photo
- **EXIF Metadata Embedding**: Photo descriptions, tags, and photographer info embedded in images
- **Metadata Sync System**: Dedicated system to write metadata into EXIF data of existing photos
- **Database Storage**: H2 database for fast photo metadata search and browsing with full tag support
- **Thumbnail Generation**: Automatic thumbnail creation for web interface
- **Full-text Search**: Search photos by description, tags, or photographer

### Web Interface
- **Modern UI**: Bootstrap-based responsive interface
- **Real-time Progress**: Live download progress with WebSocket updates
- **Photo Browsing**: Grid view with thumbnails and detailed photo information
- **Advanced Filtering**: Filter by photographer, tags, or search terms
- **Collection Statistics**: View download stats and collection analytics
- **API Key Management**: Add/remove/validate API keys through web interface
- **Metadata Sync Management**: Batch process existing photos to embed EXIF metadata
- **Individual Photo Status**: Sync status indicators on each photo with real-time updates
- **EXIF Data Viewer**: View embedded metadata for any photo through interactive modals

## Prerequisites

- Java 17 or higher
- Maven
- Unsplash API access token (get it from https://unsplash.com/developers)

### Unsplash API Keys: Demo vs Production

**Demo Apps (Development Mode):**
- ✅ **Available immediately** after creating your Unsplash app
- ✅ **Full API access** to all public endpoints
- ⚠️ **Limited to 50 requests per hour**
- ✅ **Perfect for testing and personal use**
- Uses Client-ID authentication with your Access Key

**Production Apps:**
- 🔄 **Requires approval process** (5 business days)
- 📸 **Must submit screenshots** showing proper attribution
- 🚀 **5000 requests per hour** rate limit
- 🏢 **Intended for commercial applications**

**For this application:** Demo apps work perfectly! The 50 requests/hour limit is sufficient for downloading photos in batches.

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

This creates two JAR files in the `target` directory:
- `unsplash-downloader-1.0-SNAPSHOT.jar` - Spring Boot web application
- `unsplash-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar` - CLI-only version

## Running

### Web Interface Mode (Recommended)
Start the Spring Boot application for the full web interface:
```bash
mvn spring-boot:run
# OR
java -jar target/unsplash-downloader-1.0-SNAPSHOT.jar
```

Then open your browser to: **http://localhost:8099**

Features available in web mode:
- Modern web interface for browsing downloaded photos
- Real-time download progress with current photo display
- API key management through web interface
- Full-text search and filtering
- Collection statistics and analytics
- Thumbnail generation
- **Metadata sync system** for batch EXIF metadata processing
- **Individual photo sync status** with visual indicators on each photo
- **EXIF metadata viewer** to inspect embedded data for any photo

### Command Line Mode  
For CLI-only batch downloading:
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
- EXIF metadata with photo description and all tags
- Photographer information embedded
- Complete tag metadata stored in database and image files
- Original Unsplash photo ID in the filename

## Advanced Rate Limiting & API Key Management

The application provides intelligent rate limit handling with multiple API key support:

### Rate Limit Management
- **Demo apps:** 50 requests per hour per API key (includes both photo list and individual photo detail calls)
- **Production apps:** 5000 requests per hour per API key
- **Hourly Usage Tracking**: Resets automatically each hour
- **Smart Key Rotation**: Automatically switches to next available key when limits hit
- **Rate Limit Recovery**: Tracks when keys become available again
- **Dual API Calls**: Each photo requires 2 API calls (list + detail for tags) - plan accordingly

### Multiple API Key Features
```
With Multiple API Keys:
├── Key 1: 50 requests/hour → Rate limited at 12:00 PM
├── Key 2: 50 requests/hour → Automatically switches to Key 2
├── Key 3: 50 requests/hour → Available as backup
└── Combined: 150 requests/hour total capacity
```

### Automatic Handling
1. **403 Rate Limit Detection**: Instantly detects when a key hits its limit
2. **Seamless Key Rotation**: Automatically switches to next available key
3. **Smart Recovery**: Tracks when rate-limited keys become available again
4. **Progress Continuation**: Downloads continue uninterrupted with key switching
5. **Usage Statistics**: Real-time tracking of usage per key in web interface

### Benefits of Multiple Keys
- **Increased Throughput**: Multiply your hourly download capacity
- **Uninterrupted Downloads**: No waiting for rate limit resets
- **Fault Tolerance**: If one key fails, others continue working
- **Optimized Usage**: Spreads load across all available keys

## Incremental Downloads & State Management

The application provides robust incremental downloading that intelligently handles interruptions and avoids duplicates:

### How Incremental Downloads Work

**1. State File Tracking (`download_state.json`)**
- Tracks which photos have been processed for each user
- Records download progress, metadata, and photo IDs
- Automatically saved after each successful download
- Prevents duplicate downloads even across multiple sessions

**2. Physical File Verification**
- Before downloading, checks if the photo file already exists on disk
- Uses filename pattern: `{username}_{photoId}.jpg`
- If file exists but isn't in state file, adds it to tracking
- Ensures consistency between state records and actual files

**3. API Difference Detection**
- Compares local photo collection against Unsplash user's current photos
- Identifies new photos added since last download
- Only downloads photos that are missing locally
- Skips photos that have been removed from Unsplash (keeps local copies)

**4. Smart Resume Logic**
```
When starting a download:
├── Load existing state file (if any)
├── Scan output directory for existing photos
├── Fetch current user photos from Unsplash API
├── Calculate difference: API photos - already downloaded
└── Download only missing photos
```

### Resuming Downloads

If the download is interrupted or hits the rate limit:
1. **Automatic State Persistence**: Progress is saved after each photo
2. **Smart Recovery**: Run the same command again to resume
3. **Duplicate Prevention**: Already downloaded photos are automatically skipped
4. **Robust File Checking**: Handles cases where state file is lost but photos exist
5. **Continues from Last Position**: Resumes from the exact point of interruption

### Example Scenarios

**Scenario 1: Rate Limit Hit**
```
Downloaded: 45/120 photos → Rate limit reached
State saved: 45 photos recorded in download_state.json
Restart: Automatically skips first 45 photos, continues from photo 46
```

**Scenario 2: Lost State File**
```
Existing files: username_abc123.jpg, username_def456.jpg (no state file)
Restart: Scans directory, rebuilds state from existing files, downloads remaining
```

**Scenario 3: User Added New Photos**
```
Previous download: 100 photos
User added 5 new photos on Unsplash
Restart: Downloads only the 5 new photos, skips existing 100
```

## Logs

Logs are written to:
- Console
- `logs/unsplash-downloader.log`
- Daily rolling log files

## Metadata Sync System

The application includes a comprehensive metadata sync system that processes your existing photo collection to embed EXIF metadata into the image files themselves.

### What Gets Synced
- **Photo Descriptions**: Written to EXIF Image Description field
- **Tags**: Added as EXIF User Comment with "Tags: tag1, tag2, tag3" format  
- **Photographer Info**: Added to EXIF Artist field
- **Copyright Info**: Added to EXIF Copyright field with Unsplash attribution

### How It Works
1. **High-Performance Initialization**: Optimized setup for thousands of photos (30-60 seconds for 4000+ files)
2. **Smart Detection**: Only processes photos that haven't been synced yet
3. **File Change Detection**: Uses SHA-256 hashing to detect if files have been modified (calculated only when needed)
4. **JPEG Only**: Safely processes only JPEG files (.jpg, .jpeg)
5. **Status Tracking**: Maintains detailed sync status for each photo in dedicated database
6. **Batch Processing**: Configurable batch sizes with real-time progress tracking
7. **Retry Logic**: Automatic retry for failed sync operations
8. **New Photo Detection**: Automatically identifies newly downloaded photos

### Using the Metadata Sync

**Via Web Interface:**
1. Navigate to **Settings** page in the web interface
2. Go to the **"Metadata Sync"** section
3. Click **"Initialize Sync"** to scan your photo collection (fast setup)
4. **After downloading new photos**: Click **"Add New Photos"** to include them
5. Configure batch size (default: 100 photos per batch)
6. Click **"Start Sync"** to begin processing
7. Monitor real-time progress with comprehensive statistics and progress bars

**Statistics Overview:**
- **Total Photos**: All photos in your database
- **Synced**: Successfully processed with EXIF metadata
- **Due for Sync**: Photos needing processing (pending + failed + new)
- **Not in Sync System**: Recently downloaded photos not yet added

**Sync Status Types:**
- **PENDING**: Photo ready to be processed
- **IN_PROGRESS**: Currently being processed
- **COMPLETED**: Successfully synced metadata
- **FAILED**: Processing failed (with retry capability)
- **SKIPPED**: Non-JPEG file or file not found
- **ERROR**: Unexpected error occurred

**Management Options:**
- **Add New Photos**: Include recently downloaded photos in sync system
- **Stop Sync**: Gracefully stop current sync operation
- **Reset All**: Mark all photos as pending for re-sync
- **Cleanup**: Remove old completed sync records

### Performance
The metadata sync system is optimized for large photo collections:

- **Fast Initialization**: Processes 4000+ photos in 30-60 seconds (vs hours in previous versions)
- **Efficient Processing**: Only calculates file hashes when needed during actual sync
- **Batch Operations**: Database operations are batched for optimal performance
- **Progress Tracking**: Real-time statistics show both overall and sync system progress
- **Memory Efficient**: Handles large collections without excessive memory usage

### Individual Photo Status & EXIF Viewing

Each photo in the web interface now includes:

**Sync Status Indicators:**
- **Visual badges** on each photo showing metadata sync status
- **Color-coded indicators**: 
  - 🟢 Green: Successfully synced
  - 🟠 Orange: Pending sync
  - 🔵 Blue: Currently processing
  - 🔴 Red: Sync failed
  - ⚪ Gray: Skipped (non-JPEG) or not in sync system
- **Real-time updates**: Status changes automatically as sync processes run

**EXIF Metadata Viewer:**
- **EXIF button** on each photo card for instant metadata viewing
- **Modal interface** displaying all embedded metadata fields
- **Metadata details**: Shows Image Description, Artist, Copyright, Software, User Comment
- **File information**: Displays file path, size, and last modified time
- **Error handling**: Clear messages for files without EXIF data or missing files

### Benefits
- **Portable Metadata**: Photo information travels with the image file
- **Photo Management Software**: Works with Lightroom, Bridge, and other EXIF-aware tools
- **Future-Proof**: Metadata remains accessible even without the database
- **Search Enhancement**: Many photo organizers can search EXIF metadata
- **Backup Safety**: Metadata preserved even if database is lost
- **Individual Monitoring**: Track sync status for each photo at a glance
- **Instant Verification**: View embedded metadata without external tools

## Data Storage

### Web Interface Mode
- **Database**: H2 embedded database at `./unsplash-data/database/`
- **Photos**: Stored in configurable output directory  
- **Thumbnails**: Auto-generated at `./unsplash-data/thumbnails/`
- **Configuration**: API keys and settings in `./unsplash-data/config/`
- **State Files**: Download progress in `./unsplash-data/state/`
- **Logs**: Application logs in `./unsplash-data/logs/`

### CLI Mode
- **Photos**: Stored in specified output directory
- **Descriptions**: `descriptions.txt` containing all photo descriptions  
- **State**: `download_state.json` tracking download progress
- **Logs**: Console output and `logs/` directory

## Database Access

When running in web mode, you can access the H2 database console:
- URL: http://localhost:8099/h2-console
- JDBC URL: `jdbc:h2:file:./unsplash-data/database/unsplash_photos`
- Username: `sa` (no password)

## Troubleshooting

### API Key Issues
1. **"Invalid API key" or "dummy key" warnings**:
   - Add your real Unsplash API key through the web interface at http://localhost:8099/api-keys
   - Make sure you're using the Access Key, not Secret Key from Unsplash
   - Demo apps work fine - no need for production approval

2. **Rate limit errors (403 Forbidden)**:
   - Demo apps: 50 requests/hour limit
   - Production apps: 5000 requests/hour limit  
   - Wait for hourly reset or add multiple API keys for higher limits

### Download Issues
3. **Downloads stop mid-way**:
   - Normal behavior when hitting rate limits
   - Progress is automatically saved
   - Resume by starting download again - already downloaded photos will be skipped

4. **"Photo file exists but not in state"** messages:
   - This is normal - app is rebuilding state from existing files
   - Ensures no duplicate downloads even if state file is lost

### Technical Issues  
5. **Database connection errors**:
   - Check if another instance is running
   - Delete `*.lock.db` files in the database directory

6. **Port 8099 already in use**:
   - Stop other instances or change port in `application.properties`

7. **File permission errors**:
   - Ensure write permissions in output directory
   - Check disk space availability

8. **For other issues**:
   - Check log files in `./unsplash-data/logs/` directory
   - Verify internet connection
   - Restart the application