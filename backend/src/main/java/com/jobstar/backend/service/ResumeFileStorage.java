package com.jobstar.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface ResumeFileStorage {

    void upload(String storageKey, MultipartFile file);

    StoredResumeFile download(String storageKey);

    void delete(String storageKey);

    record StoredResumeFile(byte[] content, String contentType, String fileName) {
    }
}
