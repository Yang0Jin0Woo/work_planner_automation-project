package com.example.BPA_project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_session")
public class AnalysisSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "document_type")
    private String documentType;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "saved_file_name")
    private String savedFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "extension")
    private String extension;

    @Column(name = "absolute_path")
    private String absolutePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Lob
    @Column(name = "source_text", columnDefinition = "LONGTEXT")
    private String sourceText;

    @Column(name = "source_status")
    private String sourceStatus;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "visual_analysis_status")
    private String visualAnalysisStatus;

    @Lob
    @Column(name = "visual_analysis_note", columnDefinition = "LONGTEXT")
    private String visualAnalysisNote;

    @Column(name = "missing_check_status")
    private String missingCheckStatus;

    @Lob
    @Column(name = "missing_check_notes_json", columnDefinition = "LONGTEXT")
    private String missingCheckNotesJson;

    @Lob
    @Column(name = "analysis_result_json", columnDefinition = "LONGTEXT")
    private String analysisResultJson;

    @Column(name = "analyzed_chunk_count")
    private Integer analyzedChunkCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getSavedFileName() {
        return savedFileName;
    }

    public void setSavedFileName(String savedFileName) {
        this.savedFileName = savedFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
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

    public String getMissingCheckStatus() {
        return missingCheckStatus;
    }

    public void setMissingCheckStatus(String missingCheckStatus) {
        this.missingCheckStatus = missingCheckStatus;
    }

    public String getMissingCheckNotesJson() {
        return missingCheckNotesJson;
    }

    public void setMissingCheckNotesJson(String missingCheckNotesJson) {
        this.missingCheckNotesJson = missingCheckNotesJson;
    }

    public String getAnalysisResultJson() {
        return analysisResultJson;
    }

    public void setAnalysisResultJson(String analysisResultJson) {
        this.analysisResultJson = analysisResultJson;
    }

    public Integer getAnalyzedChunkCount() {
        return analyzedChunkCount;
    }

    public void setAnalyzedChunkCount(Integer analyzedChunkCount) {
        this.analyzedChunkCount = analyzedChunkCount;
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