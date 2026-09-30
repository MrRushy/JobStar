package com.jobstar.backend.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.jobstar.backend.model.ResumeApplicationReference;
import com.jobstar.backend.model.ResumeVersion;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ApplicationRepository;
import com.jobstar.backend.repository.ResumeVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResumeVersionService {
    private static final long MAX_RESUME_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private final ResumeVersionRepository resumeVersionRepository;
    private final ApplicationRepository applicationRepository;
    private final ResumeFileStorage resumeFileStorage;

    public ResumeVersionService(ResumeVersionRepository resumeVersionRepository, ApplicationRepository applicationRepository,
            ResumeFileStorage resumeFileStorage) {
        this.resumeVersionRepository = resumeVersionRepository;
        this.applicationRepository = applicationRepository;
        this.resumeFileStorage = resumeFileStorage;
    }

    public List<ResumeVersion> getAllResumeVersions(UserAccount owner) {
        return resumeVersionRepository.findAllByOwnerOrderByIdDesc(owner).stream().map(this::withLinkedApplications).toList();
    }

    public ResumeVersion createResumeVersion(ResumeVersion resumeVersion, UserAccount owner) {
        normalizeAndValidate(resumeVersion);
        resumeVersion.setOwner(owner);
        return withLinkedApplications(resumeVersionRepository.save(resumeVersion));
    }

    public ResumeVersion createUploadedResumeVersion(String label, String notes, MultipartFile file, UserAccount owner) {
        validateUpload(file);
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String storageKey = "users/%d/resumes/%s-%s".formatted(owner.getId(), UUID.randomUUID(), fileName);
        ResumeVersion resumeVersion = new ResumeVersion();
        resumeVersion.setLabel(label);
        resumeVersion.setNotes(notes);
        normalizeAndValidate(resumeVersion);
        resumeFileStorage.upload(storageKey, file);
        resumeVersion.setFileName(fileName);
        resumeVersion.setStorageKey(storageKey);
        resumeVersion.setContentType(contentTypeFor(fileName));
        resumeVersion.setFileSize(file.getSize());
        resumeVersion.setOwner(owner);
        return withLinkedApplications(resumeVersionRepository.save(resumeVersion));
    }

    public ResumeVersion updateResumeVersion(Long resumeVersionId, ResumeVersion updatedResumeVersion, UserAccount owner) {
        ResumeVersion resumeVersion = findResumeVersion(resumeVersionId, owner);
        normalizeAndValidate(updatedResumeVersion);
        resumeVersion.setLabel(updatedResumeVersion.getLabel());
        resumeVersion.setDocumentUrl(updatedResumeVersion.getDocumentUrl());
        resumeVersion.setNotes(updatedResumeVersion.getNotes());
        return withLinkedApplications(resumeVersionRepository.save(resumeVersion));
    }

    @Transactional
    public void deleteResumeVersion(Long resumeVersionId, boolean unlinkApplications, UserAccount owner) {
        ResumeVersion resumeVersion = findResumeVersion(resumeVersionId, owner);
        List<com.jobstar.backend.model.Application> linkedApplications = applicationRepository.findAllByResumeVersion(resumeVersion);
        if (!linkedApplications.isEmpty() && !unlinkApplications) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This resume is still used by an application. Change or clear the selected resume first.");
        }
        if (unlinkApplications) {
            linkedApplications.forEach(application -> application.setResumeVersion(null));
            applicationRepository.saveAll(linkedApplications);
        }
        if (resumeVersion.getStorageKey() != null) resumeFileStorage.delete(resumeVersion.getStorageKey());
        resumeVersionRepository.delete(resumeVersion);
    }

    public ResumeFileStorage.StoredResumeFile downloadResumeFile(Long resumeVersionId, UserAccount owner) {
        ResumeVersion resumeVersion = findResumeVersion(resumeVersionId, owner);
        if (resumeVersion.getStorageKey() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This resume does not have an uploaded file.");
        ResumeFileStorage.StoredResumeFile storedFile = resumeFileStorage.download(resumeVersion.getStorageKey());
        return new ResumeFileStorage.StoredResumeFile(storedFile.content(), storedFile.contentType(), resumeVersion.getFileName());
    }

    private ResumeVersion withLinkedApplications(ResumeVersion resumeVersion) {
        resumeVersion.setLinkedApplications(applicationRepository.findAllByResumeVersion(resumeVersion).stream()
                .map(application -> new ResumeApplicationReference(application.getId(), application.getCompany(), application.getPosition())).toList());
        return resumeVersion;
    }

    private ResumeVersion findResumeVersion(Long resumeVersionId, UserAccount owner) {
        return resumeVersionRepository.findByIdAndOwner(resumeVersionId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume was not found"));
    }

    private void normalizeAndValidate(ResumeVersion resumeVersion) {
        if (resumeVersion.getLabel() == null || resumeVersion.getLabel().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume label is required");
        resumeVersion.setLabel(resumeVersion.getLabel().trim());
        resumeVersion.setDocumentUrl(normalizeOptionalText(resumeVersion.getDocumentUrl()));
        resumeVersion.setNotes(normalizeOptionalText(resumeVersion.getNotes()));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a resume file to upload.");
        if (file.getSize() > MAX_RESUME_FILE_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume files must be 10 MB or smaller.");
        if (!ALLOWED_EXTENSIONS.contains(extensionOf(sanitizeFileName(file.getOriginalFilename())))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF, DOC, or DOCX resume file.");
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded resume needs a file name.");
        String fileName = originalFileName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded resume needs a valid file name.");
        return fileName;
    }

    private String extensionOf(String fileName) {
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart >= 0 ? fileName.substring(extensionStart + 1).toLowerCase() : "";
    }

    private String contentTypeFor(String fileName) {
        return switch (extensionOf(fileName)) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private String normalizeOptionalText(String value) {
        if (value == null) return null;
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
