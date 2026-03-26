package com.example.BPA_project.dto;

import jakarta.validation.constraints.NotBlank;

public class PlanTaskDto {

    @NotBlank(message = "할 일은 비워둘 수 없습니다.")
    private String task;

    @NotBlank(message = "우선순위를 선택해주세요.")
    private String priority;

    private String dueDate;
    private String owner;

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

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
