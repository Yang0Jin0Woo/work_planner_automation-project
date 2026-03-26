package com.example.BPA_project.service;

import com.example.BPA_project.config.AppProperties;
import com.example.BPA_project.exception.FileStorageException;
import com.example.BPA_project.model.StoredFileInfo;
import com.example.BPA_project.util.FileNameUtils;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path uploadDir;
    private final Path analysisDir;
    private final Path reportDir;

    public FileStorageService(AppProperties appProperties) {
        this.uploadDir = Path.of(appProperties.getStorage().getUploadDir()).toAbsolutePath().normalize();
        this.analysisDir = Path.of(appProperties.getStorage().getAnalysisDir()).toAbsolutePath().normalize();
        this.reportDir = Path.of(appProperties.getStorage().getReportDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initDirectories() {
        try {
            Files.createDirectories(uploadDir);
            Files.createDirectories(analysisDir);
            Files.createDirectories(reportDir);
        } catch (IOException exception) {
            throw new FileStorageException("Failed to create local storage directories.", exception);
        }
    }

    public StoredFileInfo store(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new FileStorageException("Uploaded file is empty.");
        }

        String originalName = multipartFile.getOriginalFilename();
        String extension = FileNameUtils.extension(originalName);
        String safeBaseName = FileNameUtils.safeBaseName(originalName);
        String savedFileName = extension.isBlank()
                ? "%s-%s".formatted(safeBaseName, UUID.randomUUID())
                : "%s-%s.%s".formatted(safeBaseName, UUID.randomUUID(), extension);
        Path target = uploadDir.resolve(savedFileName);

        try {
            Files.copy(multipartFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new FileStorageException("Failed to store uploaded file.", exception);
        }

        StoredFileInfo info = new StoredFileInfo();
        info.setOriginalFileName(originalName);
        info.setSavedFileName(savedFileName);
        info.setContentType(multipartFile.getContentType());
        info.setExtension(extension);
        info.setAbsolutePath(target.toString());
        info.setSize(multipartFile.getSize());
        info.setUploadedAt(LocalDateTime.now());
        return info;
    }

    public Path analysisDir() {
        return analysisDir;
    }

    public Path reportDir() {
        return reportDir;
    }
}
