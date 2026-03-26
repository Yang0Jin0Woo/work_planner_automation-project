package com.example.BPA_project.util;

import com.example.BPA_project.dto.DocumentType;

public final class PromptFactory {

    private PromptFactory() {
    }

    public static String instructions(DocumentType documentType) {
        return instructions(documentType, false);
    }

    public static String instructions(DocumentType documentType, boolean compactMode) {
        String common = """
                You are an analyst for an office automation tool.
                Read the uploaded document and produce an execution plan draft rather than a simple summary.
                Your response must follow the provided JSON schema exactly.
                Keep the output practical, document-grounded, and editable by business users.
                Write all user-facing content in Korean.
                Use Korean for title, summary, scheduleDraft, goals, risks, questions, task text, owner, reviewer, and any free-text fields.
                Keep enum-like values exactly as required by the schema, such as HIGH, MEDIUM, LOW, NOT_STARTED, IN_PROGRESS, and COMPLETED.
                Each task must include task, priority, status, dueDate, completedAt, owner, and reviewer.
                Use NOT_STARTED when the source document does not justify that the task is already underway or completed.
                Use null or 'TBD' when the source document does not justify a concrete owner, reviewer, or date.
                Only set completedAt when the task is explicitly completed or the completion date is clearly supported by the document.
                Merge duplicates and near-duplicates aggressively.
                Prefer concise phrases over long sentences.
                scheduleDraft must describe the overall rollout flow in 3 to 5 sentences.
                """;

        String compact = compactMode
                ? """
                Keep the response compact.
                Return at most 2 goals, 5 tasks, 2 risks, and 2 questions.
                If the source contains many similar items, merge them into broader actionable items instead of listing each one separately.
                """
                : "";

        return common + "\n" + compact + "\n" + switch (documentType) {
            case MEETING -> """
                    This is meeting material.
                    Focus on action items, follow-up schedule, likely owners, review responsibility, and unresolved discussion points.
                    """;
            case REPORT -> """
                    This is a report.
                    Focus on core issues, response plan, progress status, priorities, risks, and decision support points.
                    """;
            case PROPOSAL -> """
                    This is a proposal.
                    Focus on rollout timeline, preparation items, review points, and prerequisites for execution.
                    """;
        };
    }

    public static String userPrompt(DocumentType documentType, String originalFileName, String extractedText) {
        return """
                Uploaded document metadata:

                - Document type: %s
                - Original file name: %s

                Document text:
                %s
                """.formatted(documentType.name(), originalFileName, extractedText);
    }
}