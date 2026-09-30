package com.jobstar.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ResumeVersionRepository;
import com.jobstar.backend.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ResumeVersionServiceTests {

    @Mock
    private ResumeVersionRepository resumeVersionRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ResumeFileStorage resumeFileStorage;

    @Mock
    private UserAccount owner;

    @InjectMocks
    private ResumeVersionService resumeVersionService;

    @Test
    void uploadsAllowedResumeIntoTheOwnersApplicationFolder() {
        MockMultipartFile file = new MockMultipartFile("file", "software-resume.pdf", "application/pdf", "resume".getBytes());
        when(owner.getId()).thenReturn(7L);
        when(applicationRepository.findAllByResumeVersion(any())).thenReturn(java.util.List.of());
        when(resumeVersionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = resumeVersionService.createUploadedResumeVersion("Software resume", "Tailored for backend", file, owner);

        ArgumentCaptor<String> storageKey = ArgumentCaptor.forClass(String.class);
        verify(resumeFileStorage).upload(storageKey.capture(), any());
        assertEquals("software-resume.pdf", saved.getFileName());
        assertEquals("application/pdf", saved.getContentType());
        assertEquals(file.getSize(), saved.getFileSize());
        org.junit.jupiter.api.Assertions.assertTrue(storageKey.getValue().startsWith("users/7/resumes/"));
    }

    @Test
    void rejectsUnsupportedResumeFileBeforeStorageIsCalled() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.exe", "application/octet-stream", "not a resume".getBytes());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> resumeVersionService.createUploadedResumeVersion("Resume", null, file, owner));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(resumeFileStorage, never()).upload(anyString(), any());
    }
}
