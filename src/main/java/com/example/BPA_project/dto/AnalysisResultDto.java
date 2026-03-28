package com.example.BPA_project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class AnalysisResultDto {

    @NotNull
    private DocumentType documentType;

    @NotBlank(message = "제목은 필수 입력값입니다.")
    private String title;

    @NotBlank(message = "요약은 비워둘 수 없습니다.")
    private String summary;

    private String scheduleDraft;

    private List<String> goals = new ArrayList<>();

    @Valid
    private List<PlanTaskDto> tasks = new ArrayList<>();

    private List<String> risks = new ArrayList<>();
    private List<String> questions = new ArrayList<>();

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getScheduleDraft() {
        return scheduleDraft;
    }

    public void setScheduleDraft(String scheduleDraft) {
        this.scheduleDraft = scheduleDraft;
    }

    public List<String> getGoals() {
        return goals;
    }

    public void setGoals(List<String> goals) {
        this.goals = goals;
    }

    public List<PlanTaskDto> getTasks() {
        return tasks;
    }

    public void setTasks(List<PlanTaskDto> tasks) {
        this.tasks = tasks;
    }

    public List<String> getRisks() {
        return risks;
    }

    public void setRisks(List<String> risks) {
        this.risks = risks;
    }

    public List<String> getQuestions() {
        return questions;
    }

    public void setQuestions(List<String> questions) {
        this.questions = questions;
    }
}