package com.example.BPA_project.dto;

public enum DocumentType {
    MEETING("Meeting Material"),
    REPORT("Report"),
    PROPOSAL("Proposal");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
