package com.example.BPA_project.controller;

import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.exception.FileStorageException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(Model model) {
        model.addAttribute("errorMessage", "업로드 파일은 10MB 이하만 가능합니다.");
        return "error";
    }

    @ExceptionHandler({
            DocumentAnalysisException.class,
            FileStorageException.class
    })
    public String handleKnownException(RuntimeException exception, Model model) {
        model.addAttribute("errorMessage", exception.getMessage());
        return "error";
    }
}