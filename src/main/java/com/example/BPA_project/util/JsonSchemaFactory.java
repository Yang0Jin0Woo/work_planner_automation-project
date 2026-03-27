package com.example.BPA_project.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonSchemaFactory {

    private JsonSchemaFactory() {
    }

    public static ObjectNode analysisSchema(ObjectMapper objectMapper) {
        return analysisSchema(objectMapper, "STANDARD");
    }

    public static ObjectNode analysisSchema(ObjectMapper objectMapper, boolean compactMode) {
        return analysisSchema(objectMapper, compactMode ? "COMPACT" : "STANDARD");
    }

    public static ObjectNode analysisSchema(ObjectMapper objectMapper, String detailLevel) {
        Limits limits = Limits.forLevel(detailLevel);

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
        ObjectNode taskItems = tasks.putObject("items");
        taskItems.put("type", "object");
        ObjectNode taskProps = taskItems.putObject("properties");
        taskProps.putObject("task")
                .put("type", "string")
                .put("maxLength", limits.taskTextMaxLength);
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
        taskItems.putArray("required")
                .add("task")
                .add("priority")
                .add("status")
                .add("dueDate")
                .add("completedAt")
                .add("owner")
                .add("reviewer");
        taskItems.put("additionalProperties", false);

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
        private static Limits forLevel(String detailLevel) {
            return switch (detailLevel) {
                case "ULTRA_COMPACT" -> new Limits(44, 220, 180, 2, 56, 3, 72, 2, 72, 1, 72);
                case "COMPACT" -> new Limits(60, 320, 240, 2, 80, 4, 90, 2, 90, 2, 90);
                default -> new Limits(100, 900, 700, 4, 140, 8, 160, 4, 160, 4, 160);
            };
        }
    }
}