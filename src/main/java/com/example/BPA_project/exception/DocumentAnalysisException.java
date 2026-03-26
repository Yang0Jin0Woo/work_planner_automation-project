package com.example.BPA_project.exception;

public class DocumentAnalysisException extends RuntimeException {

    public DocumentAnalysisException(String message) {
        super(message);
    }

    public DocumentAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
