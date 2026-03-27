package com.example.BPA_project.model;

import com.example.BPA_project.dto.AnalysisResultDto;
import java.time.LocalDateTime;
import java.util.Locale;

public class AnalysisSession {

    private String sessionId;
    private StoredFileInfo storedFileInfo;
    private AnalysisResultDto analysisResult;
    private String sourceText;
    private String sourceStatus;
    private String message;
    private String visualAnalysisStatus;
    private String visualAnalysisNote;
    private String reportPath;
    private int analyzedChunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public StoredFileInfo getStoredFileInfo() {
        return storedFileInfo;
    }

    public void setStoredFileInfo(StoredFileInfo storedFileInfo) {
        this.storedFileInfo = storedFileInfo;
    }

    public AnalysisResultDto getAnalysisResult() {
        return analysisResult;
    }

    public void setAnalysisResult(AnalysisResultDto analysisResult) {
        this.analysisResult = analysisResult;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getSourceStatus() {
        return sourceStatus;
    }

    public void setSourceStatus(String sourceStatus) {
        this.sourceStatus = sourceStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getVisualAnalysisStatus() {
        return visualAnalysisStatus;
    }

    public void setVisualAnalysisStatus(String visualAnalysisStatus) {
        this.visualAnalysisStatus = visualAnalysisStatus;
    }

    public String getVisualAnalysisNote() {
        return visualAnalysisNote;
    }

    public void setVisualAnalysisNote(String visualAnalysisNote) {
        this.visualAnalysisNote = visualAnalysisNote;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public int getAnalyzedChunkCount() {
        return analyzedChunkCount;
    }

    public void setAnalyzedChunkCount(int analyzedChunkCount) {
        this.analyzedChunkCount = analyzedChunkCount;
    }

    public int getSourceTextLength() {
        return sourceText == null ? 0 : sourceText.length();
    }

    public String getFormattedFileSize() {
        if (storedFileInfo == null) {
            return "0 B";
        }

        long size = storedFileInfo.getSize();
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", size / 1024.0);
        }
        return String.format(Locale.US, "%.2f MB", size / (1024.0 * 1024.0));
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}