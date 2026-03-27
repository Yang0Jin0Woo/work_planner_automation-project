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
    private static final Pattern TITLE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9\\s\\-_/()]{2,80}$");

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
                PageExtraction pageExtraction = splitPageText(pageText);
                String pageHeading = detectPageHeading(pageExtraction.bodyLines, pageExtraction.tableLines);

                if (!pageExtraction.bodyLines.isEmpty()) {
                    bodySections.add(buildSection("Page", pageNumber, pageHeading, pageExtraction.bodyLines));
                }
                if (!pageExtraction.tableLines.isEmpty()) {
                    tableSections.add(buildTableSection(pageNumber, pageHeading, pageExtraction.tableLines));
                }

                int imageCount = countImages(document.getPage(pageIndex).getResources());
                if (imageCount > 0) {
                    visualSections.add(buildVisualSection(pageNumber, pageHeading, imageCount));
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

    private PageExtraction splitPageText(String pageText) {
        PageExtraction extraction = new PageExtraction();
        if (!StringUtils.hasText(pageText)) {
            return extraction;
        }

        for (String rawLine : pageText.split("\\n")) {
            String line = normalize(rawLine);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (looksLikeTableRow(line)) {
                extraction.tableLines.add(line);
            } else {
                extraction.bodyLines.add(line);
            }
        }
        return extraction;
    }

    private String buildSection(String label, int number, String heading, List<String> lines) {
        StringBuilder section = new StringBuilder();
        section.append(label).append(' ').append(number);
        if (StringUtils.hasText(heading)) {
            section.append(" | title: ").append(heading);
        }
        section.append("\n").append(String.join("\n", lines));
        return section.toString();
    }

    private String buildTableSection(int pageNumber, String heading, List<String> tableLines) {
        List<String> content = new ArrayList<>();
        content.addAll(tableLines);
        content.addAll(buildStructuredTableRows(tableLines));
        return buildSection("Page", pageNumber, heading, content);
    }

    private List<String> buildStructuredTableRows(List<String> tableLines) {
        List<String> structuredRows = new ArrayList<>();
        if (tableLines.size() < 2) {
            return structuredRows;
        }

        List<String> headers = splitColumns(tableLines.get(0));
        if (headers.size() < 2) {
            return structuredRows;
        }

        for (int rowIndex = 1; rowIndex < tableLines.size(); rowIndex++) {
            List<String> values = splitColumns(tableLines.get(rowIndex));
            if (values.size() != headers.size()) {
                continue;
            }
            List<String> pairs = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                String header = normalize(headers.get(columnIndex));
                String value = normalize(values.get(columnIndex));
                if (!StringUtils.hasText(header) || !StringUtils.hasText(value) || "-".equals(value)) {
                    continue;
                }
                pairs.add(header + "=" + value);
            }
            if (!pairs.isEmpty()) {
                structuredRows.add("Structured row: " + String.join(", ", pairs));
            }
        }
        return structuredRows;
    }

    private List<String> splitColumns(String line) {
        if (line.contains("|")) {
            return normalizeParts(line.split("\\|"));
        }
        if (line.contains("\t")) {
            return normalizeParts(line.split("\\t+"));
        }
        return normalizeParts(line.split(" {2,}"));
    }

    private List<String> normalizeParts(String[] parts) {
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            normalized.add(normalize(part));
        }
        return normalized;
    }

    private String buildVisualSection(int pageNumber, String heading, int imageCount) {
        StringBuilder visual = new StringBuilder();
        visual.append("Page ").append(pageNumber);
        if (StringUtils.hasText(heading)) {
            visual.append(" | title: ").append(heading);
        }
        visual.append("\n");
        visual.append("Detected ").append(imageCount).append(" image object(s). ");
        visual.append("Embedded image text or diagram meaning may require OCR or vision analysis.");
        return visual.toString();
    }

    private String detectPageHeading(List<String> bodyLines, List<String> tableLines) {
        for (String line : bodyLines) {
            if (isLikelyHeading(line)) {
                return line;
            }
        }
        if (!tableLines.isEmpty()) {
            List<String> headers = splitColumns(tableLines.get(0));
            if (!headers.isEmpty()) {
                return String.join(" / ", headers);
            }
        }
        return null;
    }

    private boolean isLikelyHeading(String line) {
        return StringUtils.hasText(line)
                && line.length() <= 80
                && !line.contains("|")
                && TITLE_PATTERN.matcher(line).matches();
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
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private static class PageExtraction {
        private final List<String> bodyLines = new ArrayList<>();
        private final List<String> tableLines = new ArrayList<>();
    }
}