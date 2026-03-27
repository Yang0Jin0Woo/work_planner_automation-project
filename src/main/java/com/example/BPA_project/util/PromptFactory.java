package com.example.BPA_project.util;

import com.example.BPA_project.dto.DocumentType;

public final class PromptFactory {

    private PromptFactory() {
    }

    public static String instructions(DocumentType documentType) {
        return instructions(documentType, "STANDARD");
    }

    public static String instructions(DocumentType documentType, boolean compactMode) {
        return instructions(documentType, compactMode ? "COMPACT" : "STANDARD");
    }

    public static String instructions(DocumentType documentType, String detailLevel) {
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
                Use short noun phrases where possible.
                The input may already contain merged task, risk, goal, and question candidates from chunk-level extraction.
                The source may also include separate sections for body text, table text, and image or diagram metadata.
                Preserve high-signal details from those candidates instead of discarding them.
                When table text is present, prioritize deadlines, owners, statuses, and other structured fields found in rows and columns.
                When visual metadata is present, use it as supporting context but do not invent image details that are not explicitly provided.
                """;

        String detailPrompt = switch (detailLevel) {
            case "COMPACT" -> """
                    Keep the response compact.
                    Return at most 2 goals, 4 tasks, 2 risks, and 2 questions.
                    Keep the title short.
                    Keep the summary to 2 or 3 short sentences.
                    Keep scheduleDraft to 2 or 3 short sentences.
                    If the source contains many similar items, merge them into broader actionable items instead of listing each one separately.
                    Prefer only the highest-priority actions.
                    """;
            default -> """
                    scheduleDraft must describe the overall rollout flow in 3 to 5 sentences.
                    Prefer the most important items over exhaustive lists.
                    """;
        };

        return common + "\n" + detailPrompt + "\n" + switch (documentType) {
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

    public static String chunkExtractionInstructions(DocumentType documentType, int chunkNumber, int totalChunks) {
        return """
                You are analyzing one chunk of a larger business document.
                Extract structured execution-planning candidates from this chunk.
                Your response must follow the provided JSON schema exactly.
                Capture only document-grounded facts from this chunk.
                Write all user-facing content in Korean.
                Keep items concise and scannable.
                Merge very similar points inside this chunk.
                The chunk may contain body text, table text, and image or diagram metadata.
                Use table rows and columns carefully when they provide dates, owners, statuses, or comparison points.
                Treat image or diagram metadata only as supporting hints unless explicit textual content is provided.
                For tasks, note any explicit or strongly implied priority, status, due date, completion date, owner, and reviewer.
                Use NOT_STARTED when the text does not justify that the task is already underway or completed.
                Use null or 'TBD' when the chunk does not justify a concrete owner, reviewer, or date.
                This is chunk %d of %d for a %s document.
                """.formatted(chunkNumber, totalChunks, documentType.name());
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

    public static String chunkExtractionPrompt(DocumentType documentType,
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

    public static String mergedCandidatePrompt(DocumentType documentType,
                                               String originalFileName,
                                               String mergedCandidatesText) {
        return """
                Uploaded document metadata:

                - Document type: %s
                - Original file name: %s

                Merged structured candidates from chunk analysis:
                %s
                """.formatted(documentType.name(), originalFileName, mergedCandidatesText);
    }
}
