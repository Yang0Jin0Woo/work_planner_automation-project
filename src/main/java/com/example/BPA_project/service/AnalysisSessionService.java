package com.example.BPA_project.service;

import com.example.BPA_project.dto.AnalysisResultDto;
import com.example.BPA_project.dto.DocumentType;
import com.example.BPA_project.dto.PlanTaskDto;
import com.example.BPA_project.entity.AnalysisSessionEntity;
import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.model.AnalysisSession;
import com.example.BPA_project.model.StoredFileInfo;
import com.example.BPA_project.repository.AnalysisSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnalysisSessionService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final AnalysisSessionRepository analysisSessionRepository;
    private final ObjectMapper objectMapper;

    public AnalysisSessionService(AnalysisSessionRepository analysisSessionRepository,
                                  ObjectMapper objectMapper) {
        this.analysisSessionRepository = analysisSessionRepository;
        this.objectMapper = objectMapper;
    }

    public AnalysisSession createAnalyzedSession(StoredFileInfo fileInfo,
                                                 String sourceText,
                                                 int chunkCount,
                                                 AnalysisResultDto resultDto,
                                                 String visualAnalysisStatus,
                                                 String visualAnalysisNote,
                                                 String missingCheckStatus,
                                                 List<String> missingCheckNotes) {
        AnalysisSession session = baseSession(fileInfo);
        session.setSourceText(sourceText);
        session.setAnalyzedChunkCount(chunkCount);
        session.setSourceStatus("ANALYZED");
        session.setMessage("문서 분석이 완료되었습니다.");
        session.setVisualAnalysisStatus(trimOrNull(visualAnalysisStatus));
        session.setVisualAnalysisNote(trimOrNull(visualAnalysisNote));
        session.setMissingCheckStatus(trimOrNull(missingCheckStatus));
        session.setMissingCheckNotes(normalizeStrings(missingCheckNotes, "추가 메모 없음"));
        session.setAnalysisResult(normalize(resultDto));
        persist(session);
        return session;
    }

    public AnalysisSession getSession(String sessionId) {
        AnalysisSessionEntity entity = analysisSessionRepository.findById(sessionId)
                .orElseThrow(() -> new DocumentAnalysisException("분석 세션을 찾을 수 없습니다."));
        return toModel(entity);
    }

    public AnalysisSession updateAnalysis(String sessionId, AnalysisResultDto updatedResult) {
        AnalysisSession session = getSession(sessionId);
        session.setAnalysisResult(normalize(updatedResult));
        session.setUpdatedAt(LocalDateTime.now());
        persist(session);
        return session;
    }

    public List<AnalysisSession> getAllSessions() {
        return analysisSessionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toModel)
                .toList();
    }

    public void deleteSessions(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        analysisSessionRepository.deleteAllByIdInBatch(sessionIds);
    }

    public void deleteAllSessions() {
        analysisSessionRepository.deleteAllInBatch();
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
        analysisSessionRepository.save(toEntity(session));
    }

    private AnalysisSessionEntity toEntity(AnalysisSession session) {
        AnalysisSessionEntity entity = new AnalysisSessionEntity();
        entity.setSessionId(session.getSessionId());

        StoredFileInfo fileInfo = session.getStoredFileInfo();
        if (fileInfo != null) {
            entity.setOriginalFileName(fileInfo.getOriginalFileName());
            entity.setSavedFileName(fileInfo.getSavedFileName());
            entity.setContentType(fileInfo.getContentType());
            entity.setExtension(fileInfo.getExtension());
            entity.setAbsolutePath(fileInfo.getAbsolutePath());
            entity.setFileSize(fileInfo.getSize());
            entity.setUploadedAt(fileInfo.getUploadedAt());
        }

        entity.setSourceText(session.getSourceText());
        entity.setSourceStatus(session.getSourceStatus());
        entity.setMessage(session.getMessage());
        entity.setVisualAnalysisStatus(session.getVisualAnalysisStatus());
        entity.setVisualAnalysisNote(session.getVisualAnalysisNote());
        entity.setMissingCheckStatus(session.getMissingCheckStatus());
        entity.setMissingCheckNotesJson(writeJson(session.getMissingCheckNotes()));
        entity.setAnalysisResultJson(writeJson(session.getAnalysisResult()));
        entity.setAnalyzedChunkCount(session.getAnalyzedChunkCount());
        entity.setCreatedAt(session.getCreatedAt());
        entity.setUpdatedAt(session.getUpdatedAt());
        return entity;
    }

    private AnalysisSession toModel(AnalysisSessionEntity entity) {
        AnalysisSession session = new AnalysisSession();
        session.setSessionId(entity.getSessionId());
        session.setStoredFileInfo(toStoredFileInfo(entity));
        session.setSourceText(entity.getSourceText());
        session.setSourceStatus(entity.getSourceStatus());
        session.setMessage(entity.getMessage());
        session.setVisualAnalysisStatus(entity.getVisualAnalysisStatus());
        session.setVisualAnalysisNote(entity.getVisualAnalysisNote());
        session.setMissingCheckStatus(entity.getMissingCheckStatus());
        session.setMissingCheckNotes(readStringList(entity.getMissingCheckNotesJson()));
        session.setAnalysisResult(readAnalysisResult(entity.getAnalysisResultJson()));
        session.setAnalyzedChunkCount(entity.getAnalyzedChunkCount() == null ? 0 : entity.getAnalyzedChunkCount());
        session.setCreatedAt(entity.getCreatedAt());
        session.setUpdatedAt(entity.getUpdatedAt());
        return session;
    }

    private StoredFileInfo toStoredFileInfo(AnalysisSessionEntity entity) {
        StoredFileInfo fileInfo = new StoredFileInfo();
        fileInfo.setOriginalFileName(entity.getOriginalFileName());
        fileInfo.setSavedFileName(entity.getSavedFileName());
        fileInfo.setContentType(entity.getContentType());
        fileInfo.setExtension(entity.getExtension());
        fileInfo.setAbsolutePath(entity.getAbsolutePath());
        fileInfo.setSize(entity.getFileSize() == null ? 0L : entity.getFileSize());
        fileInfo.setUploadedAt(entity.getUploadedAt());
        return fileInfo;
    }

    private AnalysisResultDto readAnalysisResult(String json) {
        if (!StringUtils.hasText(json)) {
            throw new DocumentAnalysisException("저장된 분석 결과가 비어 있습니다.");
        }
        try {
            return normalize(objectMapper.readValue(json, AnalysisResultDto.class));
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("저장된 분석 결과를 읽는 데 실패했습니다.", exception);
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, STRING_LIST_TYPE));
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("저장된 누락 점검 메모를 읽는 데 실패했습니다.", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("분석 데이터를 저장하는 데 실패했습니다.", exception);
        }
    }

    private AnalysisResultDto normalize(AnalysisResultDto dto) {
        AnalysisResultDto normalized = new AnalysisResultDto();
        DocumentType documentType = dto.getDocumentType() == null ? DocumentType.MEETING : dto.getDocumentType();
        normalized.setDocumentType(documentType);
        normalized.setTitle(trimToDefault(dto.getTitle(), "제목 없음"));
        normalized.setSummary(trimToDefault(dto.getSummary(), "요약 정보 없음"));
        normalized.setScheduleDraft(trimToDefault(dto.getScheduleDraft(), "일정 초안 없음"));
        normalized.setGoals(normalizeStrings(dto.getGoals(), "목표 정보 없음"));
        normalized.setTasks(normalizeTasks(documentType, dto.getTasks()));
        normalized.setRisks(normalizeStrings(dto.getRisks(), "리스크 정보 없음"));
        normalized.setQuestions(normalizeStrings(dto.getQuestions(), "추가 확인 사항 없음"));
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

    private List<PlanTaskDto> normalizeTasks(DocumentType documentType, List<PlanTaskDto> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        int maxTaskCount = maxTaskCount(documentType);
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
            if (normalized.size() >= maxTaskCount) {
                break;
            }
        }
        return normalized;
    }

    private int maxTaskCount(DocumentType documentType) {
        return switch (documentType) {
            case REPORT -> 6;
            case PROPOSAL -> 5;
            case MEETING -> 8;
        };
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
