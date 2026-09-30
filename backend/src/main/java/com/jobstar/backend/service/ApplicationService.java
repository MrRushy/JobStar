package com.jobstar.backend.service;

import java.util.List;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.ApplicationStatus;
import com.jobstar.backend.model.ResumeVersion;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ApplicationRepository;
import com.jobstar.backend.repository.ResumeVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ResumeVersionRepository resumeVersionRepository;

    public ApplicationService(ApplicationRepository applicationRepository, ResumeVersionRepository resumeVersionRepository) {
        this.applicationRepository = applicationRepository;
        this.resumeVersionRepository = resumeVersionRepository;
    }

    public List<Application> getAllApplications(UserAccount owner) {
        return applicationRepository.findAllByOwnerOrderByIdDesc(owner);
    }

    public Application getApplicationById(Long id, UserAccount owner) {
        return findApplicationOrThrow(id, owner);
    }

    public Application createApplication(Application application, UserAccount owner) {
        normalizeAndValidate(application);
        application.setOwner(owner);
        return applicationRepository.save(application);
    }

    public Application updateApplication(Long id, Application updatedApplication, UserAccount owner) {
        Application existingApplication = findApplicationOrThrow(id, owner);
        normalizeAndValidate(updatedApplication);

        existingApplication.setCompany(updatedApplication.getCompany());
        existingApplication.setPosition(updatedApplication.getPosition());
        existingApplication.setStatus(updatedApplication.getStatus());
        existingApplication.setLocation(updatedApplication.getLocation());
        existingApplication.setJobUrl(updatedApplication.getJobUrl());
        existingApplication.setAppliedDate(updatedApplication.getAppliedDate());
        existingApplication.setNotes(updatedApplication.getNotes());
        existingApplication.setJobDescription(updatedApplication.getJobDescription());

        return applicationRepository.save(existingApplication);
    }

    public void deleteApplication(Long id, UserAccount owner) {
        applicationRepository.delete(findApplicationOrThrow(id, owner));
    }

    public Application selectResumeVersion(Long applicationId, Long resumeVersionId, UserAccount owner) {
        Application application = findApplicationOrThrow(applicationId, owner);
        ResumeVersion resumeVersion = resumeVersionId == null ? null : resumeVersionRepository.findByIdAndOwner(resumeVersionId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume was not found"));
        application.setResumeVersion(resumeVersion);
        return applicationRepository.save(application);
    }

    private Application findApplicationOrThrow(Long id, UserAccount owner) {
        return applicationRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Application with id " + id + " was not found"));
    }

    private void normalizeAndValidate(Application application) {
        if (application.getCompany() == null || application.getCompany().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company is required");
        }
        if (application.getPosition() == null || application.getPosition().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position is required");
        }

        application.setCompany(application.getCompany().trim());
        application.setPosition(application.getPosition().trim());
        application.setLocation(normalizeOptionalText(application.getLocation()));
        application.setJobUrl(normalizeOptionalText(application.getJobUrl()));
        application.setNotes(normalizeOptionalText(application.getNotes()));
        application.setJobDescription(normalizeOptionalText(application.getJobDescription()));

        if (application.getStatus() == null) {
            application.setStatus(ApplicationStatus.SAVED);
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
