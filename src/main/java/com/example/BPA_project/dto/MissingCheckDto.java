package com.example.BPA_project.dto;

import java.util.ArrayList;
import java.util.List;

public class MissingCheckDto {

    private boolean needsReview;
    private List<String> reviewNotes = new ArrayList<>();

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public List<String> getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(List<String> reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}