package com.example.BPA_project.service;

import com.example.BPA_project.exception.FileStorageException;
import com.example.BPA_project.model.StoredFileInfo;
import com.example.BPA_project.util.FileNameUtils;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

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

        StoredFileInfo info = new StoredFileInfo();
        info.setOriginalFileName(originalName);
        info.setSavedFileName(savedFileName);
        info.setContentType(multipartFile.getContentType());
        info.setExtension(extension);
        info.setAbsolutePath(null);
        info.setSize(multipartFile.getSize());
        info.setUploadedAt(LocalDateTime.now());
        return info;
    }
}