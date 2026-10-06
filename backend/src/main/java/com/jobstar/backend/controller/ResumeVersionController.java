package com.jobstar.backend.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import com.jobstar.backend.web.ResumeFileErrorPage;

import com.jobstar.backend.model.ResumeVersion;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.service.ResumeVersionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/resumes")
public class ResumeVersionController {

    private final ResumeVersionService resumeVersionService;

    public ResumeVersionController(ResumeVersionService resumeVersionService) {
        this.resumeVersionService = resumeVersionService;
    }

    @GetMapping
    public List<ResumeVersion> getAllResumeVersions(@AuthenticationPrincipal UserAccount currentUser) {
        return resumeVersionService.getAllResumeVersions(currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeVersion createResumeVersion(@RequestBody ResumeVersion resumeVersion, @AuthenticationPrincipal UserAccount currentUser) {
        return resumeVersionService.createResumeVersion(resumeVersion, currentUser);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeVersion uploadResumeVersion(@RequestParam String label,
            @RequestParam(required = false) String notes, @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserAccount currentUser) {
        return resumeVersionService.createUploadedResumeVersion(label, notes, file, currentUser);
    }

    @PutMapping("/{resumeVersionId}")
    public ResumeVersion updateResumeVersion(@PathVariable Long resumeVersionId, @RequestBody ResumeVersion resumeVersion,
            @AuthenticationPrincipal UserAccount currentUser) {
        return resumeVersionService.updateResumeVersion(resumeVersionId, resumeVersion, currentUser);
    }

    @GetMapping("/{resumeVersionId}/file")
    public ResponseEntity<?> downloadResumeFile(@PathVariable Long resumeVersionId,
            @AuthenticationPrincipal UserAccount currentUser, HttpServletRequest request) {
        try {
            var file = resumeVersionService.downloadResumeFile(resumeVersionId, currentUser);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(file.fileName() == null ? "resume" : file.fileName()).build().toString())
                    .body(file.content());
        } catch (ResponseStatusException exception) {
            if (!ResumeFileErrorPage.isBrowserFileRequest(request)) throw exception;
            return ResumeFileErrorPage.response(exception.getStatusCode().value());
        }
    }

    @DeleteMapping("/{resumeVersionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResumeVersion(@PathVariable Long resumeVersionId,
            @RequestParam(defaultValue = "false") boolean unlinkApplications,
            @AuthenticationPrincipal UserAccount currentUser) {
        resumeVersionService.deleteResumeVersion(resumeVersionId, unlinkApplications, currentUser);
    }
}
