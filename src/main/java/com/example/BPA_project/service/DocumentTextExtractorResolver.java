package com.example.BPA_project.service;

import com.example.BPA_project.exception.DocumentAnalysisException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentTextExtractorResolver {

    private final List<DocumentTextExtractor> extractors;

    public DocumentTextExtractorResolver(List<DocumentTextExtractor> extractors) {
        this.extractors = extractors;
    }

    public DocumentTextExtractor resolve(String extension) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(extension))
                .findFirst()
                .orElseThrow(() -> new DocumentAnalysisException("Unsupported file type: " + extension + "."));
    }
}
