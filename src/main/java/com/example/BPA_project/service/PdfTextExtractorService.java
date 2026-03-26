package com.example.BPA_project.service;

import com.example.BPA_project.exception.DocumentAnalysisException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractorService {

    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw new DocumentAnalysisException("No extractable text was found in the PDF.");
            }
            return text.strip();
        } catch (IOException exception) {
            throw new DocumentAnalysisException("Failed to extract text from the PDF.", exception);
        }
    }
}