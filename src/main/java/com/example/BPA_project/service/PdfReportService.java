package com.example.BPA_project.service;

import com.example.BPA_project.exception.PdfGenerationException;
import com.example.BPA_project.model.AnalysisSession;
import com.example.BPA_project.util.FileNameUtils;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PdfReportService {

    private final HtmlReportService htmlReportService;
    private final FileStorageService fileStorageService;
    private final AnalysisSessionService analysisSessionService;

    public PdfReportService(HtmlReportService htmlReportService,
                            FileStorageService fileStorageService,
                            AnalysisSessionService analysisSessionService) {
        this.htmlReportService = htmlReportService;
        this.fileStorageService = fileStorageService;
        this.analysisSessionService = analysisSessionService;
    }

    public Path generate(AnalysisSession session) {
        String html = htmlReportService.render(session);
        String baseName = FileNameUtils.safeBaseName(session.getStoredFileInfo().getOriginalFileName());
        Path target = fileStorageService.reportDir()
                .resolve(baseName + "-report-" + UUID.randomUUID() + ".pdf")
                .toAbsolutePath()
                .normalize();

        try (OutputStream outputStream = Files.newOutputStream(target)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            analysisSessionService.updateReportPath(session.getSessionId(), target.toString());
            return target;
        } catch (IOException exception) {
            throw new PdfGenerationException("Failed to save the PDF file.", exception);
        } catch (Exception exception) {
            throw new PdfGenerationException("Failed to render the PDF report.", exception);
        }
    }
}
