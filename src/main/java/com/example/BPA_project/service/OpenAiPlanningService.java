package com.example.BPA_project.service;

import com.example.BPA_project.config.AppProperties;
import com.example.BPA_project.dto.AnalysisResultDto;
import com.example.BPA_project.dto.DocumentType;
import com.example.BPA_project.exception.DocumentAnalysisException;
import com.example.BPA_project.util.JsonSchemaFactory;
import com.example.BPA_project.util.PromptFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
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

    private static final int CHUNK_SIZE = 9_000;
    private static final int CHUNK_OVERLAP = 1_000;
    private static final int MAX_CHUNKS = 8;
    private static final int MAX_FINAL_INPUT_CHARS = 18_000;
    private static final int CHUNK_SUMMARY_OUTPUT_TOKENS = 600;
    private static final int STRUCTURED_RETRY_OUTPUT_TOKENS = 3_600;
    private static final int STRUCTURED_COMPACT_RETRY_OUTPUT_TOKENS = 4_200;

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

            List<String> chunkSummaries = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                chunkSummaries.add(summarizeChunk(documentType, originalFileName, chunks.get(index), index + 1, chunks.size()));
            }

            String combinedSummary = combineChunkSummaries(chunkSummaries);
            return requestStructuredPlan(documentType, originalFileName, combinedSummary);
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

    private String summarizeChunk(DocumentType documentType,
                                  String originalFileName,
                                  String chunkText,
                                  int chunkNumber,
                                  int totalChunks) throws JsonProcessingException {
        ObjectNode requestBody = baseRequestBody(CHUNK_SUMMARY_OUTPUT_TOKENS);
        requestBody.put("instructions", chunkSummaryInstructions(documentType, chunkNumber, totalChunks));

        ArrayNode input = requestBody.putArray("input");
        ObjectNode userMessage = input.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "input_text")
                .put("text", chunkUserPrompt(documentType, originalFileName, chunkText, chunkNumber, totalChunks));

        return parseTextResponse(sendRequest(requestBody));
    }

    private AnalysisResultDto requestStructuredPlan(DocumentType documentType,
                                                    String originalFileName,
                                                    String summarizedText) throws JsonProcessingException {
        int initialTokens = appProperties.getOpenAi().getMaxOutputTokens();
        String responseBody = sendRequest(buildStructuredPlanRequest(documentType, originalFileName, summarizedText, initialTokens, false));

        if (isMaxOutputTokenIncomplete(responseBody) && initialTokens < STRUCTURED_RETRY_OUTPUT_TOKENS) {
            responseBody = sendRequest(buildStructuredPlanRequest(
                    documentType,
                    originalFileName,
                    summarizedText,
                    STRUCTURED_RETRY_OUTPUT_TOKENS,
                    false
            ));
        }

        if (isMaxOutputTokenIncomplete(responseBody)) {
            responseBody = sendRequest(buildStructuredPlanRequest(
                    documentType,
                    originalFileName,
                    summarizedText,
                    STRUCTURED_COMPACT_RETRY_OUTPUT_TOKENS,
                    true
            ));
        }

        return parseStructuredResponse(responseBody);
    }

    private ObjectNode buildStructuredPlanRequest(DocumentType documentType,
                                                  String originalFileName,
                                                  String summarizedText,
                                                  int maxOutputTokens,
                                                  boolean compactMode) {
        ObjectNode requestBody = baseRequestBody(maxOutputTokens);
        requestBody.put("instructions", PromptFactory.instructions(documentType, compactMode));

        ArrayNode input = requestBody.putArray("input");
        ObjectNode userMessage = input.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "input_text")
                .put("text", PromptFactory.userPrompt(documentType, originalFileName, truncateForFinalStep(summarizedText)));

        ObjectNode text = requestBody.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", "execution_plan");
        format.put("strict", true);
        format.set("schema", JsonSchemaFactory.analysisSchema(objectMapper));
        return requestBody;
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

    private AnalysisResultDto parseStructuredResponse(String responseBody) {
        try {
            JsonNode root = readCompletedResponse(responseBody);

            JsonNode directOutputText = root.path("output_text");
            if (directOutputText.isTextual() && StringUtils.hasText(directOutputText.asText())) {
                return objectMapper.readValue(directOutputText.asText(), AnalysisResultDto.class);
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
                        return objectMapper.readValue(contentItem.path("text").asText(), AnalysisResultDto.class);
                    }
                }
            }

            throw new DocumentAnalysisException("Structured JSON output was not found in the OpenAI response.");
        } catch (JsonProcessingException exception) {
            throw new DocumentAnalysisException("Failed to parse the OpenAI structured JSON response. The model output may have been truncated.", exception);
        }
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

    private String combineChunkSummaries(List<String> chunkSummaries) {
        StringBuilder combined = new StringBuilder();
        for (int index = 0; index < chunkSummaries.size(); index++) {
            combined.append("[Chunk ").append(index + 1).append("]\n");
            combined.append(chunkSummaries.get(index).trim()).append("\n\n");
        }
        return combined.toString().trim();
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

    private String chunkSummaryInstructions(DocumentType documentType, int chunkNumber, int totalChunks) {
        return """
                You are analyzing one chunk of a larger business document.
                Produce a concise chunk summary for later aggregation.
                Do not return JSON.
                Capture only document-grounded facts from this chunk.
                Keep it compact and scannable.
                Write the chunk summary itself in Korean. Use Korean section headers corresponding to summary, goals, tasks, risks, and questions.
                Limit each section to the most important items only and merge very similar points.
                For tasks, note any explicit or strongly implied priority, status, due date, completion date, owner, and reviewer.
                If a field is not supported by the text, say null or TBD.
                This is chunk %d of %d for a %s document.
                """.formatted(chunkNumber, totalChunks, documentType.name());
    }

    private String chunkUserPrompt(DocumentType documentType,
                                   String originalFileName,
                                   String chunkText,
                                   int chunkNumber,
                                   int totalChunks) {
        return """
                Uploaded document metadata:

                - Document type: %s
                - Original file name: %s
                - Chunk: %d of %d

                Chunk text:
                %s
                """.formatted(documentType.name(), originalFileName, chunkNumber, totalChunks, chunkText);
    }
}