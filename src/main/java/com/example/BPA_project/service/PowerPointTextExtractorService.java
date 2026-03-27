package com.example.BPA_project.service;

import com.example.BPA_project.exception.DocumentAnalysisException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.springframework.stereotype.Service;

@Service
public class PowerPointTextExtractorService implements DocumentTextExtractor {

    @Override
    public boolean supports(String extension) {
        return "ppt".equals(extension) || "pptx".equals(extension);
    }

    @Override
    public String extractText(byte[] fileBytes, String originalFileName) {
        try (SlideShow<?, ?> slideShow = SlideShowFactory.create(new ByteArrayInputStream(fileBytes))) {
            SlideShowExtractor<?, ?> extractor = new SlideShowExtractor<>(slideShow);
            extractor.setSlidesByDefault(true);
            extractor.setNotesByDefault(true);
            String text = extractor.getText();
            return validateExtractedText(text);
        } catch (IOException exception) {
            throw new DocumentAnalysisException("Failed to extract text from the PowerPoint file.", exception);
        }
    }

    private String validateExtractedText(String text) {
        if (text == null || text.isBlank()) {
            throw new DocumentAnalysisException("No extractable text was found in the PowerPoint file.");
        }
        return text.strip();
    }
}
