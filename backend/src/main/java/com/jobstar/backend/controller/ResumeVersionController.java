package com.jobstar.backend.controller;

import java.util.List;

import com.jobstar.backend.model.ResumeVersion;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.service.ResumeVersionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications/{applicationId}/resumes")
public class ResumeVersionController {

    private final ResumeVersionService resumeVersionService;

    public ResumeVersionController(ResumeVersionService resumeVersionService) {
        this.resumeVersionService = resumeVersionService;
    }

    @GetMapping
    public List<ResumeVersion> getAllResumeVersions(@PathVariable Long applicationId,
            @AuthenticationPrincipal UserAccount currentUser) {
        return resumeVersionService.getAllResumeVersions(applicationId, currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeVersion createResumeVersion(@PathVariable Long applicationId, @RequestBody ResumeVersion resumeVersion,
            @AuthenticationPrincipal UserAccount currentUser) {
        return resumeVersionService.createResumeVersion(applicationId, resumeVersion, currentUser);
    }

    @PutMapping("/{resumeVersionId}")
    public ResumeVersion updateResumeVersion(@PathVariable Long applicationId, @PathVariable Long resumeVersionId,
            @RequestBody ResumeVersion resumeVersion, @AuthenticationPrincipal UserAccount currentUser) {
        return resumeVersionService.updateResumeVersion(applicationId, resumeVersionId, resumeVersion, currentUser);
    }

    @DeleteMapping("/{resumeVersionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResumeVersion(@PathVariable Long applicationId, @PathVariable Long resumeVersionId,
            @AuthenticationPrincipal UserAccount currentUser) {
        resumeVersionService.deleteResumeVersion(applicationId, resumeVersionId, currentUser);
    }
}
