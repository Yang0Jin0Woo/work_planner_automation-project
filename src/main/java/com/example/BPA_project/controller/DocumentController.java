package com.example.BPA_project.controller;

import com.example.BPA_project.dto.AnalysisResultDto;
import com.example.BPA_project.dto.UploadForm;
import com.example.BPA_project.model.AnalysisSession;
import com.example.BPA_project.model.StoredFileInfo;
import com.example.BPA_project.service.AnalysisSessionService;
import com.example.BPA_project.service.FileStorageService;
import com.example.BPA_project.service.OpenAiPlanningService;
import com.example.BPA_project.service.PdfTextExtractorService;
import com.example.BPA_project.util.FileNameUtils;
import jakarta.validation.Valid;
import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final FileStorageService fileStorageService;
    private final PdfTextExtractorService pdfTextExtractorService;
    private final OpenAiPlanningService openAiPlanningService;
    private final AnalysisSessionService analysisSessionService;

    public DocumentController(FileStorageService fileStorageService,
                              PdfTextExtractorService pdfTextExtractorService,
                              OpenAiPlanningService openAiPlanningService,
                              AnalysisSessionService analysisSessionService) {
        this.fileStorageService = fileStorageService;
        this.pdfTextExtractorService = pdfTextExtractorService;
        this.openAiPlanningService = openAiPlanningService;
        this.analysisSessionService = analysisSessionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(@Valid @ModelAttribute("uploadForm") UploadForm uploadForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors() || uploadForm.getFile() == null || uploadForm.getFile().isEmpty()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.uploadForm", bindingResult);
            redirectAttributes.addFlashAttribute("uploadForm", uploadForm);
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a document type and file.");
            return "redirect:/";
        }

        String extension = FileNameUtils.extension(uploadForm.getFile().getOriginalFilename());
        if (!extension.equals("pdf") && !extension.equals("ppt") && !extension.equals("pptx")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only PDF, PPT, and PPTX files are supported.");
            return "redirect:/";
        }

        StoredFileInfo storedFileInfo = fileStorageService.store(uploadForm.getFile());
        AnalysisSession session;

        if ("pdf".equals(storedFileInfo.getExtension())) {
            String sourceText = pdfTextExtractorService.extractText(Path.of(storedFileInfo.getAbsolutePath()));
            AnalysisResultDto result = openAiPlanningService.analyze(
                    uploadForm.getDocumentType(),
                    storedFileInfo.getOriginalFileName(),
                    sourceText
            );
            session = analysisSessionService.createPdfSession(storedFileInfo, sourceText, result);
        } else {
            session = analysisSessionService.createUnsupportedSession(storedFileInfo, uploadForm.getDocumentType());
        }

        redirectAttributes.addFlashAttribute("successMessage", "Document upload and processing completed.");
        return "redirect:/documents/" + session.getSessionId();
    }

    @GetMapping("/{sessionId}")
    public String result(@PathVariable String sessionId, Model model) {
        AnalysisSession session = analysisSessionService.getSession(sessionId);
        model.addAttribute("session", session);
        model.addAttribute("result", session.getAnalysisResult());
        return "result";
    }

    @PostMapping("/{sessionId}/update")
    public String update(@PathVariable String sessionId,
                         @ModelAttribute("result") AnalysisResultDto result,
                         RedirectAttributes redirectAttributes) {
        analysisSessionService.updateAnalysis(sessionId, result);
        redirectAttributes.addFlashAttribute("successMessage", "Analysis result updated.");
        return "redirect:/documents/" + sessionId;
    }
}
