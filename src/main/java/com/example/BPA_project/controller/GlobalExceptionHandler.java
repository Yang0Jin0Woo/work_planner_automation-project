package com.example.BPA_project.controller;

import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.exception.FileStorageException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            DocumentAnalysisException.class,
            FileStorageException.class
    })
    public String handleKnownException(RuntimeException exception, Model model) {
        model.addAttribute("errorMessage", exception.getMessage());
        return "error";
    }
}