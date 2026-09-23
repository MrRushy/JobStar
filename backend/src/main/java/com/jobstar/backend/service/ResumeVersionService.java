package com.jobstar.backend.service;

import java.util.List;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.ResumeVersion;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ResumeVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResumeVersionService {

    private final ApplicationService applicationService;
    private final ResumeVersionRepository resumeVersionRepository;

    public ResumeVersionService(ApplicationService applicationService, ResumeVersionRepository resumeVersionRepository) {
        this.applicationService = applicationService;
        this.resumeVersionRepository = resumeVersionRepository;
    }

    public List<ResumeVersion> getAllResumeVersions(Long applicationId, UserAccount owner) {
        return resumeVersionRepository.findAllByApplicationOrderByIdDesc(findApplication(applicationId, owner));
    }

    public ResumeVersion createResumeVersion(Long applicationId, ResumeVersion resumeVersion, UserAccount owner) {
        normalizeAndValidate(resumeVersion);
        resumeVersion.setApplication(findApplication(applicationId, owner));
        return resumeVersionRepository.save(resumeVersion);
    }

    public ResumeVersion updateResumeVersion(Long applicationId, Long resumeVersionId, ResumeVersion updatedResumeVersion,
            UserAccount owner) {
        ResumeVersion resumeVersion = findResumeVersion(resumeVersionId, findApplication(applicationId, owner));
        normalizeAndValidate(updatedResumeVersion);

        resumeVersion.setLabel(updatedResumeVersion.getLabel());
        resumeVersion.setDocumentUrl(updatedResumeVersion.getDocumentUrl());
        resumeVersion.setNotes(updatedResumeVersion.getNotes());
        return resumeVersionRepository.save(resumeVersion);
    }

    public void deleteResumeVersion(Long applicationId, Long resumeVersionId, UserAccount owner) {
        resumeVersionRepository.delete(findResumeVersion(resumeVersionId, findApplication(applicationId, owner)));
    }

    private Application findApplication(Long applicationId, UserAccount owner) {
        return applicationService.getApplicationById(applicationId, owner);
    }

    private ResumeVersion findResumeVersion(Long resumeVersionId, Application application) {
        return resumeVersionRepository.findByIdAndApplication(resumeVersionId, application)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version was not found"));
    }

    private void normalizeAndValidate(ResumeVersion resumeVersion) {
        if (resumeVersion.getLabel() == null || resumeVersion.getLabel().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume version label is required");
        }

        resumeVersion.setLabel(resumeVersion.getLabel().trim());
        resumeVersion.setDocumentUrl(normalizeOptionalText(resumeVersion.getDocumentUrl()));
        resumeVersion.setNotes(normalizeOptionalText(resumeVersion.getNotes()));
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
