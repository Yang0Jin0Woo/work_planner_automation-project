package com.example.BPA_project.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Storage storage = new Storage();
    private final OpenAi openAi = new OpenAi();

    public Storage getStorage() {
        return storage;
    }

    public OpenAi getOpenAi() {
        return openAi;
    }

    public static class Storage {
        @NotBlank
        private String uploadDir;
        @NotBlank
        private String analysisDir;
        @NotBlank
        private String reportDir;

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }

        public String getAnalysisDir() {
            return analysisDir;
        }

        public void setAnalysisDir(String analysisDir) {
            this.analysisDir = analysisDir;
        }

        public String getReportDir() {
            return reportDir;
        }

        public void setReportDir(String reportDir) {
            this.reportDir = reportDir;
        }
    }

    public static class OpenAi {
        @NotBlank
        private String apiUrl;
        private String apiKey;
        private String model = "gpt-4.1";
        private int maxOutputTokens = 1800;

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }
    }
}
