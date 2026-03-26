package com.example.BPA_project.model;

import com.example.BPA_project.dto.AnalysisResultDto;
import java.time.LocalDateTime;

public class AnalysisSession {

    private String sessionId;
    private StoredFileInfo storedFileInfo;
    private AnalysisResultDto analysisResult;
    private String sourceText;
    private String sourceStatus;
    private String message;
    private String reportPath;
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

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
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
