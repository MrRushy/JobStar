package com.jobstar.backend.service;

import java.util.List;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.ApplicationStatus;
import com.jobstar.backend.repository.ApplicationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public List<Application> getAllApplications() {
        return applicationRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public Application getApplicationById(Long id) {
        return findApplicationOrThrow(id);
    }

    public Application createApplication(Application application) {
        normalizeAndValidate(application);
        return applicationRepository.save(application);
    }

    public Application updateApplication(Long id, Application updatedApplication) {
        Application existingApplication = findApplicationOrThrow(id);
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

    public void deleteApplication(Long id) {
        applicationRepository.delete(findApplicationOrThrow(id));
    }

    private Application findApplicationOrThrow(Long id) {
        return applicationRepository.findById(id)
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

        if (application.getStatus() == null) {
            application.setStatus(ApplicationStatus.SAVED);
        }
    }
}
