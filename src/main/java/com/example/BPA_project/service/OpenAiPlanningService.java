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
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", appProperties.getOpenAi().getModel());
            requestBody.put("max_output_tokens", appProperties.getOpenAi().getMaxOutputTokens());
            requestBody.put("instructions", PromptFactory.instructions(documentType));

            ArrayNode input = requestBody.putArray("input");
            ObjectNode userMessage = input.addObject();
            userMessage.put("role", "user");
            ArrayNode content = userMessage.putArray("content");
            content.addObject()
                    .put("type", "input_text")
                    .put("text", PromptFactory.userPrompt(documentType, originalFileName, truncate(extractedText)));

            ObjectNode text = requestBody.putObject("text");
            ObjectNode format = text.putObject("format");
            format.put("type", "json_schema");
            format.put("name", "execution_plan");
            format.put("strict", true);
            format.set("schema", JsonSchemaFactory.analysisSchema(objectMapper));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(appProperties.getOpenAi().getApiKey());

            ResponseEntity<String> response = restTemplate.exchange(
                    appProperties.getOpenAi().getApiUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers),
                    String.class
            );

            return parseResponse(response.getBody());
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

    private AnalysisResultDto parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String status = root.path("status").asText();
            if ("incomplete".equals(status)) {
                String reason = root.path("incomplete_details").path("reason").asText("unknown");
                throw new DocumentAnalysisException("OpenAI response was incomplete. Reason: " + reason + ".");
            }

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

    private String truncate(String text) {
        int maxLength = 24_000;
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}