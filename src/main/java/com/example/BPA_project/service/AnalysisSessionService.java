package com.example.BPA_project.service;

import com.example.BPA_project.dto.AnalysisResultDto;
import com.example.BPA_project.dto.PlanTaskDto;
import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.model.AnalysisSession;
import com.example.BPA_project.model.StoredFileInfo;
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

    private static final int MAX_TASK_COUNT = 5;

    private final Map<String, AnalysisSession> sessions = new ConcurrentHashMap<>();

    public AnalysisSession createAnalyzedSession(StoredFileInfo fileInfo,
                                                 String sourceText,
                                                 int chunkCount,
                                                 AnalysisResultDto resultDto) {
        AnalysisSession session = baseSession(fileInfo);
        session.setSourceText(sourceText);
        session.setAnalyzedChunkCount(chunkCount);
        session.setSourceStatus("ANALYZED");
        session.setMessage("Document analysis completed.");
        session.setAnalysisResult(normalize(resultDto));
        persist(session);
        return session;
    }

    public AnalysisSession getSession(String sessionId) {
        AnalysisSession session = sessions.get(sessionId);
        if (session == null) {
            throw new DocumentAnalysisException("Analysis session was not found. Results are kept in memory only.");
        }
        return session;
    }

    public AnalysisSession updateAnalysis(String sessionId, AnalysisResultDto updatedResult) {
        AnalysisSession session = getSession(sessionId);
        session.setAnalysisResult(normalize(updatedResult));
        session.setUpdatedAt(LocalDateTime.now());
        persist(session);
        return session;
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
        normalized.setQuestions(normalizeStrings(dto.getQuestions(), "No follow-up questions."));
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
            item.setStatus(normalizeStatus(task.getStatus()));
            item.setDueDate(trimOrNull(task.getDueDate()));
            item.setCompletedAt(trimOrNull(task.getCompletedAt()));
            item.setOwner(trimOrNull(task.getOwner()));
            item.setReviewer(trimOrNull(task.getReviewer()));
            normalized.add(item);
            if (normalized.size() >= MAX_TASK_COUNT) {
                break;
            }
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = trimToDefault(status, "NOT_STARTED").toUpperCase();
        return switch (normalized) {
            case "NOT_STARTED", "IN_PROGRESS", "COMPLETED" -> normalized;
            default -> "NOT_STARTED";
        };
    }

    private String trimToDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
