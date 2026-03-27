package com.example.BPA_project.service;

import com.example.BPA_project.config.AppProperties;
import com.example.BPA_project.dto.AnalysisResultDto;
import com.example.BPA_project.dto.ChunkAnalysisDto;
import com.example.BPA_project.dto.DocumentType;
import com.example.BPA_project.dto.PlanTaskDto;
import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.util.JsonSchemaFactory;
import com.example.BPA_project.util.PromptFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiPlanningService {

    private enum AnalysisMode {
        STANDARD,
        COMPACT
    }

    private enum DetailLevel {
        STANDARD,
        COMPACT
    }

    private static final int CHUNK_SIZE = 9_000;
    private static final int CHUNK_OVERLAP = 1_000;
    private static final int MAX_CHUNKS = 8;
    private static final int MAX_FINAL_INPUT_CHARS = 18_000;
    private static final int CHUNK_ANALYSIS_OUTPUT_TOKENS = 900;
    private static final int SUMMARY_COMPRESSION_OUTPUT_TOKENS = 500;
    private static final int STRUCTURED_RETRY_OUTPUT_TOKENS = 4_000;
    private static final int STRUCTURED_COMPACT_RETRY_OUTPUT_TOKENS = 5_000;
    private static final int COMPACT_TRIGGER_TEXT_LENGTH = 10_000;
    private static final int COMPACT_TRIGGER_CHUNK_COUNT = 2;
    private static final int COMPACT_TRIGGER_SUMMARY_LENGTH = 7_500;
    private static final int COMPRESSED_SUMMARY_TARGET_CHARS = 5_500;
    private static final int MERGED_CANDIDATE_LIMIT = 12;

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OpenAiPlanningService(AppProperties appProperties,
                                 ObjectMapper objectMapper,
                                 RestTemplateBuilder restTemplateBuilder) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder.build();
    }

    public AnalysisResultDto analyze(DocumentType documentType, String originalFileName, String extractedText) {
        if (!StringUtils.hasText(appProperties.getOpenAi().getApiKey())) {
            throw new DocumentAnalysisException("OPENAI_API_KEY is not configured.");
        }

        try {
            List<String> chunks = splitIntoChunks(extractedText);
            if (chunks.isEmpty()) {
                throw new DocumentAnalysisException("No text was available for analysis.");
            }

            List<ChunkAnalysisDto> chunkAnalyses = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                chunkAnalyses.add(extractChunkAnalysis(documentType, originalFileName, chunks.get(index), index + 1, chunks.size()));
            }

            String mergedCandidates = mergeChunkAnalyses(chunkAnalyses);
            AnalysisMode analysisMode = chooseAnalysisMode(extractedText, chunks.size(), mergedCandidates);
            String finalInput = prepareStructuredInput(documentType, originalFileName, mergedCandidates, analysisMode);
            return requestStructuredPlan(documentType, originalFileName, finalInput, analysisMode);
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("Failed to build the OpenAI request JSON.", exception);
        } catch (HttpStatusCodeException exception) {
            throw new DocumentAnalysisException(buildApiErrorMessage(exception), exception);
        } catch (ResourceAccessException exception) {
            throw new DocumentAnalysisException("Could not reach the OpenAI API. Check network access and proxy settings.", exception);
        } catch (RestClientException exception) {
            throw new DocumentAnalysisException("OpenAI API call failed: " + exception.getMessage(), exception);
        }
    }

    public int estimateChunkCount(String text) {
        return splitIntoChunks(text).size();
    }

    private ChunkAnalysisDto extractChunkAnalysis(DocumentType documentType,
                                                  String originalFileName,
                                                  String chunkText,
                                                  int chunkNumber,
                                                  int totalChunks) throws JsonProcessingException {
        ObjectNode requestBody = baseRequestBody(CHUNK_ANALYSIS_OUTPUT_TOKENS);
        requestBody.put("instructions", PromptFactory.chunkExtractionInstructions(documentType, chunkNumber, totalChunks));

        ArrayNode input = requestBody.putArray("input");
        ObjectNode userMessage = input.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "input_text")
                .put("text", PromptFactory.chunkExtractionPrompt(documentType, originalFileName, chunkText, chunkNumber, totalChunks));

        ObjectNode text = requestBody.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", "chunk_analysis");
        format.put("strict", true);
        format.set("schema", JsonSchemaFactory.chunkAnalysisSchema(objectMapper));

        return parseStructuredResponse(
                sendRequest(requestBody),
                ChunkAnalysisDto.class,
                "Structured chunk analysis was not found in the OpenAI response.",
                "Failed to parse the OpenAI chunk analysis response."
        );
    }

    private String prepareStructuredInput(DocumentType documentType,
                                          String originalFileName,
                                          String mergedCandidates,
                                          AnalysisMode analysisMode) throws JsonProcessingException {
        if (analysisMode != AnalysisMode.COMPACT && mergedCandidates.length() <= COMPRESSED_SUMMARY_TARGET_CHARS) {
            return mergedCandidates;
        }
        return compressSummary(documentType, originalFileName, mergedCandidates);
    }

    private String compressSummary(DocumentType documentType,
                                   String originalFileName,
                                   String mergedCandidates) throws JsonProcessingException {
        ObjectNode requestBody = baseRequestBody(SUMMARY_COMPRESSION_OUTPUT_TOKENS);
        requestBody.put("instructions", summaryCompressionInstructions(documentType));

        ArrayNode input = requestBody.putArray("input");
        ObjectNode userMessage = input.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "input_text")
                .put("text", PromptFactory.mergedCandidatePrompt(documentType, originalFileName, truncateForFinalStep(mergedCandidates)));

        return parseTextResponse(sendRequest(requestBody));
    }

    private AnalysisResultDto requestStructuredPlan(DocumentType documentType,
                                                    String originalFileName,
                                                    String mergedCandidates,
                                                    AnalysisMode analysisMode) throws JsonProcessingException {
        int initialTokens = appProperties.getOpenAi().getMaxOutputTokens();
        DetailLevel initialLevel = analysisMode == AnalysisMode.COMPACT ? DetailLevel.COMPACT : DetailLevel.STANDARD;
        String responseBody = sendRequest(buildStructuredPlanRequest(
                documentType,
                originalFileName,
                mergedCandidates,
                initialTokens,
                initialLevel
        ));

        if (isMaxOutputTokenIncomplete(responseBody)
                && initialLevel == DetailLevel.STANDARD
                && initialTokens < STRUCTURED_RETRY_OUTPUT_TOKENS) {
            responseBody = sendRequest(buildStructuredPlanRequest(
                    documentType,
                    originalFileName,
                    mergedCandidates,
                    STRUCTURED_RETRY_OUTPUT_TOKENS,
                    DetailLevel.STANDARD
            ));
        }

        if (isMaxOutputTokenIncomplete(responseBody)) {
            responseBody = sendRequest(buildStructuredPlanRequest(
                    documentType,
                    originalFileName,
                    mergedCandidates,
                    STRUCTURED_COMPACT_RETRY_OUTPUT_TOKENS,
                    DetailLevel.COMPACT
            ));
        }

        return parseStructuredResponse(
                responseBody,
                AnalysisResultDto.class,
                "Structured JSON output was not found in the OpenAI response.",
                "Failed to parse the OpenAI structured JSON response. The model output may have been truncated."
        );
    }

    private ObjectNode buildStructuredPlanRequest(DocumentType documentType,
                                                  String originalFileName,
                                                  String mergedCandidates,
                                                  int maxOutputTokens,
                                                  DetailLevel detailLevel) {
        ObjectNode requestBody = baseRequestBody(maxOutputTokens);
        requestBody.put("instructions", PromptFactory.instructions(documentType, detailLevel.name()));

        ArrayNode input = requestBody.putArray("input");
        ObjectNode userMessage = input.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "input_text")
                .put("text", PromptFactory.mergedCandidatePrompt(documentType, originalFileName, truncateForFinalStep(mergedCandidates)));

        ObjectNode text = requestBody.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", "execution_plan");
        format.put("strict", true);
        format.set("schema", JsonSchemaFactory.analysisSchema(objectMapper, detailLevel.name()));
        return requestBody;
    }

    private AnalysisMode chooseAnalysisMode(String extractedText, int chunkCount, String mergedCandidates) {
        int textLength = extractedText == null ? 0 : extractedText.length();
        int mergedLength = mergedCandidates == null ? 0 : mergedCandidates.length();

        if (textLength >= COMPACT_TRIGGER_TEXT_LENGTH
                || chunkCount >= COMPACT_TRIGGER_CHUNK_COUNT
                || mergedLength >= COMPACT_TRIGGER_SUMMARY_LENGTH) {
            return AnalysisMode.COMPACT;
        }
        return AnalysisMode.STANDARD;
    }

    private boolean isMaxOutputTokenIncomplete(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return "incomplete".equals(root.path("status").asText())
                    && "max_output_tokens".equals(root.path("incomplete_details").path("reason").asText());
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private ObjectNode baseRequestBody(int maxOutputTokens) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", appProperties.getOpenAi().getModel());
        requestBody.put("max_output_tokens", maxOutputTokens);
        return requestBody;
    }

    private String sendRequest(ObjectNode requestBody) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appProperties.getOpenAi().getApiKey());

        ResponseEntity<String> response = restTemplate.exchange(
                appProperties.getOpenAi().getApiUrl(),
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers),
                String.class
        );
        return response.getBody();
    }

    private <T> T parseStructuredResponse(String responseBody,
                                          Class<T> targetType,
                                          String missingOutputMessage,
                                          String parseErrorMessage) {
        try {
            String outputText = readStructuredOutputText(responseBody, missingOutputMessage);
            return objectMapper.readValue(outputText, targetType);
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException(parseErrorMessage, exception);
        }
    }

    private String readStructuredOutputText(String responseBody, String missingOutputMessage) throws JsonProcessingException {
        JsonNode root = readCompletedResponse(responseBody);

        JsonNode directOutputText = root.path("output_text");
        if (directOutputText.isTextual() && StringUtils.hasText(directOutputText.asText())) {
            return directOutputText.asText();
        }

        JsonNode output = root.path("output");
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }

            for (JsonNode contentItem : item.path("content")) {
                if ("refusal".equals(contentItem.path("type").asText())) {
                    throw new DocumentAnalysisException("OpenAI refused the request: " + contentItem.path("refusal").asText());
                }
                if ("output_text".equals(contentItem.path("type").asText())) {
                    return contentItem.path("text").asText();
                }
            }
        }

        throw new DocumentAnalysisException(missingOutputMessage);
    }

    private String parseTextResponse(String responseBody) {
        try {
            JsonNode root = readCompletedResponse(responseBody);
            JsonNode directOutputText = root.path("output_text");
            if (directOutputText.isTextual() && StringUtils.hasText(directOutputText.asText())) {
                return directOutputText.asText().trim();
            }

            JsonNode output = root.path("output");
            for (JsonNode item : output) {
                if (!"message".equals(item.path("type").asText())) {
                    continue;
                }
                for (JsonNode contentItem : item.path("content")) {
                    if ("refusal".equals(contentItem.path("type").asText())) {
                        throw new DocumentAnalysisException("OpenAI refused the request: " + contentItem.path("refusal").asText());
                    }
                    if ("output_text".equals(contentItem.path("type").asText())) {
                        return contentItem.path("text").asText().trim();
                    }
                }
            }

            throw new DocumentAnalysisException("Text output was not found in the OpenAI response.");
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("Failed to parse the OpenAI text response.", exception);
        }
    }

    private JsonNode readCompletedResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        String status = root.path("status").asText();
        if ("incomplete".equals(status)) {
            String reason = root.path("incomplete_details").path("reason").asText("unknown");
            throw new DocumentAnalysisException("OpenAI response was incomplete. Reason: " + reason + ".");
        }
        return root;
    }

    private String buildApiErrorMessage(HttpStatusCodeException exception) {
        String body = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return "OpenAI API call failed with status " + exception.getStatusCode() + ".";
        }

        try {
            JsonNode errorRoot = objectMapper.readTree(body);
            JsonNode error = errorRoot.path("error");
            String message = error.path("message").asText(body);
            return "OpenAI API call failed with status " + exception.getStatusCode() + ": " + message;
        } catch (JsonProcessingException parsingException) {
            return "OpenAI API call failed with status " + exception.getStatusCode() + ": " + body;
        }
    }

    private List<String> splitIntoChunks(String text) {
        String normalized = normalizeWhitespace(text);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int splitPoint = findSplitPoint(normalized, start, end);
                if (splitPoint > start) {
                    end = splitPoint;
                }
            }
            chunks.add(normalized.substring(start, end).trim());
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private int findSplitPoint(String text, int start, int end) {
        int minIndex = Math.min(end - 1, start + (CHUNK_SIZE / 2));
        for (int index = end - 1; index >= minIndex; index--) {
            char current = text.charAt(index);
            if (current == '\n' || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        return end;
    }

    private String mergeChunkAnalyses(List<ChunkAnalysisDto> chunkAnalyses) {
        List<String> summaries = new ArrayList<>();
        List<String> goals = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        Map<String, PlanTaskDto> mergedTasks = new LinkedHashMap<>();

        for (ChunkAnalysisDto chunkAnalysis : chunkAnalyses) {
            addUnique(summaries, chunkAnalysis.getSummary(), MERGED_CANDIDATE_LIMIT);
            addUnique(goals, chunkAnalysis.getGoals(), MERGED_CANDIDATE_LIMIT);
            addUnique(risks, chunkAnalysis.getRisks(), MERGED_CANDIDATE_LIMIT);
            addUnique(questions, chunkAnalysis.getQuestions(), MERGED_CANDIDATE_LIMIT);
            mergeTasks(mergedTasks, chunkAnalysis.getTasks());
        }

        return buildMergedCandidateText(
                summaries,
                goals,
                new ArrayList<>(mergedTasks.values()),
                risks,
                questions
        );
    }

    private void addUnique(List<String> target, List<String> source, int limit) {
        if (source == null) {
            return;
        }
        for (String item : source) {
            addUnique(target, item, limit);
        }
    }

    private void addUnique(List<String> target, String item, int limit) {
        String normalized = normalizeCandidate(item);
        if (!StringUtils.hasText(normalized) || target.size() >= limit) {
            return;
        }
        for (String existing : target) {
            if (normalizeKey(existing).equals(normalizeKey(normalized))) {
                return;
            }
        }
        target.add(normalized);
    }

    private void mergeTasks(Map<String, PlanTaskDto> mergedTasks, List<PlanTaskDto> tasks) {
        if (tasks == null) {
            return;
        }
        for (PlanTaskDto task : tasks) {
            if (task == null || !StringUtils.hasText(task.getTask()) || mergedTasks.size() >= MERGED_CANDIDATE_LIMIT && !mergedTasks.containsKey(normalizeKey(task.getTask()))) {
                continue;
            }
            String key = normalizeKey(task.getTask());
            PlanTaskDto normalizedTask = normalizeTask(task);
            PlanTaskDto existing = mergedTasks.get(key);
            if (existing == null) {
                mergedTasks.put(key, normalizedTask);
                continue;
            }
            mergedTasks.put(key, mergeTask(existing, normalizedTask));
        }
    }

    private PlanTaskDto normalizeTask(PlanTaskDto task) {
        PlanTaskDto normalized = new PlanTaskDto();
        normalized.setTask(normalizeCandidate(task.getTask()));
        normalized.setPriority(normalizePriority(task.getPriority()));
        normalized.setStatus(normalizeStatus(task.getStatus()));
        normalized.setDueDate(trimOrNull(task.getDueDate()));
        normalized.setCompletedAt(trimOrNull(task.getCompletedAt()));
        normalized.setOwner(trimOrNull(task.getOwner()));
        normalized.setReviewer(trimOrNull(task.getReviewer()));
        return normalized;
    }

    private PlanTaskDto mergeTask(PlanTaskDto existing, PlanTaskDto incoming) {
        PlanTaskDto merged = new PlanTaskDto();
        merged.setTask(preferLonger(existing.getTask(), incoming.getTask()));
        merged.setPriority(preferPriority(existing.getPriority(), incoming.getPriority()));
        merged.setStatus(preferStatus(existing.getStatus(), incoming.getStatus()));
        merged.setDueDate(preferFilled(existing.getDueDate(), incoming.getDueDate()));
        merged.setCompletedAt(preferFilled(existing.getCompletedAt(), incoming.getCompletedAt()));
        merged.setOwner(preferFilled(existing.getOwner(), incoming.getOwner()));
        merged.setReviewer(preferFilled(existing.getReviewer(), incoming.getReviewer()));
        return merged;
    }

    private String buildMergedCandidateText(List<String> summaries,
                                            List<String> goals,
                                            List<PlanTaskDto> tasks,
                                            List<String> risks,
                                            List<String> questions) {
        StringBuilder merged = new StringBuilder();
        appendSection(merged, "Summary candidates", summaries);
        appendSection(merged, "Goal candidates", goals);
        appendTaskSection(merged, tasks);
        appendSection(merged, "Risk candidates", risks);
        appendSection(merged, "Question candidates", questions);
        return merged.toString().trim();
    }

    private void appendSection(StringBuilder builder, String title, List<String> items) {
        builder.append(title).append(":\n");
        if (items == null || items.isEmpty()) {
            builder.append("- None\n\n");
            return;
        }
        for (String item : items) {
            builder.append("- ").append(item).append("\n");
        }
        builder.append("\n");
    }

    private void appendTaskSection(StringBuilder builder, List<PlanTaskDto> tasks) {
        builder.append("Task candidates:\n");
        if (tasks == null || tasks.isEmpty()) {
            builder.append("- None\n\n");
            return;
        }
        for (PlanTaskDto task : tasks) {
            builder.append("- ")
                    .append('[').append(task.getPriority()).append("] ")
                    .append('[').append(task.getStatus()).append("] ")
                    .append(task.getTask())
                    .append(" | dueDate: ").append(orTbd(task.getDueDate()))
                    .append(" | completedAt: ").append(orTbd(task.getCompletedAt()))
                    .append(" | owner: ").append(orTbd(task.getOwner()))
                    .append(" | reviewer: ").append(orTbd(task.getReviewer()))
                    .append("\n");
        }
        builder.append("\n");
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String truncateForFinalStep(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_FINAL_INPUT_CHARS ? text : text.substring(0, MAX_FINAL_INPUT_CHARS);
    }

    private String summaryCompressionInstructions(DocumentType documentType) {
        return """
                You are compressing merged business-document candidates for a later structured planning step.
                Do not return JSON.
                Write in Korean.
                Keep only the most important facts needed for execution planning.
                Preserve high-priority tasks, deadlines, owners, reviewers, and major risks when present.
                Merge duplicates aggressively.
                Limit the output to five short sections: summary, goals, tasks, risks, questions.
                Prefer short bullet-like phrases over sentences.
                Keep the output under 5,500 characters.
                The document type is %s.
                """.formatted(documentType.name());
    }

    private String normalizeCandidate(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", " ") : null;
    }

    private String normalizeKey(String value) {
        String normalized = normalizeCandidate(value);
        if (normalized == null) {
            return "";
        }
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizePriority(String priority) {
        String normalized = StringUtils.hasText(priority) ? priority.trim().toUpperCase(Locale.ROOT) : "MEDIUM";
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "MEDIUM";
        };
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "NOT_STARTED";
        return switch (normalized) {
            case "NOT_STARTED", "IN_PROGRESS", "COMPLETED" -> normalized;
            default -> "NOT_STARTED";
        };
    }

    private String preferPriority(String left, String right) {
        return priorityRank(right) > priorityRank(left) ? right : left;
    }

    private int priorityRank(String priority) {
        return switch (normalizePriority(priority)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private String preferStatus(String left, String right) {
        return statusRank(right) > statusRank(left) ? right : left;
    }

    private int statusRank(String status) {
        return switch (normalizeStatus(status)) {
            case "COMPLETED" -> 3;
            case "IN_PROGRESS" -> 2;
            default -> 1;
        };
    }

    private String preferFilled(String left, String right) {
        if (StringUtils.hasText(left) && !"TBD".equalsIgnoreCase(left)) {
            return left.trim();
        }
        if (StringUtils.hasText(right)) {
            return right.trim();
        }
        return left == null ? null : left.trim();
    }

    private String preferLonger(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        return right.trim().length() > left.trim().length() ? right.trim() : left.trim();
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String orTbd(String value) {
        return StringUtils.hasText(value) ? value : "TBD";
    }
}
