package com.example.BPA_project.util;

import com.example.BPA_project.dto.DocumentType;

public final class PromptFactory {

    private PromptFactory() {
    }

    public static String instructions(DocumentType documentType) {
        String common = """
                You are an analyst for an office automation tool.
                Read the uploaded document and produce an execution plan draft rather than a simple summary.
                Your response must follow the provided JSON schema exactly.
                Keep the output practical, document-grounded, and editable by business users.
                Use null or 'TBD' when the source document does not justify a concrete owner or date.
                scheduleDraft must describe the overall rollout flow in 3 to 5 sentences.
                """;

        return common + "\n" + switch (documentType) {
            case MEETING -> """
                    This is meeting material.
                    Focus on action items, follow-up schedule, likely owners, and unresolved discussion points.
                    """;
            case REPORT -> """
                    This is a report.
                    Focus on core issues, response plan, priorities, risks, and decision support points.
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
