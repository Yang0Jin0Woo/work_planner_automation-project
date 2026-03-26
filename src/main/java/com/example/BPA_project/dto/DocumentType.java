package com.example.BPA_project.dto;

public enum DocumentType {
    MEETING("회의자료"),
    REPORT("보고서"),
    PROPOSAL("제안서");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}