package de.dittnet.unsplashDownloader;

import de.dittnet.unsplashDownloader.model.Photo;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

public class ImageMetadataHandler {
    private static final Logger logger = LoggerFactory.getLogger(ImageMetadataHandler.class);

    public void addMetadata(File imageFile, Photo photo)
        throws ImageReadException, ImageWriteException, IOException {

        String description = photo.getDescription();
        if (description == null) {
            description = "No description available";
        }

        String photographer = photo.getUser() != null ? photo.getUser().getName() : "Unknown";
        String tags = formatTags(photo.getTags());

        // Create a temporary file for the output
        File tempFile = new File(imageFile.getParentFile(), "temp_" + imageFile.getName());

        try (FileOutputStream fos = new FileOutputStream(tempFile);
            OutputStream os = new BufferedOutputStream(fos)) {

            TiffOutputSet outputSet = null;

            // Get existing metadata
            ImageMetadata metadata = Imaging.getMetadata(imageFile);

            if (metadata instanceof JpegImageMetadata jpegMetadata) {
                TiffImageMetadata exif = jpegMetadata.getExif();
                if (exif != null) {
                    outputSet = exif.getOutputSet();
                }
            }

            // If no existing metadata, create a new set
            if (outputSet == null) {
                outputSet = new TiffOutputSet();
            }

            // Get or create directories
            TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
            TiffOutputDirectory rootDirectory = outputSet.getOrCreateRootDirectory();

            // Add description using IMAGE_DESCRIPTION tag
            rootDirectory.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
            rootDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, description);

            // Add tags as part of the description if available
            if (tags != null && !tags.isEmpty()) {
                String fullDescription = String.format("%s\nTags: %s", description, tags);
                rootDirectory.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
                rootDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, fullDescription);
            }

            // Add photographer using ARTIST tag
            rootDirectory.removeField(TiffTagConstants.TIFF_TAG_ARTIST);
            rootDirectory.add(TiffTagConstants.TIFF_TAG_ARTIST, photographer);

            // Add title if available using DOCUMENT_NAME tag
            if (photo.getTitle() != null && !photo.getTitle().isEmpty()) {
                rootDirectory.removeField(TiffTagConstants.TIFF_TAG_DOCUMENT_NAME);
                rootDirectory.add(TiffTagConstants.TIFF_TAG_DOCUMENT_NAME, photo.getTitle());
            }

            // Add software tag
            rootDirectory.removeField(TiffTagConstants.TIFF_TAG_SOFTWARE);
            rootDirectory.add(TiffTagConstants.TIFF_TAG_SOFTWARE, "Unsplash Downloader");

            // Add user comment in EXIF
            exifDirectory.removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT);
            exifDirectory.add(ExifTagConstants.EXIF_TAG_USER_COMMENT, description);

            // Write copyright if available
            if (photo.getUser() != null) {
                String copyright = String.format("© %s on Unsplash", photo.getUser().getName());
                rootDirectory.removeField(TiffTagConstants.TIFF_TAG_COPYRIGHT);
                rootDirectory.add(TiffTagConstants.TIFF_TAG_COPYRIGHT, copyright);
            }

            // Write the metadata to the image
            new ExifRewriter().updateExifMetadataLossless(imageFile, os, outputSet);
        }

        // Replace original file with the updated one
        if (!imageFile.delete()) {
            throw new IOException("Could not delete original file");
        }
        if (!tempFile.renameTo(imageFile)) {
            throw new IOException("Could not rename temporary file");
        }
    }

    private String formatTags(List<Photo.Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream()
            .map(Photo.Tag::getTitle)
            .collect(Collectors.joining(", "));
    }
}
