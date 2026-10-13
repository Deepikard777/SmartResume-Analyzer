package com.resumeanalyzer.service;

import com.resumeanalyzer.exception.FileStorageException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageDirectory;

    public FileStorageService(@Value("${resume.storage.directory:./uploads/resumes}") String storageDirectoryPath) {
        this.storageDirectory = Paths.get(storageDirectoryPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.storageDirectory);
        } catch (IOException ex) {
            throw new FileStorageException("Could not initialize storage directory: " + this.storageDirectory, ex);
        }
    }

    public String storeFile(MultipartFile file, String extension) {
        if (file.isEmpty()) {
            throw new FileStorageException("Cannot store an empty file.");
        }

        // Clean and ensure leading dot for extension
        String normalizedExtension = extension.startsWith(".") ? extension.toLowerCase() : "." + extension.toLowerCase();
        String uniqueStoredFileName = UUID.randomUUID().toString() + normalizedExtension;

        try {
            Path targetLocation = this.storageDirectory.resolve(uniqueStoredFileName).normalize();

            // Guard against path traversal
            if (!targetLocation.startsWith(this.storageDirectory)) {
                throw new FileStorageException("Cannot store file outside current storage directory.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            return uniqueStoredFileName;
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store file " + uniqueStoredFileName, ex);
        }
    }

    public Path loadFilePath(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            throw new FileStorageException("Invalid filename provided.");
        }

        Path filePath = this.storageDirectory.resolve(storedFileName).normalize();
        if (!filePath.startsWith(this.storageDirectory)) {
            throw new FileStorageException("Path traversal attempt detected: " + storedFileName);
        }

        return filePath;
    }

    public boolean deleteFile(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            return false;
        }

        try {
            Path filePath = loadFilePath(storedFileName);
            return Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to delete stored file: " + storedFileName, ex);
        }
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }
}
