package com.example.BPA_project.service;

import org.springframework.util.StringUtils;

public class DocumentExtractionResult {

    private final String bodyText;
    private final String tableText;
    private final String visualText;

    public DocumentExtractionResult(String bodyText, String tableText, String visualText) {
        this.bodyText = normalize(bodyText);
        this.tableText = normalize(tableText);
        this.visualText = normalize(visualText);
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

    public String toAnalysisText() {
        StringBuilder combined = new StringBuilder();
        appendSection(combined, "Body text", bodyText);
        appendSection(combined, "Table text", tableText);
        appendSection(combined, "Image and diagram metadata", visualText);
        return combined.toString().trim();
    }

    public boolean hasAnyContent() {
        return StringUtils.hasText(bodyText)
                || StringUtils.hasText(tableText)
                || StringUtils.hasText(visualText);
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