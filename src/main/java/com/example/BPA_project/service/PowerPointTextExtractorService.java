package com.example.BPA_project.service;

import com.example.BPA_project.exception.DocumentAnalysisException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
                slideNumber++;
            }

            DocumentExtractionResult result = new DocumentExtractionResult(
                    joinSections(bodySections),
                    joinSections(tableSections),
                    joinSections(visualSections)
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
            return;
        }

        String className = shape.getClass().getSimpleName();
        if (className.contains("GraphicFrame") || className.contains("Diagram") || className.contains("Chart")) {
            extraction.visualLines.add(buildVisualNote("Chart or diagram", shape.getShapeName()));
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
        List<String> content = new ArrayList<>();
        content.addAll(tableLines);
        content.addAll(buildStructuredTableRows(tableLines));
        return buildSection(slideNumber, slideTitle, content);
    }

    private String buildVisualSection(int slideNumber, String slideTitle, List<String> visualLines) {
        return buildSection(slideNumber, slideTitle, visualLines);
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
        String[] parts = line.split("\\|");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            normalized.add(normalize(part));
        }
        return normalized;
    }

    private String detectSlideTitle(List<String> bodyLines, List<String> tableLines) {
        for (String line : bodyLines) {
            if (StringUtils.hasText(line) && line.length() <= 120) {
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

    private static class SlideExtraction {
        private final List<String> bodyLines = new ArrayList<>();
        private final List<String> tableLines = new ArrayList<>();
        private final List<String> visualLines = new ArrayList<>();
    }
}