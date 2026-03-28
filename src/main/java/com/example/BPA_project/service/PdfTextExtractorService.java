package com.example.BPA_project.service;

import com.example.BPA_project.exception.DocumentAnalysisException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern KOREAN_SECTION_PATTERN = Pattern.compile("^(?:[가-하][.]|[가-하][)])\\s*.+$");
    private static final Pattern ROMAN_SECTION_PATTERN = Pattern.compile("^(?:[Ⅰ-Ⅹ]+[.]|[①-⑳])\\s*.+$");
    private static final Pattern BRACKET_SECTION_PATTERN = Pattern.compile("^\\[[^\\]]+\\]\\s*.+$");
    private static final Pattern YEAR_SECTION_PATTERN = Pattern.compile("^[0-9]{4}년\\s+.+$");
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
        TableInterpretation interpretation = interpretTable(tableLines);
        List<String> content = new ArrayList<>();
        if (StringUtils.hasText(interpretation.tableTitle())) {
            content.add("Table title: " + interpretation.tableTitle());
        }
        if (StringUtils.hasText(interpretation.tableType())) {
            content.add("Table type: " + interpretation.tableType());
        }
        content.addAll(tableLines);
        content.addAll(interpretation.structuredRows());
        return buildSection("Page", pageNumber, heading, content);
    }

    private TableInterpretation interpretTable(List<String> tableLines) {
        List<String> structuredRows = new ArrayList<>();
        if (tableLines.size() < 2) {
            return new TableInterpretation(null, null, structuredRows);
        }

        int headerRowIndex = detectHeaderRowIndex(tableLines);
        if (headerRowIndex < 0 || headerRowIndex >= tableLines.size() - 1) {
            String tableTitle = detectTableTitle(tableLines, 0);
            return new TableInterpretation(tableTitle, detectTableType(tableTitle, List.of()), structuredRows);
        }

        int headerEndIndex = detectHeaderEndIndex(tableLines, headerRowIndex);
        List<String> headers = mergeHeaderRows(tableLines, headerRowIndex, headerEndIndex);
        String tableTitle = detectTableTitle(tableLines, headerRowIndex);
        String tableType = detectTableType(tableTitle, headers);
        if (headers.size() < 2) {
            return new TableInterpretation(tableTitle, tableType, structuredRows);
        }

        String rowContext = null;
        List<String> previousValues = null;
        for (int rowIndex = headerEndIndex + 1; rowIndex < tableLines.size(); rowIndex++) {
            List<String> rawValues = splitColumns(tableLines.get(rowIndex));
            List<String> values = fillForwardBlanks(alignColumns(rawValues, headers.size()), previousValues);
            if (values.isEmpty() || isSeparatorRow(values) || looksLikeAnotherHeader(values)) {
                continue;
            }

            String subsectionLabel = detectSubsectionLabel(values, headers.size());
            if (StringUtils.hasText(subsectionLabel)) {
                rowContext = subsectionLabel;
                continue;
            }

            List<String> pairs = isKeyValueStyleRow(values, headers.size())
                    ? buildKeyValuePairs(values, tableType, rowContext)
                    : buildStructuredPairs(headers, values, tableType, rowContext);
            if (!pairs.isEmpty()) {
                structuredRows.add("Structured row: " + String.join(", ", pairs));
                previousValues = values;
            }
        }
        return new TableInterpretation(tableTitle, tableType, structuredRows);
    }

    private List<String> buildStructuredPairs(List<String> headers, List<String> values, String tableType, String rowContext) {
        List<String> pairs = new ArrayList<>();
        if (StringUtils.hasText(rowContext)) {
            pairs.add("section=" + rowContext);
        }
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            String header = normalize(headers.get(columnIndex));
            String value = columnIndex < values.size() ? normalize(values.get(columnIndex)) : null;
            if (!StringUtils.hasText(header) || !StringUtils.hasText(value) || "-".equals(value)) {
                continue;
            }
            pairs.add(header + "=" + value);

            String normalizedDate = normalizeDateValue(header, value, tableType);
            if (StringUtils.hasText(normalizedDate)) {
                pairs.add(header + "(normalized)=" + normalizedDate);
            }

            String normalizedAmount = normalizeAmountValue(header, value, tableType);
            if (StringUtils.hasText(normalizedAmount)) {
                pairs.add(header + "(normalized)=" + normalizedAmount);
            }
        }
        return pairs;
    }

    private List<String> buildKeyValuePairs(List<String> values, String tableType, String rowContext) {
        List<String> pairs = new ArrayList<>();
        List<String> nonEmpty = nonEmptyColumns(values);
        if (nonEmpty.size() < 2) {
            return pairs;
        }
        if (StringUtils.hasText(rowContext)) {
            pairs.add("section=" + rowContext);
        }

        String key = nonEmpty.get(0);
        String value = nonEmpty.get(1);
        pairs.add(key + "=" + value);

        String normalizedDate = normalizeDateValue(key, value, tableType);
        if (StringUtils.hasText(normalizedDate)) {
            pairs.add(key + "(normalized)=" + normalizedDate);
        }

        String normalizedAmount = normalizeAmountValue(key, value, tableType);
        if (StringUtils.hasText(normalizedAmount)) {
            pairs.add(key + "(normalized)=" + normalizedAmount);
        }
        return pairs;
    }

    private String detectTableType(String tableTitle, List<String> headers) {
        String merged = ((tableTitle == null ? "" : tableTitle) + " " + String.join(" ", headers)).toLowerCase(Locale.ROOT);
        if (containsAny(merged, "일정", "기한", "마감", "due", "date", "schedule")) {
            return "SCHEDULE";
        }
        if (containsAny(merged, "예산", "금액", "비용", "매출", "budget", "amount", "cost")) {
            return "BUDGET";
        }
        if (containsAny(merged, "담당", "부서", "책임", "역할", "owner", "team", "role")) {
            return "OWNER";
        }
        if (containsAny(merged, "상태", "진행", "완료", "status", "progress")) {
            return "STATUS";
        }
        return "GENERAL";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeDateValue(String header, String value, String tableType) {
        String lowerHeader = header.toLowerCase(Locale.ROOT);
        if (!("SCHEDULE".equals(tableType)
                || lowerHeader.contains("일정")
                || lowerHeader.contains("기한")
                || lowerHeader.contains("날짜")
                || lowerHeader.contains("date")
                || lowerHeader.contains("due"))) {
            return null;
        }

        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 8) {
            return digits.substring(0, 4) + "-" + digits.substring(4, 6) + "-" + digits.substring(6, 8);
        }
        if (digits.length() == 6) {
            return "20" + digits.substring(0, 2) + "-" + digits.substring(2, 4) + "-" + digits.substring(4, 6);
        }
        return null;
    }

    private String normalizeAmountValue(String header, String value, String tableType) {
        String lowerHeader = header.toLowerCase(Locale.ROOT);
        if (!("BUDGET".equals(tableType)
                || lowerHeader.contains("금액")
                || lowerHeader.contains("예산")
                || lowerHeader.contains("비용")
                || lowerHeader.contains("amount")
                || lowerHeader.contains("cost"))) {
            return null;
        }

        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (value.contains("만원")) {
            return digits + "만원";
        }
        return digits + "원";
    }

    private int detectHeaderRowIndex(List<String> tableLines) {
        int candidateLimit = Math.min(4, tableLines.size());
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

    private int detectHeaderEndIndex(List<String> tableLines, int headerRowIndex) {
        int endIndex = headerRowIndex;
        int expectedColumns = splitColumns(tableLines.get(headerRowIndex)).size();
        for (int index = headerRowIndex + 1; index < Math.min(tableLines.size(), headerRowIndex + 3); index++) {
            List<String> columns = splitColumns(tableLines.get(index));
            if (columns.size() < 2) {
                break;
            }
            if (Math.abs(columns.size() - expectedColumns) > 1) {
                break;
            }
            if (scoreHeaderRow(columns, index) >= 4 && !containsLikelyDataValue(columns)) {
                endIndex = index;
                expectedColumns = Math.max(expectedColumns, columns.size());
                continue;
            }
            break;
        }
        return endIndex;
    }

    private List<String> mergeHeaderRows(List<String> tableLines, int headerStartIndex, int headerEndIndex) {
        int width = 0;
        List<List<String>> headerRows = new ArrayList<>();
        for (int index = headerStartIndex; index <= headerEndIndex; index++) {
            List<String> columns = splitColumns(tableLines.get(index));
            width = Math.max(width, columns.size());
            headerRows.add(columns);
        }

        List<String> mergedHeaders = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < width; columnIndex++) {
            List<String> parts = new ArrayList<>();
            for (List<String> headerRow : headerRows) {
                String value = columnIndex < headerRow.size() ? normalize(headerRow.get(columnIndex)) : null;
                if (!StringUtils.hasText(value) || "-".equals(value)) {
                    continue;
                }
                if (parts.isEmpty() || !parts.get(parts.size() - 1).equals(value)) {
                    parts.add(value);
                }
            }
            mergedHeaders.add(parts.isEmpty() ? "column" + (columnIndex + 1) : String.join(" / ", parts));
        }
        return mergedHeaders;
    }

    private String detectTableTitle(List<String> tableLines, int headerRowIndex) {
        List<String> titleLines = new ArrayList<>();
        for (int index = 0; index < headerRowIndex; index++) {
            List<String> columns = splitColumns(tableLines.get(index));
            if (columns.size() > 2) {
                continue;
            }
            List<String> nonEmpty = nonEmptyColumns(columns);
            if (nonEmpty.size() != 1) {
                continue;
            }
            String candidate = nonEmpty.get(0);
            if (looksLikeTitleOnlyCell(candidate) || isLikelyHeading(candidate, null)) {
                titleLines.add(candidate);
            }
        }
        return titleLines.isEmpty() ? null : String.join(" | ", titleLines);
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
            if (normalized.matches(".*[0-9]{2,}.*")) {
                score -= 1;
            }
        }

        if (filledColumns == columns.size()) {
            score += 1;
        }
        score += keywordMatches * 2;
        return score;
    }

    private boolean isHeaderKeyword(String value) {
        return value.contains("일정")
                || value.contains("기한")
                || value.contains("날짜")
                || value.contains("담당")
                || value.contains("부서")
                || value.contains("금액")
                || value.contains("예산")
                || value.contains("상태")
                || value.contains("항목")
                || value.contains("구분")
                || value.contains("내용")
                || value.contains("비고")
                || value.contains("진행")
                || value.contains("완료")
                || value.contains("책임")
                || value.contains("분류");
    }

    private boolean looksLikeTitleOnlyCell(String value) {
        return value.length() > 20 && !value.contains("=") && !value.matches(".*[0-9].*");
    }

    private boolean containsLikelyDataValue(List<String> columns) {
        for (String column : columns) {
            String normalized = normalize(column);
            if (!StringUtils.hasText(normalized) || "-".equals(normalized)) {
                continue;
            }
            if (normalized.matches(".*[0-9]{2,}.*") || normalized.contains("%") || normalized.contains("/") || normalized.contains("-")) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeAnotherHeader(List<String> values) {
        return scoreHeaderRow(values, 1) >= 5 && !containsLikelyDataValue(values);
    }
    private List<String> fillForwardBlanks(List<String> values, List<String> previousValues) {
        if (previousValues == null || previousValues.isEmpty() || values.isEmpty()) {
            return values;
        }
        List<String> filled = new ArrayList<>(values);
        int limit = Math.min(filled.size(), previousValues.size());
        for (int index = 0; index < limit; index++) {
            String current = normalize(filled.get(index));
            String previous = normalize(previousValues.get(index));
            if ((!StringUtils.hasText(current) || "-".equals(current))
                    && StringUtils.hasText(previous)
                    && index == 0) {
                filled.set(index, previous);
            }
        }
        return filled;
    }

    private String detectSubsectionLabel(List<String> values, int headerSize) {
        List<String> nonEmpty = nonEmptyColumns(values);
        if (nonEmpty.size() != 1) {
            return null;
        }
        String candidate = nonEmpty.get(0);
        if (candidate.length() > 40) {
            return null;
        }
        if (isLikelyHeading(candidate, null) || candidate.endsWith("단계") || candidate.endsWith("구간") || candidate.endsWith("일정")) {
            return candidate;
        }
        if (headerSize > 2 && !candidate.matches(".*[0-9]{2,}.*")) {
            return candidate;
        }
        return null;
    }

    private boolean isKeyValueStyleRow(List<String> values, int headerSize) {
        List<String> nonEmpty = nonEmptyColumns(values);
        return headerSize <= 2 && nonEmpty.size() >= 2 && nonEmpty.get(0).length() <= 20;
    }

    private boolean isSeparatorRow(List<String> values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (!normalized.matches("[-=]{2,}")) {
                return false;
            }
        }
        return true;
    }

    private List<String> alignColumns(List<String> values, int headerSize) {
        List<String> aligned = new ArrayList<>(values);
        if (aligned.size() > headerSize) {
            List<String> trimmed = new ArrayList<>(aligned.subList(0, headerSize - 1));
            trimmed.add(String.join(" / ", aligned.subList(headerSize - 1, aligned.size())));
            return trimmed;
        }
        while (aligned.size() < headerSize) {
            aligned.add("-");
        }
        return aligned;
    }

    private List<String> nonEmptyColumns(List<String> columns) {
        List<String> nonEmpty = new ArrayList<>();
        for (String column : columns) {
            String normalized = normalize(column);
            if (StringUtils.hasText(normalized) && !"-".equals(normalized)) {
                nonEmpty.add(normalized);
            }
        }
        return nonEmpty;
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
                List<String> headers = mergeHeaderRows(tableLines, headerRowIndex, detectHeaderEndIndex(tableLines, headerRowIndex));
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

        if (!line.endsWith(".") && !line.endsWith("다.") && !line.endsWith("요.")) {
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

    private record TableInterpretation(String tableTitle, String tableType, List<String> structuredRows) {
    }

    private static class PageExtraction {
        private final List<String> bodyLines = new ArrayList<>();
        private final List<String> tableLines = new ArrayList<>();
    }
}