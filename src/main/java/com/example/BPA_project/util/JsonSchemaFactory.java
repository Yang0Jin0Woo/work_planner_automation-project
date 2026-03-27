package com.example.BPA_project.util;

import com.example.BPA_project.dto.DocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonSchemaFactory {

    private JsonSchemaFactory() {
    }

    public static ObjectNode analysisSchema(ObjectMapper objectMapper) {
        return analysisSchema(objectMapper, "STANDARD", DocumentType.MEETING);
    }

    public static ObjectNode analysisSchema(ObjectMapper objectMapper, boolean compactMode) {
        return analysisSchema(objectMapper, compactMode ? "COMPACT" : "STANDARD", DocumentType.MEETING);
    }

    public static ObjectNode analysisSchema(ObjectMapper objectMapper, String detailLevel) {
        return analysisSchema(objectMapper, detailLevel, DocumentType.MEETING);
    }

    public static ObjectNode analysisSchema(ObjectMapper objectMapper, String detailLevel, DocumentType documentType) {
        Limits limits = Limits.forLevelAndType(detailLevel, documentType);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");

        ObjectNode properties = root.putObject("properties");
        properties.putObject("documentType")
                .put("type", "string")
                .putArray("enum")
                .add("MEETING")
                .add("REPORT")
                .add("PROPOSAL");
        properties.putObject("title")
                .put("type", "string")
                .put("maxLength", limits.titleMaxLength);
        properties.putObject("summary")
                .put("type", "string")
                .put("maxLength", limits.summaryMaxLength);
        properties.putObject("scheduleDraft")
                .put("type", "string")
                .put("maxLength", limits.scheduleMaxLength);

        ObjectNode goals = properties.putObject("goals");
        goals.put("type", "array");
        goals.put("maxItems", limits.goalMaxItems);
        goals.putObject("items")
                .put("type", "string")
                .put("maxLength", limits.goalItemMaxLength);

        ObjectNode tasks = properties.putObject("tasks");
        tasks.put("type", "array");
        tasks.put("maxItems", limits.taskMaxItems);
        tasks.set("items", taskItemSchema(objectMapper, limits.taskTextMaxLength));

        ObjectNode risks = properties.putObject("risks");
        risks.put("type", "array");
        risks.put("maxItems", limits.riskMaxItems);
        risks.putObject("items")
                .put("type", "string")
                .put("maxLength", limits.riskItemMaxLength);

        ObjectNode questions = properties.putObject("questions");
        questions.put("type", "array");
        questions.put("maxItems", limits.questionMaxItems);
        questions.putObject("items")
                .put("type", "string")
                .put("maxLength", limits.questionItemMaxLength);

        root.putArray("required")
                .add("documentType")
                .add("title")
                .add("summary")
                .add("scheduleDraft")
                .add("goals")
                .add("tasks")
                .add("risks")
                .add("questions");
        root.put("additionalProperties", false);
        return root;
    }

    public static ObjectNode chunkAnalysisSchema(ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");

        ObjectNode properties = root.putObject("properties");
        properties.putObject("summary")
                .put("type", "string")
                .put("maxLength", 400);

        ObjectNode goals = properties.putObject("goals");
        goals.put("type", "array");
        goals.put("maxItems", 4);
        goals.putObject("items")
                .put("type", "string")
                .put("maxLength", 100);

        ObjectNode tasks = properties.putObject("tasks");
        tasks.put("type", "array");
        tasks.put("maxItems", 5);
        tasks.set("items", taskItemSchema(objectMapper, 120));

        ObjectNode risks = properties.putObject("risks");
        risks.put("type", "array");
        risks.put("maxItems", 4);
        risks.putObject("items")
                .put("type", "string")
                .put("maxLength", 100);

        ObjectNode questions = properties.putObject("questions");
        questions.put("type", "array");
        questions.put("maxItems", 4);
        questions.putObject("items")
                .put("type", "string")
                .put("maxLength", 100);

        root.putArray("required")
                .add("summary")
                .add("goals")
                .add("tasks")
                .add("risks")
                .add("questions");
        root.put("additionalProperties", false);
        return root;
    }

    private static ObjectNode taskItemSchema(ObjectMapper objectMapper, int taskTextMaxLength) {
        ObjectNode taskItem = objectMapper.createObjectNode();
        taskItem.put("type", "object");
        ObjectNode taskProps = taskItem.putObject("properties");
        taskProps.putObject("task")
                .put("type", "string")
                .put("maxLength", taskTextMaxLength);
        taskProps.putObject("priority")
                .put("type", "string")
                .putArray("enum")
                .add("HIGH")
                .add("MEDIUM")
                .add("LOW");
        taskProps.putObject("status")
                .put("type", "string")
                .putArray("enum")
                .add("NOT_STARTED")
                .add("IN_PROGRESS")
                .add("COMPLETED");
        taskProps.putObject("dueDate").putArray("type").add("string").add("null");
        taskProps.putObject("completedAt").putArray("type").add("string").add("null");
        taskProps.putObject("owner").putArray("type").add("string").add("null");
        taskProps.putObject("reviewer").putArray("type").add("string").add("null");
        taskItem.putArray("required")
                .add("task")
                .add("priority")
                .add("status")
                .add("dueDate")
                .add("completedAt")
                .add("owner")
                .add("reviewer");
        taskItem.put("additionalProperties", false);
        return taskItem;
    }

    private record Limits(int titleMaxLength,
                          int summaryMaxLength,
                          int scheduleMaxLength,
                          int goalMaxItems,
                          int goalItemMaxLength,
                          int taskMaxItems,
                          int taskTextMaxLength,
                          int riskMaxItems,
                          int riskItemMaxLength,
                          int questionMaxItems,
                          int questionItemMaxLength) {
        private static Limits forLevelAndType(String detailLevel, DocumentType documentType) {
            if ("COMPACT".equals(detailLevel)) {
                return new Limits(60, 320, 240, 2, 80, 4, 90, 2, 90, 2, 90);
            }

            return switch (documentType) {
                case REPORT -> new Limits(100, 900, 700, 4, 140, 6, 160, 4, 160, 4, 160);
                case PROPOSAL -> new Limits(100, 900, 700, 4, 140, 5, 160, 4, 160, 4, 160);
                case MEETING -> new Limits(100, 900, 700, 4, 140, 8, 160, 4, 160, 4, 160);
            };
        }
    }
}