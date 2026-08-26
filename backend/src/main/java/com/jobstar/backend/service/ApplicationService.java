package com.jobstar.backend.service;

import java.util.List;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.ApplicationStatus;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
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

        return applicationRepository.save(existingApplication);
    }

    public void deleteApplication(Long id, UserAccount owner) {
        applicationRepository.delete(findApplicationOrThrow(id, owner));
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
