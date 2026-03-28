package com.example.BPA_project.dto;

import jakarta.validation.constraints.NotBlank;

public class PlanTaskDto {

    @NotBlank(message = "작업명은 비워둘 수 없습니다.")
    private String task;

    @NotBlank(message = "우선순위를 선택해주세요.")
    private String priority;

    @NotBlank(message = "상태를 선택해주세요.")
    private String status;

    private String dueDate;
    private String completedAt;
    private String owner;
    private String reviewer;

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }
}
