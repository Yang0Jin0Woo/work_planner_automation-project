package com.example.BPA_project.service;

import com.example.BPA_project.dto.DocumentType;
import com.example.BPA_project.dto.MissingCheckDto;
import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.model.StoredFileInfo;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncDocumentAnalysisService {

    private final DocumentTextExtractorResolver documentTextExtractorResolver;
    private final OpenAiPlanningService openAiPlanningService;
    private final AnalysisSessionService analysisSessionService;

    public AsyncDocumentAnalysisService(DocumentTextExtractorResolver documentTextExtractorResolver,
                                        OpenAiPlanningService openAiPlanningService,
                                        AnalysisSessionService analysisSessionService) {
        this.documentTextExtractorResolver = documentTextExtractorResolver;
        this.openAiPlanningService = openAiPlanningService;
        this.analysisSessionService = analysisSessionService;
    }

    @Async("analysisTaskExecutor")
    public void analyze(String sessionId,
                        DocumentType documentType,
                        StoredFileInfo storedFileInfo,
                        byte[] fileBytes) {
        try {
            analysisSessionService.markProcessing(sessionId);

            DocumentTextExtractor extractor = documentTextExtractorResolver.resolve(storedFileInfo.getExtension());
            DocumentExtractionResult extractionResult = extractor.extract(fileBytes, storedFileInfo.getOriginalFileName());

            AnalysisInputPreparation analysisPreparation = openAiPlanningService.prepareAnalysisInput(
                    documentType,
                    storedFileInfo.getOriginalFileName(),
                    extractionResult
            );
            String analysisInput = analysisPreparation.getAnalysisText();
            int chunkCount = openAiPlanningService.estimateChunkCount(analysisInput);

            var result = openAiPlanningService.analyze(
                    documentType,
                    storedFileInfo.getOriginalFileName(),
                    analysisInput
            );
            MissingCheckDto missingCheck = openAiPlanningService.verifyMissingItems(
                    documentType,
                    storedFileInfo.getOriginalFileName(),
                    analysisInput,
                    result
            );
            String missingCheckStatus = missingCheck.isNeedsReview() ? "확인 필요" : "점검 완료";
            List<String> missingCheckNotes = missingCheck.getReviewNotes();

            analysisSessionService.completeAnalysis(
                    sessionId,
                    analysisInput,
                    chunkCount,
                    result,
                    analysisPreparation.getVisualStatus(),
                    analysisPreparation.getVisualNote(),
                    missingCheckStatus,
                    missingCheckNotes
            );
        } catch (Exception exception) {
            String message = exception instanceof DocumentAnalysisException
                    ? exception.getMessage()
                    : "문서 분석 중 오류가 발생했습니다.";
            analysisSessionService.failAnalysis(sessionId, message);
        }
    }
}