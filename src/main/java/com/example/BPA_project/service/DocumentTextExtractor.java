package com.example.BPA_project.service;

public interface DocumentTextExtractor {

    boolean supports(String extension);

    DocumentExtractionResult extract(byte[] fileBytes, String originalFileName);

    default String extractText(byte[] fileBytes, String originalFileName) {
        return extract(fileBytes, originalFileName).toAnalysisText();
    }
}
