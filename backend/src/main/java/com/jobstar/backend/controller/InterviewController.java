package com.jobstar.backend.controller;

import java.util.List;

import com.jobstar.backend.model.Interview;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.service.InterviewService;
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
@RequestMapping("/api/applications/{applicationId}/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping
    public List<Interview> getAllInterviews(@PathVariable Long applicationId, @AuthenticationPrincipal UserAccount currentUser) {
        return interviewService.getAllInterviews(applicationId, currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Interview createInterview(@PathVariable Long applicationId, @RequestBody Interview interview,
            @AuthenticationPrincipal UserAccount currentUser) {
        return interviewService.createInterview(applicationId, interview, currentUser);
    }

    @PutMapping("/{interviewId}")
    public Interview updateInterview(@PathVariable Long applicationId, @PathVariable Long interviewId,
            @RequestBody Interview interview, @AuthenticationPrincipal UserAccount currentUser) {
        return interviewService.updateInterview(applicationId, interviewId, interview, currentUser);
    }

    @DeleteMapping("/{interviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInterview(@PathVariable Long applicationId, @PathVariable Long interviewId,
            @AuthenticationPrincipal UserAccount currentUser) {
        interviewService.deleteInterview(applicationId, interviewId, currentUser);
    }
}
