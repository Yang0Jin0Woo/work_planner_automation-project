package com.example.BPA_project.controller;

import com.example.BPA_project.dto.DocumentType;
import com.example.BPA_project.dto.UploadForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        if (!model.containsAttribute("uploadForm")) {
            model.addAttribute("uploadForm", new UploadForm());
        }
        model.addAttribute("documentTypes", DocumentType.values());
        return "home";
    }
}
