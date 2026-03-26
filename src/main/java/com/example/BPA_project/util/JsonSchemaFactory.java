package com.example.BPA_project.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonSchemaFactory {

    private JsonSchemaFactory() {
    }

    public static ObjectNode analysisSchema(ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");

        ObjectNode properties = root.putObject("properties");
        properties.putObject("documentType")
                .put("type", "string")
                .putArray("enum")
                .add("MEETING")
                .add("REPORT")
                .add("PROPOSAL");
        properties.putObject("title").put("type", "string");
        properties.putObject("summary").put("type", "string");
        properties.putObject("scheduleDraft").put("type", "string");

        ObjectNode goals = properties.putObject("goals");
        goals.put("type", "array");
        goals.putObject("items").put("type", "string");

        ObjectNode tasks = properties.putObject("tasks");
        tasks.put("type", "array");
        ObjectNode taskItems = tasks.putObject("items");
        taskItems.put("type", "object");
        ObjectNode taskProps = taskItems.putObject("properties");
        taskProps.putObject("task").put("type", "string");
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
        risks.putObject("items").put("type", "string");

        ObjectNode questions = properties.putObject("questions");
        questions.put("type", "array");
        questions.putObject("items").put("type", "string");

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
}