package com.example.BPA_project.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

        ArrayNode goalsItems = properties.putObject("goals").put("type", "array").putArray("items");
        goalsItems.addObject().put("type", "string");

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
        taskProps.putObject("dueDate").putArray("type").add("string").add("null");
        taskProps.putObject("owner").putArray("type").add("string").add("null");
        taskItems.putArray("required").add("task").add("priority").add("dueDate").add("owner");
        taskItems.put("additionalProperties", false);

        ArrayNode risksItems = properties.putObject("risks").put("type", "array").putArray("items");
        risksItems.addObject().put("type", "string");

        ArrayNode questionItems = properties.putObject("questions").put("type", "array").putArray("items");
        questionItems.addObject().put("type", "string");

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
