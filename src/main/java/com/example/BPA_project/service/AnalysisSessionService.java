package com.example.BPA_project.service;

import com.example.BPA_project.dto.AnalysisResultDto;
import com.example.BPA_project.dto.DocumentType;
import com.example.BPA_project.dto.PlanTaskDto;
import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.exception.FileStorageException;
import com.example.BPA_project.model.AnalysisSession;
import com.example.BPA_project.model.StoredFileInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnalysisSessionService {

    private final Map<String, AnalysisSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path analysisDir;

    public AnalysisSessionService(ObjectMapper objectMapper, FileStorageService fileStorageService) {
        this.objectMapper = objectMapper;
        this.analysisDir = fileStorageService.analysisDir();
    }

    public AnalysisSession createPdfSession(StoredFileInfo fileInfo, String sourceText, AnalysisResultDto resultDto) {
        AnalysisSession session = baseSession(fileInfo);
        session.setSourceText(sourceText);
        session.setSourceStatus("ANALYZED");
        session.setMessage("PDF analysis completed.");
        session.setAnalysisResult(normalize(resultDto));
        persist(session);
        return session;
    }

    public AnalysisSession createUnsupportedSession(StoredFileInfo fileInfo, DocumentType documentType) {
        AnalysisResultDto result = new AnalysisResultDto();
        result.setDocumentType(documentType);
        result.setTitle(fileInfo.getOriginalFileName());
        result.setSummary("PPT and PPTX upload is stored, but this MVP only provides a guidance message without conversion.");
        result.setScheduleDraft("Convert the slide deck to PDF and upload it again to generate an AI execution plan draft.");
        result.setGoals(List.of("Validate the PDF-first MVP", "Keep a clear extension point for PPT conversion"));
        result.setTasks(new ArrayList<>());
        result.setRisks(List.of("Slide text extraction and conversion are not implemented in this version."));
        result.setQuestions(List.of("Should automatic PPT to PDF conversion be added in the next version?"));

        AnalysisSession session = baseSession(fileInfo);
        session.setSourceText("");
        session.setSourceStatus("UPLOADED_ONLY");
        session.setMessage("PPT and PPTX files are stored only. Convert them to PDF for analysis.");
        session.setAnalysisResult(result);
        persist(session);
        return session;
    }

    public AnalysisSession getSession(String sessionId) {
        AnalysisSession session = sessions.get(sessionId);
        if (session != null) {
            return session;
        }

        Path path = analysisDir.resolve(sessionId + ".json");
        if (!Files.exists(path)) {
            throw new DocumentAnalysisException("Analysis session was not found.");
        }

        try {
            AnalysisSession loaded = objectMapper.readValue(path.toFile(), AnalysisSession.class);
            sessions.put(sessionId, loaded);
            return loaded;
        } catch (IOException exception) {
            throw new FileStorageException("Failed to read the saved analysis session.", exception);
        }
    }

    public AnalysisSession updateAnalysis(String sessionId, AnalysisResultDto updatedResult) {
        AnalysisSession session = getSession(sessionId);
        session.setAnalysisResult(normalize(updatedResult));
        session.setUpdatedAt(LocalDateTime.now());
        persist(session);
        return session;
    }

    public void updateReportPath(String sessionId, String reportPath) {
        AnalysisSession session = getSession(sessionId);
        session.setReportPath(reportPath);
        session.setUpdatedAt(LocalDateTime.now());
        persist(session);
    }

    private AnalysisSession baseSession(StoredFileInfo fileInfo) {
        AnalysisSession session = new AnalysisSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setStoredFileInfo(fileInfo);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }

    private void persist(AnalysisSession session) {
        sessions.put(session.getSessionId(), session);
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(analysisDir.resolve(session.getSessionId() + ".json").toFile(), session);
        } catch (IOException exception) {
            throw new FileStorageException("Failed to save the analysis JSON file.", exception);
        }
    }

    private AnalysisResultDto normalize(AnalysisResultDto dto) {
        AnalysisResultDto normalized = new AnalysisResultDto();
        normalized.setDocumentType(dto.getDocumentType());
        normalized.setTitle(trimToDefault(dto.getTitle(), "Untitled"));
        normalized.setSummary(trimToDefault(dto.getSummary(), "No summary available."));
        normalized.setScheduleDraft(trimToDefault(dto.getScheduleDraft(), "No schedule draft available."));
        normalized.setGoals(normalizeStrings(dto.getGoals(), "No goals available."));
        normalized.setTasks(normalizeTasks(dto.getTasks()));
        normalized.setRisks(normalizeStrings(dto.getRisks(), "No risks available."));
        normalized.setQuestions(normalizeStrings(dto.getQuestions(), "No questions available."));
        return normalized;
    }

    private List<String> normalizeStrings(List<String> items, String fallback) {
        if (items == null) {
            return new ArrayList<>(List.of(fallback));
        }

        List<String> normalized = items.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        return normalized.isEmpty() ? new ArrayList<>(List.of(fallback)) : new ArrayList<>(normalized);
    }

    private List<PlanTaskDto> normalizeTasks(List<PlanTaskDto> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<PlanTaskDto> normalized = new ArrayList<>();
        for (PlanTaskDto task : tasks) {
            if (task == null || !StringUtils.hasText(task.getTask())) {
                continue;
            }
            PlanTaskDto item = new PlanTaskDto();
            item.setTask(task.getTask().trim());
            item.setPriority(trimToDefault(task.getPriority(), "MEDIUM"));
            item.setDueDate(trimOrNull(task.getDueDate()));
            item.setOwner(trimOrNull(task.getOwner()));
            normalized.add(item);
        }
        return normalized;
    }

    private String trimToDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
