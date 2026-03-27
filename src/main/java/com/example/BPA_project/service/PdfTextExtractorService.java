package com.example.BPA_project.service;

import com.example.BPA_project.exception.DocumentAnalysisException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PdfTextExtractorService implements DocumentTextExtractor {

    private static final Pattern TABLE_LINE_PATTERN = Pattern.compile(".*(\\t| {2,}|\\|).*");

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public DocumentExtractionResult extract(byte[] fileBytes, String originalFileName) {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> bodySections = new ArrayList<>();
            List<String> tableSections = new ArrayList<>();
            List<String> visualSections = new ArrayList<>();

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                int pageNumber = pageIndex + 1;
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = normalize(stripper.getText(document));
                splitPageText(pageText, pageNumber, bodySections, tableSections);

                int imageCount = countImages(document.getPage(pageIndex).getResources());
                if (imageCount > 0) {
                    visualSections.add("Page " + pageNumber + ": detected " + imageCount + " image object(s). Embedded image text or diagram meaning may require OCR or vision analysis.");
                }
            }

            DocumentExtractionResult result = new DocumentExtractionResult(
                    joinSections(bodySections),
                    joinSections(tableSections),
                    joinSections(visualSections)
            );
            if (!result.hasAnyContent()) {
                throw new DocumentAnalysisException("No extractable text was found in the PDF.");
            }
            return result;
        } catch (IOException exception) {
            throw new DocumentAnalysisException("Failed to extract text from the PDF.", exception);
        }
    }

    private void splitPageText(String pageText,
                               int pageNumber,
                               List<String> bodySections,
                               List<String> tableSections) {
        if (!StringUtils.hasText(pageText)) {
            return;
        }

        List<String> bodyLines = new ArrayList<>();
        List<String> tableLines = new ArrayList<>();
        for (String rawLine : pageText.split("\\n")) {
            String line = normalize(rawLine);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (looksLikeTableRow(line)) {
                tableLines.add(line);
            } else {
                bodyLines.add(line);
            }
        }

        if (!bodyLines.isEmpty()) {
            bodySections.add("Page " + pageNumber + "\n" + String.join("\n", bodyLines));
        }
        if (!tableLines.isEmpty()) {
            tableSections.add("Page " + pageNumber + "\n" + String.join("\n", tableLines));
        }
    }

    private boolean looksLikeTableRow(String line) {
        return TABLE_LINE_PATTERN.matcher(line).matches();
    }

    private int countImages(PDResources resources) throws IOException {
        if (resources == null) {
            return 0;
        }

        int count = 0;
        for (COSName objectName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(objectName);
            if (xObject instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                count++;
            } else if (xObject instanceof PDFormXObject formXObject) {
                count += countImages(formXObject.getResources());
            }
        }
        return count;
    }

    private String joinSections(List<String> sections) {
        return sections.isEmpty() ? null : String.join("\n\n", sections);
    }

    private String normalize(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}