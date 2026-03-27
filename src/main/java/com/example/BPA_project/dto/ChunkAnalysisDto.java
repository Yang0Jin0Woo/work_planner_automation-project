package com.example.BPA_project.dto;

import java.util.ArrayList;
import java.util.List;

public class ChunkAnalysisDto {

    private String summary;
    private List<String> goals = new ArrayList<>();
    private List<PlanTaskDto> tasks = new ArrayList<>();
    private List<String> risks = new ArrayList<>();
    private List<String> questions = new ArrayList<>();

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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
