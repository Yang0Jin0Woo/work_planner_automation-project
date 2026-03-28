package com.example.BPA_project.repository;

import com.example.BPA_project.entity.AnalysisSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisSessionRepository extends JpaRepository<AnalysisSessionEntity, String> {
}
