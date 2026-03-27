package com.example.BPA_project.service;

public interface DocumentTextExtractor {

    boolean supports(String extension);

    String extractText(byte[] fileBytes, String originalFileName);
}
