package com.example.BPA_project.service;

public class AnalysisInputPreparation {

    private final String analysisText;
    private final String visualStatus;
    private final String visualNote;

    public AnalysisInputPreparation(String analysisText, String visualStatus, String visualNote) {
        this.analysisText = analysisText;
        this.visualStatus = visualStatus;
        this.visualNote = visualNote;
    }

    public String getAnalysisText() {
        return analysisText;
    }

    public String getVisualStatus() {
        return visualStatus;
    }

    public String getVisualNote() {
        return visualNote;
    }
}