package com.example.BPA_project.util;

import java.text.Normalizer;

public final class FileNameUtils {

    private FileNameUtils() {
    }

    public static String extension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    public static String safeBaseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }

        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String baseName = normalized.replaceAll("\\.[^.]+$", "");
        String safe = baseName.replaceAll("[^a-zA-Z0-9-_]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return safe.isBlank() ? "document" : safe;
    }
}
