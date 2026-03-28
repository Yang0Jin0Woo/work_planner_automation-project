package com.example.BPA_project.controller;

import com.example.BPA_project.dto.DocumentType;
import com.example.BPA_project.dto.UploadForm;
import com.example.BPA_project.service.AnalysisSessionService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final AnalysisSessionService analysisSessionService;

    public HomeController(AnalysisSessionService analysisSessionService) {
        this.analysisSessionService = analysisSessionService;
    }

    @GetMapping("/")
    public String home(Model model) {
        if (!model.containsAttribute("uploadForm")) {
            model.addAttribute("uploadForm", new UploadForm());
        }
        model.addAttribute("documentTypes", DocumentType.values());
        return "home";
    }

    @GetMapping("/documents/delete")
    public String deletePage(Model model) {
        model.addAttribute("sessions", analysisSessionService.getAllSessions());
        return "delete";
    }

    @PostMapping("/documents/delete-selected")
    public String deleteSelectedSessions(@RequestParam(name = "sessionIds", required = false) List<String> sessionIds,
                                         RedirectAttributes redirectAttributes) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Select at least one analysis result to delete.");
            return "redirect:/documents/delete";
        }

        analysisSessionService.deleteSessions(sessionIds);
        redirectAttributes.addFlashAttribute("successMessage", sessionIds.size() + " analysis result(s) deleted.");
        return "redirect:/documents/delete";
    }

    @PostMapping("/documents/delete-all")
    public String deleteAllSessions(RedirectAttributes redirectAttributes) {
        analysisSessionService.deleteAllSessions();
        redirectAttributes.addFlashAttribute("successMessage", "All saved analysis sessions were deleted.");
        return "redirect:/documents/delete";
    }
}
