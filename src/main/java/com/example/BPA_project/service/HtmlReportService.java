package com.example.BPA_project.service;

import com.example.BPA_project.model.AnalysisSession;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class HtmlReportService {

    private final TemplateEngine templateEngine;

    public HtmlReportService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(AnalysisSession session) {
        Context context = new Context(Locale.KOREAN);
        context.setVariable("session", session);
        context.setVariable("result", session.getAnalysisResult());
        context.setVariable("fileInfo", session.getStoredFileInfo());
        return templateEngine.process("report", context);
    }
}
