package com.jobstar.backend.controller;

import java.util.List;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.service.ApplicationService;
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
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public List<Application> getAllApplications(@AuthenticationPrincipal UserAccount currentUser) {
        return applicationService.getAllApplications(currentUser);
    }

    @GetMapping("/{id}")
    public Application getApplicationById(@PathVariable Long id, @AuthenticationPrincipal UserAccount currentUser) {
        return applicationService.getApplicationById(id, currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Application createApplication(@RequestBody Application application, @AuthenticationPrincipal UserAccount currentUser) {
        return applicationService.createApplication(application, currentUser);
    }

    @PutMapping("/{id}")
    public Application updateApplication(@PathVariable Long id, @RequestBody Application application,
            @AuthenticationPrincipal UserAccount currentUser) {
        return applicationService.updateApplication(id, application, currentUser);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApplication(@PathVariable Long id, @AuthenticationPrincipal UserAccount currentUser) {
        applicationService.deleteApplication(id, currentUser);
    }
}
