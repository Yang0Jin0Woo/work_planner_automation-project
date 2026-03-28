package com.example.BPA_project.controller;

import com.example.BPA_project.dto.AnalysisResultDto;
import com.example.BPA_project.dto.MissingCheckDto;
import com.example.BPA_project.dto.UploadForm;
import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.model.AnalysisSession;
import com.example.BPA_project.model.StoredFileInfo;
import com.example.BPA_project.service.AnalysisInputPreparation;
import com.example.BPA_project.service.AnalysisSessionService;
import com.example.BPA_project.service.DocumentExtractionResult;
import com.example.BPA_project.service.DocumentTextExtractor;
import com.example.BPA_project.service.DocumentTextExtractorResolver;
import com.example.BPA_project.service.FileStorageService;
import com.example.BPA_project.service.OpenAiPlanningService;
import com.example.BPA_project.util.FileNameUtils;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
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
    private final DocumentTextExtractorResolver documentTextExtractorResolver;
    private final OpenAiPlanningService openAiPlanningService;
    private final AnalysisSessionService analysisSessionService;

    public DocumentController(FileStorageService fileStorageService,
                              DocumentTextExtractorResolver documentTextExtractorResolver,
                              OpenAiPlanningService openAiPlanningService,
                              AnalysisSessionService analysisSessionService) {
        this.fileStorageService = fileStorageService;
        this.documentTextExtractorResolver = documentTextExtractorResolver;
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
            redirectAttributes.addFlashAttribute("errorMessage", "\uBB38\uC11C \uC720\uD615\uACFC \uD30C\uC77C\uC744 \uD655\uC778\uD574\uC8FC\uC138\uC694.");
            return "redirect:/";
        }

        String extension = FileNameUtils.extension(uploadForm.getFile().getOriginalFilename());
        if (!extension.equals("pdf") && !extension.equals("ppt") && !extension.equals("pptx")) {
            redirectAttributes.addFlashAttribute("errorMessage", "PDF, PPT, PPTX \uD30C\uC77C\uB9CC \uC5C5\uB85C\uB4DC\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.");
            return "redirect:/";
        }

        StoredFileInfo storedFileInfo = fileStorageService.store(uploadForm.getFile());
        DocumentExtractionResult extractionResult = extractDocument(uploadForm, storedFileInfo);
        AnalysisInputPreparation analysisPreparation = openAiPlanningService.prepareAnalysisInput(
                uploadForm.getDocumentType(),
                storedFileInfo.getOriginalFileName(),
                extractionResult
        );
        String analysisInput = analysisPreparation.getAnalysisText();
        int chunkCount = openAiPlanningService.estimateChunkCount(analysisInput);
        AnalysisResultDto result = openAiPlanningService.analyze(
                uploadForm.getDocumentType(),
                storedFileInfo.getOriginalFileName(),
                analysisInput
        );
        MissingCheckDto missingCheck = openAiPlanningService.verifyMissingItems(
                uploadForm.getDocumentType(),
                storedFileInfo.getOriginalFileName(),
                analysisInput,
                result
        );
        String missingCheckStatus = missingCheck.isNeedsReview() ? "\uD655\uC778 \uD544\uC694" : "\uC810\uAC80 \uC644\uB8CC";
        AnalysisSession analysisSession = analysisSessionService.createAnalyzedSession(
                storedFileInfo,
                analysisInput,
                chunkCount,
                result,
                analysisPreparation.getVisualStatus(),
                analysisPreparation.getVisualNote(),
                missingCheckStatus,
                missingCheck.getReviewNotes()
        );

        redirectAttributes.addFlashAttribute("successMessage", "\uBB38\uC11C \uC5C5\uB85C\uB4DC\uC640 \uBD84\uC11D\uC774 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4.");
        return "redirect:/documents/" + analysisSession.getSessionId();
    }

    @GetMapping("/{sessionId}")
    public String result(@PathVariable String sessionId, org.springframework.ui.Model model) {
        AnalysisSession analysisSession = analysisSessionService.getSession(sessionId);
        model.addAttribute("analysisSession", analysisSession);
        model.addAttribute("result", analysisSession.getAnalysisResult());
        return "result";
    }

    @PostMapping("/{sessionId}/update")
    public String update(@PathVariable String sessionId,
                         @ModelAttribute("result") AnalysisResultDto result,
                         RedirectAttributes redirectAttributes) {
        analysisSessionService.updateAnalysis(sessionId, result);
        redirectAttributes.addFlashAttribute("successMessage", "\uBCC0\uACBD\uC0AC\uD56D\uC744 \uC800\uC7A5\uD588\uC2B5\uB2C8\uB2E4.");
        return "redirect:/documents/" + sessionId;
    }

    private DocumentExtractionResult extractDocument(UploadForm uploadForm, StoredFileInfo storedFileInfo) {
        try {
            DocumentTextExtractor extractor = documentTextExtractorResolver.resolve(storedFileInfo.getExtension());
            return extractor.extract(uploadForm.getFile().getBytes(), storedFileInfo.getOriginalFileName());
        } catch (IOException exception) {
            throw new DocumentAnalysisException("\uC5C5\uB85C\uB4DC\uD55C \uD30C\uC77C\uC744 \uC77D\uB294 \uB370 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.", exception);
        }
    }
}