package com.example.BPA_project.service;

import com.example.BPA_project.exception.DocumentAnalysisException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.poi.sl.usermodel.GroupShape;
import org.apache.poi.sl.usermodel.PictureShape;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TableShape;
import org.apache.poi.sl.usermodel.TextShape;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PowerPointTextExtractorService implements DocumentTextExtractor {

    private static final Pattern NUMBER_SECTION_PATTERN = Pattern.compile("^(?:[0-9]{1,2}(?:\\.[0-9]{1,2})*|[0-9]{1,2}[)])\\s*.+$");
    private static final Pattern KOREAN_SECTION_PATTERN = Pattern.compile("^(?:[\\uAC00-\\uD558][.]|[\\uAC00-\\uD558][)])\\s*.+$");
    private static final Pattern ROMAN_SECTION_PATTERN = Pattern.compile("^(?:[\\u2160-\\u2169]+[.]|[\\u2460-\\u2473])\\s*.+$");
    private static final Pattern BRACKET_SECTION_PATTERN = Pattern.compile("^\\[[^\\]]+\\]\\s*.+$");
    private static final Pattern YEAR_SECTION_PATTERN = Pattern.compile("^[0-9]{4}\\uB144\\s+.+$");
    private static final int MAX_VISUAL_ASSETS = 3;

    @Override
    public boolean supports(String extension) {
        return "ppt".equals(extension) || "pptx".equals(extension);
    }

    @Override
    public DocumentExtractionResult extract(byte[] fileBytes, String originalFileName) {
        try (SlideShow<?, ?> slideShow = SlideShowFactory.create(new ByteArrayInputStream(fileBytes))) {
            List<String> bodySections = new ArrayList<>();
            List<String> tableSections = new ArrayList<>();
            List<String> visualSections = new ArrayList<>();
            List<VisualAsset> visualAssets = new ArrayList<>();

            int slideNumber = 1;
            for (Slide<?, ?> slide : slideShow.getSlides()) {
                SlideExtraction slideExtraction = new SlideExtraction();
                collectShapes(slide.getShapes(), slideExtraction);
                String slideTitle = detectSlideTitle(slideExtraction.bodyLines, slideExtraction.tableLines);

                if (!slideExtraction.bodyLines.isEmpty()) {
                    bodySections.add(buildSection(slideNumber, slideTitle, slideExtraction.bodyLines));
                }
                if (!slideExtraction.tableLines.isEmpty()) {
                    tableSections.add(buildTableSection(slideNumber, slideTitle, slideExtraction.tableLines));
                }
                if (!slideExtraction.visualLines.isEmpty()) {
                    visualSections.add(buildVisualSection(slideNumber, slideTitle, slideExtraction.visualLines));
                }
                if (slideExtraction.hasVisualAsset) {
                    addVisualAsset(visualAssets, renderSlideAsset(slide, slideShow.getPageSize(), slideNumber, slideTitle));
                }
                slideNumber++;
            }

            DocumentExtractionResult result = new DocumentExtractionResult(
                    joinSections(bodySections),
                    joinSections(tableSections),
                    joinSections(visualSections),
                    visualAssets
            );
            if (!result.hasAnyContent()) {
                throw new DocumentAnalysisException("No extractable text was found in the PowerPoint file.");
            }
            return result;
        } catch (IOException exception) {
            throw new DocumentAnalysisException("Failed to extract text from the PowerPoint file.", exception);
        }
    }

    private void collectShapes(List<? extends Shape<?, ?>> shapes, SlideExtraction extraction) {
        for (Shape<?, ?> shape : shapes) {
            collectShape(shape, extraction);
        }
    }

    private void collectShape(Shape<?, ?> shape, SlideExtraction extraction) {
        if (shape instanceof GroupShape<?, ?> groupShape) {
            collectShapes(groupShape.getShapes(), extraction);
            return;
        }

        if (shape instanceof TableShape<?, ?> tableShape) {
            extractTable(tableShape, extraction.tableLines);
            return;
        }

        if (shape instanceof TextShape<?, ?> textShape) {
            String text = normalize(textShape.getText());
            if (StringUtils.hasText(text)) {
                extraction.bodyLines.add(text);
            }
        }

        if (shape instanceof PictureShape<?, ?> pictureShape) {
            extraction.visualLines.add(buildVisualNote("Image", pictureShape.getShapeName()));
            extraction.hasVisualAsset = true;
            return;
        }

        String className = shape.getClass().getSimpleName();
        if (className.contains("GraphicFrame") || className.contains("Diagram") || className.contains("Chart")) {
            extraction.visualLines.add(buildVisualNote("Chart or diagram", shape.getShapeName()));
            extraction.hasVisualAsset = true;
            return;
        }

        if (!(shape instanceof TextShape<?, ?>) && StringUtils.hasText(shape.getShapeName())) {
            extraction.visualLines.add(buildVisualNote("Visual object", shape.getShapeName()));
        }
    }

    private String buildSection(int slideNumber, String slideTitle, List<String> lines) {
        StringBuilder section = new StringBuilder();
        section.append("Slide ").append(slideNumber);
        if (StringUtils.hasText(slideTitle)) {
            section.append(" | title: ").append(slideTitle);
        }
        section.append("\n").append(String.join("\n", lines));
        return section.toString();
    }

    private String buildTableSection(int slideNumber, String slideTitle, List<String> tableLines) {
        TableInterpretation interpretation = interpretTable(tableLines);
        List<String> content = new ArrayList<>();
        if (StringUtils.hasText(interpretation.tableTitle())) {
            content.add("Table title: " + interpretation.tableTitle());
        }
        content.addAll(tableLines);
        content.addAll(interpretation.structuredRows());
        return buildSection(slideNumber, slideTitle, content);
    }

    private String buildVisualSection(int slideNumber, String slideTitle, List<String> visualLines) {
        List<String> content = new ArrayList<>(visualLines);
        content.add("Slide snapshot is queued for OCR and chart or diagram interpretation.");
        return buildSection(slideNumber, slideTitle, content);
    }

    private void extractTable(TableShape<?, ?> tableShape, List<String> tableLines) {
        int rowCount = tableShape.getNumberOfRows();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            List<String> cells = new ArrayList<>();
            int columnCount = tableShape.getNumberOfColumns();
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                TableCell<?, ?> cell = tableShape.getCell(rowIndex, columnIndex);
                String text = cell == null ? null : normalize(cell.getText());
                cells.add(StringUtils.hasText(text) ? text : "-");
            }
            tableLines.add(String.join(" | ", cells));
        }
    }

    private TableInterpretation interpretTable(List<String> tableLines) {
        List<String> structuredRows = new ArrayList<>();
        if (tableLines.size() < 2) {
            return new TableInterpretation(null, structuredRows);
        }

        int headerRowIndex = detectHeaderRowIndex(tableLines);
        if (headerRowIndex < 0 || headerRowIndex >= tableLines.size() - 1) {
            return new TableInterpretation(detectTableTitle(tableLines, 0), structuredRows);
        }

        int headerEndIndex = detectHeaderEndIndex(tableLines, headerRowIndex);
        List<String> headers = mergeHeaderRows(tableLines, headerRowIndex, headerEndIndex);
        if (headers.size() < 2) {
            return new TableInterpretation(detectTableTitle(tableLines, headerRowIndex), structuredRows);
        }

        String tableTitle = detectTableTitle(tableLines, headerRowIndex);
        for (int rowIndex = headerEndIndex + 1; rowIndex < tableLines.size(); rowIndex++) {
            List<String> values = alignColumns(splitColumns(tableLines.get(rowIndex)), headers.size());
            if (values.isEmpty() || isSeparatorRow(values) || looksLikeAnotherHeader(values)) {
                continue;
            }

            List<String> pairs = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                String header = normalize(headers.get(columnIndex));
                String value = columnIndex < values.size() ? normalize(values.get(columnIndex)) : null;
                if (!StringUtils.hasText(header) || !StringUtils.hasText(value) || "-".equals(value)) {
                    continue;
                }
                pairs.add(header + "=" + value);
            }
            if (!pairs.isEmpty()) {
                structuredRows.add("Structured row: " + String.join(", ", pairs));
            }
        }
        return new TableInterpretation(tableTitle, structuredRows);
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
        String[] parts = line.split("\\|");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            normalized.add(normalize(part));
        }
        return normalized;
    }

    private String detectSlideTitle(List<String> bodyLines, List<String> tableLines) {
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

        if (!line.endsWith(".") && !line.endsWith("\uB2E4.") && !line.endsWith("\uC694.")) {
            score += 1;
        }

        if (nextLine != null && line.length() < nextLine.length()) {
            score += 1;
        }

        return score >= 4;
    }

    private boolean matchesHeadingPattern(String line) {
        return NUMBER_SECTION_PATTERN.matcher(line).matches()
                || KOREAN_SECTION_PATTERN.matcher(line).matches()
                || ROMAN_SECTION_PATTERN.matcher(line).matches()
                || BRACKET_SECTION_PATTERN.matcher(line).matches()
                || YEAR_SECTION_PATTERN.matcher(line).matches();
    }

    private void addVisualAsset(List<VisualAsset> visualAssets, VisualAsset visualAsset) {
        if (visualAsset == null || !visualAsset.hasData() || visualAssets.size() >= MAX_VISUAL_ASSETS) {
            return;
        }
        visualAssets.add(visualAsset);
    }

    private VisualAsset renderSlideAsset(Slide<?, ?> slide, Dimension pageSize, int slideNumber, String slideTitle) throws IOException {
        int width = Math.max(pageSize.width, 960);
        int height = Math.max(pageSize.height, 540);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setPaint(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        slide.draw(graphics);
        graphics.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return new VisualAsset(buildVisualAssetLabel(slideNumber, slideTitle), "image/png", outputStream.toByteArray());
    }

    private String buildVisualAssetLabel(int slideNumber, String slideTitle) {
        if (StringUtils.hasText(slideTitle)) {
            return "Slide " + slideNumber + " | title: " + slideTitle;
        }
        return "Slide " + slideNumber;
    }

    private String buildVisualNote(String kind, String shapeName) {
        if (!StringUtils.hasText(shapeName)) {
            return kind + " detected";
        }
        return kind + " detected: " + shapeName.trim();
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

    private record TableInterpretation(String tableTitle, List<String> structuredRows) {
    }

    private static class SlideExtraction {
        private final List<String> bodyLines = new ArrayList<>();
        private final List<String> tableLines = new ArrayList<>();
        private final List<String> visualLines = new ArrayList<>();
        private boolean hasVisualAsset;
    }
}