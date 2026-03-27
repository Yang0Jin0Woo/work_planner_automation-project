package com.example.BPA_project.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;

public class DocumentExtractionResult {

    private final String bodyText;
    private final String tableText;
    private final String visualText;
    private final List<VisualAsset> visualAssets;

    public DocumentExtractionResult(String bodyText, String tableText, String visualText) {
        this(bodyText, tableText, visualText, List.of());
    }

    public DocumentExtractionResult(String bodyText,
                                    String tableText,
                                    String visualText,
                                    List<VisualAsset> visualAssets) {
        this.bodyText = normalize(bodyText);
        this.tableText = normalize(tableText);
        this.visualText = normalize(visualText);
        this.visualAssets = visualAssets == null ? List.of() : List.copyOf(visualAssets);
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getTableText() {
        return tableText;
    }

    public String getVisualText() {
        return visualText;
    }

    public List<VisualAsset> getVisualAssets() {
        return Collections.unmodifiableList(visualAssets);
    }

    public String toAnalysisText() {
        return toAnalysisText(null);
    }

    public String toAnalysisText(String visualAnalysisText) {
        StringBuilder combined = new StringBuilder();
        appendSection(combined, "Body text", bodyText);
        appendSection(combined, "Table text", tableText);
        appendSection(combined, "Image and diagram metadata", visualText);
        appendSection(combined, "OCR and chart interpretation", normalize(visualAnalysisText));
        return combined.toString().trim();
    }

    public boolean hasAnyContent() {
        return StringUtils.hasText(bodyText)
                || StringUtils.hasText(tableText)
                || StringUtils.hasText(visualText);
    }

    public boolean hasVisualAssets() {
        return visualAssets.stream().anyMatch(VisualAsset::hasData);
    }

    private void appendSection(StringBuilder combined, String title, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (!combined.isEmpty()) {
            combined.append("\n\n");
        }
        combined.append('[').append(title).append("]\n").append(content);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}