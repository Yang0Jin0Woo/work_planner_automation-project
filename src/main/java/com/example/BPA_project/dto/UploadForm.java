package com.example.BPA_project.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public class UploadForm {

    @NotNull(message = "문서 유형을 선택해주세요.")
    private DocumentType documentType;

    @NotNull(message = "파일을 업로드해주세요.")
    private MultipartFile file;

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}