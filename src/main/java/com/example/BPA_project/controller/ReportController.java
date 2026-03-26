package com.example.BPA_project.controller;

import com.example.BPA_project.model.AnalysisSession;
import com.example.BPA_project.service.AnalysisSessionService;
import com.example.BPA_project.service.PdfReportService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final AnalysisSessionService analysisSessionService;
    private final PdfReportService pdfReportService;

    public ReportController(AnalysisSessionService analysisSessionService, PdfReportService pdfReportService) {
        this.analysisSessionService = analysisSessionService;
        this.pdfReportService = pdfReportService;
    }

    @GetMapping("/{sessionId}/download")
    public ResponseEntity<Resource> download(@PathVariable String sessionId) throws IOException {
        AnalysisSession session = analysisSessionService.getSession(sessionId);
        Path pdf = pdfReportService.generate(session);
        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(pdf));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(pdf.getFileName().toString()).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(resource.contentLength())
                .body(resource);
    }
}
