package com.example.BPA_project.service;

import com.example.BPA_project.dto.AnalysisResultDto;
import com.example.BPA_project.dto.DocumentType;
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

    private final Map<String, AnalysisSession> sessions = new ConcurrentHashMap<>();

    public AnalysisSession createPdfSession(StoredFileInfo fileInfo,
                                            String sourceText,
                                            int chunkCount,
                                            AnalysisResultDto resultDto) {
        AnalysisSession session = baseSession(fileInfo);
        session.setSourceText(sourceText);
        session.setAnalyzedChunkCount(chunkCount);
        session.setSourceStatus("ANALYZED");
        session.setMessage("PDF 분석이 완료되었습니다.");
        session.setAnalysisResult(normalize(resultDto));
        persist(session);
        return session;
    }

    public AnalysisSession createUnsupportedSession(StoredFileInfo fileInfo, DocumentType documentType) {
        AnalysisResultDto result = new AnalysisResultDto();
        result.setDocumentType(documentType);
        result.setTitle(fileInfo.getOriginalFileName());
        result.setSummary("PPT 또는 PPTX 파일은 업로드만 지원되며, 현재 MVP에서는 변환 없이 안내 메시지만 제공합니다.");
        result.setScheduleDraft("슬라이드를 PDF로 변환한 뒤 다시 업로드하면 AI 실행 계획 초안을 생성할 수 있습니다.");
        result.setGoals(List.of("PDF 전환 MVP 검증", "PPT 변환 확장 사양 정의"));
        result.setTasks(new ArrayList<>());
        result.setRisks(List.of("슬라이드 텍스트 추출과 변환 기능은 아직 구현되지 않았습니다."));
        result.setQuestions(List.of("다음 버전에서 PPT/PPTX 자동 변환을 지원할지 결정이 필요합니다."));

        AnalysisSession session = baseSession(fileInfo);
        session.setSourceText("");
        session.setAnalyzedChunkCount(0);
        session.setSourceStatus("UPLOADED_ONLY");
        session.setMessage("PPT 또는 PPTX 파일은 업로드만 처리했습니다. 분석하려면 PDF로 변환 후 다시 업로드해주세요.");
        session.setAnalysisResult(result);
        persist(session);
        return session;
    }

    public AnalysisSession getSession(String sessionId) {
        AnalysisSession session = sessions.get(sessionId);
        if (session == null) {
            throw new DocumentAnalysisException("분석 세션을 찾을 수 없습니다. 서버 재시작 후에는 이전 결과가 유지되지 않습니다.");
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
        normalized.setTitle(trimToDefault(dto.getTitle(), "제목 없음"));
        normalized.setSummary(trimToDefault(dto.getSummary(), "요약 정보가 없습니다."));
        normalized.setScheduleDraft(trimToDefault(dto.getScheduleDraft(), "일정 초안 정보가 없습니다."));
        normalized.setGoals(normalizeStrings(dto.getGoals(), "목표 정보가 없습니다."));
        normalized.setTasks(normalizeTasks(dto.getTasks()));
        normalized.setRisks(normalizeStrings(dto.getRisks(), "리스크 정보가 없습니다."));
        normalized.setQuestions(normalizeStrings(dto.getQuestions(), "추가 확인 필요 항목이 없습니다."));
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