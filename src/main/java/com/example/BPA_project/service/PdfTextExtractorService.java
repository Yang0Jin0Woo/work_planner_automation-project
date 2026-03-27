package com.example.BPA_project.service;

import com.example.BPA_project.exception.DocumentAnalysisException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PdfTextExtractorService implements DocumentTextExtractor {

    private static final Pattern TABLE_LINE_PATTERN = Pattern.compile(".*(\\t| {2,}|\\|).*");
    private static final Pattern ENGLISH_TITLE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9\\s\\-_/()]{2,80}$");
    private static final Pattern NUMBER_SECTION_PATTERN = Pattern.compile("^(?:[0-9]{1,2}(?:\\.[0-9]{1,2})*|[0-9]{1,2}[)])\\s*.+$");
    private static final Pattern KOREAN_SECTION_PATTERN = Pattern.compile("^(?:[\\uAC00-\\uD558][.]|[\\uAC00-\\uD558][)])\\s*.+$");
    private static final Pattern ROMAN_SECTION_PATTERN = Pattern.compile("^(?:[\\u2160-\\u2169]+[.]|[\\u2460-\\u2473])\\s*.+$");
    private static final Pattern BRACKET_SECTION_PATTERN = Pattern.compile("^\\[[^\\]]+\\]\\s*.+$");
    private static final Pattern YEAR_SECTION_PATTERN = Pattern.compile("^[0-9]{4}\\uB144\\s+.+$");
    private static final int MAX_VISUAL_ASSETS = 3;

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public DocumentExtractionResult extract(byte[] fileBytes, String originalFileName) {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            List<String> bodySections = new ArrayList<>();
            List<String> tableSections = new ArrayList<>();
            List<String> visualSections = new ArrayList<>();
            List<VisualAsset> visualAssets = new ArrayList<>();

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
                    addVisualAsset(visualAssets, renderPageAsset(renderer, pageIndex, pageNumber, pageHeading));
                }
            }

            DocumentExtractionResult result = new DocumentExtractionResult(
                    joinSections(bodySections),
                    joinSections(tableSections),
                    joinSections(visualSections),
                    visualAssets
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

        int headerRowIndex = detectHeaderRowIndex(tableLines);
        if (headerRowIndex < 0 || headerRowIndex >= tableLines.size() - 1) {
            return structuredRows;
        }

        List<String> headers = splitColumns(tableLines.get(headerRowIndex));
        if (headers.size() < 2) {
            return structuredRows;
        }

        for (int rowIndex = headerRowIndex + 1; rowIndex < tableLines.size(); rowIndex++) {
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

    private int detectHeaderRowIndex(List<String> tableLines) {
        int candidateLimit = Math.min(3, tableLines.size());
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int index = 0; index < candidateLimit; index++) {
            List<String> columns = splitColumns(tableLines.get(index));
            int score = scoreHeaderRow(columns, index);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestScore >= 2 ? bestIndex : 0;
    }

    private int scoreHeaderRow(List<String> columns, int rowIndex) {
        if (columns.size() < 2) {
            return Integer.MIN_VALUE;
        }

        int score = 0;
        if (rowIndex == 0) {
            score += 1;
        }

        int keywordMatches = 0;
        int filledColumns = 0;
        for (String column : columns) {
            String normalized = normalize(column);
            if (!StringUtils.hasText(normalized) || "-".equals(normalized)) {
                continue;
            }
            filledColumns++;
            if (isHeaderKeyword(normalized)) {
                keywordMatches++;
            }
            if (normalized.length() <= 12) {
                score += 1;
            } else if (normalized.length() >= 30) {
                score -= 1;
            }
            if (looksLikeTitleOnlyCell(normalized)) {
                score -= 2;
            }
        }

        if (filledColumns == columns.size()) {
            score += 1;
        }
        score += keywordMatches * 2;
        return score;
    }

    private boolean isHeaderKeyword(String value) {
        return value.contains("\uC77C\uC815")
                || value.contains("\uAE30\uD55C")
                || value.contains("\uB0A0\uC9DC")
                || value.contains("\uB2F4\uB2F9")
                || value.contains("\uBD80\uC11C")
                || value.contains("\uAE08\uC561")
                || value.contains("\uC608\uC0B0")
                || value.contains("\uC0C1\uD0DC")
                || value.contains("\uD56D\uBAA9")
                || value.contains("\uAD6C\uBD84")
                || value.contains("\uB0B4\uC6A9")
                || value.contains("\uBE44\uACE0")
                || value.contains("\uC9C4\uD589")
                || value.contains("\uC644\uB8CC")
                || value.contains("\uCC45\uC784")
                || value.contains("\uBD84\uB958");
    }

    private boolean looksLikeTitleOnlyCell(String value) {
        return value.length() > 20 && !value.contains("=") && !value.matches(".*[0-9].*");
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
        visual.append("\nDetected ").append(imageCount).append(" image object(s). ");
        visual.append("Page snapshot is queued for OCR and chart or diagram interpretation.");
        return visual.toString();
    }

    private String detectPageHeading(List<String> bodyLines, List<String> tableLines) {
        for (int index = 0; index < bodyLines.size(); index++) {
            String line = bodyLines.get(index);
            String nextLine = index + 1 < bodyLines.size() ? bodyLines.get(index + 1) : null;
            if (isLikelyHeading(line, nextLine)) {
                return line;
            }
        }
        if (!tableLines.isEmpty()) {
            int headerRowIndex = detectHeaderRowIndex(tableLines);
            if (headerRowIndex >= 0) {
                List<String> headers = splitColumns(tableLines.get(headerRowIndex));
                if (!headers.isEmpty()) {
                    return String.join(" / ", headers);
                }
            }
        }
        return null;
    }

    private boolean isLikelyHeading(String line, String nextLine) {
        if (!StringUtils.hasText(line) || line.contains("|")) {
            return false;
        }

        int score = 0;
        if (line.length() <= 40) {
            score += 2;
        } else if (line.length() <= 80) {
            score += 1;
        }

        if (matchesHeadingPattern(line)) {
            score += 3;
        }

        if (!line.endsWith(".") && !line.endsWith("\uB2E4.") && !line.endsWith("\uC694.")) {
            score += 1;
        }

        if (nextLine != null && line.length() < nextLine.length()) {
            score += 1;
        }

        return score >= 4;
    }

    private boolean matchesHeadingPattern(String line) {
        return ENGLISH_TITLE_PATTERN.matcher(line).matches()
                || NUMBER_SECTION_PATTERN.matcher(line).matches()
                || KOREAN_SECTION_PATTERN.matcher(line).matches()
                || ROMAN_SECTION_PATTERN.matcher(line).matches()
                || BRACKET_SECTION_PATTERN.matcher(line).matches()
                || YEAR_SECTION_PATTERN.matcher(line).matches();
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

    private void addVisualAsset(List<VisualAsset> visualAssets, VisualAsset visualAsset) {
        if (visualAsset == null || !visualAsset.hasData() || visualAssets.size() >= MAX_VISUAL_ASSETS) {
            return;
        }
        visualAssets.add(visualAsset);
    }

    private VisualAsset renderPageAsset(PDFRenderer renderer, int pageIndex, int pageNumber, String heading) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, 120);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return new VisualAsset(buildVisualAssetLabel(pageNumber, heading), "image/png", outputStream.toByteArray());
    }

    private String buildVisualAssetLabel(int pageNumber, String heading) {
        if (StringUtils.hasText(heading)) {
            return "PDF page " + pageNumber + " | title: " + heading;
        }
        return "PDF page " + pageNumber;
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