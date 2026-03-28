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
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnalysisSessionService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final AnalysisSessionRepository analysisSessionRepository;
    private final ObjectMapper objectMapper;

    public AnalysisSessionService(AnalysisSessionRepository analysisSessionRepository,
                                  ObjectMapper objectMapper) {
        this.analysisSessionRepository = analysisSessionRepository;
        this.objectMapper = objectMapper;
    }

    public AnalysisSession createPendingSession(DocumentType documentType, StoredFileInfo fileInfo) {
        AnalysisSession session = baseSession(documentType, fileInfo);
        session.setSourceStatus(STATUS_PENDING);
        session.setMessage("업로드가 완료되었습니다. 곧 분석을 시작합니다.");
        session.setVisualAnalysisStatus("대기 중");
        session.setVisualAnalysisNote("시각 자료 분석 전입니다.");
        session.setMissingCheckStatus("대기 중");
        session.setMissingCheckNotes(new ArrayList<>(List.of("분석이 완료되면 누락 점검 결과가 표시됩니다.")));
        session.setAnalysisResult(createPlaceholderResult(documentType));
        persist(session);
        return session;
    }

    public void markProcessing(String sessionId) {
        AnalysisSession session = getSession(sessionId);
        session.setSourceStatus(STATUS_PROCESSING);
        session.setMessage("문서를 분석하고 있습니다. 잠시만 기다려주세요.");
        session.setVisualAnalysisStatus("분석 중");
        session.setVisualAnalysisNote("본문 추출과 시각 자료 분석을 진행하고 있습니다.");
        session.setMissingCheckStatus("대기 중");
        session.setMissingCheckNotes(new ArrayList<>(List.of("본 분석이 끝나면 누락 점검을 수행합니다.")));
        session.setUpdatedAt(LocalDateTime.now());
        persist(session);
    }

    public AnalysisSession completeAnalysis(String sessionId,
                                            String sourceText,
                                            int chunkCount,
                                            AnalysisResultDto resultDto,
                                            String visualAnalysisStatus,
                                            String visualAnalysisNote,
                                            String missingCheckStatus,
                                            List<String> missingCheckNotes) {
        AnalysisSession session = getSession(sessionId);
        session.setSourceText(sourceText);
        session.setAnalyzedChunkCount(chunkCount);
        session.setSourceStatus(STATUS_COMPLETED);
        session.setMessage("문서 분석이 완료되었습니다.");
        session.setVisualAnalysisStatus(trimToDefault(visualAnalysisStatus, "자료 없음"));
        session.setVisualAnalysisNote(trimToDefault(visualAnalysisNote, "추가 시각 자료 분석은 수행되지 않았습니다."));
        session.setMissingCheckStatus(trimToDefault(missingCheckStatus, "점검 완료"));
        session.setMissingCheckNotes(normalizeStrings(missingCheckNotes, "추가 메모 없음"));
        session.setAnalysisResult(normalize(resultDto, session.getDocumentType()));
        session.setUpdatedAt(LocalDateTime.now());
        persist(session);
        return session;
    }

    public void failAnalysis(String sessionId, String errorMessage) {
        AnalysisSession session = getSession(sessionId);
        session.setSourceStatus(STATUS_FAILED);
        session.setMessage(trimToDefault(errorMessage, "문서 분석 중 오류가 발생했습니다."));
        session.setVisualAnalysisStatus("실패");
        session.setVisualAnalysisNote("분석이 중단되어 시각 자료 검토를 마치지 못했습니다.");
        session.setMissingCheckStatus("실행 안 됨");
        session.setMissingCheckNotes(new ArrayList<>(List.of("오류를 확인한 뒤 다시 업로드해주세요.")));
        session.setUpdatedAt(LocalDateTime.now());
        persist(session);
    }

    public AnalysisSession getSession(String sessionId) {
        AnalysisSessionEntity entity = analysisSessionRepository.findById(sessionId)
                .orElseThrow(() -> new DocumentAnalysisException("분석 세션을 찾을 수 없습니다."));
        return toModel(entity);
    }

    public AnalysisSession updateAnalysis(String sessionId, AnalysisResultDto updatedResult) {
        AnalysisSession session = getSession(sessionId);
        session.setAnalysisResult(normalize(updatedResult, session.getDocumentType()));
        session.setSourceStatus(STATUS_COMPLETED);
        session.setMessage("분석 결과를 수정해 저장했습니다.");
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

    private AnalysisSession baseSession(DocumentType documentType, StoredFileInfo fileInfo) {
        AnalysisSession session = new AnalysisSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setDocumentType(documentType == null ? DocumentType.MEETING : documentType);
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
        entity.setDocumentType(session.getDocumentType() == null ? null : session.getDocumentType().name());

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
        session.setDocumentType(readDocumentType(entity.getDocumentType()));
        session.setStoredFileInfo(toStoredFileInfo(entity));
        session.setSourceText(entity.getSourceText());
        session.setSourceStatus(entity.getSourceStatus());
        session.setMessage(entity.getMessage());
        session.setVisualAnalysisStatus(entity.getVisualAnalysisStatus());
        session.setVisualAnalysisNote(entity.getVisualAnalysisNote());
        session.setMissingCheckStatus(entity.getMissingCheckStatus());
        session.setMissingCheckNotes(readStringList(entity.getMissingCheckNotesJson()));
        session.setAnalysisResult(readAnalysisResult(entity.getAnalysisResultJson(), session.getDocumentType()));
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

    private DocumentType readDocumentType(String documentType) {
        if (!StringUtils.hasText(documentType)) {
            return DocumentType.MEETING;
        }
        try {
            return DocumentType.valueOf(documentType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DocumentType.MEETING;
        }
    }

    private AnalysisResultDto readAnalysisResult(String json, DocumentType documentType) {
        if (!StringUtils.hasText(json)) {
            return createPlaceholderResult(documentType);
        }
        try {
            return normalize(objectMapper.readValue(json, AnalysisResultDto.class), documentType);
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("저장된 분석 결과를 읽는 중 오류가 발생했습니다.", exception);
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, STRING_LIST_TYPE));
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("저장된 메모를 읽는 중 오류가 발생했습니다.", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("분석 데이터를 저장하는 중 오류가 발생했습니다.", exception);
        }
    }

    private AnalysisResultDto normalize(AnalysisResultDto dto, DocumentType fallbackDocumentType) {
        AnalysisResultDto source = dto == null ? createPlaceholderResult(fallbackDocumentType) : dto;
        AnalysisResultDto normalized = new AnalysisResultDto();
        DocumentType documentType = source.getDocumentType() == null ? fallbackDocumentType : source.getDocumentType();
        if (documentType == null) {
            documentType = DocumentType.MEETING;
        }
        normalized.setDocumentType(documentType);
        normalized.setTitle(trimToDefault(source.getTitle(), "제목 없음"));
        normalized.setSummary(trimToDefault(source.getSummary(), "요약 정보가 없습니다."));
        normalized.setScheduleDraft(trimToDefault(source.getScheduleDraft(), "일정 초안이 없습니다."));
        normalized.setGoals(normalizeStrings(source.getGoals(), "목표 정보가 없습니다."));
        normalized.setTasks(normalizeTasks(documentType, source.getTasks()));
        normalized.setRisks(normalizeStrings(source.getRisks(), "리스크 정보가 없습니다."));
        normalized.setQuestions(normalizeStrings(source.getQuestions(), "추가 확인 사항이 없습니다."));
        return normalized;
    }

    private AnalysisResultDto createPlaceholderResult(DocumentType documentType) {
        AnalysisResultDto placeholder = new AnalysisResultDto();
        placeholder.setDocumentType(documentType == null ? DocumentType.MEETING : documentType);
        placeholder.setTitle("분석 준비 중");
        placeholder.setSummary("문서 업로드는 완료되었고 AI 분석을 준비하고 있습니다.");
        placeholder.setScheduleDraft("분석 완료 후 일정 초안이 생성됩니다.");
        placeholder.setGoals(new ArrayList<>(List.of("분석 완료 후 목표가 표시됩니다.")));
        placeholder.setTasks(new ArrayList<>());
        placeholder.setRisks(new ArrayList<>(List.of("분석 완료 후 리스크가 표시됩니다.")));
        placeholder.setQuestions(new ArrayList<>(List.of("분석 완료 후 확인 필요 항목이 표시됩니다.")));
        return placeholder;
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
        String normalized = trimToDefault(status, "NOT_STARTED").toUpperCase(Locale.ROOT);
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